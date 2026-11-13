package com.eventra.auth;

import org.springframework.core.env.Environment;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Component
public class AuthCookieHelper {

    private final JwtService jwtService;
    private final Environment environment;

    public AuthCookieHelper(JwtService jwtService, Environment environment) {
        this.jwtService = jwtService;
        this.environment = environment;
    }

    public ResponseCookie createAuthCookie(String token) {
        boolean isDev = Arrays.asList(environment.getActiveProfiles()).contains("dev");
        boolean secure = !isDev;

        return ResponseCookie.from(JwtAuthenticationFilter.COOKIE_NAME, token)
                .httpOnly(true)
                .secure(secure)
                .path("/")
                .sameSite("Strict")
                .maxAge(jwtService.getExpirySeconds())
                .build();
    }

    public ResponseCookie createLogoutCookie() {
        boolean isDev = Arrays.asList(environment.getActiveProfiles()).contains("dev");
        boolean secure = !isDev;

        return ResponseCookie.from(JwtAuthenticationFilter.COOKIE_NAME, "")
                .httpOnly(true)
                .secure(secure)
                .path("/")
                .sameSite("Strict")
                .maxAge(0)
                .build();
    }
}

