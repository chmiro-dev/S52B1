package com.bank.security.auth;

import com.bank.security.auth.model.CustomPrincipal;
import com.bank.security.auth.model.CustomRolePrincipal;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.security.auth.Subject;
import javax.security.auth.callback.*;
import javax.security.auth.login.FailedLoginException;
import javax.security.auth.login.LoginException;
import javax.security.auth.spi.LoginModule;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.security.spec.InvalidKeySpecException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

public class DatabaseLoginModule implements LoginModule {

    private Subject subject;
    private CallbackHandler callbackHandler;
    private Map<String, Object> sharedState;

    private boolean succeeded = false;
    private boolean commitSucceeded = false;

    private String username;
    UserData pendingUserData; // Stores fetched user data until commit()
    private Principal userPrincipal;
    private final List<Principal> rolePrincipals = new ArrayList<>();

    @SuppressWarnings("unchecked")
    @Override
    public void initialize(Subject subject, CallbackHandler callbackHandler,
                           Map<String, ?> sharedState, Map<String, ?> options) {
        this.subject = subject;
        this.callbackHandler = callbackHandler;
        this.sharedState = (Map<String, Object>) sharedState;
    }

    @Override
    public boolean login() throws LoginException {
        if (callbackHandler == null) {
            throw new LoginException("Error: CallbackHandler is required.");
        }

        NameCallback nameCallback = new NameCallback("Username: ");
        PasswordCallback passwordCallback = new PasswordCallback("Password: ", false);

        try {
            callbackHandler.handle(new Callback[]{nameCallback, passwordCallback});
            this.username = nameCallback.getName();
            char[] password = passwordCallback.getPassword();

            try {
                succeeded = validateCredentials(this.username, password);
            } finally {
                if (password != null) {
                    Arrays.fill(password, '0');
                }
                passwordCallback.clearPassword();
            }

        } catch (IOException | UnsupportedCallbackException e) {
            throw new LoginException("Callback handling failed: " + e.getMessage());
        }

        if (!succeeded) {
            cleanUpState();
            throw new FailedLoginException("Authentication failed: Invalid credentials.");
        }

        if (sharedState != null) {
            sharedState.put("javax.security.auth.login.name", this.username);
        }

        return true;
    }

    @Override
    public boolean commit() throws LoginException {
        if (!succeeded) {
            return false;
        }

        if (subject.isReadOnly()) {
            throw new LoginException("Subject is read-only.");
        }

        // 1. Set User Principal
        this.userPrincipal = new CustomPrincipal(this.username);

        // 2. Dynamically attach roles fetched from the database
        if (this.pendingUserData != null && this.pendingUserData.roles() != null) {
            for (String roleName : this.pendingUserData.roles()) {
                this.rolePrincipals.add(new CustomRolePrincipal(roleName));
            }
        }

        subject.getPrincipals().add(this.userPrincipal);
        subject.getPrincipals().addAll(this.rolePrincipals);

        commitSucceeded = true;

        this.pendingUserData = null; 

        return true;
    }

    @Override
    public boolean abort() throws LoginException {
        if (!succeeded) {
            cleanUpState(); 
            return false;
        } else if (!commitSucceeded) {
            succeeded = false;
            cleanUpState();
        } else {
            logout();
        }
        return true;
    }

    @Override
    public boolean logout() throws LoginException {
        if (subject != null && !subject.isReadOnly()) {
            if (userPrincipal != null) {
                subject.getPrincipals().remove(userPrincipal);
            }
            subject.getPrincipals().removeAll(rolePrincipals);
        }

        succeeded = false;
        commitSucceeded = false;
        cleanUpState();
        return true;
    }

    private boolean validateCredentials(String user, char[] pass) {
        if (user == null || pass == null || pass.length == 0) {
            return false;
        }

        this.pendingUserData = fetchUserFromDatabase(user);
        if (this.pendingUserData == null) {
            return false; 
        }

        byte[] storedSalt = this.pendingUserData.salt();
        byte[] storedHash = this.pendingUserData.passwordHash();

        try {
            PBEKeySpec spec = new PBEKeySpec(pass, storedSalt, 210000, 256);
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            byte[] computedHash = factory.generateSecret(spec).getEncoded();

            return MessageDigest.isEqual(storedHash, computedHash);

        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            return false;
        }
    }

    UserData fetchUserFromDatabase(String username) {
        String dbUrl = System.getenv().getOrDefault("DB_URL", "jdbc:h2:mem:bankdb;DB_CLOSE_DELAY=-1");
        String dbUser = System.getenv().getOrDefault("DB_USER", "sa");
        String dbPass = System.getenv().getOrDefault("DB_PASSWORD", "");

        String userSql = "SELECT password_hash, salt FROM users WHERE username = ?";
        String roleSql = "SELECT role_name FROM user_roles WHERE username = ?";

        try (Connection conn = DriverManager.getConnection(dbUrl, dbUser, dbPass)) {
            return queryUserData(conn, username, userSql, roleSql);
        } catch (SQLException e) {
            return null;
        }
    }

    private UserData queryUserData(Connection conn, String username, String userSql, String roleSql) throws SQLException {
        byte[] passwordHash = null;
        byte[] salt = null;

        // 1. Fetch credentials
        try (PreparedStatement stmt = conn.prepareStatement(userSql)) {
            stmt.setString(1, username);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    passwordHash = rs.getBytes("password_hash");
                    salt = rs.getBytes("salt");
                } else {
                    return null;
                }
            }
        }

        // 2. Fetch assigned roles
        List<String> roles = new ArrayList<>();
        try (PreparedStatement stmt = conn.prepareStatement(roleSql)) {
            stmt.setString(1, username);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    roles.add(rs.getString("role_name"));
                }
            }
        }

        return new UserData(salt, passwordHash, roles);
    }

    private void cleanUpState() {
        username = null;
        pendingUserData = null;
        userPrincipal = null;
        rolePrincipals.clear();
    }
}