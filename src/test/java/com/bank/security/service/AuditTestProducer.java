package com.bank.security.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import jakarta.enterprise.inject.Alternative;
import jakarta.enterprise.inject.Default;
import jakarta.annotation.Priority;
import jakarta.interceptor.Interceptor;
import jakarta.security.enterprise.SecurityContext;
import jakarta.security.enterprise.authentication.mechanism.http.AuthenticationParameters;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.security.Principal;
import java.util.Set;

@Alternative
@Priority(Interceptor.Priority.APPLICATION + 1)
@ApplicationScoped
public class AuditTestProducer {

    private static final EntityManagerFactory EMF = Persistence.createEntityManagerFactory("bankTestPU");
    private static EntityManager entityManager;

    @Produces
    @Default
    @ApplicationScoped
    public EntityManager produceEntityManager() {
        if (entityManager == null || !entityManager.isOpen()) {
            entityManager = EMF.createEntityManager();
        }
        return entityManager;
    }

    @Produces
    @ApplicationScoped
    public SecurityContext produceSecurityContext() {
        return new SecurityContext() {
            @Override
            public Principal getCallerPrincipal() {
                return () -> "test_user";
            }

            @Override
            public <T extends Principal> Set<T> getPrincipalsByType(Class<T> pType) {
                return Set.of();
            }

            @Override
            public boolean isCallerInRole(String role) {
                return false;
            }

            @Override
            public Set<String> getAllDeclaredCallerRoles() {
                return Set.of();
            }

            @Override
            public boolean hasAccessToWebResource(String resource, String... methods) {
                return true;
            }

            @Override
            public jakarta.security.enterprise.AuthenticationStatus authenticate(
                    HttpServletRequest request,
                    HttpServletResponse response,
                    AuthenticationParameters parameters) {
                return jakarta.security.enterprise.AuthenticationStatus.SUCCESS;
            }
        };
    }
}