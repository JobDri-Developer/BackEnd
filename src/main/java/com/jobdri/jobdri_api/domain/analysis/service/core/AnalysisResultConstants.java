package com.jobdri.jobdri_api.domain.analysis.service.core;

public final class AnalysisResultConstants {

    public static final int MIN_SCORE = 0;
    public static final int MAX_SCORE = 100;
    public static final int MAX_ANALYSES_PER_QUESTION = 3;
    public static final int MAX_HIGHLIGHTS = 3;
    public static final int MAX_HIGHLIGHT_TITLE_LENGTH = 80;
    public static final int MAX_HIGHLIGHT_QUOTE_LENGTH = 200;
    public static final int MAX_MISSING_KEYWORDS = 3;
    public static final int MAX_MISSING_KEYWORD_LENGTH = 60;
    public static final double JOB_FIT_WEIGHT = 0.50;
    public static final double IMPACT_WEIGHT = 0.30;
    public static final double COMPLETENESS_WEIGHT = 0.20;

    private AnalysisResultConstants() {
    }
}
