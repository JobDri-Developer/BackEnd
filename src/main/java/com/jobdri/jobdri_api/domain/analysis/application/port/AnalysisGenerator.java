package com.jobdri.jobdri_api.domain.analysis.application.port;

import com.jobdri.jobdri_api.domain.analysis.dto.external.llm.AnalysisLlmResponse;
import com.jobdri.jobdri_api.domain.analysis.service.core.AnalysisExecutionPayload;

public interface AnalysisGenerator {
    AnalysisLlmResponse analyze(AnalysisExecutionPayload payload);
}
