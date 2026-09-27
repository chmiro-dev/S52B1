package com.bank.config;

import jakarta.annotation.security.DeclareRoles;
import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;

@ApplicationPath("/api/v1")
@DeclareRoles({ "USER", "ADMIN", "SYSTEM" })
public class RestApplication extends Application {
    // Container configuration for REST endpoints and roles
}