package com.eventra.event;

import com.eventra.auth.UserPrincipal;
import com.eventra.event.dto.CreateEventRequest;
import com.eventra.event.dto.EventResponse;
import com.eventra.event.dto.UpdateEventRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/events")
public class EventController {

    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    @GetMapping
    @PreAuthorize("hasRole('ORGANISER')")
    public ResponseEntity<List<EventResponse>> listEvents() {
        return ResponseEntity.ok(eventService.listAll());
    }

    @PostMapping
    @PreAuthorize("hasRole('ORGANISER')")
    public ResponseEntity<EventResponse> createEvent(
            @Valid @RequestBody CreateEventRequest req,
            @AuthenticationPrincipal UserPrincipal principal) {
        UUID createdById = principal != null ? principal.getId() : null;
        EventResponse created = eventService.create(req, createdById);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ORGANISER')")
    public ResponseEntity<EventResponse> getEvent(@PathVariable UUID id) {
        return ResponseEntity.ok(eventService.getById(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ORGANISER')")
    public ResponseEntity<EventResponse> updateEvent(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateEventRequest req) {
        return ResponseEntity.ok(eventService.update(id, req));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ORGANISER')")
    public ResponseEntity<Void> deleteEvent(@PathVariable UUID id) {
        eventService.delete(id);
        return ResponseEntity.noContent().build();
    }
}

