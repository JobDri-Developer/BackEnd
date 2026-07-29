package com.jobdri.jobdri_api.global.cohere;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(CohereProperties.class)
public class CohereConfig {
}
