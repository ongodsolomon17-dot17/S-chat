package com.stech.schat.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log =
            LoggerFactory.getLogger(SupabaseStorageService.class);

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/gif",
            "image/webp",
            "video/mp4",
            "video/quicktime",
            // Voice notes — audio/webm and audio/ogg are what MediaRecorder produces in
            // Chrome/Firefox; audio/mp4 and audio/x-m4a cover Safari/iOS recordings.
            "audio/webm",
            "audio/ogg",
            "audio/mp4",
            "audio/x-m4a",
            "audio/mpeg",
            "application/pdf",
            "text/plain",
            "text/csv",
            "application/zip",
            "application/x-zip-compressed",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation"
    );

    private static final long MAX_FILE_BYTES = 50L * 1024 * 1024;

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

        log.info("Supabase Storage initialized for configured bucket");
    }

    @Override
    public String upload(String folder, MultipartFile file) throws Exception {

        log.debug("Storage upload started. folder={}, size={}, contentType={}",
                folder, file.getSize(), file.getContentType());

        if (file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }
        String originalName = file.getOriginalFilename();
        if (originalName != null && (originalName.contains("..") || originalName.contains("\\") || originalName.contains("/"))) {
            throw new IllegalArgumentException("Invalid file name");
        }

        if (file.getSize() > MAX_FILE_BYTES) {
            throw new IllegalArgumentException(
                    "File exceeds the 50MB limit"
            );
        }

        String contentType = file.getContentType();

        if (contentType == null ||
                !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase())) {

            log.warn("Rejected upload because of unsupported MIME type: {}",
                    contentType);

            throw new IllegalArgumentException(
                    "Unsupported file type: " + contentType
            );
        }

        String normalizedType = contentType.toLowerCase();
        String normalizedFolder = folder == null ? "files" : folder.toLowerCase();
        if ("profile-pictures".equals(normalizedFolder) && !normalizedType.startsWith("image/")) {
            throw new IllegalArgumentException("Profile picture must be an image");
        }
        if ("status".equals(normalizedFolder)
                && !(normalizedType.startsWith("image/") || normalizedType.startsWith("video/") || normalizedType.startsWith("audio/"))) {
            throw new IllegalArgumentException("Status media must be an image, video, or audio file");
        }

        byte[] bytes = file.getBytes();

        /*
         * JPEG, PNG and GIF can be validated by Java ImageIO.
         *
         * WebP is intentionally not passed through ImageIO because
         * standard Java ImageIO does not reliably provide a WebP decoder.
         */
        if (contentType.startsWith("image/")
                && !"image/webp".equalsIgnoreCase(contentType)) {

            validateImageSignature(contentType, bytes);
        }

        String safeExtension = switch (contentType.toLowerCase()) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/gif" -> ".gif";
            case "image/webp" -> ".webp";
            case "video/mp4" -> ".mp4";
            case "video/quicktime" -> ".mov";
            case "audio/webm" -> ".webm";
            case "audio/ogg" -> ".ogg";
            case "audio/mp4", "audio/x-m4a" -> ".m4a";
            case "audio/mpeg" -> ".mp3";
            case "application/pdf" -> ".pdf";
            case "text/plain" -> ".txt";
            case "text/csv" -> ".csv";
            case "application/zip", "application/x-zip-compressed" -> ".zip";
            case "application/msword" -> ".doc";
            case "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> ".docx";
            case "application/vnd.ms-excel" -> ".xls";
            case "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" -> ".xlsx";
            case "application/vnd.ms-powerpoint" -> ".ppt";
            case "application/vnd.openxmlformats-officedocument.presentationml.presentation" -> ".pptx";
            default -> ".bin";
        };

        String safeFolder = folder == null
                ? "files"
                : folder.replaceAll("[^a-zA-Z0-9_-]", "");

        if (safeFolder.isBlank()) {
            safeFolder = "files";
        }

        String objectPath =
                safeFolder + "/" + UUID.randomUUID() + safeExtension;

        String uploadUrl =
                supabaseUrl
                        + "/storage/v1/object/"
                        + bucket
                        + "/"
                        + objectPath;

        log.info("Uploading to Supabase. bucket={}, objectPath={}, contentType={}",
                bucket,
                objectPath,
                contentType);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(uploadUrl))
                .header("apikey", serviceKey)
                .header("Content-Type", contentType)
                .header("x-upsert", "false")
                .PUT(HttpRequest.BodyPublishers.ofByteArray(bytes))
                .build();

        HttpResponse<String> response =
                httpClient.send(
                        request,
                        HttpResponse.BodyHandlers.ofString()
                );

        log.debug("Supabase upload response. status={}", response.statusCode());

        if (response.statusCode() >= 300) {

            log.error(
                    "Supabase Storage upload FAILED. status={}, objectPath={}",
                    response.statusCode(), objectPath
            );

            throw new IllegalStateException(
                    "Upload to storage failed (status "
                            + response.statusCode()
                            + ")"
            );
        }

        String publicUrl =
                supabaseUrl
                        + "/storage/v1/object/public/"
                        + bucket
                        + "/"
                        + objectPath;

        log.debug("Supabase upload successful. objectPath={}", objectPath);

        return publicUrl;
    }

    @Override
    public boolean isManagedUrl(String url) {
        if (url == null || url.isBlank()) return false;
        try {
            URI parsed = URI.create(url);
            String expectedHost = URI.create(supabaseUrl).getHost();
            if (expectedHost == null || !expectedHost.equalsIgnoreCase(parsed.getHost())) return false;
            String marker = "/storage/v1/object/public/" + bucket + "/";
            String path = parsed.getPath();
            return path != null && path.startsWith(marker) && path.length() > marker.length()
                    && !path.contains("..") && !path.contains("\\");
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    @Override
    public void delete(String publicUrl) {

        if (publicUrl == null || publicUrl.isBlank()) {
            return;
        }

        String marker =
                "/storage/v1/object/public/" + bucket + "/";

        int idx = publicUrl.indexOf(marker);

        if (idx < 0) {
            return;
        }

        String objectPath =
                publicUrl.substring(idx + marker.length());

        try {

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(
                            supabaseUrl
                                    + "/storage/v1/object/"
                                    + bucket
                                    + "/"
                                    + objectPath
                    ))
                    .header("apikey", serviceKey)
                    .DELETE()
                    .build();

            HttpResponse<String> response =
                    httpClient.send(
                            request,
                            HttpResponse.BodyHandlers.ofString()
                    );

            if (response.statusCode() >= 300
                    && response.statusCode() != 404) {

                log.warn(
                        "Failed to delete storage object. objectPath={}, status={}",
                        objectPath,
                        response.statusCode()
                );
            }

        } catch (Exception ex) {

            log.warn(
                    "Failed to delete storage object {}: {}",
                    objectPath,
                    ex.getMessage(),
                    ex
            );
        }
    }

    private void validateImageSignature(
            String contentType,
            byte[] bytes
    ) {

        try {

            if (contentType.startsWith("image/")) {

                if (ImageIO.read(
                        new ByteArrayInputStream(bytes)
                ) == null) {

                    throw new IllegalArgumentException(
                            "The uploaded image is invalid or corrupted"
                    );
                }

                return;
            }

        } catch (java.io.IOException ex) {

            throw new IllegalArgumentException(
                    "The uploaded file could not be validated",
                    ex
            );
        }
    }
}