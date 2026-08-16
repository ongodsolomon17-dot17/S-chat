package com.stech.schat.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.stech.schat.dto.AiChatMessage;
import com.stech.schat.dto.AiChatRequest;
import com.stech.schat.dto.AiChatResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

@Service
public class GeminiService {

    private static final Logger log = LoggerFactory.getLogger(GeminiService.class);

    private static final int MAX_MESSAGES = 24;
    private static final int MAX_MESSAGE_CHARS = 4000;
    private static final int MAX_TOTAL_CHARS = 16000;

    private static final String SYSTEM_INSTRUCTION = """
            You are Gemini, the built-in AI assistant inside the S-Chat application by S-TECH.
            Your display name is Gemini. If the user asks your name, answer Gemini.
            If the user asks who made or owns this app, say S-TECH.
            Behave like a helpful, friendly general-purpose assistant.
            Keep normal answers reasonably concise unless the user asks for detail.
            Never claim to have performed an action in S-Chat that you cannot actually perform.
            """;

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String apiKey;
    private final String model;

    public GeminiService(
            ObjectMapper objectMapper,
            @Value("${app.gemini.api-key:}") String apiKey,
            @Value("${app.gemini.model:gemini-3.5-flash}") String model
    ) {
        this.objectMapper = objectMapper;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = normalizeModel(model);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    private String normalizeModel(String configuredModel) {
        if (configuredModel == null || configuredModel.isBlank()) {
            return "gemini-3.5-flash";
        }

        String normalized = configuredModel.trim();

        // Prevent an old Render environment variable from selecting the
        // Gemini 2.5 Flash model that is unavailable to new users.
        if ("gemini-2.5-flash".equals(normalized)
                || "models/gemini-2.5-flash".equals(normalized)) {
            log.warn("Configured Gemini model {} is unavailable for new users. Falling back to gemini-3.5-flash.", normalized);
            return "gemini-3.5-flash";
        }

        if (normalized.startsWith("models/")) {
            normalized = normalized.substring("models/".length());
        }

        return normalized;
    }

    public AiChatResponse generate(AiChatRequest request) {
        if (apiKey.isBlank()) {
            throw new IllegalStateException("AI assistant is not configured on the server");
        }

        List<AiChatMessage> messages = validateAndLimit(request);

        try {
            ObjectNode root = objectMapper.createObjectNode();

            ObjectNode systemInstruction = root.putObject("systemInstruction");
            ArrayNode systemParts = systemInstruction.putArray("parts");
            systemParts.addObject().put("text", SYSTEM_INSTRUCTION);

            ArrayNode contents = root.putArray("contents");
            for (AiChatMessage message : messages) {
                ObjectNode content = contents.addObject();
                content.put("role", message.role());
                content.putArray("parts").addObject().put("text", message.content());
            }

            ObjectNode generationConfig = root.putObject("generationConfig");
            generationConfig.put("maxOutputTokens", 2048);

            String requestBody = objectMapper.writeValueAsString(root);
            String endpoint = "https://generativelanguage.googleapis.com/v1beta/models/"
                    + model + ":generateContent";

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(45))
                    .header("Content-Type", "application/json")
                    .header("x-goog-api-key", apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(
                    httpRequest,
                    HttpResponse.BodyHandlers.ofString()
            );

            JsonNode body = objectMapper.readTree(response.body());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                String providerMessage = body.path("error").path("message").asText("");
                log.warn("Gemini API returned HTTP {}: {}", response.statusCode(), providerMessage);
                throw new IllegalStateException("The AI assistant is temporarily unavailable. Please try again.");
            }

            String text = extractText(body);
            if (text.isBlank()) {
                log.warn("Gemini returned no text candidate: {}", response.body());
                throw new IllegalStateException("The AI assistant returned an empty response. Please try again.");
            }

            return new AiChatResponse(text.trim());
        } catch (IllegalStateException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("The AI request was interrupted. Please try again.");
        } catch (Exception e) {
            log.error("Gemini request failed", e);
            throw new IllegalStateException("The AI assistant is temporarily unavailable. Please try again.");
        }
    }

    private List<AiChatMessage> validateAndLimit(AiChatRequest request) {
        if (request == null || request.messages() == null || request.messages().isEmpty()) {
            throw new IllegalArgumentException("At least one message is required");
        }

        List<AiChatMessage> messages = request.messages();
        int start = Math.max(0, messages.size() - MAX_MESSAGES);
        List<AiChatMessage> limited = messages.subList(start, messages.size());

        int totalChars = 0;
        for (AiChatMessage message : limited) {
            if (message == null || message.content() == null || message.content().isBlank()) {
                throw new IllegalArgumentException("AI messages cannot be empty");
            }
            if (!"user".equals(message.role()) && !"model".equals(message.role())) {
                throw new IllegalArgumentException("Invalid AI message role");
            }
            if (message.content().length() > MAX_MESSAGE_CHARS) {
                throw new IllegalArgumentException("AI message is too long");
            }
            totalChars += message.content().length();
            if (totalChars > MAX_TOTAL_CHARS) {
                throw new IllegalArgumentException("AI conversation is too long");
            }
        }

        if (!"user".equals(limited.get(limited.size() - 1).role())) {
            throw new IllegalArgumentException("The latest AI message must be from the user");
        }

        return limited;
    }

    private String extractText(JsonNode body) {
        JsonNode candidates = body.path("candidates");
        if (!candidates.isArray() || candidates.isEmpty()) return "";

        JsonNode parts = candidates.get(0).path("content").path("parts");
        if (!parts.isArray()) return "";

        StringBuilder result = new StringBuilder();
        for (JsonNode part : parts) {
            String text = part.path("text").asText("");
            if (!text.isBlank()) {
                if (!result.isEmpty()) result.append('\n');
                result.append(text);
            }
        }
        return result.toString();
    }
}