package com.jobdri.jobdri_api.domain.analysis.service.ai;

import com.jobdri.jobdri_api.domain.analysis.application.port.AnalysisGenerator;
import com.jobdri.jobdri_api.domain.analysis.dto.llm.AnalysisLlmResponse;
import com.jobdri.jobdri_api.domain.analysis.service.core.AnalysisExecutionPayload;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AnalysisAiGenerator implements AnalysisGenerator {
    private final AnalysisAiClient analysisAiClient;

    @Override
    public AnalysisLlmResponse analyze(AnalysisExecutionPayload payload) {
        return analysisAiClient.analyze(payload);
    }
}
