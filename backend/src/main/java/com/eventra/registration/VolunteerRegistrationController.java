package com.eventra.registration;

import com.eventra.auth.UserPrincipal;
import com.eventra.registration.dto.RegistrationFormViewResponse;
import com.eventra.registration.dto.VolunteerEventItemResponse;
import com.eventra.registration.dto.VolunteerRegistrationRequest;
import com.eventra.registration.dto.VolunteerRegistrationResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
public class VolunteerRegistrationController {

    private final VolunteerRegistrationService volunteerRegistrationService;

    public VolunteerRegistrationController(VolunteerRegistrationService volunteerRegistrationService) {
        this.volunteerRegistrationService = volunteerRegistrationService;
    }

    @GetMapping("/api/volunteer/events")
    @PreAuthorize("hasRole('VOLUNTEER')")
    public ResponseEntity<List<VolunteerEventItemResponse>> listVolunteerEvents(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(volunteerRegistrationService.listVolunteerEvents(principal.getId()));
    }

    @GetMapping("/api/events/{eventId}/registration-form/view")
    @PreAuthorize("hasRole('VOLUNTEER')")
    public ResponseEntity<RegistrationFormViewResponse> getFormView(
            @PathVariable UUID eventId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(volunteerRegistrationService.getFormView(eventId, principal.getId()));
    }

    @GetMapping("/api/events/{eventId}/registrations/me")
    @PreAuthorize("hasRole('VOLUNTEER')")
    public ResponseEntity<VolunteerRegistrationResponse> getMyRegistration(
            @PathVariable UUID eventId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(volunteerRegistrationService.getMyRegistration(eventId, principal.getId()));
    }

    @PostMapping("/api/events/{eventId}/registrations/me")
    @PreAuthorize("hasRole('VOLUNTEER')")
    public ResponseEntity<VolunteerRegistrationResponse> register(
            @PathVariable UUID eventId,
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody VolunteerRegistrationRequest req) {
        VolunteerRegistrationResponse resp = volunteerRegistrationService.register(
                eventId, principal.getId(), principal.getEmail(), req
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(resp);
    }

    @PutMapping("/api/events/{eventId}/registrations/me")
    @PreAuthorize("hasRole('VOLUNTEER')")
    public ResponseEntity<VolunteerRegistrationResponse> updateRegistration(
            @PathVariable UUID eventId,
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody VolunteerRegistrationRequest req) {
        return ResponseEntity.ok(volunteerRegistrationService.updateRegistration(eventId, principal.getId(), req));
    }

    @DeleteMapping("/api/events/{eventId}/registrations/me")
    @PreAuthorize("hasRole('VOLUNTEER')")
    public ResponseEntity<Void> cancelRegistration(
            @PathVariable UUID eventId,
            @AuthenticationPrincipal UserPrincipal principal) {
        volunteerRegistrationService.cancelRegistration(eventId, principal.getId());
        return ResponseEntity.noContent().build();
    }
}
