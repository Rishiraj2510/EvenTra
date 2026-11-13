package com.eventra.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

@Component
public class CsrfProtectionFilter extends OncePerRequestFilter {

    public static final String REQUIRED_HEADER = "X-Requested-With";
    public static final String REQUIRED_VALUE = "EvenTra";
    private static final Set<String> MUTATING_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String method = request.getMethod();
        String uri = request.getRequestURI();

        // Except POST /api/auth/login
        if (MUTATING_METHODS.contains(method)) {
            boolean isLogin = "POST".equalsIgnoreCase(method) && "/api/auth/login".equals(uri);
            if (!isLogin) {
                String header = request.getHeader(REQUIRED_HEADER);
                if (!REQUIRED_VALUE.equals(header)) {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType("application/json");
                    response.setCharacterEncoding("UTF-8");
                    response.getWriter().write("{\"error\":\"Missing or invalid " + REQUIRED_HEADER + " header\"}");
                    return;
                }
            }
        }

        filterChain.doFilter(request, response);
    }
}

