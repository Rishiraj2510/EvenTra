package com.eventra.registration;

import com.eventra.registration.dto.RegistrationFormResponse;
import com.eventra.registration.dto.UpdateRegistrationFormRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/events/{eventId}/registration-form")
public class RegistrationFormController {

    private final RegistrationFormService registrationFormService;

    public RegistrationFormController(RegistrationFormService registrationFormService) {
        this.registrationFormService = registrationFormService;
    }

    @GetMapping
    @PreAuthorize("hasRole('ORGANISER')")
    public ResponseEntity<RegistrationFormResponse> getRegistrationForm(@PathVariable UUID eventId) {
        return ResponseEntity.ok(registrationFormService.getForm(eventId));
    }

    @PutMapping
    @PreAuthorize("hasRole('ORGANISER')")
    public ResponseEntity<RegistrationFormResponse> updateRegistrationForm(
            @PathVariable UUID eventId,
            @Valid @RequestBody UpdateRegistrationFormRequest req) {
        return ResponseEntity.ok(registrationFormService.updateForm(eventId, req));
    }

    @PostMapping("/open")
    @PreAuthorize("hasRole('ORGANISER')")
    public ResponseEntity<RegistrationFormResponse> openRegistrationForm(@PathVariable UUID eventId) {
        return ResponseEntity.ok(registrationFormService.openForm(eventId));
    }

    @PostMapping("/close")
    @PreAuthorize("hasRole('ORGANISER')")
    public ResponseEntity<RegistrationFormResponse> closeRegistrationForm(@PathVariable UUID eventId) {
        return ResponseEntity.ok(registrationFormService.closeForm(eventId));
    }
}
