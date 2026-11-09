package com.eventra.event;

public enum EventStatus {
    draft,
    planning,
    live,
    done;

    public static EventStatus fromString(String val) {
        if (val == null) {
            return null;
        }
        for (EventStatus status : values()) {
            if (status.name().equals(val)) {
                return status;
            }
        }
        return null;
    }
}

