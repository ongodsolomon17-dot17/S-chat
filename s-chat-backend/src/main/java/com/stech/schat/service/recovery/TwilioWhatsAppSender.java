package com.stech.schat.service.recovery;

import com.stech.schat.model.RecoveryChannel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * WhatsApp through Twilio. Needs TWILIO_ACCOUNT_SID, TWILIO_AUTH_TOKEN and TWILIO_WHATSAPP_FROM
 * (the Twilio sandbox number for testing, or your approved WhatsApp sender in production).
 * For production, business-initiated WhatsApp messages must use an approved template: set
 * TWILIO_WHATSAPP_CONTENT_SID (a Content Template with one variable, {{1}} = the code).
 */
@Component
public class TwilioWhatsAppSender implements RecoverySender {

    private final TwilioClient twilio;
    private final String from;
    private final String contentSid;
    private final String appName;
    private final int ttlMinutes;

    public TwilioWhatsAppSender(TwilioClient twilio,
                                @Value("${app.twilio.whatsapp-from:}") String from,
                                @Value("${app.twilio.whatsapp-content-sid:}") String contentSid,
                                @Value("${app.name:S-Chat}") String appName,
                                @Value("${app.recovery.code-ttl-minutes:10}") int ttlMinutes) {
        this.twilio = twilio;
        this.from = from == null ? "" : from.trim();
        this.contentSid = contentSid == null ? "" : contentSid.trim();
        this.appName = appName;
        this.ttlMinutes = ttlMinutes;
    }

    @Override public RecoveryChannel channel() { return RecoveryChannel.WHATSAPP; }

    @Override public boolean isConfigured() { return twilio.hasCredentials() && !from.isBlank(); }

    private static String wa(String number) { return number.startsWith("whatsapp:") ? number : "whatsapp:" + number; }

    @Override
    public void send(String destination, String code) throws Exception {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("To", wa(destination));
        form.put("From", wa(from));
        if (!contentSid.isBlank()) {
            form.put("ContentSid", contentSid);
            form.put("ContentVariables", "{\"1\":\"" + code + "\"}");
        } else {
            form.put("Body", appName + " code: " + code + ". It expires in " + ttlMinutes + " minutes. Do not share it with anyone.");
        }
        twilio.sendMessage(form);
    }
}
