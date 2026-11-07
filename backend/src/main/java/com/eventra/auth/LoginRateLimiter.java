package com.eventra.auth;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class LoginRateLimiter {

    private static final int MAX_ATTEMPTS = 5;
    private static final long WINDOW_SECONDS = 15 * 60; // 15 minutes

    private static class Attempt {
        int count;
        Instant windowStart;

        Attempt(Instant windowStart) {
            this.count = 1;
            this.windowStart = windowStart;
        }
    }

    private final ConcurrentHashMap<String, Attempt> attempts = new ConcurrentHashMap<>();

    public boolean isBlocked(String email, String ip) {
        String key = buildKey(email, ip);
        Attempt attempt = attempts.get(key);
        if (attempt == null) {
            return false;
        }

        Instant now = Instant.now();
        if (now.isAfter(attempt.windowStart.plusSeconds(WINDOW_SECONDS))) {
            attempts.remove(key);
            return false;
        }

        return attempt.count >= MAX_ATTEMPTS;
    }

    public void recordFailedAttempt(String email, String ip) {
        String key = buildKey(email, ip);
        Instant now = Instant.now();

        attempts.compute(key, (k, existing) -> {
            if (existing == null || now.isAfter(existing.windowStart.plusSeconds(WINDOW_SECONDS))) {
                return new Attempt(now);
            }
            existing.count++;
            return existing;
        });
    }

    public void recordSuccess(String email, String ip) {
        attempts.remove(buildKey(email, ip));
    }

    public void reset() {
        attempts.clear();
    }

    private String buildKey(String email, String ip) {
        String cleanEmail = email != null ? email.trim().toLowerCase() : "";
        String cleanIp = ip != null ? ip.trim() : "";
        return cleanEmail + "|" + cleanIp;
    }
}

