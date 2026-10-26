package com.example.recruitment.service;

import com.example.recruitment.entity.ResumeFile;
import com.example.recruitment.exception.ResourceNotFoundException;
import com.example.recruitment.repository.ResumeFileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Stores candidate resumes inside the DATABASE (table resume_files).
 *
 * Resumes are never served as public URLs: they can only be downloaded through the login-protected
 * candidate endpoints. The rest of the application is unchanged: a candidate still keeps the random
 * file name (candidates.resume_file) and the name the candidate uploaded (resume_original_name).
 *
 * save() and delete() each run in their OWN short transaction. That matches how a file on disk behaved:
 * a saved resume is there immediately, and the clean-up calls that already exist in CandidateServiceImpl and
 * ClientServiceImpl (after a rollback, after a replace, after a client is deleted) keep working as before.
 *
 * Older resumes that are still files in the old private folder (app.resume-dir) are copied into the database
 * once when the application starts, and they can still be read from the folder until then.
 */
@Service
public class ResumeStorageService {

    private static final Logger log = LoggerFactory.getLogger(ResumeStorageService.class);

    private static final Map<String, String> TYPES = Map.of(
            "pdf", "application/pdf",
            "doc", "application/msword",
            "docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document");

    private final ResumeFileRepository files;
    private final Path legacyDir;
    private final long maxBytes;

    public record Stored(String filename, String originalName) { }
    public record Loaded(Resource resource, String downloadName, String mediaType) { }

    public ResumeStorageService(ResumeFileRepository files,
                                @Value("${app.resume-dir:private-resumes}") String legacyResumeDir,
                                @Value("${app.resume.max-size-mb:5}") long maxSizeMb) {
        if (maxSizeMb < 1) throw new IllegalArgumentException("app.resume.max-size-mb must be at least 1");
        this.files = files;
        this.maxBytes = maxSizeMb * 1024 * 1024;
        this.legacyDir = Paths.get(legacyResumeDir).toAbsolutePath().normalize();
    }

    /** Largest accepted resume, in bytes (the frontend shows this limit too). */
    public long getMaxBytes() { return maxBytes; }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Stored save(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("Please attach a resume. The file is missing or empty");
        if (file.getSize() > maxBytes) throw new IllegalArgumentException("Resume must be " + (maxBytes / (1024 * 1024)) + " MB or smaller");
        String original = cleanName(file.getOriginalFilename());
        String ext = extensionOf(original);
        if (!TYPES.containsKey(ext)) throw new IllegalArgumentException("Resume must be a PDF, DOC or DOCX file");
        try {
            byte[] data = file.getBytes();
            if (!looksLike(ext, data)) throw new IllegalArgumentException("The file content does not match its type");
            String filename = UUID.randomUUID() + "." + ext; // random name: no clashes, no user-controlled values
            files.save(new ResumeFile(filename, TYPES.get(ext), data));
            return new Stored(filename, original);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not store resume", e);
        }
    }

    @Transactional(readOnly = true)
    public Loaded load(String filename, String downloadName) {
        if (filename == null || filename.isBlank()) throw new ResourceNotFoundException("Resume file not found");

        Optional<ResumeFile> row = files.findByFilename(filename);
        if (row.isPresent()) {
            ResumeFile r = row.get();
            return new Loaded(new ByteArrayResource(r.getData()), downloadName, r.getContentType());
        }

        // Not in the database yet: an old resume that is still a file in the old folder.
        Path path = legacyDir.resolve(filename).normalize();
        if (!path.startsWith(legacyDir) || !Files.isRegularFile(path)) throw new ResourceNotFoundException("Resume file not found");
        String type = TYPES.getOrDefault(extensionOf(filename), "application/octet-stream");
        return new Loaded(new FileSystemResource(path), downloadName, type);
    }

    /** Removes a stored resume (used after a replace, a failed save, or when a client and its candidates are deleted). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void delete(String filename) {
        if (filename == null || filename.isBlank()) return;
        files.deleteByFilename(filename);

        // An old copy in the old folder, if there is one.
        Path path = legacyDir.resolve(filename).normalize();
        if (!path.startsWith(legacyDir)) return; // never delete outside the resume folder
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // A leftover file is harmless; do not fail the request because of it.
        }
    }

    /**
     * Copies resumes that are still files in the old folder into the database. Runs each time the application
     * starts and skips what is already there. The old files are NOT deleted, so nothing can be lost.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void importLegacyFiles() {
        if (!Files.isDirectory(legacyDir)) return;
        int imported = 0;
        try (Stream<Path> list = Files.list(legacyDir)) {
            for (Path p : (Iterable<Path>) list::iterator) {
                if (!Files.isRegularFile(p)) continue;
                String name = p.getFileName().toString();
                String type = TYPES.get(extensionOf(name));
                if (type == null || files.existsByFilename(name)) continue;
                try {
                    files.save(new ResumeFile(name, type, Files.readAllBytes(p)));
                    imported++;
                } catch (IOException | RuntimeException e) {
                    log.warn("Could not import old resume file {}: {}", name, e.getMessage());
                }
            }
        } catch (IOException e) {
            log.warn("Could not read the old resume folder {}: {}", legacyDir, e.getMessage());
        }
        if (imported > 0) log.info("Copied {} old resume file(s) from {} into the database", imported, legacyDir);
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
