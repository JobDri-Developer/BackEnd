package com.jobdri.jobdri_api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableAsync
@EnableRetry
@EnableScheduling
public class JobdriApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(JobdriApiApplication.class, args);
	}

}
