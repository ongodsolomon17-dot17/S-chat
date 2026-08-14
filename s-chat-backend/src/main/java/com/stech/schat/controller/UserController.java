package com.stech.schat.controller;

import com.stech.schat.dto.ProfileDto;
import com.stech.schat.dto.ProfileUpdateRequest;
import com.stech.schat.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public ResponseEntity<ProfileDto> getMyProfile(Authentication auth) {
        return ResponseEntity.ok(userService.getProfile(currentUserId(auth)));
    }

    @PatchMapping("/me")
    public ResponseEntity<ProfileDto> updateMyProfile(Authentication auth, @Valid @RequestBody ProfileUpdateRequest request) {
        return ResponseEntity.ok(userService.updateProfile(currentUserId(auth), request));
    }

    @PostMapping(value = "/me/profile-picture", consumes = "multipart/form-data")
    public ResponseEntity<ProfileDto> uploadProfilePicture(Authentication auth, @RequestParam("file") MultipartFile file) throws Exception {
        return ResponseEntity.ok(userService.updateProfilePicture(currentUserId(auth), file));
    }

    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteMyAccount(Authentication auth) {
        userService.softDeleteAccount(currentUserId(auth));
        return ResponseEntity.noContent().build();
    }

    static UUID currentUserId(Authentication auth) {
        return UUID.fromString((String) auth.getPrincipal());
    }
}
