package com.eventra.task;

public enum TaskStatus {
    pending("pending"),
    in_progress("in-progress"),
    completed("completed");

    private final String value;

    TaskStatus(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static TaskStatus fromString(String val) {
        if (val == null) {
            return null;
        }
        for (TaskStatus status : values()) {
            if (status.value.equals(val)) {
                return status;
            }
        }
        return null;
    }
}
