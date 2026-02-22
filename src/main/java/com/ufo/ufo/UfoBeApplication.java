package com.ufo.ufo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
public class UfoBeApplication {

	public static void main(String[] args) {
		SpringApplication.run(UfoBeApplication.class, args);
	}

}
