package com.example.recruitment.service;

import com.example.recruitment.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ResumeStorageServiceTest {

    @TempDir
    Path tempDir;

    private ResumeStorageService storage() {
        return new ResumeStorageService(tempDir.toString(), 1); // 1 MB limit for the tests
    }

    private static byte[] pdfBytes(int size) {
        byte[] data = new byte[size];
        byte[] header = "%PDF-1.4".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(header, 0, data, 0, Math.min(header.length, size));
        return data;
    }

    @Test
    void storesAValidPdfUnderARandomName() throws Exception {
        MockMultipartFile file = new MockMultipartFile("resume", "My Resume.pdf", "application/pdf", pdfBytes(100));

        ResumeStorageService.Stored stored = storage().save(file);

        assertEquals("My Resume.pdf", stored.originalName());
        assertNotEquals("My Resume.pdf", stored.filename());
        assertTrue(stored.filename().endsWith(".pdf"));
        assertTrue(Files.isRegularFile(tempDir.resolve(stored.filename())));
    }

    @Test
    void rejectsMissingAndEmptyFiles() {
        ResumeStorageService storage = storage();
        assertThrows(IllegalArgumentException.class, () -> storage.save(null));
        assertThrows(IllegalArgumentException.class,
                () -> storage.save(new MockMultipartFile("resume", "empty.pdf", "application/pdf", new byte[0])));
    }

    @Test
    void rejectsWrongFileTypes() {
        ResumeStorageService storage = storage();
        assertThrows(IllegalArgumentException.class,
                () -> storage.save(new MockMultipartFile("resume", "notes.txt", "text/plain", "hello world".getBytes())));
        assertThrows(IllegalArgumentException.class,
                () -> storage.save(new MockMultipartFile("resume", "run.exe", "application/octet-stream", pdfBytes(50))));
    }

    @Test
    void rejectsAFileWhoseContentDoesNotMatchItsExtension() {
        MockMultipartFile fake = new MockMultipartFile("resume", "fake.pdf", "application/pdf", "not a pdf at all".getBytes());
        assertThrows(IllegalArgumentException.class, () -> storage().save(fake));
    }

    @Test
    void rejectsOversizedFiles() {
        MockMultipartFile big = new MockMultipartFile("resume", "big.pdf", "application/pdf", pdfBytes(1024 * 1024 + 1));
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> storage().save(big));
        assertTrue(e.getMessage().contains("1 MB"));
    }

    @Test
    void loadRefusesPathsOutsideTheResumeFolderAndMissingFiles() {
        ResumeStorageService storage = storage();
        assertThrows(ResourceNotFoundException.class, () -> storage.load("../secret.pdf", "x.pdf"));
        assertThrows(ResourceNotFoundException.class, () -> storage.load("does-not-exist.pdf", "x.pdf"));
    }

    @Test
    void deleteRemovesTheFileAndIgnoresUnsafeNames() throws Exception {
        ResumeStorageService storage = storage();
        ResumeStorageService.Stored stored = storage.save(
                new MockMultipartFile("resume", "a.pdf", "application/pdf", pdfBytes(20)));

        storage.delete(stored.filename());
        assertFalse(Files.exists(tempDir.resolve(stored.filename())));
        assertDoesNotThrow(() -> storage.delete("../../etc/passwd"));
        assertDoesNotThrow(() -> storage.delete(null));
    }
}
