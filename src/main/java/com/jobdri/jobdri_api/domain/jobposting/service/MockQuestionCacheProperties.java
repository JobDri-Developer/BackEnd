package com.jobdri.jobdri_api.domain.jobposting.service;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.job-posting.mock-question-cache")
public class MockQuestionCacheProperties {

    private String promptVersion = "v1";
}
