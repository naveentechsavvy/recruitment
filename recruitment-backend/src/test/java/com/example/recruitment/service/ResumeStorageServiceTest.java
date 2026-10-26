package com.example.recruitment.service;

import com.example.recruitment.entity.ResumeFile;
import com.example.recruitment.exception.ResourceNotFoundException;
import com.example.recruitment.repository.ResumeFileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ResumeStorageServiceTest {

    @TempDir
    Path tempDir;

    /** A small in-memory stand-in for the resume_files table. */
    private final Map<String, ResumeFile> db = new HashMap<>();

    private ResumeFileRepository repo() {
        ResumeFileRepository r = mock(ResumeFileRepository.class);
        when(r.save(any(ResumeFile.class))).thenAnswer(i -> {
            ResumeFile f = i.getArgument(0);
            db.put(f.getFilename(), f);
            return f;
        });
        when(r.findByFilename(anyString())).thenAnswer(i -> Optional.ofNullable(db.get((String) i.getArgument(0))));
        when(r.existsByFilename(anyString())).thenAnswer(i -> db.containsKey((String) i.getArgument(0)));
        when(r.deleteByFilename(anyString())).thenAnswer(i -> db.remove((String) i.getArgument(0)) == null ? 0 : 1);
        return r;
    }

    private ResumeStorageService storage() {
        return new ResumeStorageService(repo(), tempDir.toString(), 1); // 1 MB limit for the tests
    }

    private static byte[] pdfBytes(int size) {
        byte[] data = new byte[size];
        byte[] header = "%PDF-1.4".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(header, 0, data, 0, Math.min(header.length, size));
        return data;
    }

    @Test
    void storesAValidPdfInTheDatabaseUnderARandomName() {
        MockMultipartFile file = new MockMultipartFile("resume", "My Resume.pdf", "application/pdf", pdfBytes(100));

        ResumeStorageService.Stored stored = storage().save(file);

        assertEquals("My Resume.pdf", stored.originalName());
        assertNotEquals("My Resume.pdf", stored.filename());
        assertTrue(stored.filename().endsWith(".pdf"));
        ResumeFile row = db.get(stored.filename());
        assertNotNull(row);
        assertEquals(100, row.getSizeBytes());
        assertEquals("application/pdf", row.getContentType());
    }

    @Test
    void whatIsStoredIsWhatIsLoaded() throws Exception {
        ResumeStorageService storage = storage();
        byte[] original = pdfBytes(300);
        ResumeStorageService.Stored stored = storage.save(new MockMultipartFile("resume", "cv.pdf", "application/pdf", original));

        ResumeStorageService.Loaded loaded = storage.load(stored.filename(), "cv.pdf");

        assertEquals("cv.pdf", loaded.downloadName());
        assertEquals("application/pdf", loaded.mediaType());
        assertArrayEquals(original, ((ByteArrayResource) loaded.resource()).getByteArray());
    }

    @Test
    void rejectsMissingAndEmptyFiles() {
        ResumeStorageService storage = storage();
        assertThrows(IllegalArgumentException.class, () -> storage.save(null));
        assertThrows(IllegalArgumentException.class,
                () -> storage.save(new MockMultipartFile("resume", "empty.pdf", "application/pdf", new byte[0])));
        assertTrue(db.isEmpty());
    }

    @Test
    void rejectsWrongFileTypes() {
        ResumeStorageService storage = storage();
        assertThrows(IllegalArgumentException.class,
                () -> storage.save(new MockMultipartFile("resume", "notes.txt", "text/plain", "hello world".getBytes())));
        assertThrows(IllegalArgumentException.class,
                () -> storage.save(new MockMultipartFile("resume", "run.exe", "application/octet-stream", pdfBytes(50))));
        assertTrue(db.isEmpty());
    }

    @Test
    void rejectsAFileWhoseContentDoesNotMatchItsExtension() {
        MockMultipartFile fake = new MockMultipartFile("resume", "fake.pdf", "application/pdf", "not a pdf at all".getBytes());
        assertThrows(IllegalArgumentException.class, () -> storage().save(fake));
        assertTrue(db.isEmpty());
    }

    @Test
    void rejectsOversizedFiles() {
        MockMultipartFile big = new MockMultipartFile("resume", "big.pdf", "application/pdf", pdfBytes(1024 * 1024 + 1));
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> storage().save(big));
        assertTrue(e.getMessage().contains("1 MB"));
    }

    @Test
    void loadRefusesPathsOutsideTheOldFolderAndMissingFiles() {
        ResumeStorageService storage = storage();
        assertThrows(ResourceNotFoundException.class, () -> storage.load("../secret.pdf", "x.pdf"));
        assertThrows(ResourceNotFoundException.class, () -> storage.load("does-not-exist.pdf", "x.pdf"));
        assertThrows(ResourceNotFoundException.class, () -> storage.load(null, "x.pdf"));
    }

    @Test
    void deleteRemovesTheResumeAndIgnoresUnsafeNames() {
        ResumeStorageService storage = storage();
        ResumeStorageService.Stored stored = storage.save(
                new MockMultipartFile("resume", "a.pdf", "application/pdf", pdfBytes(20)));

        storage.delete(stored.filename());
        assertFalse(db.containsKey(stored.filename()));
        assertThrows(ResourceNotFoundException.class, () -> storage.load(stored.filename(), "a.pdf"));
        assertDoesNotThrow(() -> storage.delete("../../etc/passwd"));
        assertDoesNotThrow(() -> storage.delete(null));
    }

    @Test
    void anOldResumeStillInTheOldFolderCanBeReadAndIsCopiedIntoTheDatabase() throws Exception {
        ResumeStorageService storage = storage();
        byte[] old = pdfBytes(64);
        Files.write(tempDir.resolve("old-one.pdf"), old);
        Files.write(tempDir.resolve("notes.txt"), "ignored".getBytes());

        // readable from the old folder straight away
        assertNotNull(storage.load("old-one.pdf", "cv.pdf").resource());

        storage.importLegacyFiles();

        assertTrue(db.containsKey("old-one.pdf"));
        assertFalse(db.containsKey("notes.txt"));
        assertArrayEquals(old, db.get("old-one.pdf").getData());
        assertTrue(Files.exists(tempDir.resolve("old-one.pdf"))); // the old file is kept
    }
}
