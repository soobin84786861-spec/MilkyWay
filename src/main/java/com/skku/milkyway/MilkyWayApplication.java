package com.skku.milkyway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MilkyWayApplication {

	public static void main(String[] args) {
		SpringApplication.run(MilkyWayApplication.class, args);
	}

}
