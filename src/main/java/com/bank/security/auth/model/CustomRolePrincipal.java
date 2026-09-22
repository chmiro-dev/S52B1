package com.bank.security.auth.model;

import java.security.Principal;
import java.util.Objects;

public final class CustomRolePrincipal implements Principal {
    private final String name;

    public CustomRolePrincipal(String name) {
        Objects.requireNonNull(name, "Role name cannot be null");
        if (name.isBlank()) {
            throw new IllegalArgumentException("Role name cannot be blank");
        }
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CustomRolePrincipal that = (CustomRolePrincipal) o;
        return Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }
}