package com.eventra.auth;

import com.eventra.auth.dto.LoginRequest;
import com.eventra.auth.dto.RegisterRequest;
import com.eventra.auth.dto.UserResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final LoginRateLimiter rateLimiter;
    private final SignupRateLimiter signupRateLimiter;
    private final SignupService signupService;
    private final AuthCookieHelper cookieHelper;

    // Fixed dummy hash to ensure constant-time response for unknown email
    // BCrypt for dummy password "dummy_password_for_timing_protection"
    private static final String DUMMY_HASH = "$2a$10$w0gKqfIiqx15n0jDsl1vQ.4FwRz5s7i61i7eIqv7d5R5.cW3f1O0u";

    public AuthController(UserRepository userRepository,
                          PasswordEncoder passwordEncoder,
                          JwtService jwtService,
                          LoginRateLimiter rateLimiter,
                          SignupRateLimiter signupRateLimiter,
                          SignupService signupService,
                          AuthCookieHelper cookieHelper) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.rateLimiter = rateLimiter;
        this.signupRateLimiter = signupRateLimiter;
        this.signupService = signupService;
        this.cookieHelper = cookieHelper;
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
        ResponseCookie cookie = cookieHelper.createAuthCookie(token);

        UserResponse response = new UserResponse(user.getId(), user.getFullName(), user.getEmail(), user.getRole());

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout() {
        ResponseCookie cookie = cookieHelper.createLogoutCookie();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(Map.of("message", "Logged out successfully"));
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request, HttpServletRequest httpRequest) {
        String clientIp = httpRequest.getRemoteAddr();

        if (!signupRateLimiter.tryAcquire(clientIp)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .body(Map.of("error", "Too many attempts. Try again later."));
        }

        User user = signupService.registerVolunteer(request);

        String token = jwtService.generateToken(user.getId(), user.getRole(), user.getTokenVersion());
        ResponseCookie cookie = cookieHelper.createAuthCookie(token);

        UserResponse response = new UserResponse(user.getId(), user.getFullName(), user.getEmail(), user.getRole());

        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(response);
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
        return request.getRemoteAddr();
    }
}

