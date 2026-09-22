package com.bank.security.auth;

import com.bank.security.auth.model.CustomPrincipal;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.security.auth.Subject;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Set;

public class JaasAuthenticationService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private final byte[] hmacSecretKey;

    public JaasAuthenticationService(byte[] hmacSecretKey) {
        if (hmacSecretKey == null || hmacSecretKey.length < 32) {
            throw new IllegalArgumentException("HMAC key must be at least 256 bits (32 bytes)");
        }
        this.hmacSecretKey = hmacSecretKey;
    }

    public String authenticateAndIssueToken(CallbackHandler callbackHandler) throws LoginException {
        // Step 1: Perform JAAS Authentication
        LoginContext lc = new LoginContext("BankSecurityApp", callbackHandler);
        lc.login();

        // Step 2: Extract Identity safely from Subject
        Subject subject = lc.getSubject();
        Set<CustomPrincipal> principals = subject.getPrincipals(CustomPrincipal.class);

        String username = principals.stream()
                .map(CustomPrincipal::getName)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Authenticated Subject lacks a CustomPrincipal identity"));

        // Step 3: Generate Payload and HMAC Signature
        long expirationTimeMs = System.currentTimeMillis() + 3600000; // 1 hour validity
        String payload = "sub=" + username + "&exp=" + expirationTimeMs;
        byte[] payloadBytes = payload.getBytes(StandardCharsets.UTF_8);

        byte[] signature = computeHmacSha256(payloadBytes);

        // Step 4: Combine into URL-safe Session Token
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        return encoder.encodeToString(payloadBytes) + "." + encoder.encodeToString(signature);
    }

    private byte[] computeHmacSha256(byte[] data) {
        try {
            Mac hmac = Mac.getInstance(HMAC_ALGORITHM);
            hmac.init(new SecretKeySpec(this.hmacSecretKey, HMAC_ALGORITHM));
            return hmac.doFinal(data);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("Failed to compute HMAC-SHA256 signature", e);
        }
    }
}