package com.eventra.auth;

import com.eventra.auth.dto.LoginRequest;
import com.eventra.auth.dto.RegisterRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "jwt.secret=eventra_test_super_secret_key_at_least_32_bytes_long!!",
        "jwt.expiry-minutes=30",
        "app.signup.allowed-email-domains=acropolis.in"
})
@AutoConfigureMockMvc
class SignupIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SignupRateLimiter signupRateLimiter;

    @Autowired
    private LoginRateLimiter loginRateLimiter;

    @Autowired
    private ObjectMapper objectMapper;

    private final List<UUID> createdUserIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        signupRateLimiter.reset();
        loginRateLimiter.reset();
    }

    @AfterEach
    void tearDown() {
        for (UUID id : createdUserIds) {
            userRepository.deleteById(id);
        }
        createdUserIds.clear();
        signupRateLimiter.reset();
        loginRateLimiter.reset();
    }

    private void trackUser(UUID id) {
        if (id != null) {
            createdUserIds.add(id);
        }
    }

    // -------------------------------------------------------------------------
    // 1. Valid sign-up -> 201, role volunteer, cookie HttpOnly, /api/auth/me works, login works
    // -------------------------------------------------------------------------
    @Test
    void validSignupSucceedsAndCanLoginAndAccessMe() throws Exception {
        String rand = UUID.randomUUID().toString().substring(0, 8);
        String email = "student_" + rand + "@acropolis.in";
        String password = "StrongPassword123!";

        RegisterRequest request = new RegisterRequest("Jane Doe", email, password);

        MvcResult res = mockMvc.perform(post("/api/auth/register")
                        .header("X-Requested-With", "EvenTra")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.fullName").value("Jane Doe"))
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.role").value("volunteer"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andReturn();

        String userIdStr = objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asText();
        trackUser(UUID.fromString(userIdStr));

        // Cookie checks
        String setCookie = res.getResponse().getHeader("Set-Cookie");
        assertNotNull(setCookie);
        assertTrue(setCookie.contains("eventra_token="));
        assertTrue(setCookie.contains("HttpOnly"));
        assertTrue(setCookie.contains("SameSite=Strict"));

        // Extract cookie value for GET /api/auth/me
        String tokenVal = setCookie.split("eventra_token=")[1].split(";")[0];

        mockMvc.perform(get("/api/auth/me")
                        .cookie(new Cookie("eventra_token", tokenVal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userIdStr))
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.role").value("volunteer"));

        // Login with password works
        LoginRequest loginReq = new LoginRequest(email, password);
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userIdStr))
                .andExpect(jsonPath("$.role").value("volunteer"));
    }

    // -------------------------------------------------------------------------
    // 2. Body containing "role":"organiser" -> 400 (unknown field), no user created
    // -------------------------------------------------------------------------
    @Test
    void bodyWithRoleOrganiserRejectedAndNoUserCreated() throws Exception {
        String rand = UUID.randomUUID().toString().substring(0, 8);
        String email = "hacker_" + rand + "@acropolis.in";
        String payload = "{\"fullName\":\"Hacker\",\"email\":\"" + email + "\",\"password\":\"ValidPass123!\",\"role\":\"organiser\"}";

        mockMvc.perform(post("/api/auth/register")
                        .header("X-Requested-With", "EvenTra")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Unknown field"));

        assertTrue(userRepository.findByEmailIgnoreCase(email).isEmpty());
    }

    // -------------------------------------------------------------------------
    // 3. Email with uppercase & spaces stored trimmed and lowercased; 2nd sign-up -> 409
    // -------------------------------------------------------------------------
    @Test
    void emailNormalizedAndDuplicateReturns409() throws Exception {
        String rand = UUID.randomUUID().toString().substring(0, 8);
        String emailWithSpacesAndUpper = "  Student_" + rand + "@ACROPOLIS.IN  ";
        String normalizedEmail = "student_" + rand + "@acropolis.in";

        RegisterRequest request = new RegisterRequest("Test Student", emailWithSpacesAndUpper, "Password1234!");

        MvcResult res = mockMvc.perform(post("/api/auth/register")
                        .header("X-Requested-With", "EvenTra")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(normalizedEmail))
                .andReturn();

        String userIdStr = objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asText();
        trackUser(UUID.fromString(userIdStr));

        User user = userRepository.findById(UUID.fromString(userIdStr)).orElseThrow();
        assertEquals(normalizedEmail, user.getEmail());

        // Second sign-up with different case
        RegisterRequest dupRequest = new RegisterRequest("Different Name", "STUDENT_" + rand + "@acropolis.in", "Password1234!");
        mockMvc.perform(post("/api/auth/register")
                        .header("X-Requested-With", "EvenTra")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dupRequest)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Email already registered"));
    }

    // -------------------------------------------------------------------------
    // 4. Domain rejects: x@gmail.com, x@acropolis.in.evil.com, x@mail.acropolis.in,
    //    acropolis.in@evil.com, x@@acropolis.in -> 400; x@ACROPOLIS.IN accepted
    // -------------------------------------------------------------------------
    @Test
    void domainAllowlistValidation() throws Exception {
        List<String> rejectedEmails = List.of(
                "user@gmail.com",
                "user@acropolis.in.evil.com",
                "user@mail.acropolis.in",
                "acropolis.in@evil.com",
                "user@@acropolis.in"
        );

        for (String rejected : rejectedEmails) {
            RegisterRequest req = new RegisterRequest("Test User", rejected, "ValidPass123!");
            mockMvc.perform(post("/api/auth/register")
                            .header("X-Requested-With", "EvenTra")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").exists());
        }

        // Reset rate limiter so the 6th call in this test method doesn't hit the 5-attempt limit
        signupRateLimiter.reset();

        // x@ACROPOLIS.IN is accepted
        String rand = UUID.randomUUID().toString().substring(0, 8);
        String upperEmail = "valid_" + rand + "@ACROPOLIS.IN";
        RegisterRequest okReq = new RegisterRequest("Accepted User", upperEmail, "ValidPass123!");
        MvcResult res = mockMvc.perform(post("/api/auth/register")
                        .header("X-Requested-With", "EvenTra")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(okReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(upperEmail.toLowerCase(Locale.ROOT)))
                .andReturn();

        String userIdStr = objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asText();
        trackUser(UUID.fromString(userIdStr));
    }

    // -------------------------------------------------------------------------
    // 5. Password rules: 9 chars -> 400; 73-byte password -> 400; equal to email -> 400
    // -------------------------------------------------------------------------
    @Test
    void passwordRulesValidation() throws Exception {
        String email = "student_pw@acropolis.in";

        // 9 chars -> 400
        RegisterRequest shortPw = new RegisterRequest("User Name", email, "123456789");
        mockMvc.perform(post("/api/auth/register")
                        .header("X-Requested-With", "EvenTra")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(shortPw)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Password must be at least 10 characters"));

        // 73-byte password in UTF-8 -> 400
        String longPw73Bytes = "A".repeat(73);
        assertEquals(73, longPw73Bytes.getBytes(StandardCharsets.UTF_8).length);
        RegisterRequest longPw = new RegisterRequest("User Name", email, longPw73Bytes);
        mockMvc.perform(post("/api/auth/register")
                        .header("X-Requested-With", "EvenTra")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(longPw)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Password must not exceed 72 bytes"));

        // Password equal to email -> 400
        RegisterRequest sameAsEmail = new RegisterRequest("User Name", email, email);
        mockMvc.perform(post("/api/auth/register")
                        .header("X-Requested-With", "EvenTra")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sameAsEmail)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Password must not equal the email address"));
    }

    // -------------------------------------------------------------------------
    // 6. fullName blank or 1 character -> 400
    // -------------------------------------------------------------------------
    @Test
    void fullNameValidation() throws Exception {
        String email = "student_fn@acropolis.in";

        RegisterRequest blankName = new RegisterRequest("   ", email, "ValidPass123!");
        mockMvc.perform(post("/api/auth/register")
                        .header("X-Requested-With", "EvenTra")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(blankName)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());

        RegisterRequest oneCharName = new RegisterRequest("A", email, "ValidPass123!");
        mockMvc.perform(post("/api/auth/register")
                        .header("X-Requested-With", "EvenTra")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(oneCharName)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Full name must be between 2 and 100 characters"));
    }

    // -------------------------------------------------------------------------
    // 7. Missing X-Requested-With header -> rejected
    // -------------------------------------------------------------------------
    @Test
    void missingCsrfHeaderIsRejected() throws Exception {
        RegisterRequest req = new RegisterRequest("Jane Doe", "student_csrf@acropolis.in", "ValidPass123!");
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Missing or invalid X-Requested-With header"));
    }

    // -------------------------------------------------------------------------
    // 8. 6th attempt from the same IP within the hour -> 429
    // -------------------------------------------------------------------------
    @Test
    void sixthAttemptFromSameIpReturns429() throws Exception {
        RegisterRequest badReq = new RegisterRequest("Test", "invalid-domain@gmail.com", "ValidPass123!");

        // 5 attempts
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/auth/register")
                            .header("X-Requested-With", "EvenTra")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(badReq)))
                    .andExpect(status().isBadRequest());
        }

        // 6th attempt -> 429
        mockMvc.perform(post("/api/auth/register")
                        .header("X-Requested-With", "EvenTra")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badReq)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error").value("Too many attempts. Try again later."));
    }

    // -------------------------------------------------------------------------
    // 9. Stored password_hash starts with $2 and never appears in any response body
    // -------------------------------------------------------------------------
    @Test
    void storedPasswordHashStartsBCryptAndNeverReturnedInResponseBody() throws Exception {
        String rand = UUID.randomUUID().toString().substring(0, 8);
        String email = "hash_check_" + rand + "@acropolis.in";
        RegisterRequest req = new RegisterRequest("Hash Check", email, "MySecretPass123!");

        MvcResult res = mockMvc.perform(post("/api/auth/register")
                        .header("X-Requested-With", "EvenTra")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        String body = res.getResponse().getContentAsString();
        assertFalse(body.contains("passwordHash"));
        assertFalse(body.contains("MySecretPass123!"));
        assertFalse(body.contains("$2"));

        String userIdStr = objectMapper.readTree(body).get("id").asText();
        trackUser(UUID.fromString(userIdStr));

        User user = userRepository.findById(UUID.fromString(userIdStr)).orElseThrow();
        assertTrue(user.getPasswordHash().startsWith("$2"), "Password hash must start with $2");
    }

    // -------------------------------------------------------------------------
    // 10. Two concurrent sign-ups with the same email -> exactly one 201 and one 409
    // -------------------------------------------------------------------------
    @Test
    void concurrentSignupsWithSameEmailYieldsOne201AndOne409() throws Exception {
        String rand = UUID.randomUUID().toString().substring(0, 8);
        String email = "concurrent_" + rand + "@acropolis.in";
        RegisterRequest req = new RegisterRequest("Concurrent User", email, "ValidPass123!");
        String jsonPayload = objectMapper.writeValueAsString(req);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch readyLatch = new CountDownLatch(2);
        CountDownLatch startLatch = new CountDownLatch(1);

        AtomicInteger status201Count = new AtomicInteger();
        AtomicInteger status409Count = new AtomicInteger();

        List<Future<Void>> futures = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            futures.add(executor.submit(() -> {
                readyLatch.countDown();
                startLatch.await();
                MvcResult mvcRes = mockMvc.perform(post("/api/auth/register")
                                .header("X-Requested-With", "EvenTra")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(jsonPayload))
                        .andReturn();

                int sc = mvcRes.getResponse().getStatus();
                if (sc == 201) {
                    status201Count.incrementAndGet();
                    try {
                        String id = objectMapper.readTree(mvcRes.getResponse().getContentAsString()).get("id").asText();
                        synchronized (createdUserIds) {
                            createdUserIds.add(UUID.fromString(id));
                        }
                    } catch (Exception ignored) {}
                } else if (sc == 409) {
                    status409Count.incrementAndGet();
                }
                return null;
            }));
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown();

        for (Future<Void> f : futures) {
            f.get(10, TimeUnit.SECONDS);
        }
        executor.shutdown();

        assertEquals(1, status201Count.get(), "Exactly one concurrent registration must succeed with 201");
        assertEquals(1, status409Count.get(), "Exactly one concurrent registration must fail with 409");
    }

    // -------------------------------------------------------------------------
    // 11. 100 parallel tryAcquire calls for 1 IP result in exactly 5 true
    // -------------------------------------------------------------------------
    @Test
    void parallelTryAcquireYieldsExactlyFiveAllowed() throws Exception {
        signupRateLimiter.reset();
        String testIp = "192.168.1.100";
        int totalThreads = 100;
        ExecutorService executor = Executors.newFixedThreadPool(totalThreads);
        CountDownLatch readyLatch = new CountDownLatch(totalThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger allowedCount = new AtomicInteger();
        AtomicInteger rejectedCount = new AtomicInteger();

        List<Future<Void>> futures = new ArrayList<>();
        for (int i = 0; i < totalThreads; i++) {
            futures.add(executor.submit(() -> {
                readyLatch.countDown();
                startLatch.await();
                boolean allowed = signupRateLimiter.tryAcquire(testIp);
                if (allowed) {
                    allowedCount.incrementAndGet();
                } else {
                    rejectedCount.incrementAndGet();
                }
                return null;
            }));
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown();

        for (Future<Void> f : futures) {
            f.get(10, TimeUnit.SECONDS);
        }
        executor.shutdown();

        assertEquals(5, allowedCount.get(), "Exactly 5 parallel attempts must be allowed");
        assertEquals(95, rejectedCount.get(), "Remaining 95 parallel attempts must be rejected");
    }

    // -------------------------------------------------------------------------
    // 12. Map purging when size exceeds 10,000 entries
    // -------------------------------------------------------------------------
    @Test
    void mapPurgingWhenSizeExceedsTenThousand() {
        signupRateLimiter.reset();

        Instant expiredInstant = Instant.now().minusSeconds(3660);

        // Pre-fill 10,001 expired entries
        for (int i = 1; i <= 10_001; i++) {
            signupRateLimiter.putForTest("expired-ip-" + i, 5, expiredInstant);
        }

        assertEquals(10_001, signupRateLimiter.size(), "Initial size should be 10,001");

        // Calling tryAcquire with a new IP triggers size check (> 10,000) and purges expired entries
        boolean acquired = signupRateLimiter.tryAcquire("active-ip-1");
        assertTrue(acquired, "New IP should be allowed");

        // Only the active IP should remain
        assertEquals(1, signupRateLimiter.size(), "All 10,001 expired entries should have been purged");
    }
}

