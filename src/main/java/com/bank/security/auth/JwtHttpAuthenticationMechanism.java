package com.bank.security.auth;

import io.jsonwebtoken.Claims;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.security.enterprise.AuthenticationException;
import jakarta.security.enterprise.AuthenticationStatus;
import jakarta.security.enterprise.authentication.mechanism.http.HttpAuthenticationMechanism;
import jakarta.security.enterprise.authentication.mechanism.http.HttpMessageContext;
import jakarta.security.enterprise.identitystore.CredentialValidationResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@ApplicationScoped
public class JwtHttpAuthenticationMechanism implements HttpAuthenticationMechanism {

    @Inject
    private JwtTokenIssuer jwtTokenIssuer;

    @Override
    public AuthenticationStatus validateRequest(HttpServletRequest request,
            HttpServletResponse response,
            HttpMessageContext httpMessageContext) throws AuthenticationException {

        String authHeader = request.getHeader("Authorization");

        // 1. Check for Bearer token in Authorization header
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);

            try {
                // 2. Validate token signature and extract claims
                Claims claims = jwtTokenIssuer.validateAndParseToken(token);
                String username = claims.getSubject();

                @SuppressWarnings("unchecked")
                List<String> rolesList = claims.get("roles", List.class);
                Set<String> roles = rolesList != null ? new HashSet<>(rolesList) : Set.of();

                // 3. Notify container of successful authentication
                return httpMessageContext.notifyContainerAboutLogin(
                        new CredentialValidationResult(username, roles));
            } catch (Exception e) {
                // Invalid or expired token
                return httpMessageContext.responseUnauthorized();
            }
        }

        // 4. Allow unauthenticated access if the endpoint isn't protected by
        // @RolesAllowed / @DeclareRoles
        return httpMessageContext.doNothing();
    }
}