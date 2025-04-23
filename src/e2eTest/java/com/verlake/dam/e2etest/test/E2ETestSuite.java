package com.verlake.dam.e2etest.test;

import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;
import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.IncludeClassNamePatterns;

@Suite
@SuiteDisplayName("E2E Test Suite")
@SelectClasses({
        BaseE2ETest.class,    // Base setup will run first
        AuditTrailE2ETest.class,
        AssetE2ETest.class
})
@IncludeEngines("junit-jupiter")
@IncludeClassNamePatterns(".*E2ETest")
public class E2ETestSuite {
    // The suite will execute tests in the order specified in @SelectClasses
    // BaseE2ETest's @BeforeAll will run first, followed by individual test classes
}
