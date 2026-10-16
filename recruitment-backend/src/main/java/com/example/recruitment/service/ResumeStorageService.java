package com.example.recruitment.service;

import com.example.recruitment.exception.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Stores candidate resumes in a PRIVATE folder. Unlike logos, this folder is not under /uploads,
 * so files can only be downloaded through the admin download endpoint.
 */
@Service
public class ResumeStorageService {

    private static final Map<String, String> TYPES = Map.of(
            "pdf", "application/pdf",
            "doc", "application/msword",
            "docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document");

    private final Path dir;
    private final long maxBytes;

    public record Stored(String filename, String originalName) { }
    public record Loaded(Resource resource, String downloadName, String mediaType) { }

    public ResumeStorageService(@Value("${app.resume-dir:private-resumes}") String resumeDir,
                                @Value("${app.resume.max-size-mb:5}") long maxSizeMb) {
        if (maxSizeMb < 1) throw new IllegalArgumentException("app.resume.max-size-mb must be at least 1");
        this.maxBytes = maxSizeMb * 1024 * 1024;
        this.dir = Paths.get(resumeDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not create resume directory " + dir, e);
        }
    }

    /** Largest accepted resume, in bytes (the frontend shows this limit too). */
    public long getMaxBytes() { return maxBytes; }

    public Stored save(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("Please attach a resume. The file is missing or empty");
        if (file.getSize() > maxBytes) throw new IllegalArgumentException("Resume must be " + (maxBytes / (1024 * 1024)) + " MB or smaller");
        String original = cleanName(file.getOriginalFilename());
        String ext = extensionOf(original);
        if (!TYPES.containsKey(ext)) throw new IllegalArgumentException("Resume must be a PDF, DOC or DOCX file");
        try {
            byte[] data = file.getBytes();
            if (!looksLike(ext, data)) throw new IllegalArgumentException("The file content does not match its type");
            String filename = UUID.randomUUID() + "." + ext; // random name: no clashes, no user-controlled paths
            Files.write(dir.resolve(filename), data);
            return new Stored(filename, original);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not store resume", e);
        }
    }

    public Loaded load(String filename, String downloadName) {
        Path path = dir.resolve(filename).normalize();
        if (!path.startsWith(dir) || !Files.isRegularFile(path)) throw new ResourceNotFoundException("Resume file not found");
        String type = TYPES.getOrDefault(extensionOf(filename), "application/octet-stream");
        return new Loaded(new FileSystemResource(path), downloadName, type);
    }

    /** Removes a stored resume file (used when a client and its candidates are deleted). */
    public void delete(String filename) {
        if (filename == null || filename.isBlank()) return;
        Path path = dir.resolve(filename).normalize();
        if (!path.startsWith(dir)) return; // never delete outside the resume folder
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // A leftover file is harmless; do not fail the request because of it.
        }
    }

    private static boolean looksLike(String ext, byte[] d) {
        if (d.length < 4) return false;
        return switch (ext) {
            case "pdf" -> d[0] == '%' && d[1] == 'P' && d[2] == 'D' && d[3] == 'F';
            case "docx" -> d[0] == 'P' && d[1] == 'K';
            case "doc" -> (d[0] & 0xFF) == 0xD0 && (d[1] & 0xFF) == 0xCF;
            default -> false;
        };
    }

    private static String cleanName(String name) {
        String n = name == null ? "resume" : name.replace('\\', '/');
        n = n.substring(n.lastIndexOf('/') + 1).replaceAll("\\p{Cntrl}", "").trim();
        if (n.isEmpty()) n = "resume";
        return n.length() > 150 ? n.substring(n.length() - 150) : n;
    }

    private static String extensionOf(String name) {
        int i = name.lastIndexOf('.');
        return i < 0 ? "" : name.substring(i + 1).toLowerCase(Locale.ROOT);
    }
}
