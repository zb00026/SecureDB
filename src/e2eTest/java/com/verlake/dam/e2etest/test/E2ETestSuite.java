package com.verlake.dam.e2etest.test;

import org.junit.platform.suite.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@Suite
@SuiteDisplayName("E2E Test Suite")
@SelectClasses({
        AuditTrailE2ETest.class, // Will run first
        AssetE2ETest.class // Will run second
})
@IncludeEngines("junit-jupiter")
@IncludeClassNamePatterns(".*E2ETest")
public class E2ETestSuite {

}
