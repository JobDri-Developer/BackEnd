package com.jobdri.jobdri_api.domain.analysis.service.ai;

import java.util.List;

// 개선 문구를 후처리할 때 사용하는 내부 규칙 모음이다.
public final class AnalysisImprovementRules {
    private static final List<String> BANNED_PHRASES = List.of(
            "추가하세요",
            "보완하세요",
            "수정해주세요",
            "수정하세요",
            "작성해주세요",
            "작성하세요",
            "필요합니다",
            "해야 합니다",
            "해주세요",
            "명확히 해야",
            "명확히 하세요"
    );

    private static final List<String> IMPERATIVE_ENDINGS = List.of(
            "하세요",
            "하십시오",
            "해주십시오",
            "해 주십시오"
    );

    private AnalysisImprovementRules() {
    }

    public static String bannedPhrasesText() {
        return String.join(", ", BANNED_PHRASES);
    }

    public static boolean isInstructionLike(String improvement) {
        if (improvement == null) {
            return false;
        }

        String compact = improvement.replaceAll("\\s+", "");
        return BANNED_PHRASES.stream()
                .map(phrase -> phrase.replaceAll("\\s+", ""))
                .anyMatch(compact::contains)
                || IMPERATIVE_ENDINGS.stream()
                .map(ending -> ending.replaceAll("\\s+", ""))
                .anyMatch(ending -> compact.endsWith(ending) || compact.endsWith(ending + "."));
    }
}
