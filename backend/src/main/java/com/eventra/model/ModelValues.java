package com.eventra.model;

import java.util.Collections;
import java.util.List;

public final class ModelValues {

    public static final int MIN_YEAR_OF_STUDY = 1;
    public static final int MAX_YEAR_OF_STUDY = 4;

    public static final int MIN_HOURS_PER_EVENT = 1;
    public static final int MAX_HOURS_PER_EVENT = 24;
    public static final int MAX_SKILLS = 30;
    public static final int MAX_AVAILABILITY_SLOTS = 21;
    public static final int MAX_INTERESTS = 20;

    public static final List<String> SKILL_LEVELS = Collections.unmodifiableList(
            List.of("Beginner", "Intermediate", "Advanced")
    );

    public static final List<String> DAYS = Collections.unmodifiableList(
            List.of("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
    );

    public static final List<String> SLOTS = Collections.unmodifiableList(
            List.of("Morning", "Afternoon", "Evening")
    );

    private ModelValues() {
    }
}
