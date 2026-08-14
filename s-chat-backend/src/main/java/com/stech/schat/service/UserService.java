package com.stech.schat.service;

import com.stech.schat.dto.ProfileDto;
import com.stech.schat.dto.ProfileUpdateRequest;
import com.stech.schat.exception.DuplicateUserException;
import com.stech.schat.exception.ResourceNotFoundException;
import com.stech.schat.model.User;
import com.stech.schat.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.UUID;

@Service
public class UserService {

    private static final String ID_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // no 0/O/1/I ambiguity
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final StorageService storageService;

    public UserService(UserRepository userRepository, StorageService storageService) {
        this.userRepository = userRepository;
        this.storageService = storageService;
    }

    /** Generates a unique default public ID like "SC-7F3K9QZP", retrying on the rare collision. */
    public String generateUniquePublicId() {
        String candidate;
        do {
            StringBuilder sb = new StringBuilder("SC-");
            for (int i = 0; i < 8; i++) {
                sb.append(ID_ALPHABET.charAt(RANDOM.nextInt(ID_ALPHABET.length())));
            }
            candidate = sb.toString();
        } while (userRepository.existsByPublicId(candidate));
        return candidate;
    }

    public ProfileDto getProfile(UUID userId) {
        return toProfileDto(getActiveUserOrThrow(userId));
    }

    @Transactional
    public ProfileDto updateProfile(UUID userId, ProfileUpdateRequest request) {
        User user = getActiveUserOrThrow(userId);

        if (request.publicId() != null && !request.publicId().equalsIgnoreCase(user.getPublicId())) {
            if (userRepository.existsByPublicId(request.publicId())) {
                throw new DuplicateUserException("That S-Chat ID is already taken");
            }
            user.setPublicId(request.publicId());
        }
        if (request.phoneNumber() != null) {
            user.setPhoneNumber(request.phoneNumber().isBlank() ? null : request.phoneNumber());
        }
        if (request.addByIdOnly() != null) {
            user.setAddByIdOnly(request.addByIdOnly());
        }
        if (request.approvalRequired() != null) {
            user.setApprovalRequired(request.approvalRequired());
        }

        userRepository.save(user);
        return toProfileDto(user);
    }

    @Transactional
    public ProfileDto updateProfilePicture(UUID userId, MultipartFile file) throws Exception {
        User user = getActiveUserOrThrow(userId);
        String url = storageService.upload("profile-pictures", file);
        user.setProfilePictureUrl(url);
        userRepository.save(user);
        return toProfileDto(user);
    }

    @Transactional
    public void softDeleteAccount(UUID userId) {
        User user = getActiveUserOrThrow(userId);
        // Deliberately does NOT touch chat_messages, friend_requests, or status_posts —
        // those rows keep referencing this user's id so history is preserved for the
        // other party. Only this User row is flagged, which blocks login and hides the
        // account from search/add going forward.
        user.setDeleted(true);
        user.setAccountEnabled(false);
        user.setDeletedAt(Instant.now());
        userRepository.save(user);
    }

    private User getActiveUserOrThrow(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (user.isDeleted()) {
            throw new ResourceNotFoundException("User not found");
        }
        return user;
    }

    private ProfileDto toProfileDto(User user) {
        return new ProfileDto(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getPhoneNumber(),
                user.getPublicId(),
                user.getProfilePictureUrl(),
                user.isAddByIdOnly(),
                user.isApprovalRequired(),
                user.getRole().name()
        );
    }
}
