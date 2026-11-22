package com.stech.schat.controller;

import com.stech.schat.dto.LegalVersionsDto;
import com.stech.schat.dto.PublicProfileDto;
import com.stech.schat.dto.UserLookupDto;
import com.stech.schat.exception.RateLimitExceededException;
import com.stech.schat.exception.ResourceNotFoundException;
import com.stech.schat.model.User;
import com.stech.schat.repository.UserRepository;
import com.stech.schat.security.PublicLookupRateLimiter;
import com.stech.schat.service.FriendService;
import com.stech.schat.service.LegalService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

import static com.stech.schat.controller.UserController.currentUserId;

/**
 * Backs the shareable chat link https://your-domain/chat/{username}.
 *  - GET /api/public/users/{username}   anonymous: minimal public profile
 *  - GET /api/users/by-username/{u}     signed in: profile + relationship (so the UI can offer "Message")
 *  - GET /api/public/legal              current terms/privacy versions (for the signup popup)
 */
@RestController
@RequestMapping("/api")
public class ProfileLinkController {

    private static final String UNAVAILABLE = "This profile is not available";

    private final UserRepository userRepository;
    private final FriendService friendService;
    private final LegalService legalService;
    private final PublicLookupRateLimiter rateLimiter;

    public ProfileLinkController(UserRepository userRepository, FriendService friendService,
                                 LegalService legalService, PublicLookupRateLimiter rateLimiter) {
        this.userRepository = userRepository;
        this.friendService = friendService;
        this.legalService = legalService;
        this.rateLimiter = rateLimiter;
    }

    @GetMapping("/public/legal")
    public ResponseEntity<LegalVersionsDto> legal() {
        return ResponseEntity.ok(legalService.currentVersions());
    }

    @GetMapping("/public/users/{username}")
    public ResponseEntity<PublicProfileDto> publicProfile(@PathVariable String username, HttpServletRequest request) {
        if (!rateLimiter.tryConsume(request.getRemoteAddr())) {
            throw new RateLimitExceededException("Too many requests. Please try again in a minute.");
        }
        User user = findAvailable(username);
        if (!user.isChatLinkEnabled()) throw new ResourceNotFoundException(UNAVAILABLE);
        return ResponseEntity.ok(new PublicProfileDto(
                user.getUsername(), user.getProfilePictureUrl(), user.getBio(), user.getCreatedAt()));
    }

    @GetMapping("/users/by-username/{username}")
    public ResponseEntity<UserLookupDto> lookup(Authentication auth, @PathVariable String username) {
        UUID viewerId = currentUserId(auth);
        User target = findAvailable(username);

        if (target.getId().equals(viewerId)) {
            return ResponseEntity.ok(new UserLookupDto(target.getId(), target.getUsername(), target.getPublicId(),
                    target.getProfilePictureUrl(), target.getBio(), "SELF", null));
        }
        // A blocked pair sees "not available" in both directions, so a block is not revealed.
        if (friendService.isBlockedEitherWay(viewerId, target.getId())) {
            throw new ResourceNotFoundException(UNAVAILABLE);
        }
        FriendService.RelationshipInfo rel = friendService.relationship(viewerId, target.getId());
        if (!target.isChatLinkEnabled() && !"FRIENDS".equals(rel.status())) {
            throw new ResourceNotFoundException(UNAVAILABLE);
        }
        return ResponseEntity.ok(new UserLookupDto(target.getId(), target.getUsername(), target.getPublicId(),
                target.getProfilePictureUrl(), target.getBio(), rel.status(), rel.requestId()));
    }

    private User findAvailable(String username) {
        if (username == null || username.length() > 32 || !username.matches("^[a-zA-Z0-9_.]+$")) {
            throw new ResourceNotFoundException(UNAVAILABLE);
        }
        return userRepository.findByUsernameIgnoreCase(username)
                .filter(u -> !u.isDeleted() && u.isAccountEnabled())
                .orElseThrow(() -> new ResourceNotFoundException(UNAVAILABLE));
    }
}
