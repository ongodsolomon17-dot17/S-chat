package com.stech.schat.service;

import com.stech.schat.model.AccountRecoveryRequest;
import com.stech.schat.model.RecoveryChannel;
import com.stech.schat.model.User;
import com.stech.schat.repository.AccountRecoveryRequestRepository;
import com.stech.schat.repository.UserRepository;
import com.stech.schat.service.recovery.RecoverySender;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Password recovery by one-time code over email, SMS or WhatsApp.
 *
 * Security properties: the request endpoint answers identically whether or not an account exists (no user
 * enumeration, delivery happens asynchronously after commit so timing does not leak either); only a keyed hash of
 * the code is stored; codes expire, allow {@value #MAX_ATTEMPTS} wrong guesses, and are single-use; a successful
 * reset revokes every session and clears any login lockout.
 */
@Service
public class RecoveryService {

    private static final Logger log = LoggerFactory.getLogger(RecoveryService.class);
    private static final int MAX_ATTEMPTS = 5;
    private static final int MAX_REQUESTS_PER_HOUR = 5;
    private static final Duration COOLDOWN = Duration.ofSeconds(60);
    public static final String BAD_CODE = "That code is incorrect or has expired. Request a new one and try again.";

    private final UserRepository userRepository;
    private final AccountRecoveryRequestRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final Map<RecoveryChannel, RecoverySender> senders = new EnumMap<>(RecoveryChannel.class);
    private final byte[] hmacKey;
    private final long ttlMinutes;
    private final SecureRandom random = new SecureRandom();
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(2, 2, 30, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(100), r -> { Thread t = new Thread(r, "recovery-sender"); t.setDaemon(true); return t; },
            new ThreadPoolExecutor.DiscardPolicy());

    public RecoveryService(UserRepository userRepository, AccountRecoveryRequestRepository repository,
                           PasswordEncoder passwordEncoder, List<RecoverySender> allSenders,
                           @Value("${app.jwt.secret}") String jwtSecret,
                           @Value("${app.recovery.code-ttl-minutes:10}") long ttlMinutes) {
        this.userRepository = userRepository;
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        allSenders.forEach(s -> senders.put(s.channel(), s));
        this.hmacKey = ("recovery:" + jwtSecret).getBytes(StandardCharsets.UTF_8);
        this.ttlMinutes = ttlMinutes;
        allSenders.forEach(s -> log.info("Account recovery via {}: {}", s.channel(), s.isConfigured() ? "ENABLED" : "disabled (not configured)"));
    }

    /** Channels that have credentials configured on this server. */
    public List<RecoveryChannel> availableChannels() {
        return Arrays.stream(RecoveryChannel.values()).filter(c -> senders.containsKey(c) && senders.get(c).isConfigured()).toList();
    }

    @Transactional
    public void request(String identifier, RecoveryChannel channel) {
        RecoverySender sender = senders.get(channel);
        if (sender == null || !sender.isConfigured()) {
            throw new IllegalArgumentException("That recovery method isn't available right now. Choose another one.");
        }
        Optional<User> found = findUser(identifier);
        if (found.isEmpty()) return; // identical response for unknown accounts
        User user = found.get();
        String destination = destinationFor(user, channel);
        if (destination == null) return; // account has no usable contact for this channel; same response

        Instant now = Instant.now();
        Optional<AccountRecoveryRequest> last = repository.findFirstByUserIdOrderByCreatedAtDesc(user.getId());
        if (last.isPresent() && last.get().getCreatedAt().isAfter(now.minus(COOLDOWN))) return;
        if (repository.countByUserIdAndCreatedAtAfter(user.getId(), now.minus(Duration.ofHours(1))) >= MAX_REQUESTS_PER_HOUR) {
            log.warn("Recovery request limit reached for a user; ignoring");
            return;
        }
        repository.consumeAllActive(user.getId(), now); // a new code invalidates older ones
        String code = String.format("%06d", random.nextInt(1_000_000));
        repository.save(AccountRecoveryRequest.builder()
                .userId(user.getId()).channel(channel).codeHash(hash(user, code))
                .expiresAt(now.plus(Duration.ofMinutes(ttlMinutes))).build());

        // Send only after the transaction committed, off the request thread (uniform response time).
        Runnable deliver = () -> executor.execute(() -> {
            try {
                sender.send(destination, code);
                log.info("Recovery code sent via {} to {}", channel, mask(destination));
            } catch (Exception e) {
                log.error("Could not send recovery code via {} to {}: {}", channel, mask(destination), e.toString());
            }
        });
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { deliver.run(); }
            });
        } else {
            deliver.run();
        }
    }

    /** Wrong-code attempts must be saved even though we throw, hence noRollbackFor. */
    @Transactional(noRollbackFor = IllegalArgumentException.class)
    public void reset(String identifier, String code, String newPassword) {
        User user = findUser(identifier).orElseThrow(() -> new IllegalArgumentException(BAD_CODE));
        AccountRecoveryRequest req = repository.findFirstByUserIdAndConsumedAtIsNullOrderByCreatedAtDesc(user.getId())
                .orElseThrow(() -> new IllegalArgumentException(BAD_CODE));
        Instant now = Instant.now();
        if (req.getExpiresAt().isBefore(now) || req.getAttempts() >= MAX_ATTEMPTS) {
            throw new IllegalArgumentException(BAD_CODE);
        }
        req.setAttempts(req.getAttempts() + 1);
        boolean ok = MessageDigest.isEqual(hash(user, code).getBytes(StandardCharsets.UTF_8), req.getCodeHash().getBytes(StandardCharsets.UTF_8));
        if (!ok) {
            repository.save(req);
            throw new IllegalArgumentException(BAD_CODE);
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setRefreshTokenVersion(user.getRefreshTokenVersion() + 1); // sign out everywhere
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        userRepository.save(user);
        repository.consumeAllActive(user.getId(), now);
    }

    @Scheduled(cron = "0 30 3 * * *")
    @Transactional
    public void purgeOld() {
        int n = repository.deleteExpiredBefore(Instant.now().minus(Duration.ofDays(2)));
        if (n > 0) log.info("Purged {} old recovery requests", n);
    }

    @PreDestroy
    void shutdown() { executor.shutdownNow(); }

    // ---- helpers ----
    private Optional<User> findUser(String identifier) {
        String id = identifier == null ? "" : identifier.trim();
        if (id.isEmpty()) return Optional.empty();
        return userRepository.findByUsernameIgnoreCaseOrEmailIgnoreCase(id, id).filter(u -> !u.isDeleted() && u.isAccountEnabled());
    }

    private String destinationFor(User user, RecoveryChannel channel) {
        if (channel == RecoveryChannel.EMAIL) return user.getEmail();
        return normalizePhone(user.getPhoneNumber());
    }

    /** Returns E.164 (+countrycode digits) or null if the stored number is not in international format. */
    static String normalizePhone(String raw) {
        if (raw == null) return null;
        String s = raw.replaceAll("[\\s\\-().]", "");
        return s.matches("^\\+[1-9]\\d{6,14}$") ? s : null;
    }

    private String hash(User user, String code) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(hmacKey, "HmacSHA256"));
            byte[] out = mac.doFinal((user.getId() + ":" + code).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : out) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Could not process the recovery code", e);
        }
    }

    private static String mask(String dest) {
        if (dest == null || dest.length() < 5) return "***";
        int at = dest.indexOf('@');
        if (at > 1) return dest.charAt(0) + "***" + dest.substring(at);
        return dest.substring(0, 3) + "***" + dest.substring(dest.length() - 2);
    }
}
