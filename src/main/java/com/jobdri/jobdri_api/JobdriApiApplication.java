package com.jobdri.jobdri_api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.Arrays;

@SpringBootApplication
@EnableAsync
@EnableRetry
@EnableScheduling
public class JobdriApiApplication {

	public static void main(String[] args) {
		disableDevtoolsRestartForAnalysisEval(args);
		SpringApplication.run(JobdriApiApplication.class, args);
	}

	private static void disableDevtoolsRestartForAnalysisEval(String[] args) {
		String activeProfiles = System.getProperty("spring.profiles.active", System.getenv("SPRING_PROFILES_ACTIVE"));
		boolean analysisEvalProfile = containsAnalysisEvalProfile(activeProfiles);
		if (!analysisEvalProfile) {
			analysisEvalProfile = Arrays.stream(args)
					.filter(arg -> arg.startsWith("--spring.profiles.active="))
					.map(arg -> arg.substring("--spring.profiles.active=".length()))
					.anyMatch(JobdriApiApplication::containsAnalysisEvalProfile);
		}
		if (analysisEvalProfile) {
			System.setProperty("spring.devtools.restart.enabled", "false");
		}
	}

	private static boolean containsAnalysisEvalProfile(String activeProfiles) {
		return activeProfiles != null && Arrays.stream(activeProfiles.split(","))
				.map(String::trim)
				.anyMatch("analysis-eval"::equals);
	}

}
