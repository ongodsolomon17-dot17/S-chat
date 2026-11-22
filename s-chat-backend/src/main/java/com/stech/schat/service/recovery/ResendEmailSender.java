package com.stech.schat.service.recovery;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.stech.schat.model.RecoveryChannel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** Email through Resend (https://resend.com). Needs RESEND_API_KEY + RESEND_FROM_ADDRESS. */
@Component
public class ResendEmailSender implements RecoverySender {

    private final ObjectMapper mapper;
    private final String apiKey;
    private final String from;
    private final String appName;
    private final int ttlMinutes;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public ResendEmailSender(ObjectMapper mapper,
                             @Value("${app.resend.api-key:}") String apiKey,
                             @Value("${app.resend.from-address:}") String from,
                             @Value("${app.name:S-Chat}") String appName,
                             @Value("${app.recovery.code-ttl-minutes:10}") int ttlMinutes) {
        this.mapper = mapper;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.from = from == null ? "" : from.trim();
        this.appName = appName;
        this.ttlMinutes = ttlMinutes;
    }

    @Override public RecoveryChannel channel() { return RecoveryChannel.EMAIL; }

    @Override public boolean isConfigured() { return !apiKey.isBlank() && !from.isBlank(); }

    @Override
    public void send(String destination, String code) throws Exception {
        String text = "Your " + appName + " password reset code is " + code + ".\n\n"
                + "It expires in " + ttlMinutes + " minutes. If you did not ask for this, you can ignore this email — your password has not changed.";
        String htmlBody = "<div style=\"font-family:Arial,sans-serif;max-width:480px;margin:auto\">"
                + "<h2>" + appName + " password reset</h2><p>Use this code to reset your password:</p>"
                + "<p style=\"font-size:32px;letter-spacing:6px;font-weight:bold\">" + code + "</p>"
                + "<p>It expires in " + ttlMinutes + " minutes.</p>"
                + "<p style=\"color:#666\">If you did not ask for this, you can ignore this email — your password has not changed.</p></div>";
        ObjectNode body = mapper.createObjectNode();
        body.put("from", from);
        body.putArray("to").add(destination);
        body.put("subject", "Your " + appName + " password reset code");
        body.put("html", htmlBody);
        body.put("text", text);
        HttpRequest req = HttpRequest.newBuilder().uri(URI.create("https://api.resend.com/emails")).timeout(Duration.ofSeconds(20))
                .header("Authorization", "Bearer " + apiKey).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))).build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() < 200 || res.statusCode() >= 300) {
            throw new IllegalStateException("Resend returned HTTP " + res.statusCode() + ": " + truncate(res.body()));
        }
    }

    private static String truncate(String s) { return s == null ? "" : s.length() > 300 ? s.substring(0, 300) : s; }
}
