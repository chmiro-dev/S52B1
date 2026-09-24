package com.bank.security.config;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

public class HikariH2IntegrationIT {

    private static HikariDataSource dataSource;

    @BeforeAll
    static void setUp() {
        DatabaseConfig config = new DatabaseConfig();
        dataSource = (HikariDataSource) config.getDataSource();
    }

    @AfterAll
    static void tearDown() {
        if (dataSource != null) {
            dataSource.close();
        }
    }

    @Test
    void testHikariCPConnectionToH2() throws SQLException {
        assertNotNull(dataSource, "DataSource should not be null");
        
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT 1")) {

            assertTrue(resultSet.next());
            assertEquals(1, resultSet.getInt(1));
        }

        assertEquals("S52B1-HikariPool", dataSource.getPoolName());
        assertFalse(dataSource.isClosed());
    }
}