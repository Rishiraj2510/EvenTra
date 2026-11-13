package com.eventra.auth;

import com.eventra.auth.dto.RegisterRequest;
import com.eventra.common.EventValidationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class SignupService {

    private static final Pattern BASIC_EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final SignupProperties signupProperties;

    public SignupService(UserRepository userRepository,
                         PasswordEncoder passwordEncoder,
                         SignupProperties signupProperties) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.signupProperties = signupProperties;
    }

    @Transactional
    public User registerVolunteer(RegisterRequest request) {
        String fullName = validateAndNormalizeFullName(request.getFullName());
        String email = validateAndNormalizeEmail(request.getEmail());
        validatePassword(request.getPassword(), email);

        if (userRepository.findByEmailIgnoreCase(email).isPresent()) {
            throw new EmailAlreadyRegisteredException("Email already registered");
        }

        User user = new User();
        user.setFullName(fullName);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setRole("volunteer");
        user.setTokenVersion(0);

        try {
            return userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            throw new EmailAlreadyRegisteredException("Email already registered");
        }
    }

    private String validateAndNormalizeFullName(String raw) {
        if (raw == null) {
            throw new EventValidationException("Full name is required");
        }
        String trimmed = raw.trim();
        if (trimmed.length() < 2 || trimmed.length() > 100) {
            throw new EventValidationException("Full name must be between 2 and 100 characters");
        }
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (Character.isISOControl(c)) {
                throw new EventValidationException("Full name must not contain control characters");
            }
        }
        return trimmed;
    }

    private String validateAndNormalizeEmail(String raw) {
        if (raw == null) {
            throw new EventValidationException("Email is required");
        }
        String trimmed = raw.trim();

        // Reject whitespace inside email
        if (trimmed.contains(" ") || trimmed.contains("\t") || trimmed.contains("\n") || trimmed.contains("\r")) {
            throw new EventValidationException("Email must not contain whitespace");
        }

        if (trimmed.length() > 254) {
            throw new EventValidationException("Email exceeds maximum length of 254 characters");
        }

        // Count '@' occurrences: must be exactly one
        int atCount = 0;
        int atIndex = -1;
        for (int i = 0; i < trimmed.length(); i++) {
            if (trimmed.charAt(i) == '@') {
                atCount++;
                atIndex = i;
            }
        }
        if (atCount != 1) {
            throw new EventValidationException("Invalid email format");
        }

        // Regex check
        if (!BASIC_EMAIL_PATTERN.matcher(trimmed).matches()) {
            throw new EventValidationException("Invalid email format");
        }

        String domain = trimmed.substring(atIndex + 1).toLowerCase(Locale.ROOT);
        if (!signupProperties.isDomainAllowed(domain)) {
            throw new EventValidationException("Sign-up is limited to college email addresses");
        }

        return trimmed.toLowerCase(Locale.ROOT);
    }

    private void validatePassword(String rawPassword, String normalizedEmail) {
        if (rawPassword == null) {
            throw new EventValidationException("Password is required");
        }

        if (rawPassword.length() < 10) {
            throw new EventValidationException("Password must be at least 10 characters");
        }

        byte[] utf8Bytes = rawPassword.getBytes(StandardCharsets.UTF_8);
        if (utf8Bytes.length > 72) {
            throw new EventValidationException("Password must not exceed 72 bytes");
        }

        if (rawPassword.equalsIgnoreCase(normalizedEmail)) {
            throw new EventValidationException("Password must not equal the email address");
        }
    }
}

