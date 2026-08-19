package com.stech.schat.controller;

import com.stech.schat.dto.CallRecordDto;
import com.stech.schat.service.CallService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import static com.stech.schat.controller.UserController.currentUserId;

@RestController
@RequestMapping("/api/calls")
public class CallController {
    private final CallService callService;
    public CallController(CallService callService) { this.callService = callService; }

    @GetMapping("/history")
    public ResponseEntity<List<CallRecordDto>> history(
            Authentication auth, @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(callService.history(currentUserId(auth), size));
    }
}
