package com.eventra.volunteer;

import com.eventra.auth.UserPrincipal;
import com.eventra.volunteer.dto.VolunteerTaskResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class VolunteerTaskController {

    private final VolunteerTaskService volunteerTaskService;

    public VolunteerTaskController(VolunteerTaskService volunteerTaskService) {
        this.volunteerTaskService = volunteerTaskService;
    }

    @GetMapping("/api/volunteer/events/{eventId}/tasks")
    @PreAuthorize("hasRole('VOLUNTEER')")
    public ResponseEntity<List<VolunteerTaskResponse>> getVolunteerTasks(
            @PathVariable UUID eventId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(volunteerTaskService.getVolunteerTasks(eventId, principal.getId()));
    }
}
