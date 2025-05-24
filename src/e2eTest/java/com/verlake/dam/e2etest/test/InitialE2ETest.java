package com.verlake.dam.e2etest.test;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class InitialE2ETest {
    private static String activeProfile;

    @BeforeAll
    static void setupTestData() throws SQLException {
        activeProfile = System.getProperty("spring.profiles.active", "dev");
        
        if (activeProfile.equals("dev")) {
            DriverManagerDataSource dataSource = new DriverManagerDataSource();
            dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
            dataSource.setUrl(System.getProperty("MYSQL_URL"));
            dataSource.setUsername(System.getProperty("MYSQL_USERNAME"));
            dataSource.setPassword(System.getProperty("MYSQL_PASSWORD"));

            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement()) {
                
                String[] statements = {
                    "SET FOREIGN_KEY_CHECKS = 0",
                    "TRUNCATE TABLE audit_trails",
                    "TRUNCATE TABLE user_roles",
                    "TRUNCATE TABLE users",
                    "TRUNCATE TABLE assets",
                    "TRUNCATE TABLE asset_objects",
                    "TRUNCATE TABLE asset_approvers",
                    "TRUNCATE TABLE access_requests",
                    "TRUNCATE TABLE access_level_objects",
                    "TRUNCATE TABLE asset_credentials",
                    "TRUNCATE TABLE s3_bucket_settings",
                    "SET FOREIGN_KEY_CHECKS = 1",
                    "INSERT INTO users (email, first_name, last_name, is_active) VALUES ('chuc06872@gmail.com', 'E2E', 'Admin', true)",
                    "INSERT INTO user_roles (user_id, role_id) VALUES (1, 1)"
                };

                for (String sql : statements) {
                    stmt.execute(sql);
                }
            }
        }
    }
} 