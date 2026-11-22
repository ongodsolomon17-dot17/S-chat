package com.stech.schat.controller;

import com.stech.schat.dto.AcceptTermsRequest;
import com.stech.schat.dto.ChangePasswordRequest;
import com.stech.schat.exception.ResourceNotFoundException;
import com.stech.schat.model.User;
import com.stech.schat.repository.UserRepository;
import com.stech.schat.service.AuthService;
import com.stech.schat.service.LegalService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.stech.schat.controller.UserController.currentUserId;

@RestController
@RequestMapping("/api/account")
public class AccountController {

    private final AuthService authService;
    private final LegalService legalService;
    private final UserRepository userRepository;

    public AccountController(AuthService authService, LegalService legalService, UserRepository userRepository) {
        this.authService = authService;
        this.legalService = legalService;
        this.userRepository = userRepository;
    }

    @PostMapping("/password")
    public ResponseEntity<Void> changePassword(Authentication auth, @Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(currentUserId(auth), request.currentPassword(), request.newPassword());
        return ResponseEntity.noContent().build();
    }

    /** For existing users when the Terms/Privacy versions change. */
    @PostMapping("/accept-terms")
    public ResponseEntity<Void> acceptTerms(Authentication auth, @Valid @RequestBody AcceptTermsRequest request) {
        legalService.requireCurrentTerms(request.termsVersion());
        User user = userRepository.findById(currentUserId(auth))
                .filter(u -> !u.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        legalService.recordAcceptance(user);
        return ResponseEntity.noContent().build();
    }
}
