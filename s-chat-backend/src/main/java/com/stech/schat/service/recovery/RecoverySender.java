package com.stech.schat.service.recovery;

import com.stech.schat.model.RecoveryChannel;

/** One delivery channel for recovery codes. A channel is offered to users only if {@link #isConfigured()}. */
public interface RecoverySender {
    RecoveryChannel channel();

    boolean isConfigured();

    /** Sends the code. Must never log the code itself. Throws on provider failure. */
    void send(String destination, String code) throws Exception;
}
