package com.eventra.volunteer;

import com.eventra.volunteer.dto.UpdateVolunteerRequest;
import com.eventra.volunteer.dto.VolunteerResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/volunteers")
public class VolunteerController {

    private final VolunteerService volunteerService;

    public VolunteerController(VolunteerService volunteerService) {
        this.volunteerService = volunteerService;
    }

    @GetMapping
    @PreAuthorize("hasRole('ORGANISER')")
    public ResponseEntity<List<VolunteerResponse>> listVolunteers(@RequestParam UUID eventId) {
        return ResponseEntity.ok(volunteerService.listByEventId(eventId));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ORGANISER')")
    public ResponseEntity<VolunteerResponse> getVolunteer(@PathVariable UUID id) {
        return ResponseEntity.ok(volunteerService.getById(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ORGANISER')")
    public ResponseEntity<VolunteerResponse> updateVolunteer(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateVolunteerRequest req) {
        return ResponseEntity.ok(volunteerService.update(id, req));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ORGANISER')")
    public ResponseEntity<Void> deleteVolunteer(@PathVariable UUID id) {
        volunteerService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
