package com.eventra.profile;

import com.eventra.auth.UserPrincipal;
import com.eventra.profile.dto.UpdateUserProfileRequest;
import com.eventra.profile.dto.UserProfileResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UserProfileController {

    private final UserProfileService userProfileService;

    public UserProfileController(UserProfileService userProfileService) {
        this.userProfileService = userProfileService;
    }

    @GetMapping("/api/me/profile")
    @PreAuthorize("hasRole('VOLUNTEER')")
    public ResponseEntity<UserProfileResponse> getProfile(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(userProfileService.getProfile(principal.getId()));
    }

    @PutMapping("/api/me/profile")
    @PreAuthorize("hasRole('VOLUNTEER')")
    public ResponseEntity<UserProfileResponse> updateProfile(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody UpdateUserProfileRequest req) {
        return ResponseEntity.ok(userProfileService.updateProfile(principal.getId(), req));
    }
}
