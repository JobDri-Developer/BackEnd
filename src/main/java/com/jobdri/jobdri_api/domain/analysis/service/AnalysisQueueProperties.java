package com.jobdri.jobdri_api.domain.analysis.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public record AnalysisQueueProperties(
        @Value("${app.worker.analysis.routing-key:analysis.execute}") String routingKey
) {
}
