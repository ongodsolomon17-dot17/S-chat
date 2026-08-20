package com.stech.schat.controller;

import com.stech.schat.dto.CallRecordDto;
import com.stech.schat.service.CallService;
import com.stech.schat.service.TurnCredentialService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import static com.stech.schat.controller.UserController.currentUserId;

@RestController
@RequestMapping("/api/calls")
public class CallController {
    private final CallService callService;
    private final TurnCredentialService turnCredentialService;
    public CallController(CallService callService, TurnCredentialService turnCredentialService) {
        this.callService = callService;
        this.turnCredentialService = turnCredentialService;
    }

    @GetMapping("/ice-servers")
    public ResponseEntity<java.util.Map<String, Object>> iceServers(Authentication auth) {
        return ResponseEntity.ok(turnCredentialService.issue(currentUserId(auth)));
    }

    @GetMapping("/history")
    public ResponseEntity<List<CallRecordDto>> history(
            Authentication auth, @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(callService.history(currentUserId(auth), size));
    }
}
