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
				() -> System.getenv("KEYCLOAK_URL") + "/realms/DAM");
	}

	@Test
	void contextLoads() {
	}

}
