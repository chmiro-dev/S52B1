package com.bank.security.auth;

import com.bank.security.domain.UserEntity;
import com.bank.security.repository.UserRepository; // Or direct JPA EntityManager/JDBC access
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.security.enterprise.credential.Credential;
import jakarta.security.enterprise.credential.UsernamePasswordCredential;
import jakarta.security.enterprise.identitystore.CredentialValidationResult;
import jakarta.security.enterprise.identitystore.IdentityStore;

import java.util.EnumSet;
import java.util.Set;

@ApplicationScoped
public class CustomDatabaseIdentityStore implements IdentityStore {

    @Inject
    private UserRepository userRepository;

    @Inject
    private PasswordHashHelper passwordHashHelper; // Hashing helper required by Phase 2

    @Override
    public Set<ValidationType> validationTypes() {
        return EnumSet.of(ValidationType.VALIDATE);
    }

    @Override
    public CredentialValidationResult validate(Credential credential) {
        if (!(credential instanceof UsernamePasswordCredential usernamePasswordCredential)) {
            return CredentialValidationResult.NOT_VALIDATED_RESULT;
        }

        String username = usernamePasswordCredential.getCaller();
        String rawPassword = usernamePasswordCredential.getPasswordAsString();

        // 1. Fetch user entity from repository
        UserEntity user = userRepository.findByUsername(username).orElse(null);
        if (user == null) {
            return CredentialValidationResult.INVALID_RESULT;
        }

        // 2. Verify hashed password (Argon2 / PBKDF2)
        boolean isValidPassword = passwordHashHelper.verifyPassword(rawPassword, user.getPasswordHash());
        if (!isValidPassword) {
            return CredentialValidationResult.INVALID_RESULT;
        }

        Set<String> roles = user.getRoles();

        return new CredentialValidationResult(
                user.getUsername(),
                roles);
    }
}