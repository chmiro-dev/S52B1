package com.bank.security.auth.model;

import java.security.Principal;
import java.util.Objects;

/**
 * Represents the authenticated primary user identity within the JAAS Subject.
 */
public record CustomPrincipal(String name) implements Principal {

    public CustomPrincipal {
        Objects.requireNonNull(name, "Principal name cannot be null");
        if (name.isBlank()) {
            throw new IllegalArgumentException("Principal name cannot be blank");
        }
    }

    @Override
    public String getName() {
        return name;
    }
}