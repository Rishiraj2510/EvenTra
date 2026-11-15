package com.eventra.registration;

import com.eventra.common.TextRules;
import com.eventra.common.ValidationException;
import com.eventra.event.EventConflictException;
import com.eventra.event.EventNotFoundException;
import com.eventra.event.EventRepository;
import com.eventra.model.ExtraQuestion;
import com.eventra.registration.dto.ExtraQuestionDto;
import com.eventra.registration.dto.RegistrationFormResponse;
import com.eventra.registration.dto.UpdateRegistrationFormRequest;
import com.eventra.volunteer.VolunteerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;

@Service
public class RegistrationFormService {

    private static final Pattern QUESTION_ID_PATTERN = Pattern.compile("^[a-z0-9_-]+$");

    private final RegistrationFormRepository registrationFormRepository;
    private final EventRepository eventRepository;
    private final VolunteerRepository volunteerRepository;

    public RegistrationFormService(RegistrationFormRepository registrationFormRepository,
                                   EventRepository eventRepository,
                                   VolunteerRepository volunteerRepository) {
        this.registrationFormRepository = registrationFormRepository;
        this.eventRepository = eventRepository;
        this.volunteerRepository = volunteerRepository;
    }

    public RegistrationFormResponse getForm(UUID eventId) {
        if (!eventRepository.existsById(eventId)) {
            throw new EventNotFoundException("Event not found: " + eventId);
        }

        long responseCount = volunteerRepository.countByEventId(eventId);
        Optional<RegistrationForm> opt = registrationFormRepository.findByEventId(eventId);

        if (opt.isEmpty()) {
            return new RegistrationFormResponse(
                    eventId,
                    "draft",
                    null,
                    null,
                    null,
                    Collections.emptyList(),
                    Collections.emptyList(),
                    responseCount
            );
        }

        return toResponse(opt.get(), responseCount);
    }

    @Transactional
    public RegistrationFormResponse updateForm(UUID eventId, UpdateRegistrationFormRequest req) {
        // Lock event row
        eventRepository.findWithLockById(eventId)
                .orElseThrow(() -> new EventNotFoundException("Event not found: " + eventId));

        long responseCount = volunteerRepository.countByEventId(eventId);

        // Validation
        validateDates(req.getOpensAt(), req.getClosesAt());
        validateMaxResponses(req.getMaxResponses(), responseCount);
        List<String> cleanedSkills = cleanAndValidateSkills(req.getOfferedSkills());
        List<ExtraQuestion> cleanedQuestions = cleanAndValidateExtraQuestions(req.getExtraQuestions());

        RegistrationForm form = registrationFormRepository.findByEventId(eventId)
                .orElseGet(() -> {
                    RegistrationForm newForm = new RegistrationForm();
                    newForm.setEventId(eventId);
                    newForm.setStatus("draft");
                    return newForm;
                });

        form.setOpensAt(req.getOpensAt());
        form.setClosesAt(req.getClosesAt());
        form.setMaxResponses(req.getMaxResponses());
        form.setOfferedSkills(cleanedSkills);
        form.setExtraQuestions(cleanedQuestions);
        form.setUpdatedAt(Instant.now());

        RegistrationForm saved = registrationFormRepository.save(form);
        return toResponse(saved, responseCount);
    }

    @Transactional
    public RegistrationFormResponse openForm(UUID eventId) {
        // Lock event row
        eventRepository.findWithLockById(eventId)
                .orElseThrow(() -> new EventNotFoundException("Event not found: " + eventId));

        // NOTE: Run 8 must refuse opening when the event's plan is published.

        long responseCount = volunteerRepository.countByEventId(eventId);
        RegistrationForm form = registrationFormRepository.findByEventId(eventId)
                .orElseGet(() -> {
                    RegistrationForm newForm = new RegistrationForm();
                    newForm.setEventId(eventId);
                    newForm.setStatus("draft");
                    return newForm;
                });

        String currentStatus = form.getStatus();
        if ("open".equals(currentStatus)) {
            throw new EventConflictException("Registration form is already open");
        }
        if (!"draft".equals(currentStatus) && !"closed".equals(currentStatus)) {
            throw new EventConflictException("Invalid registration form status transition");
        }

        form.setStatus("open");
        form.setUpdatedAt(Instant.now());

        RegistrationForm saved = registrationFormRepository.save(form);
        return toResponse(saved, responseCount);
    }

