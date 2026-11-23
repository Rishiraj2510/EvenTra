package com.eventra.plan;

import com.eventra.auth.UserPrincipal;
import com.eventra.plan.dto.MyAssignmentResponse;
import com.eventra.plan.dto.PlanResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/events/{eventId}/plan")
public class PlanController {

    private final PlanService planService;

    public PlanController(PlanService planService) {
        this.planService = planService;
    }

    @GetMapping
    @PreAuthorize("hasRole('ORGANISER')")
    public ResponseEntity<PlanResponse> getPlan(@PathVariable UUID eventId) {
        return ResponseEntity.ok(planService.getPlan(eventId));
    }

    @PostMapping("/generate")
    @PreAuthorize("hasRole('ORGANISER')")
    public ResponseEntity<PlanResponse> generatePlan(@PathVariable UUID eventId) {
        planService.generate(eventId);
        return ResponseEntity.ok(planService.getPlan(eventId));
    }

    @PostMapping("/approve")
    @PreAuthorize("hasRole('ORGANISER')")
    public ResponseEntity<PlanResponse> approvePlan(@PathVariable UUID eventId) {
        planService.approve(eventId);
        return ResponseEntity.ok(planService.getPlan(eventId));
    }

    @PostMapping("/publish")
    @PreAuthorize("hasRole('ORGANISER')")
    public ResponseEntity<PlanResponse> publishPlan(@PathVariable UUID eventId) {
        planService.publish(eventId);
        return ResponseEntity.ok(planService.getPlan(eventId));
    }

    @GetMapping("/mine")
    @PreAuthorize("hasRole('VOLUNTEER')")
    public ResponseEntity<List<MyAssignmentResponse>> getMyPlan(
            @PathVariable UUID eventId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(planService.getMyAssignments(eventId, principal.getId()));
    }
}
