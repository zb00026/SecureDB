package com.verlake.dam;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
class DamApplicationTests {

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", () -> System.getenv("MYSQL_URL"));
		registry.add("spring.datasource.username", () -> System.getenv("MYSQL_USERNAME"));
		registry.add("spring.datasource.password", () -> System.getenv("MYSQL_PASSWORD"));
		registry.add("spring.security.oauth2.client.provider.keycloak.issuer-uri",
				() -> System.getenv("KEYCLOAK_URL") + "/realms/" + System.getenv("KEYCLOAK_REALM_NAME"));
		registry.add("google.oauth2.issuer-uri",
				() -> System.getenv("GOOGLE_ISSUE_URI"));
		registry.add("google.oauth2.jwks-uri",
				() -> System.getenv("GOOGLE_JWKS_URI"));
		registry.add("auth.provider",
				() -> System.getenv("AUTH_PROVIDER"));
		registry.add("keycloak.auth-server-url",
				() -> System.getenv("KEYCLOAK_URL"));
		registry.add("HOST_DOMAIN_URI",
				() -> System.getenv("HOST_DOMAIN_URI"));
		registry.add("MAIL_SENDER",
				() -> System.getenv("MAIL_SENDER"));
		registry.add("aws.accessKeyId",
				() -> System.getenv("AWS_ACCESS_KEY_ID"));
		registry.add("aws.secretKey",
				() -> System.getenv("AWS_SECRET_ACCESS_KEY"));
		registry.add("aws.region",
				() -> System.getenv("HOST_REGION"));
		registry.add("jwt.secret",
				() -> System.getenv("JWT_SECRET"));
	}

	@Test
	void contextLoads() {
	}

}
