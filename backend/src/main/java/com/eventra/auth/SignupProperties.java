package com.eventra.auth;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class SignupProperties {

    @Value("${app.signup.allowed-email-domains:${ALLOWED_EMAIL_DOMAINS:acropolis.in}}")
    private String allowedEmailDomainsRaw;

    private Set<String> allowedDomains = Collections.emptySet();

    public SignupProperties() {}

    public SignupProperties(String rawDomains) {
        this.allowedEmailDomainsRaw = rawDomains;
        init();
    }

    @PostConstruct
    public void init() {
        if (allowedEmailDomainsRaw == null) {
            throw new IllegalStateException("Allowed email domains configuration cannot be null or empty");
        }
        Set<String> parsed = new HashSet<>();
        for (String part : allowedEmailDomainsRaw.split(",")) {
            String trimmed = part.trim().toLowerCase(Locale.ROOT);
            if (!trimmed.isEmpty()) {
                parsed.add(trimmed);
            }
        }
        if (parsed.isEmpty()) {
            throw new IllegalStateException("Allowed email domains list is empty. App refuses to start.");
        }
        this.allowedDomains = Collections.unmodifiableSet(parsed);
    }

    public Set<String> getAllowedDomains() {
        return allowedDomains;
    }

    public boolean isDomainAllowed(String domain) {
        if (domain == null) return false;
        return allowedDomains.contains(domain.toLowerCase(Locale.ROOT));
    }
}

