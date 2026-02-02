package com.verlake.dam;


import co.elastic.apm.attach.ElasticApmAttacher;
import io.github.cdimascio.dotenv.Dotenv;
import io.github.cdimascio.dotenv.DotenvEntry;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(
	scanBasePackages = "com.verlake.dam",
	exclude = {
		MongoAutoConfiguration.class,
		MongoDataAutoConfiguration.class
	}
)
@EnableScheduling
@EnableJpaRepositories(basePackages = "com.verlake.dam.repository")
// EntityScan configured via application.properties: spring.jpa.entity-scan.packages
public class DamApplication {

	public static void main(String[] args) {
		loadEnvironmentVariables();
		ElasticApmAttacher.attach();
		SpringApplication.run(DamApplication.class, args);
	}

	private static void loadEnvironmentVariables() {
		Dotenv dotEnv = Dotenv.configure().directory("./").filename(".env").load();
		for(DotenvEntry entry: dotEnv.entries()) {
			System.setProperty(entry.getKey(), entry.getValue());
		}

		System.setProperty("elastic.apm.server_urls", System.getProperty("APM_SERVER_URL"));
		System.setProperty("elastic.apm.service_name", System.getProperty("APM_SERVICE_NAME"));
		System.setProperty("elastic.apm.application_packages", System.getProperty("APM_APPLICATION_PACKAGES"));
		System.setProperty("elastic.apm.secret_token", System.getProperty("APM_SECRET_TOKEN"));

	}

}
