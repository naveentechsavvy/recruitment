package com.example.recruitment.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.UUID;

/** Saves logo images to disk. Only the URL is stored in MySQL, not the image itself. */
@Service
public class FileStorageService {

    private static final String URL_PREFIX = "/uploads/logos/";
    private static final long MAX_BYTES = 500 * 1024;
    // SVG is deliberately not allowed: SVG files can contain scripts.
    private static final Map<String, String> ALLOWED_TYPES =
            Map.of("image/png", "png", "image/jpeg", "jpg", "image/webp", "webp", "image/gif", "gif");

    private final Path uploadRoot;
    private final Path logoDir;

    public FileStorageService(@Value("${app.upload-dir}") String uploadDir) {
        this.uploadRoot = Paths.get(uploadDir).toAbsolutePath().normalize();
        this.logoDir = uploadRoot.resolve("logos");
        try {
            Files.createDirectories(logoDir);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not create upload directory " + logoDir, e);
        }
    }

    /** Validates and stores the file, returns its public URL path, e.g. /uploads/logos/abc.png */
    public String saveLogo(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("Logo file is empty");
        if (file.getSize() > MAX_BYTES) throw new IllegalArgumentException("Logo must be 500 KB or smaller");
        String extension = ALLOWED_TYPES.get(file.getContentType());
        if (extension == null) throw new IllegalArgumentException("Logo must be a PNG, JPG, WebP or GIF image");

        String filename = UUID.randomUUID() + "." + extension; // random name: no clashes, no user-controlled paths
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, logoDir.resolve(filename));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not store logo", e);
        }
        return URL_PREFIX + filename;
    }

    public void delete(String logoUrl) {
        if (logoUrl == null || !logoUrl.startsWith(URL_PREFIX)) return;
        Path target = logoDir.resolve(logoUrl.substring(URL_PREFIX.length())).normalize();
        if (!target.startsWith(logoDir)) return; // never delete outside the upload folder
        try {
            Files.deleteIfExists(target);
        } catch (IOException ignored) {
            // A leftover file is harmless; do not fail the request because of it.
        }
    }

    public String getUploadRootUri() {
        String uri = uploadRoot.toUri().toString();
        return uri.endsWith("/") ? uri : uri + "/";
    }
}
