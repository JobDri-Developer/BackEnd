package com.jobdri.jobdri_api.domain.analysis.type;

public enum AnalysisAsyncFailureReason {
    RATE_LIMIT,
    QUEUE_TIMEOUT,
    OPENAI_TIMEOUT,
    VALIDATION_ERROR,
    INTERNAL_ERROR
}
