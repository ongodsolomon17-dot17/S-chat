package com.stech.schat.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.stech.schat.dto.AiChatMessage;
import com.stech.schat.dto.AiChatRequest;
import com.stech.schat.dto.AiChatResponse;
import com.stech.schat.model.AiChatMessageEntity;
import com.stech.schat.model.AiConversation;
import com.stech.schat.repository.AiChatMessageRepository;
import com.stech.schat.repository.AiConversationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

@Service
public class GeminiService {
    private static final Logger log = LoggerFactory.getLogger(GeminiService.class);
    private static final int MAX_MESSAGE_CHARS = 4000;
    private static final int MAX_TOTAL_CHARS = 16000;

    // Keep the complete instruction set here. If you have further STech AI rules, keep them in this block.
    private static final String SYSTEM_INSTRUCTION = """
            You are STech AI, the official AI assistant inside S-Chat, developed by S-TECH Technologies.
            You should identify yourself as STech AI when asked who you are.
            You are helpful, intelligent, professional and conversational.
            Maintain continuity with the conversation history supplied by the application.
            Never claim to be the developer or owner of S-Chat.
            """;

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String apiKey;
    private final String model;
    private final AiConversationRepository conversationRepository;
    private final AiChatMessageRepository messageRepository;

    public GeminiService(ObjectMapper objectMapper,
                         @Value("${app.gemini.api-key:}") String apiKey,
                         @Value("${app.gemini.model:gemini-3.5-flash}") String model,
                         AiConversationRepository conversationRepository,
                         AiChatMessageRepository messageRepository) {
        this.objectMapper = objectMapper;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = normalizeModel(model);
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    @Transactional(readOnly = true)
    public List<AiChatMessage> history(UUID userId) {
        return conversationRepository.findByUserId(userId)
                .map(c -> messageRepository.findByConversationIdOrderByCreatedAtAscIdAsc(c.getId()).stream()
                        .map(m -> new AiChatMessage(m.getRole(), m.getContent()))
                        .toList())
                .orElseGet(List::of);
    }

    public AiChatResponse generate(UUID userId, AiChatRequest request) {
        if (apiKey.isBlank()) throw new IllegalStateException("AI assistant is not configured on the server");
        AiChatMessage latest = validateLatestUserMessage(request);
        String clientMessageId = normalizeClientId(request.clientMessageId());

        AiConversation conversation = conversationRepository.findByUserId(userId).orElseGet(() ->
                conversationRepository.save(AiConversation.builder().userId(userId).build()));

        // Idempotency: if the browser retries a message, don't call Gemini twice.
        if (clientMessageId != null) {
            Optional<AiChatMessageEntity> existing = messageRepository.findByConversationIdAndClientMessageId(conversation.getId(), clientMessageId);
            if (existing.isPresent()) {
                List<AiChatMessageEntity> all = messageRepository.findByConversationIdOrderByCreatedAtAscIdAsc(conversation.getId());
                for (int i = all.size() - 1; i >= 0; i--) {
                    AiChatMessageEntity m = all.get(i);
                    if (m.getCreatedAt().isBefore(existing.get().getCreatedAt()) || m.getId().equals(existing.get().getId())) break;
                    if ("model".equals(m.getRole())) return new AiChatResponse(m.getContent(), history(userId));
                }
            }
        }

        List<AiChatMessageEntity> existingHistory = messageRepository.findTop24ByConversationIdOrderByCreatedAtDescIdDesc(conversation.getId());
        Collections.reverse(existingHistory);
        List<AiChatMessage> context = new ArrayList<>(existingHistory.stream().map(m -> new AiChatMessage(m.getRole(), m.getContent())).toList());
        context.add(latest);
        validateContext(context);

        AiChatMessageEntity savedUser = messageRepository.save(AiChatMessageEntity.builder()
                .conversationId(conversation.getId()).role("user").content(latest.content()).clientMessageId(clientMessageId).build());

        try {
            String text = callGemini(context);
            messageRepository.save(AiChatMessageEntity.builder()
                    .conversationId(conversation.getId()).role("model").content(text).build());
            conversation.setUpdatedAt(java.time.Instant.now());
            conversationRepository.save(conversation);
            return new AiChatResponse(text, history(userId));
        } catch (RuntimeException ex) {
            // Don't leave a failed user turn permanently in the conversation.
            try { messageRepository.delete(savedUser); } catch (Exception deleteEx) { log.warn("Could not roll back failed AI user message", deleteEx); }
            throw ex;
        }
    }

    @Transactional
    public void clearHistory(UUID userId) {
        conversationRepository.findByUserId(userId).ifPresent(c -> {
            messageRepository.deleteAll(messageRepository.findByConversationIdOrderByCreatedAtAscIdAsc(c.getId()));
            conversationRepository.delete(c);
        });
    }

    private String callGemini(List<AiChatMessage> messages) {
        try {
            ObjectNode root = objectMapper.createObjectNode();
            ObjectNode systemInstruction = root.putObject("systemInstruction");
            systemInstruction.putArray("parts").addObject().put("text", SYSTEM_INSTRUCTION);
            ArrayNode contents = root.putArray("contents");
            for (AiChatMessage message : messages) {
                ObjectNode content = contents.addObject();
                content.put("role", message.role());
                content.putArray("parts").addObject().put("text", message.content());
            }
            root.putObject("generationConfig").put("maxOutputTokens", 2048);
            String endpoint = "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent";
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(endpoint)).timeout(Duration.ofSeconds(45))
                    .header("Content-Type", "application/json").header("x-goog-api-key", apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(root))).build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode body = objectMapper.readTree(response.body());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("Gemini API returned HTTP {}: {}", response.statusCode(), body.path("error").path("message").asText(""));
                throw new IllegalStateException("The AI assistant is temporarily unavailable. Please try again.");
            }
            String text = extractText(body);
            if (text.isBlank()) throw new IllegalStateException("The AI assistant returned an empty response. Please try again.");
            return text.trim();
        } catch (IllegalStateException e) { throw e; }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException("The AI request was interrupted. Please try again."); }
        catch (Exception e) { log.error("Gemini request failed", e); throw new IllegalStateException("The AI assistant is temporarily unavailable. Please try again."); }
    }

    private AiChatMessage validateLatestUserMessage(AiChatRequest request) {
        if (request == null || request.messages() == null || request.messages().isEmpty()) throw new IllegalArgumentException("At least one message is required");
        AiChatMessage latest = request.messages().get(request.messages().size() - 1);
        if (latest == null || latest.content() == null || latest.content().isBlank()) throw new IllegalArgumentException("AI messages cannot be empty");
        if (!"user".equals(latest.role())) throw new IllegalArgumentException("The latest AI message must be from the user");
        if (latest.content().length() > MAX_MESSAGE_CHARS) throw new IllegalArgumentException("AI message is too long");
        return latest;
    }

    private void validateContext(List<AiChatMessage> messages) {
        int total = 0;
        for (AiChatMessage m : messages) {
            if (m == null || m.content() == null || m.content().isBlank()) throw new IllegalArgumentException("AI messages cannot be empty");
            if (!"user".equals(m.role()) && !"model".equals(m.role())) throw new IllegalArgumentException("Invalid AI message role");
            if (m.content().length() > MAX_MESSAGE_CHARS) throw new IllegalArgumentException("AI message is too long");
            total += m.content().length();
            if (total > MAX_TOTAL_CHARS) throw new IllegalArgumentException("AI conversation is too long");
        }
    }

    private String normalizeClientId(String id) {
        if (id == null || id.isBlank()) return null;
        String value = id.trim();
        return value.length() > 80 ? value.substring(0, 80) : value;
    }

    private String normalizeModel(String configuredModel) {
        if (configuredModel == null || configuredModel.isBlank()) return "gemini-3.5-flash";
        String normalized = configuredModel.trim();
        if (normalized.startsWith("models/")) normalized = normalized.substring("models/".length());
        if ("gemini-2.5-flash".equals(normalized)) {
            log.warn("Configured Gemini model {} is unavailable for new users. Falling back to gemini-3.5-flash.", normalized);
            return "gemini-3.5-flash";
        }
        return normalized;
    }

    private String extractText(JsonNode body) {
        JsonNode candidates = body.path("candidates");
        if (!candidates.isArray() || candidates.isEmpty()) return "";
        JsonNode parts = candidates.get(0).path("content").path("parts");
        if (!parts.isArray()) return "";
        StringBuilder result = new StringBuilder();
        for (JsonNode part : parts) {
            String text = part.path("text").asText("");
            if (!text.isBlank()) { if (!result.isEmpty()) result.append('\n'); result.append(text); }
        }
        return result.toString();
    }
}
