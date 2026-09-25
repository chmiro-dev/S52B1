package com.bank.security.auth;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.security.enterprise.identitystore.Pbkdf2PasswordHash;
import java.nio.charset.StandardCharsets;

@ApplicationScoped
public class PasswordHashHelper {

    @Inject
    private Pbkdf2PasswordHash passwordHash;

    public String hashPassword(String rawPassword) {
        if (rawPassword == null) {
            throw new IllegalArgumentException("Password cannot be null");
        }
        return passwordHash.generate(rawPassword.toCharArray());
    }

    // Accepts String hash
    public boolean verifyPassword(String rawPassword, String storedHash) {
        if (rawPassword == null || storedHash == null) {
            return false;
        }
        return passwordHash.verify(rawPassword.toCharArray(), storedHash);
    }

    // Accepts byte[] hash
    public boolean verifyPassword(String rawPassword, byte[] storedHashBytes) {
        if (rawPassword == null || storedHashBytes == null) {
            return false;
        }
        String storedHash = new String(storedHashBytes, StandardCharsets.UTF_8);
        return verifyPassword(rawPassword, storedHash);
    }
}