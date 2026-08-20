package com.stech.schat.service;

import org.springframework.web.multipart.MultipartFile;

public interface StorageService {
    /**
     * Uploads a file under the given logical folder (e.g. "profile-pictures", "status", "attachments")
     * and returns a publicly accessible URL.
     */
    String upload(String folder, MultipartFile file) throws Exception;

    /**
     * Best-effort delete of a previously uploaded file, given the public URL returned by upload().
     * Used by cleanup jobs (e.g. expired status posts) so removed content doesn't sit in storage
     * forever. Implementations should not throw on failure — callers treat this as advisory.
     */
    void delete(String publicUrl);

    /** Returns true only for URLs issued by this application's configured storage bucket. */
    default boolean isManagedUrl(String url) { return false; }
}
