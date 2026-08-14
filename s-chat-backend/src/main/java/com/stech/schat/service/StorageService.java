package com.stech.schat.service;

import org.springframework.web.multipart.MultipartFile;

public interface StorageService {
    /**
     * Uploads a file under the given logical folder (e.g. "profile-pictures", "status", "attachments")
     * and returns a publicly accessible URL.
     */
    String upload(String folder, MultipartFile file) throws Exception;
}
