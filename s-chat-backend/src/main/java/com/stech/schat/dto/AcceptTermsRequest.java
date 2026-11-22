package com.stech.schat.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AcceptTermsRequest(
        @NotNull @AssertTrue(message = "You must accept the Terms and Conditions and Privacy Policy") Boolean accepted,
        @NotBlank String termsVersion
) {}
