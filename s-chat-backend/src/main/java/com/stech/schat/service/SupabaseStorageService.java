package com.stech.schat.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Set;
import java.util.UUID;

@Service
public class SupabaseStorageService implements StorageService {

    // Only allow types the app actually needs — never trust the client-sent extension alone,
    // this checks the browser-reported MIME type as a first filter (magic-byte checks belong
    // in front of this once attachment support broadens beyond images/video).
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/png", "image/gif", "video/mp4", "video/quicktime"
    );
    private static final long MAX_FILE_BYTES = 25L * 1024 * 1024;

    private final String supabaseUrl;
    private final String serviceKey;
    private final String bucket;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public SupabaseStorageService(
            @Value("${app.supabase.url}") String supabaseUrl,
            @Value("${app.supabase.service-key}") String serviceKey,
            @Value("${app.supabase.storage-bucket:s-chat-media}") String bucket
    ) {
        this.supabaseUrl = supabaseUrl;
        this.serviceKey = serviceKey;
        this.bucket = bucket;
    }

    @Override
    public String upload(String folder, MultipartFile file) throws Exception {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            throw new IllegalArgumentException("File exceeds the 25MB limit");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new IllegalArgumentException("Unsupported file type: " + contentType);
        }

        byte[] bytes = file.getBytes();
        validateFileSignature(contentType, bytes);

        String safeExtension = switch (contentType) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/gif" -> ".gif";
            case "video/mp4" -> ".mp4";
            case "video/quicktime" -> ".mov";
            default -> "";
        };
        String safeFolder = folder == null ? "files" : folder.replaceAll("[^a-zA-Z0-9_-]", "");
        if (safeFolder.isBlank()) safeFolder = "files";
        String objectPath = safeFolder + "/" + UUID.randomUUID() + safeExtension;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(supabaseUrl + "/storage/v1/object/" + bucket + "/" + objectPath))
                .header("Authorization", "Bearer " + serviceKey)
                .header("Content-Type", contentType)
                .header("x-upsert", "false")
                .PUT(HttpRequest.BodyPublishers.ofByteArray(bytes))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() >= 300) {
            throw new IllegalStateException("Upload to storage failed (status " + response.statusCode() + ")");
        }

        return supabaseUrl + "/storage/v1/object/public/" + bucket + "/" + objectPath;
    }
    private void validateFileSignature(String contentType, byte[] bytes) {
        try {
            if (contentType.startsWith("image/")) {
                if (ImageIO.read(new ByteArrayInputStream(bytes)) == null) {
                    throw new IllegalArgumentException("The uploaded image is invalid or corrupted");
                }
                return;
            }

            if ("video/mp4".equals(contentType) || "video/quicktime".equals(contentType)) {
                // ISO Base Media files contain an ftyp box near the beginning.
                int limit = Math.min(bytes.length - 4, 64);
                boolean hasFtyp = false;
                for (int i = 4; i <= limit; i++) {
                    if (bytes[i] == 'f' && bytes[i + 1] == 't' && bytes[i + 2] == 'y' && bytes[i + 3] == 'p') {
                        hasFtyp = true;
                        break;
                    }
                }
                if (!hasFtyp) throw new IllegalArgumentException("The uploaded video is invalid or corrupted");
            }
        } catch (java.io.IOException ex) {
            throw new IllegalArgumentException("The uploaded file could not be validated");
        }
    }

}
