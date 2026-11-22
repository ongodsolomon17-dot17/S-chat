package com.stech.schat.service.recovery;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/** Minimal Twilio Programmable Messaging client (SMS and WhatsApp share the same endpoint). */
@Component
public class TwilioClient {

    private final String sid;
    private final String token;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public TwilioClient(@Value("${app.twilio.account-sid:}") String sid,
                        @Value("${app.twilio.auth-token:}") String token) {
        this.sid = sid == null ? "" : sid.trim();
        this.token = token == null ? "" : token.trim();
    }

    public boolean hasCredentials() { return !sid.isBlank() && !token.isBlank(); }

    public void sendMessage(Map<String, String> form) throws Exception {
        String body = new LinkedHashMap<>(form).entrySet().stream()
                .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
        String basic = Base64.getEncoder().encodeToString((sid + ":" + token).getBytes(StandardCharsets.UTF_8));
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("https://api.twilio.com/2010-04-01/Accounts/" + sid + "/Messages.json"))
                .timeout(Duration.ofSeconds(20))
                .header("Authorization", "Basic " + basic).header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() < 200 || res.statusCode() >= 300) {
            String b = res.body() == null ? "" : res.body();
            throw new IllegalStateException("Twilio returned HTTP " + res.statusCode() + ": " + (b.length() > 300 ? b.substring(0, 300) : b));
        }
    }
}
