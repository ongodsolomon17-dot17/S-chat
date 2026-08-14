package com.stech.schat.dto;

import jakarta.validation.constraints.NotBlank;

public record SendFriendRequestRequest(

        @NotBlank(message = "Enter an ID, phone number, or email to add")
        String identifier,

        // true = search by S-Chat ID only. false = search by phone/email (the default).
        boolean viaId
) {}
