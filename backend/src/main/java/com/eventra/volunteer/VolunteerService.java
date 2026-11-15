package com.eventra.volunteer;

import com.eventra.common.ValidationException;
import com.eventra.event.EventNotFoundException;
import com.eventra.event.EventRepository;
import com.eventra.volunteer.dto.UpdateVolunteerRequest;
import com.eventra.volunteer.dto.VolunteerResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class VolunteerService {

    private final VolunteerRepository volunteerRepository;
    private final EventRepository eventRepository;
    private final VolunteerFieldValidator fieldValidator;

    public VolunteerService(VolunteerRepository volunteerRepository,
                            EventRepository eventRepository,
                            VolunteerFieldValidator fieldValidator) {
        this.volunteerRepository = volunteerRepository;
        this.eventRepository = eventRepository;
        this.fieldValidator = fieldValidator;
    }

    public List<VolunteerResponse> listByEventId(UUID eventId) {
        if (!eventRepository.existsById(eventId)) {
            throw new EventNotFoundException("Event not found: " + eventId);
        }
        return volunteerRepository.findByEventId(eventId).stream()
                .map(this::toResponse)
                .toList();
    }

    public VolunteerResponse getById(UUID id) {
        Volunteer volunteer = volunteerRepository.findById(id)
                .orElseThrow(() -> new VolunteerNotFoundException("Volunteer not found: " + id));
        return toResponse(volunteer);
    }

    @Transactional
    public VolunteerResponse update(UUID id, UpdateVolunteerRequest req) {
        Volunteer volunteer = volunteerRepository.findById(id)
                .orElseThrow(() -> new VolunteerNotFoundException("Volunteer not found: " + id));

        // fullName
        if (req.getFullName() != null) {
            volunteer.setFullName(fieldValidator.validateAndCleanFullName(req.getFullName()));
        }

        // phone
        if (req.getPhone() != null) {
            volunteer.setPhone(fieldValidator.validateAndCleanPhone(req.getPhone()));
        }

        // department
        if (req.getDepartment() != null) {
            volunteer.setDepartment(fieldValidator.validateAndCleanDepartment(req.getDepartment()));
        }

        // yearOfStudy
        if (req.getYearOfStudy() != null) {
            if (req.getYearOfStudy() < 1 || req.getYearOfStudy() > 6) {
                throw new ValidationException("yearOfStudy must be between 1 and 6");
            }
            volunteer.setYearOfStudy(req.getYearOfStudy());
        }

        // skills
        if (req.getSkills() != null) {
            volunteer.setSkills(fieldValidator.cleanAndValidateSkills(req.getSkills()));
        }

        // pastEventsCount
        if (req.getPastEventsCount() != null) {
            if (req.getPastEventsCount() < 0) {
                throw new ValidationException("pastEventsCount must be 0 or more");
            }
            volunteer.setPastEventsCount(req.getPastEventsCount());
        }

        // availability
        if (req.getAvailability() != null) {
            volunteer.setAvailability(fieldValidator.cleanAndValidateAvailability(req.getAvailability()));
        }

        // maxHoursPerEvent
        if (req.getMaxHoursPerEvent() != null) {
            if (req.getMaxHoursPerEvent() < 1 || req.getMaxHoursPerEvent() > 24) {
                throw new ValidationException("maxHoursPerEvent must be between 1 and 24");
            }
            volunteer.setMaxHoursPerEvent(req.getMaxHoursPerEvent());
        }

        // interests
        if (req.getInterests() != null) {
            volunteer.setInterests(fieldValidator.cleanAndValidateInterests(req.getInterests()));
        }

        // notificationPreference (missing stays unchanged on PUT)
        if (req.getNotificationPreference() != null) {
            volunteer.setNotificationPreference(fieldValidator.validateAndCleanNotificationPreference(req.getNotificationPreference()));
        }

        // status
        if (req.getStatus() != null) {
            volunteer.setStatus(fieldValidator.validateAndCleanStatus(req.getStatus()));
        }

        return toResponse(volunteerRepository.save(volunteer));
    }

    @Transactional
    public void delete(UUID id) {
        Volunteer volunteer = volunteerRepository.findById(id)
                .orElseThrow(() -> new VolunteerNotFoundException("Volunteer not found: " + id));

        // NOTE: Run 8 must refuse the delete when the volunteer has plan assignments.
        volunteerRepository.delete(volunteer);
    }

    private VolunteerResponse toResponse(Volunteer v) {
        return new VolunteerResponse(
                v.getId(),
                v.getEventId(),
                v.getUserId(),
                v.getFullName(),
                v.getEmail(),
                v.getPhone(),
                v.getDepartment(),
                v.getYearOfStudy(),
                v.getSkills() != null ? new ArrayList<>(v.getSkills()) : new ArrayList<>(),
                v.getPastEventsCount(),
                v.getAvailability() != null ? new ArrayList<>(v.getAvailability()) : new ArrayList<>(),
                v.getMaxHoursPerEvent(),
                v.getInterests() != null ? new ArrayList<>(v.getInterests()) : new ArrayList<>(),
                v.getNotificationPreference(),
                v.getStatus() != null ? v.getStatus() : "active",
                v.getExtraAnswers() != null ? new HashMap<>(v.getExtraAnswers()) : new HashMap<>(),
                v.getRegisteredAt()
        );
    }
}
