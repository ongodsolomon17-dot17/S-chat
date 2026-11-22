package com.stech.schat.service;

import com.stech.schat.dto.LegalVersionsDto;
import com.stech.schat.model.TermsAcceptance;
import com.stech.schat.model.User;
import com.stech.schat.repository.TermsAcceptanceRepository;
import com.stech.schat.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/** Current legal-document versions and the audit trail of who accepted which. */
@Service
public class LegalService {

    private final String termsVersion;
    private final String privacyVersion;
    private final UserRepository userRepository;
    private final TermsAcceptanceRepository acceptanceRepository;

    public LegalService(@Value("${app.legal.terms-version}") String termsVersion,
                        @Value("${app.legal.privacy-version}") String privacyVersion,
                        UserRepository userRepository,
                        TermsAcceptanceRepository acceptanceRepository) {
        this.termsVersion = termsVersion;
        this.privacyVersion = privacyVersion;
        this.userRepository = userRepository;
        this.acceptanceRepository = acceptanceRepository;
    }

    public LegalVersionsDto currentVersions() {
        return new LegalVersionsDto(termsVersion, privacyVersion);
    }

    /** The client must have shown the user the CURRENT version when they ticked the box. */
    public void requireCurrentTerms(String submittedTermsVersion) {
        if (submittedTermsVersion == null || !termsVersion.equals(submittedTermsVersion)) {
            throw new IllegalArgumentException(
                    "The Terms and Conditions were updated. Please reload the page and accept the latest version.");
        }
    }

    @Transactional
    public void recordAcceptance(User user) {
        Instant now = Instant.now();
        user.setTermsVersion(termsVersion);
        user.setTermsAcceptedAt(now);
        user.setPrivacyVersion(privacyVersion);
        user.setPrivacyAcceptedAt(now);
        userRepository.save(user);
        acceptanceRepository.save(TermsAcceptance.builder()
                .userId(user.getId())
                .termsVersion(termsVersion)
                .privacyVersion(privacyVersion)
                .build());
    }
}
