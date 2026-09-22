package com.bank.security.auth;

import java.util.List;

public record UserData(byte[] salt, byte[] passwordHash, List<String> roles) {}