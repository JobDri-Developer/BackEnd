package com.jobdri.jobdri_api.domain.analysis.service.sanitization;

public enum MissingKeywordRejectionReason {
    ACCEPTED,
    NULL_CANDIDATE,
    BLANK_KEYWORD,
    INVALID_FORMAT,
    UNSUPPORTED_KEYWORD,
    CERTIFICATE_OR_QUANTITATIVE_NOISE,
    TOO_GENERIC,
    NOT_RELATED_TO_JD,
    DUPLICATE_KEYWORD,
    NORMALIZATION_COLLISION,
    MAX_ACCEPTED_LIMIT
}
