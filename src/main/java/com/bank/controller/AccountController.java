package com.bank.controller;

import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;

@Path("/accounts")
public class AccountController {

    @GET
    @RolesAllowed({ "USER", "ADMIN" })
    public Response getAccountDetails(@Context SecurityContext securityContext) {
        // Authenticated principal injected automatically by GlassFish / Jakarta
        // Security
        String username = securityContext.getUserPrincipal().getName();
        return Response.ok("Account details for " + username).build();
    }
}