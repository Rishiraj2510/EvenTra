package com.eventra.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Optional;

@Component
@Profile("dev")
public class DevAccountSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DevAccountSeeder.class);

    // Characters for generating random passwords: letters and digits excluding ambiguous chars (0, O, l, 1, I)
    private static final String PASSWORD_CHARS = "23456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz";
    private static final int PASSWORD_LENGTH = 16;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    // Package-visible hook for testing only
    String lastOrganiserPassword;
    String lastVolunteerPassword;

    public DevAccountSeeder(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        seedAccounts();
    }

    public void seedAccounts() {
        Optional<User> existingOrganiser = userRepository.findByEmailIgnoreCase("organiser@eventra.local");
        Optional<User> existingVolunteer = userRepository.findByEmailIgnoreCase("volunteer@eventra.local");

        String organiserPw = null;
        String volunteerPw = null;

        if (existingOrganiser.isEmpty()) {
            organiserPw = generatePassword();
            User user = new User();
            user.setEmail("organiser@eventra.local");
            user.setFullName("EvenTra Organiser");
            user.setRole("organiser");
            user.setPasswordHash(passwordEncoder.encode(organiserPw));
            userRepository.save(user);
            this.lastOrganiserPassword = organiserPw;
        } else {
            log.info("Dev account already exists: organiser@eventra.local");
        }

        if (existingVolunteer.isEmpty()) {
            volunteerPw = generatePassword();
            User user = new User();
            user.setEmail("volunteer@eventra.local");
            user.setFullName("Aarav Volunteer");
            user.setRole("volunteer");
            user.setPasswordHash(passwordEncoder.encode(volunteerPw));
            userRepository.save(user);
            this.lastVolunteerPassword = volunteerPw;
        } else {
            log.info("Dev account already exists: volunteer@eventra.local");
        }

        if (organiserPw != null || volunteerPw != null) {
            System.out.println("=== DEV ACCOUNTS CREATED (shown once, not stored) ===");
            if (organiserPw != null) {
                System.out.println("organiser@eventra.local / " + organiserPw);
            }
            if (volunteerPw != null) {
                System.out.println("volunteer@eventra.local / " + volunteerPw);
            }
        }
    }

    private String generatePassword() {
        StringBuilder sb = new StringBuilder(PASSWORD_LENGTH);
        for (int i = 0; i < PASSWORD_LENGTH; i++) {
            int idx = RANDOM.nextInt(PASSWORD_CHARS.length());
            sb.append(PASSWORD_CHARS.charAt(idx));
        }
        return sb.toString();
    }
}

