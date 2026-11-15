package com.eventra.profile;

import com.eventra.auth.User;
import com.eventra.auth.UserRepository;
import com.eventra.model.AvailabilitySlot;
import com.eventra.model.SkillLevel;
import com.eventra.profile.dto.UpdateUserProfileRequest;
import com.eventra.profile.dto.UserProfileResponse;
import com.eventra.volunteer.VolunteerFieldValidator;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
public class UserProfileService {

    private final UserRepository userRepository;
    private final VolunteerFieldValidator fieldValidator;

    public UserProfileService(UserRepository userRepository,
                              VolunteerFieldValidator fieldValidator) {
        this.userRepository = userRepository;
        this.fieldValidator = fieldValidator;
    }

    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        return toResponse(user);
    }

    @Transactional
    public UserProfileResponse updateProfile(UUID userId, UpdateUserProfileRequest req) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        // Validate and clean fields using VolunteerFieldValidator and TextRules
        String cleanedFullName = fieldValidator.validateAndCleanFullName(req.getFullName());
        String cleanedPhone = fieldValidator.validateAndCleanPhone(req.getPhone());
        String cleanedDept = fieldValidator.validateAndCleanDepartment(req.getDepartment());
        String cleanedNotif = fieldValidator.validateAndCleanNotificationPreference(req.getNotificationPreference());
        List<SkillLevel> cleanedSkills = fieldValidator.cleanAndValidateSkills(req.getSkills());
        List<AvailabilitySlot> cleanedAvail = fieldValidator.cleanAndValidateAvailability(req.getAvailability());
        List<String> cleanedInterests = fieldValidator.cleanAndValidateInterests(req.getInterests());

        user.setFullName(cleanedFullName);
        user.setPhone(cleanedPhone);
        user.setDepartment(cleanedDept);
        user.setYearOfStudy(req.getYearOfStudy());
        user.setSkills(cleanedSkills != null ? cleanedSkills : new ArrayList<>());
        user.setAvailability(cleanedAvail != null ? cleanedAvail : new ArrayList<>());
        user.setMaxHours(req.getMaxHoursPerEvent());
        user.setInterests(cleanedInterests != null ? cleanedInterests : new ArrayList<>());
        user.setNotificationPreference(cleanedNotif);
        user.setUpdatedAt(java.time.Instant.now());

        User saved = userRepository.save(user);
        return toResponse(saved);
    }

    private UserProfileResponse toResponse(User user) {
        return new UserProfileResponse(
                user.getEmail(),
                user.getFullName(),
                user.getPhone(),
                user.getDepartment(),
                user.getYearOfStudy(),
                user.getSkills() != null ? new ArrayList<>(user.getSkills()) : Collections.emptyList(),
                user.getPastEventsCount(),
                user.getAvailability() != null ? new ArrayList<>(user.getAvailability()) : Collections.emptyList(),
                user.getMaxHours(),
                user.getInterests() != null ? new ArrayList<>(user.getInterests()) : Collections.emptyList(),
                user.getNotificationPreference()
        );
    }
}
