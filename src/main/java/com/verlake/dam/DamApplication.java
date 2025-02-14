package com.verlake.dam;

import io.github.cdimascio.dotenv.Dotenv;
import io.github.cdimascio.dotenv.DotenvEntry;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.core.env.Environment;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = "com.verlake.dam")
@EnableJpaRepositories(basePackages = "com.verlake.dam.repository")
@EntityScan(basePackages = "com.verlake.dam.entity")
public class DamApplication {

	public static void main(String[] args) {
		Dotenv.configure().load();
		String activeProfile = "dev";  // Default profile

		// Check if the args contain the --spring.profiles.active parameter
		for (String arg : args) {
			if (arg.startsWith("--spring.profiles.active=")) {
				activeProfile = arg.split("=")[1];
				break;
			}
		}
		if (activeProfile.equals("dev")) {
			Dotenv devDotEnv = Dotenv.configure().directory("./").filename(".env.dev").load();
			for(DotenvEntry entry: devDotEnv.entries()) {

				System.out.println(entry.getKey() + ": " + entry.getValue());
				System.setProperty(entry.getKey(), entry.getValue());
			}
		}
		SpringApplication.run(DamApplication.class, args);
	}

}