    @Transactional
    public RegistrationFormResponse closeForm(UUID eventId) {
        // Lock event row
        eventRepository.findWithLockById(eventId)
                .orElseThrow(() -> new EventNotFoundException("Event not found: " + eventId));

        long responseCount = volunteerRepository.countByEventId(eventId);
        RegistrationForm form = registrationFormRepository.findByEventId(eventId)
                .orElseGet(() -> {
                    RegistrationForm newForm = new RegistrationForm();
                    newForm.setEventId(eventId);
                    newForm.setStatus("draft");
                    return newForm;
                });

        String currentStatus = form.getStatus();
        if (!"open".equals(currentStatus)) {
            throw new EventConflictException("Only open registration forms can be closed");
        }

        form.setStatus("closed");
        form.setUpdatedAt(Instant.now());

        RegistrationForm saved = registrationFormRepository.save(form);
        return toResponse(saved, responseCount);
    }

    private void validateDates(Instant opensAt, Instant closesAt) {
        if (opensAt != null && closesAt != null) {
            if (!closesAt.isAfter(opensAt)) {
                throw new ValidationException("closesAt must be after opensAt");
            }
        }
    }

    private void validateMaxResponses(Integer maxResponses, long responseCount) {
        if (maxResponses != null) {
            if (maxResponses < 1 || maxResponses > 10000) {
                throw new ValidationException("maxResponses must be between 1 and 10000");
            }
            if (maxResponses < responseCount) {
                throw new EventConflictException("maxResponses cannot be lower than current response count");
            }
        }
    }

    private List<String> cleanAndValidateSkills(List<String> rawSkills) {
        if (rawSkills == null || rawSkills.isEmpty()) {
            return new ArrayList<>();
        }
        if (rawSkills.size() > 50) {
            throw new ValidationException("offeredSkills must not exceed 50 items");
        }

        List<String> result = new ArrayList<>();
        Set<String> seenLower = new HashSet<>();

        for (String raw : rawSkills) {
            if (raw == null) {
                throw new ValidationException("Skill must not be null");
            }
            TextRules.rejectControlChars(raw, "offeredSkills");
            String trimmed = raw.trim();
            if (trimmed.isEmpty() || trimmed.length() > 40) {
                throw new ValidationException("Each skill must be between 1 and 40 characters");
            }
            String lower = trimmed.toLowerCase(Locale.ROOT);
            if (seenLower.add(lower)) {
                result.add(trimmed);
            }
        }

        return result;
    }

    private List<ExtraQuestion> cleanAndValidateExtraQuestions(List<ExtraQuestionDto> rawQuestions) {
        if (rawQuestions == null || rawQuestions.isEmpty()) {
            return new ArrayList<>();
        }
        if (rawQuestions.size() > 2) {
            throw new ValidationException("extraQuestions must not exceed 2 items");
        }

        List<ExtraQuestion> result = new ArrayList<>();
        Set<String> seenIds = new HashSet<>();

        for (ExtraQuestionDto dto : rawQuestions) {
            if (dto == null) {
                throw new ValidationException("Question must not be null");
            }
            if (dto.getId() == null) {
                throw new ValidationException("Question id is required");
            }
            String trimmedId = dto.getId().trim();
            if (trimmedId.isEmpty() || trimmedId.length() > 40 || !QUESTION_ID_PATTERN.matcher(trimmedId).matches()) {
                throw new ValidationException("Question id must be between 1 and 40 characters of [a-z0-9_-]");
            }
            if (!seenIds.add(trimmedId)) {
                throw new ValidationException("Question id must be unique");
            }

            if (dto.getLabel() == null) {
                throw new ValidationException("Question label is required");
            }
            TextRules.rejectControlChars(dto.getLabel(), "extraQuestions label");
            String trimmedLabel = dto.getLabel().trim();
            if (trimmedLabel.isEmpty() || trimmedLabel.length() > 200) {
                throw new ValidationException("Question label must be between 1 and 200 characters");
            }

            result.add(new ExtraQuestion(trimmedId, trimmedLabel));
        }

        return result;
    }

    private RegistrationFormResponse toResponse(RegistrationForm form, long responseCount) {
        List<ExtraQuestionDto> questionDtos = new ArrayList<>();
        if (form.getExtraQuestions() != null) {
            for (ExtraQuestion q : form.getExtraQuestions()) {
                questionDtos.add(new ExtraQuestionDto(q.getId(), q.getLabel()));
            }
        }

        return new RegistrationFormResponse(
                form.getEventId(),
                form.getStatus(),
                form.getOpensAt(),
                form.getClosesAt(),
                form.getMaxResponses(),
                form.getOfferedSkills() != null ? new ArrayList<>(form.getOfferedSkills()) : new ArrayList<>(),
                questionDtos,
                responseCount
        );
    }
}
