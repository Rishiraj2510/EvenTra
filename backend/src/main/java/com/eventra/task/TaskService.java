package com.eventra.task;

import com.eventra.common.TextRules;
import com.eventra.common.ValidationException;
import com.eventra.event.EventNotFoundException;
import com.eventra.event.EventRepository;
import com.eventra.task.dto.CreateTaskRequest;
import com.eventra.task.dto.TaskResponse;
import com.eventra.task.dto.UpdateTaskRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class TaskService {

    private final TaskRepository taskRepository;
    private final EventRepository eventRepository;

    public TaskService(TaskRepository taskRepository, EventRepository eventRepository) {
        this.taskRepository = taskRepository;
        this.eventRepository = eventRepository;
    }

    public List<TaskResponse> listByEventId(UUID eventId) {
        if (!eventRepository.existsById(eventId)) {
            throw new EventNotFoundException("Event not found: " + eventId);
        }
        return taskRepository.findByEventId(eventId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public TaskResponse create(CreateTaskRequest req) {
        if (!eventRepository.existsById(req.getEventId())) {
            throw new EventNotFoundException("Event not found: " + req.getEventId());
        }

        // Validate text rules
        TextRules.rejectControlChars(req.getTitle(), "title");
        TextRules.rejectControlChars(req.getLocation(), "location");
        TextRules.rejectControlCharsAllowWhitespace(req.getDescription(), "description");

        // Validate times
        if (req.getEndTime().isBefore(req.getStartTime()) || req.getEndTime().equals(req.getStartTime())) {
            throw new ValidationException("endTime must be strictly after startTime");
        }

        // Validate skills
        List<String> mandatory = cleanAndValidateSkills(req.getMandatorySkills(), "mandatorySkills");
        List<String> preferred = cleanAndValidateSkills(req.getPreferredSkills(), "preferredSkills");
        validateNoSkillOverlap(mandatory, preferred);

        // Validate status
        String statusStr;
        if (req.getStatus() == null || req.getStatus().isBlank()) {
            statusStr = TaskStatus.pending.value();
        } else {
            TaskStatus status = TaskStatus.fromString(req.getStatus());
            if (status == null) {
                throw new ValidationException("Invalid status");
            }
            statusStr = status.value();
        }

        Task task = new Task();
        task.setEventId(req.getEventId());
        task.setTitle(req.getTitle().trim());
        task.setDescription(req.getDescription());
        task.setTaskDate(req.getDate());
        task.setStartTime(req.getStartTime());
        task.setEndTime(req.getEndTime());
        task.setHeadcount(req.getHeadcount());
        task.setMandatorySkills(mandatory);
        task.setPreferredSkills(preferred);
        task.setCritical(Boolean.TRUE.equals(req.getCritical()));
        task.setLocation(req.getLocation());
        task.setStatus(statusStr);

        return toResponse(taskRepository.save(task));
    }

    public TaskResponse getById(UUID id) {
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new TaskNotFoundException("Task not found: " + id));
        return toResponse(task);
    }

    @Transactional
    public TaskResponse update(UUID id, UpdateTaskRequest req) {
        // NOTE: Run 8 must mark an existing plan stale when time, headcount or skills change.
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new TaskNotFoundException("Task not found: " + id));

        // Validate text rules
        TextRules.rejectControlChars(req.getTitle(), "title");
        TextRules.rejectControlChars(req.getLocation(), "location");
        TextRules.rejectControlCharsAllowWhitespace(req.getDescription(), "description");

        // Validate times
        if (req.getEndTime().isBefore(req.getStartTime()) || req.getEndTime().equals(req.getStartTime())) {
            throw new ValidationException("endTime must be strictly after startTime");
        }

        // Validate skills
        List<String> mandatory = cleanAndValidateSkills(req.getMandatorySkills(), "mandatorySkills");
        List<String> preferred = cleanAndValidateSkills(req.getPreferredSkills(), "preferredSkills");
        validateNoSkillOverlap(mandatory, preferred);

        // Status is required on update
        if (req.getStatus() == null || req.getStatus().isBlank()) {
            throw new ValidationException("Invalid status");
        }
        TaskStatus status = TaskStatus.fromString(req.getStatus());
        if (status == null) {
            throw new ValidationException("Invalid status");
        }

        task.setTitle(req.getTitle().trim());
        task.setDescription(req.getDescription());
        task.setTaskDate(req.getDate());
        task.setStartTime(req.getStartTime());
        task.setEndTime(req.getEndTime());
        task.setHeadcount(req.getHeadcount());
        task.setMandatorySkills(mandatory);
        task.setPreferredSkills(preferred);
        task.setCritical(Boolean.TRUE.equals(req.getCritical()));
        task.setLocation(req.getLocation());
        task.setStatus(status.value());

        return toResponse(taskRepository.save(task));
    }

    @Transactional
    public void delete(UUID id) {
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new TaskNotFoundException("Task not found: " + id));
        // NOTE: Run 8 must refuse the delete when the task has plan assignments.
        taskRepository.delete(task);
    }

    private List<String> cleanAndValidateSkills(List<String> rawSkills, String fieldName) {
        if (rawSkills == null || rawSkills.isEmpty()) {
            return new ArrayList<>();
        }
        if (rawSkills.size() > 20) {
            throw new ValidationException(fieldName + " must not exceed 20 items");
        }

        List<String> result = new ArrayList<>();
        Set<String> seenLower = new HashSet<>();

        for (String raw : rawSkills) {
            if (raw == null) {
                throw new ValidationException("Skill must not be null");
            }
            TextRules.rejectControlChars(raw, fieldName);
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

    private void validateNoSkillOverlap(List<String> mandatory, List<String> preferred) {
        Set<String> mandatoryLower = new HashSet<>();
        for (String s : mandatory) {
            mandatoryLower.add(s.toLowerCase(Locale.ROOT));
        }
        for (String s : preferred) {
            if (mandatoryLower.contains(s.toLowerCase(Locale.ROOT))) {
                throw new ValidationException("A skill may not appear in both mandatory and preferred lists");
            }
        }
    }

    private TaskResponse toResponse(Task task) {
        return new TaskResponse(
                task.getId(),
                task.getEventId(),
                task.getTitle(),
                task.getDescription(),
                task.getTaskDate(),
                task.getStartTime(),
                task.getEndTime(),
                task.getHeadcount(),
                task.getMandatorySkills() != null ? new ArrayList<>(task.getMandatorySkills()) : new ArrayList<>(),
                task.getPreferredSkills() != null ? new ArrayList<>(task.getPreferredSkills()) : new ArrayList<>(),
                task.isCritical(),
                task.getLocation(),
                task.getStatus()
        );
    }
}
