package com.stech.schat.service.recovery;

import com.stech.schat.model.RecoveryChannel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/** SMS through Twilio. Needs TWILIO_ACCOUNT_SID, TWILIO_AUTH_TOKEN and TWILIO_SMS_FROM (or TWILIO_MESSAGING_SERVICE_SID). */
@Component
public class TwilioSmsSender implements RecoverySender {

    private final TwilioClient twilio;
    private final String from;
    private final String messagingServiceSid;
    private final String appName;
    private final int ttlMinutes;

    public TwilioSmsSender(TwilioClient twilio,
                           @Value("${app.twilio.sms-from:}") String from,
                           @Value("${app.twilio.messaging-service-sid:}") String messagingServiceSid,
                           @Value("${app.name:S-Chat}") String appName,
                           @Value("${app.recovery.code-ttl-minutes:10}") int ttlMinutes) {
        this.twilio = twilio;
        this.from = from == null ? "" : from.trim();
        this.messagingServiceSid = messagingServiceSid == null ? "" : messagingServiceSid.trim();
        this.appName = appName;
        this.ttlMinutes = ttlMinutes;
    }

    @Override public RecoveryChannel channel() { return RecoveryChannel.SMS; }

    @Override public boolean isConfigured() { return twilio.hasCredentials() && (!from.isBlank() || !messagingServiceSid.isBlank()); }

    @Override
    public void send(String destination, String code) throws Exception {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("To", destination);
        if (!messagingServiceSid.isBlank()) form.put("MessagingServiceSid", messagingServiceSid); else form.put("From", from);
        form.put("Body", appName + " code: " + code + ". It expires in " + ttlMinutes + " minutes. Do not share it with anyone.");
        twilio.sendMessage(form);
    }
}
