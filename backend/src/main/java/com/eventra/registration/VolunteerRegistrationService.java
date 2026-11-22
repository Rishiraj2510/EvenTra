package com.eventra.registration;

import com.eventra.common.TextRules;
import com.eventra.common.ValidationException;
import com.eventra.event.Event;
import com.eventra.event.EventConflictException;
import com.eventra.event.EventNotFoundException;
import com.eventra.event.EventRepository;
import com.eventra.model.AvailabilitySlot;
import com.eventra.model.ExtraQuestion;
import com.eventra.model.SkillLevel;
import com.eventra.registration.dto.*;
import com.eventra.volunteer.Volunteer;
import com.eventra.volunteer.VolunteerFieldValidator;
import com.eventra.volunteer.VolunteerNotFoundException;
import com.eventra.volunteer.VolunteerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class VolunteerRegistrationService {

    private final EventRepository eventRepository;
    private final RegistrationFormRepository registrationFormRepository;
    private final VolunteerRepository volunteerRepository;
    private final VolunteerFieldValidator volunteerFieldValidator;

    public VolunteerRegistrationService(EventRepository eventRepository,
                                      RegistrationFormRepository registrationFormRepository,
                                      VolunteerRepository volunteerRepository,
                                      VolunteerFieldValidator volunteerFieldValidator) {
        this.eventRepository = eventRepository;
        this.registrationFormRepository = registrationFormRepository;
        this.volunteerRepository = volunteerRepository;
        this.volunteerFieldValidator = volunteerFieldValidator;
    }

    public List<VolunteerEventItemResponse> listVolunteerEvents(UUID userId) {
        List<Event> nonDraftEvents = eventRepository.findByStatusNotOrderByStartDateAscIdAsc("draft");
        if (nonDraftEvents.isEmpty()) {
            return Collections.emptyList();
        }

        List<UUID> eventIds = nonDraftEvents.stream().map(Event::getId).toList();

        // One query for counts
        List<Object[]> countsRaw = volunteerRepository.countByEventIdIn(eventIds);
        Map<UUID, Long> countMap = new HashMap<>();
        for (Object[] row : countsRaw) {
            countMap.put((UUID) row[0], ((Number) row[1]).longValue());
        }

        // One query for caller's registrations
        List<Volunteer> myRegs = volunteerRepository.findByUserIdAndEventIdIn(userId, eventIds);
        Set<UUID> registeredEventIds = myRegs.stream().map(Volunteer::getEventId).collect(Collectors.toSet());

        // Batch forms lookup
        List<RegistrationForm> forms = registrationFormRepository.findByEventIdIn(eventIds);
        Map<UUID, RegistrationForm> formMap = forms.stream()
                .collect(Collectors.toMap(RegistrationForm::getEventId, f -> f));

        Instant now = Instant.now();
        List<VolunteerEventItemResponse> result = new ArrayList<>();

        for (Event event : nonDraftEvents) {
            UUID id = event.getId();
            long count = countMap.getOrDefault(id, 0L);
            boolean isRegistered = registeredEventIds.contains(id);
            RegistrationForm form = formMap.get(id);

            String state = computeState(event, form, count, isRegistered, now);

            result.add(new VolunteerEventItemResponse(
                    event.getId(),
                    event.getName(),
                    event.getDescription(),
                    event.getStartDate(),
                    event.getEndDate(),
                    state
            ));
        }

        return result;
    }

    public RegistrationFormViewResponse getFormView(UUID eventId, UUID userId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EventNotFoundException("Event not found: " + eventId));

        if ("draft".equalsIgnoreCase(event.getStatus())) {
            throw new EventNotFoundException("Event not found: " + eventId);
        }

        RegistrationForm form = registrationFormRepository.findByEventId(eventId).orElse(null);
        long count = volunteerRepository.countByEventId(eventId);
        boolean isRegistered = volunteerRepository.findByEventIdAndUserId(eventId, userId).isPresent();

        String state = computeState(event, form, count, isRegistered, Instant.now());

        List<String> offeredSkills = (form != null && form.getOfferedSkills() != null)
                ? form.getOfferedSkills() : Collections.emptyList();

        List<ExtraQuestionDto> questionDtos = new ArrayList<>();
        if (form != null && form.getExtraQuestions() != null) {
            for (ExtraQuestion q : form.getExtraQuestions()) {
                questionDtos.add(new ExtraQuestionDto(q.getId(), q.getLabel()));
            }
        }

        return new RegistrationFormViewResponse(
                eventId,
                state,
                form != null ? form.getOpensAt() : null,
                form != null ? form.getClosesAt() : null,
                offeredSkills,
                questionDtos
        );
    }

    public VolunteerRegistrationResponse getMyRegistration(UUID eventId, UUID userId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EventNotFoundException("Event not found: " + eventId));
        if ("draft".equalsIgnoreCase(event.getStatus())) {
            throw new EventNotFoundException("Event not found: " + eventId);
        }

        Volunteer volunteer = volunteerRepository.findByEventIdAndUserId(eventId, userId)
                .orElseThrow(() -> new VolunteerNotFoundException("Registration not found"));

        return toRegistrationResponse(volunteer);
    }

    @Transactional
    public VolunteerRegistrationResponse register(UUID eventId, UUID userId, String userEmail, VolunteerRegistrationRequest req) {
        // a. Lock the event row; 404 if missing or status draft
        Event event = eventRepository.findWithLockById(eventId)
                .orElseThrow(() -> new EventNotFoundException("Event not found: " + eventId));
        if ("draft".equalsIgnoreCase(event.getStatus())) {
            throw new EventNotFoundException("Event not found: " + eventId);
        }

        // b. Caller already registered -> 409 "Already registered"
        if (volunteerRepository.findByEventIdAndUserId(eventId, userId).isPresent()) {
            throw new EventConflictException("Already registered");
        }

        // c. Event done, form missing/not open, or outside the opens/closes window -> 409 "Registration is closed"
        RegistrationForm form = registrationFormRepository.findByEventId(eventId).orElse(null);
        Instant now = Instant.now();
        if (isClosed(event, form, now)) {
            throw new EventConflictException("Registration is closed");
        }

        // d. Count volunteers; if maxResponses is set and count >= maxResponses -> 409 "Registration is full"
        long count = volunteerRepository.countByEventId(eventId);
        if (form != null && form.getMaxResponses() != null && count >= form.getMaxResponses()) {
            throw new EventConflictException("Registration is full");
        }

        // e. Validate body (400s), then insert
        Volunteer volunteer = new Volunteer();
        volunteer.setEventId(eventId);
        volunteer.setUserId(userId);
        volunteer.setEmail(userEmail);
        volunteer.setStatus("active");
        volunteer.setRegisteredAt(now);

        applyAndValidate(volunteer, req, form);

        Volunteer saved = volunteerRepository.save(volunteer);
        return toRegistrationResponse(saved);
    }

    @Transactional
    public VolunteerRegistrationResponse updateRegistration(UUID eventId, UUID userId, VolunteerRegistrationRequest req) {
        // Lock event row
        Event event = eventRepository.findWithLockById(eventId)
                .orElseThrow(() -> new EventNotFoundException("Event not found: " + eventId));
        if ("draft".equalsIgnoreCase(event.getStatus())) {
            throw new EventNotFoundException("Event not found: " + eventId);
        }

        // 404 if caller has no registration (check FIRST before form status)
        Volunteer volunteer = volunteerRepository.findByEventIdAndUserId(eventId, userId)
                .orElseThrow(() -> new VolunteerNotFoundException("Registration not found"));

        // Allowed only while the form is open and within window; otherwise 409
        RegistrationForm form = registrationFormRepository.findByEventId(eventId).orElse(null);
        Instant now = Instant.now();
        if (isClosed(event, form, now)) {
            throw new EventConflictException("Registration is closed");
        }

        // Full replace of editable fields
        applyAndValidate(volunteer, req, form);

        Volunteer saved = volunteerRepository.save(volunteer);
        return toRegistrationResponse(saved);
    }

    @Transactional
    public void cancelRegistration(UUID eventId, UUID userId) {
        // Lock event row
        Event event = eventRepository.findWithLockById(eventId)
                .orElseThrow(() -> new EventNotFoundException("Event not found: " + eventId));
        if ("draft".equalsIgnoreCase(event.getStatus())) {
            throw new EventNotFoundException("Event not found: " + eventId);
        }

        // 404 if caller has no registration (check FIRST before form status)
        Volunteer volunteer = volunteerRepository.findByEventIdAndUserId(eventId, userId)
                .orElseThrow(() -> new VolunteerNotFoundException("Registration not found"));

        // Allowed only while the form is open (else 409)
        RegistrationForm form = registrationFormRepository.findByEventId(eventId).orElse(null);
        Instant now = Instant.now();
        if (isClosed(event, form, now)) {
            throw new EventConflictException("Registration is closed");
        }

        // NOTE: Run 8 must refuse cancel when the volunteer has plan assignments.

        volunteerRepository.delete(volunteer);
    }

    // ── Validation & mapping helpers ───────────────────────────────────────────

    private void applyAndValidate(Volunteer v, VolunteerRegistrationRequest req, RegistrationForm form) {
        // fullName
        String cleanedFullName = volunteerFieldValidator.validateAndCleanFullName(req.getFullName());
        v.setFullName(cleanedFullName);

        // phone
        String cleanedPhone = volunteerFieldValidator.validateAndCleanPhone(req.getPhone());
        v.setPhone(cleanedPhone);

        // department
        String cleanedDept = volunteerFieldValidator.validateAndCleanDepartment(req.getDepartment());
        v.setDepartment(cleanedDept);

        // yearOfStudy
        v.setYearOfStudy(req.getYearOfStudy());

        // pastEventsCount
        v.setPastEventsCount(req.getPastEventsCount());

        // maxHoursPerEvent
        v.setMaxHoursPerEvent(req.getMaxHoursPerEvent());

        // notificationPreference
        String cleanedNotif = volunteerFieldValidator.validateAndCleanNotificationPreference(req.getNotificationPreference());
        v.setNotificationPreference(cleanedNotif);

        // interests
        List<String> cleanedInterests = volunteerFieldValidator.cleanAndValidateInterests(req.getInterests());
        v.setInterests(cleanedInterests);

        // availability
        List<AvailabilitySlot> cleanedAvail = volunteerFieldValidator.cleanAndValidateAvailability(req.getAvailability());
        v.setAvailability(cleanedAvail);

        // skills matching offeredSkills canonical casing
        List<SkillLevel> cleanedSkills = cleanAndMatchSkills(req.getSkills(), form);
        v.setSkills(cleanedSkills);

        // extraAnswers
        Map<String, String> cleanedAnswers = cleanAndValidateExtraAnswers(req.getExtraAnswers(), form);
        v.setExtraAnswers(cleanedAnswers);
    }

    private List<SkillLevel> cleanAndMatchSkills(List<SkillLevel> rawSkills, RegistrationForm form) {
        List<SkillLevel> standardSkills = volunteerFieldValidator.cleanAndValidateSkills(rawSkills);
        if (standardSkills == null) {
            standardSkills = Collections.emptyList();
        }

        List<String> offeredSkills = (form != null && form.getOfferedSkills() != null)
                ? form.getOfferedSkills() : Collections.emptyList();

        if (offeredSkills.isEmpty()) {
            if (!standardSkills.isEmpty()) {
                throw new ValidationException("No skills are offered for this registration form");
            }
            return Collections.emptyList();
        }

        Map<String, String> canonicalMap = new HashMap<>();
        for (String offered : offeredSkills) {
            canonicalMap.put(offered.trim().toLowerCase(Locale.ROOT), offered.trim());
        }

        List<SkillLevel> matched = new ArrayList<>();
        for (SkillLevel sl : standardSkills) {
            String lower = sl.getName().trim().toLowerCase(Locale.ROOT);
            String canonical = canonicalMap.get(lower);
            if (canonical == null) {
                throw new ValidationException("Skill is not offered on this registration form");
            }
            matched.add(new SkillLevel(canonical, sl.getLevel()));
        }

        return matched;
    }

    private Map<String, String> cleanAndValidateExtraAnswers(Map<String, String> rawAnswers, RegistrationForm form) {
        if (rawAnswers == null) {
            throw new ValidationException("extraAnswers is required");
        }

        List<ExtraQuestion> questions = (form != null && form.getExtraQuestions() != null)
                ? form.getExtraQuestions() : Collections.emptyList();

        Set<String> allowedIds = questions.stream().map(ExtraQuestion::getId).collect(Collectors.toSet());

        Map<String, String> result = new HashMap<>();
        for (Map.Entry<String, String> entry : rawAnswers.entrySet()) {
            String key = entry.getKey();
            String val = entry.getValue();

            if (!allowedIds.contains(key)) {
                throw new ValidationException("Unknown extra question key");
            }
            if (val == null) {
                throw new ValidationException("Answer value cannot be null");
            }

            TextRules.rejectControlCharsAllowWhitespace(val, "extraAnswers");
            String trimmedVal = val.trim();
            if (trimmedVal.isEmpty() || trimmedVal.length() > 500) {
                throw new ValidationException("Each answer must be between 1 and 500 characters");
            }

            result.put(key, trimmedVal);
        }

        return result;
    }

    private boolean isClosed(Event event, RegistrationForm form, Instant now) {
        if ("done".equalsIgnoreCase(event.getStatus())) {
            return true;
        }
        if (form == null || !"open".equalsIgnoreCase(form.getStatus())) {
            return true;
        }
        if (form.getOpensAt() != null && now.isBefore(form.getOpensAt())) {
            return true;
        }
        if (form.getClosesAt() != null && now.isAfter(form.getClosesAt())) {
            return true;
        }
        return false;
    }

    private String computeState(Event event, RegistrationForm form, long count, boolean isRegistered, Instant now) {
        if (isRegistered) {
            return "registered";
        }
        if (isClosed(event, form, now)) {
            return "closed";
        }
        if (form != null && form.getMaxResponses() != null && count >= form.getMaxResponses()) {
            return "full";
        }
        return "open";
    }

    private VolunteerRegistrationResponse toRegistrationResponse(Volunteer v) {
        return new VolunteerRegistrationResponse(
                v.getId(),
                v.getEventId(),
                v.getUserId(),
                v.getFullName(),
                v.getEmail(),
                v.getPhone(),
                v.getDepartment(),
                v.getYearOfStudy(),
                v.getSkills() != null ? new ArrayList<>(v.getSkills()) : Collections.emptyList(),
                v.getPastEventsCount(),
                v.getAvailability() != null ? new ArrayList<>(v.getAvailability()) : Collections.emptyList(),
                v.getMaxHoursPerEvent(),
                v.getInterests() != null ? new ArrayList<>(v.getInterests()) : Collections.emptyList(),
                v.getNotificationPreference(),
                v.getStatus(),
                v.getRegisteredAt()
        );
    }
}
