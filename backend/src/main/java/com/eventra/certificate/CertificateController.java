package com.eventra.certificate;

import com.eventra.auth.UserPrincipal;
import com.eventra.certificate.dto.CertificateUploadResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/events/{eventId}/certificates")
public class CertificateController {

    private final CertificateService certificateService;

    public CertificateController(CertificateService certificateService) {
        this.certificateService = certificateService;
    }

    @PutMapping(value = "/{volunteerId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ORGANISER')")
    public ResponseEntity<CertificateUploadResponse> uploadCertificate(
            @PathVariable UUID eventId,
            @PathVariable UUID volunteerId,
            @RequestParam(value = "file", required = false) MultipartFile file
    ) {
        if (file == null) {
            throw new com.eventra.common.ValidationException("File is empty");
        }
        return ResponseEntity.ok(certificateService.uploadCertificate(eventId, volunteerId, file));
    }

    @GetMapping("/mine")
    @PreAuthorize("hasRole('VOLUNTEER')")
    public ResponseEntity<byte[]> getMyCertificate(
            @PathVariable UUID eventId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        CertificateService.CertificateDownload download = certificateService.getMyCertificate(eventId, principal.getId());

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, download.contentType())
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + download.filename() + "\"")
                .header("X-Content-Type-Options", "nosniff")
                .body(download.data());
    }
}

