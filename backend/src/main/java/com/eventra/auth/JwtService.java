package com.eventra.auth;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import jakarta.annotation.PostConstruct;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Service
public class JwtService {

    private final JwtProperties properties;
    private final Environment environment;
    private byte[] secretBytes;

    public JwtService(JwtProperties properties, Environment environment) {
        this.properties = properties;
        this.environment = environment;
    }

    @PostConstruct
    public void validateSecret() {
        String secret = properties.getSecret();
        boolean isDev = Arrays.asList(environment.getActiveProfiles()).contains("dev");

        if (secret == null || secret.trim().getBytes(StandardCharsets.UTF_8).length < 32) {
            if (!isDev) {
                throw new IllegalStateException("JWT_SECRET must be provided and be at least 32 bytes in non-dev profile");
            }
        }
        if (secret != null) {
            this.secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        }
    }

    public String generateToken(UUID userId, String role, int tokenVersion) {
        try {
            Instant now = Instant.now();
            Instant exp = now.plus(properties.getExpiryMinutes(), ChronoUnit.MINUTES);

            JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                    .subject(userId.toString())
                    .claim("role", role)
                    .claim("tv", tokenVersion)
                    .issueTime(Date.from(now))
                    .expirationTime(Date.from(exp))
                    .build();

            // Algorithm HS256, pinned
            SignedJWT signedJWT = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claimsSet);
            JWSSigner signer = new MACSigner(secretBytes);
            signedJWT.sign(signer);

            return signedJWT.serialize();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate JWT", e);
        }
    }

    public JWTClaimsSet verifyToken(String token) {
        try {
            SignedJWT signedJWT = SignedJWT.parse(token);

            // Reject any algorithm other than HS256 (including none, HS384, HS512, RS256, etc.)
            if (!JWSAlgorithm.HS256.equals(signedJWT.getHeader().getAlgorithm())) {
                return null;
            }

            JWSVerifier verifier = new MACVerifier(secretBytes);
            if (!signedJWT.verify(verifier)) {
                return null;
            }

            JWTClaimsSet claims = signedJWT.getJWTClaimsSet();
            Date exp = claims.getExpirationTime();
            if (exp == null || exp.before(new Date())) {
                return null;
            }

            return claims;
        } catch (Exception e) {
            return null;
        }
    }

    public long getExpirySeconds() {
        return properties.getExpiryMinutes() * 60;
    }
}
