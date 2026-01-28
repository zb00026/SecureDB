package com.verlake.dam.e2etest.test;

import org.junit.platform.suite.api.*;

@Suite
@SuiteDisplayName("E2E Test Suite")
@SelectClasses({
        InitialE2ETest.class,    // Base setup will run first
        AuditTrailE2ETest.class,
        AssetE2ETest.class,
        AccessRequestE2ETest.class,
})
@IncludeEngines("junit-jupiter")
@IncludeClassNamePatterns(".*E2ETest")
public class E2ETestSuite {
    // The suite will execute tests in the order specified in @SelectClasses
    // BaseE2ETest's @BeforeAll will run first, followed by individual test classes
}
