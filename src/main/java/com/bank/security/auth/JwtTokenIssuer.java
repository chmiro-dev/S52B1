package com.bank.security.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * JWT Issuer service designed for Java 25 and GlassFish runtime environments.
 * Enforces strong key checks (256-bit minimum) and loads keys strictly via system properties or environment variables.
 */
public class JwtTokenIssuer {

    private static final Logger LOGGER = Logger.getLogger(JwtTokenIssuer.class.getName());

    public static final String SYS_PROP_JWT_SECRET = "jwt.secret";
    public static final String ENV_JWT_SECRET = "JWT_SECRET";
    private static final int MIN_KEY_BYTES = 32; // 256 bits for HS256

    private final SecretKey secretKey;
    private final String issuer;
    private final long tokenValidityMinutes;

    public JwtTokenIssuer() {
        this("BankSecurityIssuer", 60);
    }

    public JwtTokenIssuer(String issuer, long tokenValidityMinutes) {
        this.issuer = Objects.requireNonNull(issuer, "Issuer cannot be null");
        this.tokenValidityMinutes = tokenValidityMinutes;
        this.secretKey = initializeSecretKey();
    }

    private SecretKey initializeSecretKey() {
        // 1. Resolve key from GlassFish System Property or OS Environment
        String rawSecret = System.getProperty(SYS_PROP_JWT_SECRET);
        if (rawSecret == null || rawSecret.isBlank()) {
            rawSecret = System.getenv(ENV_JWT_SECRET);
        }

        // 2. Strict Check: Prevent missing keys / hardcoded fallbacks
        if (rawSecret == null || rawSecret.isBlank()) {
            throw new IllegalStateException(
                "JWT Startup Failed: Missing secret key. Set '-Djwt.secret' in GlassFish JVM options or set 'JWT_SECRET' environment variable."
            );
        }

        byte[] keyBytes = rawSecret.getBytes(StandardCharsets.UTF_8);

        // 3. Strict Check: Enforce minimum 256-bit requirement
        if (keyBytes.length < MIN_KEY_BYTES) {
            throw new IllegalArgumentException(
                "JWT Startup Failed: Secret key must be at least " + MIN_KEY_BYTES + " bytes (256 bits). Provided: " + keyBytes.length + " bytes."
            );
        }

        LOGGER.info("JwtTokenIssuer initialized successfully with 256-bit+ key.");
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateToken(String username, List<String> roles) {
        var now = Instant.now();
        var expiration = now.plus(tokenValidityMinutes, ChronoUnit.MINUTES);

        return Jwts.builder()
                .subject(username)
                .issuer(issuer)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiration))
                .claims(Map.of("roles", roles))
                .signWith(secretKey, Jwts.SIG.HS256)
                .compact();
    }

    public Claims validateAndParseToken(String token) throws JwtException {
        Jws<Claims> claimsJws = Jwts.parser()
                .verifyWith(secretKey)
                .requireIssuer(issuer)
                .build()
                .parseSignedClaims(token);

        return claimsJws.getPayload();
    }
}