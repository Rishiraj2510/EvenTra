package com.eventra.auth;

import com.eventra.auth.dto.LoginRequest;
import com.eventra.auth.dto.UserResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final LoginRateLimiter rateLimiter;
    private final Environment environment;

    // Fixed dummy hash to ensure constant-time response for unknown email
    // BCrypt for dummy password "dummy_password_for_timing_protection"
    private static final String DUMMY_HASH = "$2a$10$w0gKqfIiqx15n0jDsl1vQ.4FwRz5s7i61i7eIqv7d5R5.cW3f1O0u";

    public AuthController(UserRepository userRepository,
                          PasswordEncoder passwordEncoder,
                          JwtService jwtService,
                          LoginRateLimiter rateLimiter,
                          Environment environment) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.rateLimiter = rateLimiter;
        this.environment = environment;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        String clientIp = getClientIp(httpRequest);
        String email = request.getEmail() != null ? request.getEmail().trim() : "";

        // Check rate limiting
        if (rateLimiter.isBlocked(email, clientIp)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .body(Map.of("error", "Too many failed login attempts. Please try again later."));
        }

        User user = userRepository.findByEmailIgnoreCase(email).orElse(null);

        boolean passwordMatches;
        if (user != null) {
            passwordMatches = passwordEncoder.matches(request.getPassword(), user.getPasswordHash());
        } else {
            // Verify against dummy hash to mitigate user enumeration through timing attacks
            passwordEncoder.matches(request.getPassword(), DUMMY_HASH);
            passwordMatches = false;
        }

        if (!passwordMatches) {
            rateLimiter.recordFailedAttempt(email, clientIp);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .body(Map.of("error", "Invalid email or password"));
        }

        rateLimiter.recordSuccess(email, clientIp);

        // Generate token
        String token = jwtService.generateToken(user.getId(), user.getRole(), user.getTokenVersion());

        boolean isDev = Arrays.asList(environment.getActiveProfiles()).contains("dev");
        boolean secure = !isDev;

        ResponseCookie cookie = ResponseCookie.from(JwtAuthenticationFilter.COOKIE_NAME, token)
                .httpOnly(true)
                .secure(secure)
                .path("/")
                .sameSite("Strict")
                .maxAge(jwtService.getExpirySeconds())
                .build();

        UserResponse response = new UserResponse(user.getId(), user.getFullName(), user.getEmail(), user.getRole());

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout() {
        boolean isDev = Arrays.asList(environment.getActiveProfiles()).contains("dev");
        boolean secure = !isDev;

        ResponseCookie cookie = ResponseCookie.from(JwtAuthenticationFilter.COOKIE_NAME, "")
                .httpOnly(true)
                .secure(secure)
                .path("/")
                .sameSite("Strict")
                .maxAge(0)
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(Map.of("message", "Logged out successfully"));
    }

    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(@AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .body(Map.of("error", "Unauthorized"));
        }

        UserResponse response = new UserResponse(
                principal.getId(),
                principal.getFullName(),
                principal.getEmail(),
                principal.getRole()
        );

        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(response);
    }

    private String getClientIp(HttpServletRequest request) {
        String xf = request.getHeader("X-Forwarded-For");
        if (xf != null && !xf.isBlank()) {
            return xf.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}

