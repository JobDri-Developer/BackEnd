package com.jobdri.jobdri_api.domain.jobposting.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public record JobPostingQueueProperties(
        @Value("${app.worker.job-posting.routing-key:job-posting.ingest}") String routingKey
) {
}
