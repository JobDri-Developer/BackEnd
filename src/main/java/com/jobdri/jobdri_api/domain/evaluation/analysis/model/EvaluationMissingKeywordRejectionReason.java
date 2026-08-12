package com.jobdri.jobdri_api.domain.evaluation.analysis.model;

public enum EvaluationMissingKeywordRejectionReason {
    ACCEPTED,
    NULL_CANDIDATE,
    BLANK_KEYWORD,
    MAX_ACCEPTED_LIMIT,
    INVALID_FORMAT,
    UNSUPPORTED_KEYWORD,
    CERTIFICATE_OR_QUANTITATIVE_NOISE,
    TOO_GENERIC,
    NOT_RELATED_TO_JD,
    DUPLICATE_KEYWORD,
    NORMALIZATION_COLLISION
}
