package com.eventra.common;

public final class TextRules {

    private TextRules() {
    }

    public static void rejectControlChars(String value, String fieldName) {
        if (value == null) {
            return;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isISOControl(c) || c == '\u0000') {
                throw new ValidationException(fieldName + " contains invalid characters");
            }
        }
    }

    public static void rejectControlCharsAllowWhitespace(String value, String fieldName) {
        if (value == null) {
            return;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\n' || c == '\r' || c == '\t') {
                continue;
            }
            if (Character.isISOControl(c) || c == '\u0000') {
                throw new ValidationException(fieldName + " contains invalid characters");
            }
        }
    }
}
