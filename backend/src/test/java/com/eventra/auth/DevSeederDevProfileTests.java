package com.eventra.auth;

import com.eventra.auth.dto.LoginRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests for DevAccountSeeder with the "dev" profile active.
 * Verifies: two users created, BCrypt hashes, login works, idempotent on second run.
 */
@SpringBootTest(properties = {
        "jwt.secret=eventra_test_super_secret_key_at_least_32_bytes_long!!"
})
@ActiveProfiles("dev")
@AutoConfigureMockMvc
class DevSeederDevProfileTests {

    @Autowired
    private DevAccountSeeder seeder;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @AfterEach
    void cleanUp() {
        userRepository.findByEmailIgnoreCase("organiser@eventra.local").ifPresent(userRepository::delete);
        userRepository.findByEmailIgnoreCase("volunteer@eventra.local").ifPresent(userRepository::delete);
    }

    @Test
    void devProfileSeederCreatesTwoUsersLoginWorksAndIdempotent() throws Exception {
        // Ensure clean start
        userRepository.findByEmailIgnoreCase("organiser@eventra.local").ifPresent(userRepository::delete);
        userRepository.findByEmailIgnoreCase("volunteer@eventra.local").ifPresent(userRepository::delete);

        // 1. Run seeder
        seeder.seedAccounts();

        // Verify organiser
        User org = userRepository.findByEmailIgnoreCase("organiser@eventra.local").orElse(null);
        assertNotNull(org, "Organiser account must be created");
        assertEquals("organiser", org.getRole());
        assertTrue(org.getPasswordHash().startsWith("$2"), "Password hash must start with $2");
        assertNotNull(seeder.lastOrganiserPassword, "Captured organiser password must not be null");
        assertTrue(passwordEncoder.matches(seeder.lastOrganiserPassword, org.getPasswordHash()));

        // Verify volunteer
        User vol = userRepository.findByEmailIgnoreCase("volunteer@eventra.local").orElse(null);
        assertNotNull(vol, "Volunteer account must be created");
        assertEquals("volunteer", vol.getRole());
        assertTrue(vol.getPasswordHash().startsWith("$2"), "Password hash must start with $2");
        assertNotNull(seeder.lastVolunteerPassword, "Captured volunteer password must not be null");
        assertTrue(passwordEncoder.matches(seeder.lastVolunteerPassword, vol.getPasswordHash()));

        // 2. Login with generated passwords works
        LoginRequest orgLogin = new LoginRequest("organiser@eventra.local", seeder.lastOrganiserPassword);
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(orgLogin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("organiser@eventra.local"))
                .andExpect(jsonPath("$.role").value("organiser"));

        LoginRequest volLogin = new LoginRequest("volunteer@eventra.local", seeder.lastVolunteerPassword);
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(volLogin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("volunteer@eventra.local"))
                .andExpect(jsonPath("$.role").value("volunteer"));

        // 3. Running seeder second time creates no duplicates and does not change either hash
        String orgHashBefore = org.getPasswordHash();
        String volHashBefore = vol.getPasswordHash();

        seeder.seedAccounts();

        User orgAfter = userRepository.findByEmailIgnoreCase("organiser@eventra.local").orElse(null);
        User volAfter = userRepository.findByEmailIgnoreCase("volunteer@eventra.local").orElse(null);
        assertNotNull(orgAfter);
        assertNotNull(volAfter);
        assertEquals(orgHashBefore, orgAfter.getPasswordHash(), "Organiser hash must not change");
        assertEquals(volHashBefore, volAfter.getPasswordHash(), "Volunteer hash must not change");
    }
}

