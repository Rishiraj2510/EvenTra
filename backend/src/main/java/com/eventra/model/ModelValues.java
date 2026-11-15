package com.eventra.model;

import java.util.Collections;
import java.util.List;

public final class ModelValues {

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
