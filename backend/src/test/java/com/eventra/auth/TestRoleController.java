package com.eventra.auth;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/test-role")
public class TestRoleController {

    @GetMapping("/organiser-only")
    @PreAuthorize("hasRole('ORGANISER')")
    public Map<String, String> organiserOnly() {
        return Map.of("status", "ok", "message", "Organiser access granted");
    }
}

