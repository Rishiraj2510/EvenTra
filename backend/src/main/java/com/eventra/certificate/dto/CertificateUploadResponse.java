package com.eventra.certificate.dto;

import java.time.Instant;
import java.util.UUID;

public record CertificateUploadResponse(
        UUID volunteerId,
        String contentType,
        int sizeBytes,
        Instant updatedAt
) {
}

