package com.verlake.dam;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = "com.verlake.dam")
@EnableJpaRepositories(basePackages = "com.verlake.dam.repository")
@EntityScan(basePackages = "com.verlake.dam.entity")
public class DamApplication {

	public static void main(String[] args) {
		SpringApplication.run(DamApplication.class, args);
	}

}
