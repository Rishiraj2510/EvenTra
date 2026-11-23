package com.eventra.certificate.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

@Component
public class CertificateStorageService {

    private final Path rootDir;

    public CertificateStorageService(@Value("${eventra.certificates.dir:${java.io.tmpdir}/eventra-certificates}") String dirPath) {
        this.rootDir = Paths.get(dirPath).toAbsolutePath().normalize();
    }

    public Path getRootDir() {
        return rootDir;
    }

    public void storeFile(String storageKey, byte[] data) throws IOException {
        Path targetPath = resolveAndVerify(storageKey);
        Files.createDirectories(targetPath.getParent());
        Files.write(targetPath, data);
    }

    public byte[] readFile(String storageKey) throws IOException {
        Path targetPath = resolveAndVerify(storageKey);
        return Files.readAllBytes(targetPath);
    }

    public void deleteFile(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            return;
        }
        try {
            Path targetPath = resolveAndVerify(storageKey);
            Files.deleteIfExists(targetPath);
        } catch (Exception ignored) {
            // Never fail transaction or log PII
        }
    }

    public Path resolveAndVerify(String storageKey) {
        Path resolved = rootDir.resolve(storageKey).normalize();
        if (!resolved.startsWith(rootDir)) {
            throw new SecurityException("Storage key escapes root directory");
        }
        return resolved;
    }
}

