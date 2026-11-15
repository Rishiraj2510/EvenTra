package com.eventra.volunteer;

import com.eventra.common.TextRules;
import com.eventra.common.ValidationException;
import com.eventra.model.AvailabilitySlot;
import com.eventra.model.ModelValues;
import com.eventra.model.SkillLevel;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Pattern;

@Component
public class VolunteerFieldValidator {

    private static final Set<String> ALLOWED_NOTIF_PREFS = Set.of("email", "whatsapp");
    private static final Set<String> ALLOWED_STATUSES = Set.of("active", "inactive");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^[0-9+\\-() ]+$");

    public String validateAndCleanFullName(String fullName) {
        if (fullName == null) {
            return null;
        }
        TextRules.rejectControlChars(fullName, "fullName");
        String trimmedName = fullName.trim();
        if (trimmedName.length() < 2 || trimmedName.length() > 100) {
            throw new ValidationException("fullName must be between 2 and 100 characters");
        }
        return trimmedName;
    }

    public String validateAndCleanPhone(String phone) {
        if (phone == null) {
            return null;
        }
        TextRules.rejectControlChars(phone, "phone");
        String trimmedPhone = phone.trim();
        if (trimmedPhone.isEmpty()) {
            return null;
        }
        if (trimmedPhone.length() > 20 || !PHONE_PATTERN.matcher(trimmedPhone).matches()) {
            throw new ValidationException("phone must be at most 20 characters and contain only digits, spaces, +, -, parentheses");
        }
        return trimmedPhone;
    }

    public String validateAndCleanDepartment(String department) {
        if (department == null) {
            return null;
        }
        TextRules.rejectControlChars(department, "department");
        String trimmedDept = department.trim();
        if (trimmedDept.length() > 100) {
            throw new ValidationException("department must not exceed 100 characters");
        }
        return trimmedDept.isEmpty() ? null : trimmedDept;
    }

    public String validateAndCleanNotificationPreference(String notificationPreference) {
        if (notificationPreference == null) {
            return null;
        }
        TextRules.rejectControlChars(notificationPreference, "notificationPreference");
        String trimmedPref = notificationPreference.trim().toLowerCase(Locale.ROOT);
        if (!ALLOWED_NOTIF_PREFS.contains(trimmedPref)) {
            throw new ValidationException("notificationPreference must be email or whatsapp");
        }
        return trimmedPref;
    }

    public String validateAndCleanStatus(String status) {
        if (status == null) {
            return null;
        }
        TextRules.rejectControlChars(status, "status");
        String trimmedStatus = status.trim().toLowerCase(Locale.ROOT);
        if (!ALLOWED_STATUSES.contains(trimmedStatus)) {
            throw new ValidationException("status must be active or inactive");
        }
        return trimmedStatus;
    }

    public List<SkillLevel> cleanAndValidateSkills(List<SkillLevel> rawSkills) {
        if (rawSkills == null) {
            return null;
        }
        if (rawSkills.size() > ModelValues.MAX_SKILLS) {
            throw new ValidationException("skills must not exceed 30 items");
        }

        List<SkillLevel> result = new ArrayList<>();
        Set<String> seenLower = new HashSet<>();

        for (SkillLevel sl : rawSkills) {
            if (sl == null || sl.getName() == null || sl.getLevel() == null) {
                throw new ValidationException("Skill name and level are required");
            }
            TextRules.rejectControlChars(sl.getName(), "skills");
            TextRules.rejectControlChars(sl.getLevel(), "skills");

            String trimmedName = sl.getName().trim();
            if (trimmedName.isEmpty() || trimmedName.length() > 40) {
                throw new ValidationException("Each skill name must be between 1 and 40 characters");
            }

            // Validate level against ModelValues.SKILL_LEVELS
            String rawLevel = sl.getLevel().trim();
            String matchedLevel = null;
            for (String allowed : ModelValues.SKILL_LEVELS) {
                if (allowed.equalsIgnoreCase(rawLevel)) {
                    matchedLevel = allowed;
                    break;
                }
            }
            if (matchedLevel == null) {
                throw new ValidationException("Invalid skill level");
            }

            String lowerName = trimmedName.toLowerCase(Locale.ROOT);
            if (seenLower.add(lowerName)) {
                result.add(new SkillLevel(trimmedName, matchedLevel));
            }
        }

        return result;
    }

    public List<AvailabilitySlot> cleanAndValidateAvailability(List<AvailabilitySlot> rawSlots) {
        if (rawSlots == null) {
            return null;
        }
        if (rawSlots.size() > ModelValues.MAX_AVAILABILITY_SLOTS) {
            throw new ValidationException("availability must not exceed 21 entries");
        }

        List<AvailabilitySlot> result = new ArrayList<>();
        Set<String> seenPairs = new HashSet<>();

        for (AvailabilitySlot slot : rawSlots) {
            if (slot == null || slot.getDay() == null || slot.getSlot() == null) {
                throw new ValidationException("Availability day and slot are required");
            }
            TextRules.rejectControlChars(slot.getDay(), "availability");
            TextRules.rejectControlChars(slot.getSlot(), "availability");

            String trimmedDay = slot.getDay().trim();
            String matchedDay = null;
            for (String allowed : ModelValues.DAYS) {
                if (allowed.equalsIgnoreCase(trimmedDay)) {
                    matchedDay = allowed;
                    break;
                }
            }
            if (matchedDay == null) {
                throw new ValidationException("Invalid availability day");
            }

            String trimmedSlot = slot.getSlot().trim();
            String matchedSlot = null;
            for (String allowed : ModelValues.SLOTS) {
                if (allowed.equalsIgnoreCase(trimmedSlot)) {
                    matchedSlot = allowed;
                    break;
                }
            }
            if (matchedSlot == null) {
                throw new ValidationException("Invalid availability slot");
            }

            String pairKey = matchedDay.toLowerCase(Locale.ROOT) + ":" + matchedSlot.toLowerCase(Locale.ROOT);
            if (!seenPairs.add(pairKey)) {
                throw new ValidationException("Duplicate availability pair: " + matchedDay + " " + matchedSlot);
            }

            result.add(new AvailabilitySlot(matchedDay, matchedSlot));
        }

        return result;
    }

    public List<String> cleanAndValidateInterests(List<String> rawInterests) {
        if (rawInterests == null) {
            return null;
        }
        if (rawInterests.size() > ModelValues.MAX_INTERESTS) {
            throw new ValidationException("interests must not exceed 20 items");
        }

        List<String> result = new ArrayList<>();
        for (String item : rawInterests) {
            if (item == null) {
                throw new ValidationException("Interest item must not be null");
            }
            TextRules.rejectControlChars(item, "interests");
            String trimmed = item.trim();
            if (trimmed.isEmpty() || trimmed.length() > 40) {
                throw new ValidationException("Each interest must be between 1 and 40 characters");
            }
            result.add(trimmed);
        }
        return result;
    }
}
