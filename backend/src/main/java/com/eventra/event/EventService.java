package com.eventra.event;

import com.eventra.common.EventValidationException;
import com.eventra.event.dto.CreateEventRequest;
import com.eventra.event.dto.EventResponse;
import com.eventra.event.dto.UpdateEventRequest;
import com.eventra.task.TaskRepository;
import com.eventra.volunteer.VolunteerRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
public class EventService {

    private static final Set<String> ALLOWED_CERT_KEYS = Set.of(
            "type", "templateId", "backgroundImage", "namePosition", "textColor"
    );
    private static final int MAX_CERT_JSON_BYTES = 10 * 1024; // 10 KB
    private static final int MAX_CERT_STRING_LEN = 500;

    private final EventRepository eventRepository;
    private final TaskRepository taskRepository;
    private final VolunteerRepository volunteerRepository;
    private final ObjectMapper objectMapper;

    public EventService(EventRepository eventRepository,
                        TaskRepository taskRepository,
                        VolunteerRepository volunteerRepository,
                        ObjectMapper objectMapper) {
        this.eventRepository = eventRepository;
        this.taskRepository = taskRepository;
        this.volunteerRepository = volunteerRepository;
        this.objectMapper = objectMapper;
    }

    public List<EventResponse> listAll() {
        return eventRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public EventResponse create(CreateEventRequest req, UUID createdById) {
        validateDates(req.getStartDate(), req.getEndDate());

        String statusStr = req.getStatus();
        if (statusStr == null || statusStr.isBlank()) {
            statusStr = EventStatus.draft.name();
        } else {
            EventStatus status = EventStatus.fromString(statusStr);
            if (status == null) {
                throw new EventValidationException("Invalid status");
            }
            statusStr = status.name();
        }

        validateCertificateTemplate(req.getCertificateTemplate());

        Event event = new Event();
        event.setName(req.getName().trim());
        event.setDescription(req.getDescription());
        event.setStartDate(req.getStartDate());
        event.setEndDate(req.getEndDate());
        event.setStatus(statusStr);
        event.setCreatedBy(createdById);
        if (req.getCertificateTemplate() != null) {
            event.setCertificateTemplate(req.getCertificateTemplate());
        }
        return toResponse(eventRepository.save(event));
    }

    public EventResponse getById(UUID id) {
        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new EventNotFoundException("Event not found: " + id));
        return toResponse(event);
    }

    @Transactional
    public EventResponse update(UUID id, UpdateEventRequest req) {
        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new EventNotFoundException("Event not found: " + id));
        validateDates(req.getStartDate(), req.getEndDate());

        String statusStr = req.getStatus();
        if (statusStr == null || statusStr.isBlank()) {
            throw new EventValidationException("Invalid status");
        }
        EventStatus status = EventStatus.fromString(statusStr);
        if (status == null) {
            throw new EventValidationException("Invalid status");
        }

        if (req.getCertificateTemplate() != null) {
            validateCertificateTemplate(req.getCertificateTemplate());
            event.setCertificateTemplate(req.getCertificateTemplate());
        }

        event.setName(req.getName().trim());
        event.setDescription(req.getDescription());
        event.setStartDate(req.getStartDate());
        event.setEndDate(req.getEndDate());
        event.setStatus(status.name());

        return toResponse(eventRepository.save(event));
    }

    @Transactional
    public void delete(UUID id) {
        Event event = eventRepository.findWithLockById(id)
                .orElseThrow(() -> new EventNotFoundException("Event not found: " + id));

        long taskCount = taskRepository.countByEventId(id);
        long volunteerCount = volunteerRepository.countByEventId(id);
        if (taskCount > 0 || volunteerCount > 0) {
            throw new EventConflictException(
                    "Cannot delete event: it has " + taskCount + " task(s) and " + volunteerCount + " volunteer(s).");
        }

        try {
            eventRepository.delete(event);
            eventRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw new EventConflictException("Cannot delete event: conflict with existing data");
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private void validateDates(java.time.Instant startDate, java.time.Instant endDate) {
        if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
            throw new EventValidationException("End date must not be before start date.");
        }
    }

    private void validateCertificateTemplate(Map<String, Object> template) {
        if (template == null || template.isEmpty()) {
            return;
        }

        for (Map.Entry<String, Object> entry : template.entrySet()) {
            String key = entry.getKey();
            if (!ALLOWED_CERT_KEYS.contains(key)) {
                throw new EventValidationException("Disallowed key in certificateTemplate: " + key);
            }
            Object val = entry.getValue();
            if (val instanceof String s && s.length() > MAX_CERT_STRING_LEN) {
                throw new EventValidationException("Certificate template value for " + key + " exceeds 500 characters");
            }
        }

        try {
            String json = objectMapper.writeValueAsString(template);
            if (json.getBytes(StandardCharsets.UTF_8).length > MAX_CERT_JSON_BYTES) {
                throw new EventValidationException("Certificate template serialized size exceeds 10 KB");
            }
        } catch (JsonProcessingException e) {
            throw new EventValidationException("Invalid certificate template JSON");
        }
    }

    private EventResponse toResponse(Event event) {
        EventResponse r = new EventResponse();
        r.setId(event.getId());
        r.setName(event.getName());
        r.setDescription(event.getDescription());
        r.setStartDate(event.getStartDate());
        r.setEndDate(event.getEndDate());
        r.setStatus(event.getStatus());
        r.setCreatedBy(event.getCreatedBy());
        r.setCertificateTemplate(event.getCertificateTemplate());
        r.setCreatedAt(event.getCreatedAt());
        r.setUpdatedAt(event.getUpdatedAt());
        return r;
    }
}
