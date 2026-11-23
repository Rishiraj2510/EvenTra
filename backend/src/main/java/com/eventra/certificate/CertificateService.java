package com.eventra.certificate;

import com.eventra.certificate.dto.CertificateUploadResponse;
import com.eventra.certificate.storage.CertificateStorageService;
import com.eventra.common.ValidationException;
import com.eventra.event.Event;
import com.eventra.event.EventConflictException;
import com.eventra.event.EventNotFoundException;
import com.eventra.event.EventRepository;
import com.eventra.plan.AssignmentRepository;
import com.eventra.plan.Plan;
import com.eventra.plan.PlanRepository;
import com.eventra.plan.PlanStatus;
import com.eventra.volunteer.Volunteer;
import com.eventra.volunteer.VolunteerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

@Service
public class CertificateService {

    public static final int MAX_FILE_SIZE = 5242880; // 5 MB

    private final EventRepository eventRepository;
    private final PlanRepository planRepository;
    private final AssignmentRepository assignmentRepository;
    private final VolunteerRepository volunteerRepository;
    private final CertificateRepository certificateRepository;
    private final CertificateStorageService storageService;

    public CertificateService(
            EventRepository eventRepository,
            PlanRepository planRepository,
            AssignmentRepository assignmentRepository,
            VolunteerRepository volunteerRepository,
            CertificateRepository certificateRepository,
            CertificateStorageService storageService
    ) {
        this.eventRepository = eventRepository;
        this.planRepository = planRepository;
        this.assignmentRepository = assignmentRepository;
        this.volunteerRepository = volunteerRepository;
        this.certificateRepository = certificateRepository;
        this.storageService = storageService;
    }

