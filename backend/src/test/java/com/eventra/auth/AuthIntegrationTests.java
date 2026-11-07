package com.eventra.auth;

import com.eventra.EvenTraApplication;
import com.eventra.auth.dto.LoginRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "jwt.secret=eventra_test_super_secret_key_at_least_32_bytes_long!!",
        "jwt.expiry-minutes=30"
})
@AutoConfigureMockMvc
class AuthIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private LoginRateLimiter rateLimiter;

    @Autowired
    private ObjectMapper objectMapper;

    private final List<UUID> createdUserIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        rateLimiter.reset();
    }

    @AfterEach
    void tearDown() {
        for (UUID id : createdUserIds) {
            userRepository.deleteById(id);
        }
        createdUserIds.clear();
        rateLimiter.reset();
    }

    private User createUser(String role, String rawPassword) {
        String randomSuffix = UUID.randomUUID().toString().substring(0, 8);
        User user = new User();
        user.setEmail("user_" + randomSuffix + "@test.local");
        user.setFullName("User " + randomSuffix);
        user.setRole(role);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setTokenVersion(0);
        user = userRepository.save(user);
        createdUserIds.add(user.getId());
        return user;
    }

    // -------------------------------------------------------------------------
    // 1. Login success sets cookie with HttpOnly and SameSite=Strict; body has no password or hash
    // -------------------------------------------------------------------------
    @Test
    void loginSuccessSetsCookieAndOmitsPassword() throws Exception {
        User user = createUser("organiser", "ValidPassword123!");

        LoginRequest request = new LoginRequest(user.getEmail(), "ValidPassword123!");

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(user.getId().toString()))
                .andExpect(jsonPath("$.fullName").value(user.getFullName()))
                .andExpect(jsonPath("$.email").value(user.getEmail()))
                .andExpect(jsonPath("$.role").value("organiser"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andReturn();

        String setCookie = result.getResponse().getHeader("Set-Cookie");
        assertNotNull(setCookie, "Set-Cookie header must be present");
        assertTrue(setCookie.contains("eventra_token="), "Cookie name must be eventra_token");
        assertTrue(setCookie.contains("HttpOnly"), "Cookie must be HttpOnly");
        assertTrue(setCookie.contains("SameSite=Strict"), "Cookie must have SameSite=Strict");
    }

    // -------------------------------------------------------------------------
    // 2. Wrong password and unknown email return identical status and body
    // -------------------------------------------------------------------------
    @Test
    void wrongPasswordAndUnknownEmailReturnIdenticalStatusAndBody() throws Exception {
        User user = createUser("volunteer", "CorrectPassword123!");

        LoginRequest wrongPwRequest = new LoginRequest(user.getEmail(), "WrongPassword!");
        MvcResult resWrongPw = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(wrongPwRequest)))
                .andExpect(status().isUnauthorized())
                .andReturn();

        LoginRequest unknownEmailRequest = new LoginRequest("nonexistent_" + UUID.randomUUID() + "@test.local", "SomePassword!");
        MvcResult resUnknownEmail = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(unknownEmailRequest)))
                .andExpect(status().isUnauthorized())
                .andReturn();

        assertEquals(resWrongPw.getResponse().getStatus(), resUnknownEmail.getResponse().getStatus());
        assertEquals(resWrongPw.getResponse().getContentAsString(), resUnknownEmail.getResponse().getContentAsString());
        assertTrue(resWrongPw.getResponse().getContentAsString().contains("Invalid email or password"));
    }

    // -------------------------------------------------------------------------
    // 3. /api/auth/me works with cookie, 401 without
    // -------------------------------------------------------------------------
    @Test
    void meWorksWithCookie401Without() throws Exception {
        User user = createUser("organiser", "Pass12345!");
        String token = jwtService.generateToken(user.getId(), user.getRole(), user.getTokenVersion());

        // Without cookie -> 401
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").exists());

        // With cookie -> 200
        mockMvc.perform(get("/api/auth/me")
                        .cookie(new Cookie("eventra_token", token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(user.getId().toString()))
                .andExpect(jsonPath("$.email").value(user.getEmail()))
                .andExpect(jsonPath("$.role").value("organiser"));
    }

    // -------------------------------------------------------------------------
    // 4. Incrementing token_version makes an existing token return 401
    // -------------------------------------------------------------------------
    @Test
    void incrementingTokenVersionInvalidatesToken() throws Exception {
        User user = createUser("volunteer", "Pass12345!");
        String token = jwtService.generateToken(user.getId(), user.getRole(), user.getTokenVersion());

        // Token is valid initially
        mockMvc.perform(get("/api/auth/me")
                        .cookie(new Cookie("eventra_token", token)))
                .andExpect(status().isOk());

        // Increment token version in database
        user.setTokenVersion(user.getTokenVersion() + 1);
        userRepository.save(user);

        // Same token should now be rejected with 401
        mockMvc.perform(get("/api/auth/me")
                        .cookie(new Cookie("eventra_token", token)))
                .andExpect(status().isUnauthorized());
    }

    // -------------------------------------------------------------------------
    // 5. Tampered token and alg=none token return 401
    // -------------------------------------------------------------------------
    @Test
    void tamperedAndAlgNoneTokensReturn401() throws Exception {
        User user = createUser("volunteer", "Pass12345!");
        String token = jwtService.generateToken(user.getId(), user.getRole(), user.getTokenVersion());

        // 1. Tampered signature
        String tampered = token.substring(0, token.length() - 5) + "ABCDE";
        mockMvc.perform(get("/api/auth/me")
                        .cookie(new Cookie("eventra_token", tampered)))
                .andExpect(status().isUnauthorized());

        // 2. alg=none token
        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                .subject(user.getId().toString())
                .claim("role", user.getRole())
                .claim("tv", user.getTokenVersion())
                .issueTime(new Date())
                .expirationTime(new Date(System.currentTimeMillis() + 3600000))
                .build();
        PlainJWT noneJwt = new PlainJWT(claimsSet);
        String noneToken = noneJwt.serialize();

        mockMvc.perform(get("/api/auth/me")
                        .cookie(new Cookie("eventra_token", noneToken)))
                .andExpect(status().isUnauthorized());

        // 3. Different HMAC algorithm or invalid secret
        SignedJWT foreignJwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claimsSet);
        JWSSigner foreignSigner = new MACSigner("a_different_secret_key_that_is_32_bytes_long!!".getBytes(StandardCharsets.UTF_8));
        foreignJwt.sign(foreignSigner);
        mockMvc.perform(get("/api/auth/me")
                        .cookie(new Cookie("eventra_token", foreignJwt.serialize())))
                .andExpect(status().isUnauthorized());
    }

    // -------------------------------------------------------------------------
    // 6. POST without X-Requested-With returns 403; with it passes
    // -------------------------------------------------------------------------
    @Test
    void mutatingRequestsRequireCsrfHeader() throws Exception {
        User user = createUser("organiser", "Pass12345!");
        String token = jwtService.generateToken(user.getId(), user.getRole(), user.getTokenVersion());

        // POST /api/auth/logout without X-Requested-With -> 403
        mockMvc.perform(post("/api/auth/logout")
                        .cookie(new Cookie("eventra_token", token)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").exists());

        // POST /api/auth/logout with wrong header value -> 403
        mockMvc.perform(post("/api/auth/logout")
                        .cookie(new Cookie("eventra_token", token))
                        .header("X-Requested-With", "WrongClient"))
                .andExpect(status().isForbidden());

        // POST /api/auth/logout with valid header -> 200
        mockMvc.perform(post("/api/auth/logout")
                        .cookie(new Cookie("eventra_token", token))
                        .header("X-Requested-With", "EvenTra"))
                .andExpect(status().isOk());
    }

    // -------------------------------------------------------------------------
    // 7. Volunteer gets 403 on test-only @PreAuthorize("hasRole('ORGANISER')"), organiser gets 200
    // -------------------------------------------------------------------------
    @Test
    void roleBasedAuthorization() throws Exception {
        User volunteer = createUser("volunteer", "Pass12345!");
        String volunteerToken = jwtService.generateToken(volunteer.getId(), volunteer.getRole(), volunteer.getTokenVersion());

        User organiser = createUser("organiser", "Pass12345!");
        String organiserToken = jwtService.generateToken(organiser.getId(), organiser.getRole(), organiser.getTokenVersion());

        // Volunteer accessing organiser-only endpoint -> 403
        mockMvc.perform(get("/api/test-role/organiser-only")
                        .cookie(new Cookie("eventra_token", volunteerToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Forbidden"));

        // Organiser accessing organiser-only endpoint -> 200
        mockMvc.perform(get("/api/test-role/organiser-only")
                        .cookie(new Cookie("eventra_token", organiserToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));
    }

    // -------------------------------------------------------------------------
    // 8. Sixth failed login returns 429
    // -------------------------------------------------------------------------
    @Test
    void sixthFailedLoginReturns429() throws Exception {
        User user = createUser("volunteer", "CorrectPassword123!");
        LoginRequest badRequest = new LoginRequest(user.getEmail(), "WrongPassword!");

        // 5 failed attempts -> 401
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(badRequest)))
                    .andExpect(status().isUnauthorized());
        }

        // 6th attempt -> 429
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badRequest)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error").exists());
    }

    // -------------------------------------------------------------------------
    // 9. App fails to start without JWT_SECRET outside dev
    // -------------------------------------------------------------------------
    @Test
    void appFailsToStartWithoutJwtSecretOutsideDev() {
        assertThrows(Throwable.class, () -> {
            try (ConfigurableApplicationContext ctx = new SpringApplicationBuilder(EvenTraApplication.class)
                    .profiles("default")
                    .properties("JWT_SECRET=", "jwt.secret=", "server.port=0")
                    .run()) {
                fail("Context should not start without JWT_SECRET");
            }
        });
    }

    // -------------------------------------------------------------------------
    // 10. Seed runner in dev profile creates two users, BCrypt hashes, login works, idempotent
    // -------------------------------------------------------------------------
    @Test
    void devAccountSeederCreatesAccountsAndIsIdempotent() {
        // Clean any pre-existing seed users before running test
        userRepository.findByEmailIgnoreCase("organiser@eventra.local").ifPresent(userRepository::delete);
        userRepository.findByEmailIgnoreCase("volunteer@eventra.local").ifPresent(userRepository::delete);

        try {
            DevAccountSeeder seeder = new DevAccountSeeder(userRepository, passwordEncoder);
            seeder.seedAccounts();

            User org = userRepository.findByEmailIgnoreCase("organiser@eventra.local").orElse(null);
            assertNotNull(org);
            assertEquals("organiser", org.getRole());
            assertTrue(org.getPasswordHash().startsWith("$2"), "Password hash must be BCrypt");
            assertNotNull(seeder.lastOrganiserPassword);
            assertTrue(passwordEncoder.matches(seeder.lastOrganiserPassword, org.getPasswordHash()));

            User vol = userRepository.findByEmailIgnoreCase("volunteer@eventra.local").orElse(null);
            assertNotNull(vol);
            assertEquals("volunteer", vol.getRole());
            assertTrue(vol.getPasswordHash().startsWith("$2"), "Password hash must be BCrypt");
            assertNotNull(seeder.lastVolunteerPassword);
            assertTrue(passwordEncoder.matches(seeder.lastVolunteerPassword, vol.getPasswordHash()));

            String orgHashBefore = org.getPasswordHash();
            String volHashBefore = vol.getPasswordHash();

            // Run second time -> must be idempotent, no hash changes
            seeder.seedAccounts();

            User orgAfter = userRepository.findByEmailIgnoreCase("organiser@eventra.local").orElse(null);
            User volAfter = userRepository.findByEmailIgnoreCase("volunteer@eventra.local").orElse(null);
            assertNotNull(orgAfter);
            assertNotNull(volAfter);
            assertEquals(orgHashBefore, orgAfter.getPasswordHash());
            assertEquals(volHashBefore, volAfter.getPasswordHash());

        } finally {
            // Clean up the two rows
            userRepository.findByEmailIgnoreCase("organiser@eventra.local").ifPresent(userRepository::delete);
            userRepository.findByEmailIgnoreCase("volunteer@eventra.local").ifPresent(userRepository::delete);
        }
    }
}
