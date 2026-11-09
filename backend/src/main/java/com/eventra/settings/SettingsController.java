package com.eventra.settings;

import com.eventra.settings.dto.SettingsRequest;
import com.eventra.settings.dto.SettingsResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/settings")
public class SettingsController {

    private final SettingsService settingsService;

    public SettingsController(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @GetMapping
    @PreAuthorize("hasRole('ORGANISER')")
    public ResponseEntity<SettingsResponse> getSettings() {
        return ResponseEntity.ok(settingsService.getSettings());
    }

    @PutMapping
    @PreAuthorize("hasRole('ORGANISER')")
    public ResponseEntity<SettingsResponse> updateSettings(@Valid @RequestBody SettingsRequest req) {
        return ResponseEntity.ok(settingsService.updateSettings(req));
    }
}

