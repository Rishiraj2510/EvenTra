package com.eventra.volunteer;

import com.eventra.event.Event;
import com.eventra.event.EventNotFoundException;
import com.eventra.event.EventRepository;
import com.eventra.task.Task;
import com.eventra.task.TaskRepository;
import com.eventra.volunteer.dto.VolunteerTaskResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
public class VolunteerTaskService {

    private final EventRepository eventRepository;
    private final TaskRepository taskRepository;
    private final VolunteerRepository volunteerRepository;

    public VolunteerTaskService(EventRepository eventRepository,
                                TaskRepository taskRepository,
                                VolunteerRepository volunteerRepository) {
        this.eventRepository = eventRepository;
        this.taskRepository = taskRepository;
        this.volunteerRepository = volunteerRepository;
    }

    @Transactional(readOnly = true)
    public List<VolunteerTaskResponse> getVolunteerTasks(UUID eventId, UUID userId) {
        // Event check: missing or draft -> 404
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EventNotFoundException("Event not found"));
        if ("draft".equalsIgnoreCase(event.getStatus())) {
            throw new EventNotFoundException("Event not found");
        }

        // Caller must have a volunteers row for that event -> 404 if not found
        volunteerRepository.findByEventIdAndUserId(eventId, userId)
                .orElseThrow(() -> new EventNotFoundException("Event not found"));

        // Run 8 must expose the volunteer's own assignments only after the plan is published (separate endpoint).

        List<Task> tasks = taskRepository.findByEventIdOrderByTaskDateAscStartTimeAscIdAsc(eventId);
        List<VolunteerTaskResponse> result = new ArrayList<>();
        for (Task t : tasks) {
            result.add(new VolunteerTaskResponse(
                    t.getId(),
                    t.getTitle(),
                    t.getDescription(),
                    t.getTaskDate(),
                    t.getStartTime(),
                    t.getEndTime(),
                    t.getLocation(),
                    t.isCritical(),
                    t.getMandatorySkills() != null ? new ArrayList<>(t.getMandatorySkills()) : Collections.emptyList(),
                    t.getPreferredSkills() != null ? new ArrayList<>(t.getPreferredSkills()) : Collections.emptyList()
            ));
        }

        return result;
    }
}