    @Transactional
    public CertificateUploadResponse uploadCertificate(UUID eventId, UUID volunteerId, MultipartFile file) {
        // 1. Lock event (404 "Event not found: <id>")
        Event event = eventRepository.findWithLockById(eventId)
                .orElseThrow(() -> new EventNotFoundException("Event not found: " + eventId));

        // 2. Plan not published 409 "Plan is not published"
        Plan plan = planRepository.findByEventId(eventId)
                .orElseThrow(() -> new EventConflictException("Plan is not published"));

        if (!PlanStatus.PUBLISHED.equals(plan.getStatus())) {
            throw new EventConflictException("Plan is not published");
        }

        // 3. Volunteer of THIS event with at least one assignment in this plan, else 404 "Volunteer not found"
        Volunteer volunteer = volunteerRepository.findById(volunteerId)
                .filter(v -> eventId.equals(v.getEventId()))
                .orElseThrow(() -> new EventNotFoundException("Volunteer not found"));

        boolean hasAssignmentInPlan = assignmentRepository.findByPlanIdAndVolunteerIdWithTask(plan.getId(), volunteer.getId())
                .stream().findAny().isPresent();

        if (!hasAssignmentInPlan) {
            throw new EventNotFoundException("Volunteer not found");
        }

        // 4. Validate file
        if (file == null || file.isEmpty()) {
            throw new ValidationException("File is empty");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new ValidationException("File is empty");
        }

        if (bytes.length == 0) {
            throw new ValidationException("File is empty");
        }

        if (bytes.length > MAX_FILE_SIZE) {
            throw new ValidationException("File too large");
        }

        String contentType = detectContentType(bytes);
        String extension = getExtensionForContentType(contentType);

        String storageKey = UUID.randomUUID() + extension;

        // Check if existing certificate
        Optional<Certificate> existingOpt = certificateRepository.findByEventIdAndVolunteerId(eventId, volunteerId);
        String oldStorageKey = existingOpt.map(Certificate::getStorageKey).orElse(null);

        // Store new file to disk
        try {
            storageService.storeFile(storageKey, bytes);
        } catch (IOException e) {
            throw new RuntimeException("Failed to store certificate file", e);
        }

        // Register transaction cleanup in case of rollback
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status != TransactionSynchronization.STATUS_COMMITTED) {
                        storageService.deleteFile(storageKey);
                    }
                }
            });
        }

        Certificate certificate;
        if (existingOpt.isPresent()) {
            certificate = existingOpt.get();
            certificate.setStorageKey(storageKey);
            certificate.setContentType(contentType);
            certificate.setSizeBytes(bytes.length);
        } else {
            certificate = new Certificate(event, volunteer, storageKey, contentType, bytes.length);
        }

        try {
            certificate = certificateRepository.saveAndFlush(certificate);
        } catch (Exception e) {
            // Delete new file immediately if flush fails
            storageService.deleteFile(storageKey);
            throw e;
        }

        // If old file existed, delete old file only after transaction commits
        if (oldStorageKey != null) {
            final String fileToDelete = oldStorageKey;
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        storageService.deleteFile(fileToDelete);
                    }
                });
            } else {
                storageService.deleteFile(fileToDelete);
            }
        }

        return new CertificateUploadResponse(
                volunteer.getId(),
                certificate.getContentType(),
                certificate.getSizeBytes(),
                certificate.getUpdatedAt()
        );
    }

    public record CertificateDownload(byte[] data, String contentType, String filename) {}

    @Transactional(readOnly = true)
    public CertificateDownload getMyCertificate(UUID eventId, UUID userId) {
        if (!eventRepository.existsById(eventId)) {
            throw new EventNotFoundException("Not found");
        }

        Volunteer volunteer = volunteerRepository.findByEventIdAndUserId(eventId, userId)
                .orElseThrow(() -> new EventNotFoundException("Not found"));

        Plan plan = planRepository.findByEventId(eventId)
                .orElseThrow(() -> new EventNotFoundException("Not found"));

        if (!PlanStatus.PUBLISHED.equals(plan.getStatus())) {
            throw new EventNotFoundException("Not found");
        }

        Certificate cert = certificateRepository.findByEventIdAndVolunteerId(eventId, volunteer.getId())
                .orElseThrow(() -> new EventNotFoundException("Not found"));

        byte[] data;
        try {
            data = storageService.readFile(cert.getStorageKey());
        } catch (IOException e) {
            throw new EventNotFoundException("Not found");
        }

        String filename = switch (cert.getContentType()) {
            case "application/pdf" -> "certificate.pdf";
            case "image/png" -> "certificate.png";
            case "image/jpeg" -> "certificate.jpg";
            default -> "certificate";
        };

        return new CertificateDownload(data, cert.getContentType(), filename);
    }

    private String detectContentType(byte[] bytes) {
        // PDF: starts with %PDF- (hex: 25 50 44 46 2D)
        if (bytes.length >= 5
                && bytes[0] == 0x25
                && bytes[1] == 0x50
                && bytes[2] == 0x44
                && bytes[3] == 0x46
                && bytes[4] == 0x2D) {
            return "application/pdf";
        }

        // PNG: starts with 89 50 4E 47 0D 0A 1A 0A
        if (bytes.length >= 8
                && (bytes[0] & 0xFF) == 0x89
                && (bytes[1] & 0xFF) == 0x50
                && (bytes[2] & 0xFF) == 0x4E
                && (bytes[3] & 0xFF) == 0x47
                && (bytes[4] & 0xFF) == 0x0D
                && (bytes[5] & 0xFF) == 0x0A
                && (bytes[6] & 0xFF) == 0x1A
                && (bytes[7] & 0xFF) == 0x0A) {
            return "image/png";
        }

        // JPEG: starts with FF D8 FF
        if (bytes.length >= 3
                && (bytes[0] & 0xFF) == 0xFF
                && (bytes[1] & 0xFF) == 0xD8
                && (bytes[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }

        throw new ValidationException("Unsupported file type");
    }

    private String getExtensionForContentType(String contentType) {
        return switch (contentType) {
            case "application/pdf" -> ".pdf";
            case "image/png" -> ".png";
            case "image/jpeg" -> ".jpg";
            default -> "";
        };
    }
}

