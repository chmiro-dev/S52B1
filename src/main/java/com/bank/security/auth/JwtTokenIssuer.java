package com.bank.security.auth;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.Base64;

public class JwtTokenIssuer {

    private static final String SIGNING_ALG = "SHA256withECDSA";
    private static final String ENCRYPTION_ALG = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;

    private final PrivateKey privateKey;
    private final PublicKey publicKey;
    private final SecretKey secretKey;

    public JwtTokenIssuer(PrivateKey privateKey, PublicKey publicKey, SecretKey secretKey) {
        this.privateKey = privateKey;
        this.publicKey = publicKey;
        this.secretKey = secretKey;
    }

    /**
     * Phase 1: Sign the raw payload using ECDSA
     */
    public String sign(String payload) {
        try {
            Signature signature = Signature.getInstance(SIGNING_ALG);
            signature.initSign(this.privateKey);

            byte[] payloadBytes = payload.getBytes(StandardCharsets.UTF_8);
            signature.update(payloadBytes);
            byte[] sigBytes = signature.sign();

            String encPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(payloadBytes);
            String encSignature = Base64.getUrlEncoder().withoutPadding().encodeToString(sigBytes);

            return encPayload + "." + encSignature;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to digitally sign token payload", e);
        }
    }

    /**
     * Phase 2: Encrypt the signed JWS token using AES-GCM
     */
    public String encrypt(String signedToken) {
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            SecureRandomHolder.INSTANCE.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ENCRYPTION_ALG);
            cipher.init(Cipher.ENCRYPT_MODE, this.secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));

            byte[] cipherText = cipher.doFinal(signedToken.getBytes(StandardCharsets.UTF_8));

            ByteBuffer buffer = ByteBuffer.allocate(iv.length + cipherText.length);
            buffer.put(iv);
            buffer.put(cipherText);

            return Base64.getUrlEncoder().withoutPadding().encodeToString(buffer.array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to encrypt signed token", e);
        }
    }

    /**
     * Reverse Phase 2: Decrypt the AES-GCM cipher payload
     */
    public String decrypt(String encryptedToken) {
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(encryptedToken);
            ByteBuffer buffer = ByteBuffer.wrap(decoded);

            byte[] iv = new byte[GCM_IV_LENGTH];
            buffer.get(iv);

            byte[] cipherText = new byte[buffer.remaining()];
            buffer.get(cipherText);

            Cipher cipher = Cipher.getInstance(ENCRYPTION_ALG);
            cipher.init(Cipher.DECRYPT_MODE, this.secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));

            byte[] plainText = cipher.doFinal(cipherText);
            return new String(plainText, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new SecurityException("Token decryption failed: invalid key or tampered cipher", e);
        }
    }

    /**
     * Reverse Phase 1: Verify the ECDSA signature and extract original payload
     */
    public String verifyAndExtractPayload(String signedToken) {
        try {
            String[] parts = signedToken.split("\\.");
            if (parts.length != 2) {
                throw new IllegalArgumentException("Invalid signed token structure");
            }

            byte[] payloadBytes = Base64.getUrlDecoder().decode(parts[0]);
            byte[] signatureBytes = Base64.getUrlDecoder().decode(parts[1]);

            Signature signature = Signature.getInstance(SIGNING_ALG);
            signature.initVerify(this.publicKey);
            signature.update(payloadBytes);

            if (!signature.verify(signatureBytes)) {
                throw new SecurityException("Digital signature verification failed!");
            }

            return new String(payloadBytes, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new SecurityException("Token verification error", e);
        }
    }

    public String issueToken(String payload) {
        return encrypt(sign(payload));
    }

    public String processToken(String token) {
        return verifyAndExtractPayload(decrypt(token));
    }

    // Thread-safe singleton holder for SecureRandom
    private static class SecureRandomHolder {
        private static final SecureRandom INSTANCE = new SecureRandom();
    }
}