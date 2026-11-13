package com.eventra.auth;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class SignupRateLimiter {

    private static final int MAX_ATTEMPTS = 5;
    private static final long WINDOW_SECONDS = 60 * 60; // 60 minutes
    private static final int MAX_MAP_SIZE = 10_000;

    static class Attempt {
        int count;
        Instant windowStart;

        Attempt(int count, Instant windowStart) {
            this.count = count;
            this.windowStart = windowStart;
        }
    }

    private final ConcurrentHashMap<String, Attempt> attempts = new ConcurrentHashMap<>();

    public boolean tryAcquire(String ip) {
        String cleanIp = ip != null ? ip.trim() : "";
        Instant now = Instant.now();

        // Memory bounding: purge expired entries when size exceeds 10,000
        if (attempts.size() > MAX_MAP_SIZE) {
            attempts.entrySet().removeIf(entry ->
                    now.isAfter(entry.getValue().windowStart.plusSeconds(WINDOW_SECONDS))
            );
        }

        AtomicBoolean allowed = new AtomicBoolean(false);

        attempts.compute(cleanIp, (k, existing) -> {
            if (existing == null || now.isAfter(existing.windowStart.plusSeconds(WINDOW_SECONDS))) {
                allowed.set(true);
                return new Attempt(1, now);
            }
            if (existing.count < MAX_ATTEMPTS) {
                allowed.set(true);
                return new Attempt(existing.count + 1, existing.windowStart);
            }
            // Already at or exceeding MAX_ATTEMPTS
            allowed.set(false);
            return existing;
        });

        return allowed.get();
    }

    public void reset() {
        attempts.clear();
    }

    int size() {
        return attempts.size();
    }

    void putForTest(String ip, int count, Instant windowStart) {
        attempts.put(ip, new Attempt(count, windowStart));
    }
}
