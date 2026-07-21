package com.jobdri.jobdri_api.domain.analysis.service;

import com.jobdri.jobdri_api.domain.analysis.dto.response.MissingKeywordSource;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AnalysisSanitizationRules {
    private static final double MIN_KEYWORD_TOKEN_MATCH_RATIO = 0.5;
    private static final Pattern TOKEN_PATTERN = Pattern.compile("[a-zA-Z0-9+#.]+|[가-힣]+");
    private static final Pattern CAREER_YEAR_PATTERN = Pattern.compile("경력\\s*\\d+\\s*년|\\d+\\s*년\\s*(이상|이하|미만|초과)");
    private static final String[] STRUCTURED_QUALIFICATION_TERMS = {
            "자격증", "면허", "면허증", "공인성적", "어학성적", "토익", "toeic", "토플", "toefl",
            "opic", "ielts", "학위", "전공", "졸업", "학력", "신입", "근무 가능", "국적", "나이", "연령"
    };
    private static final String[] META_IMPROVEMENT_TERMS = {
            "구체적으로 작성했습니다", "명확히 설명했습니다", "성과를 강조했습니다", "수치로 명시했습니다",
            "더 설득력 있게 작성했습니다", "경험을 구체적으로 작성했습니다", "구체적으로 설명했습니다",
            "구체적으로 서술했습니다", "명확히 서술했습니다", "구체적으로 설명합니다",
            "구체적으로 서술합니다", "명확히 설명합니다", "명확히 서술합니다"
    };
    private static final String[] CONTRADICTORY_PROVEN_REASON_TERMS = {
            "근거가 부족", "성과가 부족", "수치가 부족", "구체성이 부족", "보완이 필요",
            "드러나지 않음", "확인하기 어려움"
    };
    private static final Set<String> STOP_WORDS = Set.of(
            "경험", "역량", "업무", "관련", "가능", "보유", "필수", "우대", "자격", "요건",
            "사항", "직무", "수행", "활용", "사용", "기반", "중심", "대한", "통한", "등",
            "및", "또는", "위한", "있는", "없는"
    );

    private AnalysisSanitizationRules() {
    }

    public static boolean isValidMissingKeyword(
            String keyword,
            MissingKeywordSource source,
            String mainTasks,
            String qualifications
    ) {
        if (!StringUtils.hasText(keyword) || source == null) {
            return false;
        }
        if (source == MissingKeywordSource.PREFERENCE) {
            return false;
        }
        if (isStructuredQualificationKeyword(keyword)) {
            return false;
        }
        if (source == MissingKeywordSource.MAIN_TASK) {
            return isGroundedInSource(keyword, mainTasks);
        }
        if (source == MissingKeywordSource.QUALIFICATION) {
            return isGroundedInSource(keyword, qualifications);
        }
        return false;
    }

    public static String normalizeImprovement(
            String sentence,
            String answer,
            String improvement,
            boolean provenStatus
    ) {
        if (provenStatus || !StringUtils.hasText(improvement)) {
            return "";
        }

        String normalized = improvement.trim();
        if (AnalysisImprovementRules.isInstructionLike(normalized)
                || equalsNormalized(sentence, normalized)
                || isCopiedFromAnotherAnswerSentence(sentence, answer, normalized)
                || isMetaImprovement(normalized)
                || changesSentenceTense(sentence, normalized)) {
            return "";
        }
        return normalized;
    }

    public static boolean isContradictoryProvenReason(String reason) {
        if (!StringUtils.hasText(reason)) {
            return false;
        }
        String normalized = normalize(reason);
        for (String term : CONTRADICTORY_PROVEN_REASON_TERMS) {
            if (normalized.contains(normalize(term))) {
                return true;
            }
        }
        return false;
    }

    public static boolean isStructuredQualificationKeyword(String keyword) {
        String normalized = normalize(keyword);
        if (CAREER_YEAR_PATTERN.matcher(keyword).find()) {
            return true;
        }
        for (String term : STRUCTURED_QUALIFICATION_TERMS) {
            if (normalized.contains(normalize(term))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isGroundedInSource(String keyword, String sourceText) {
        Set<String> keywordTokens = coreTokens(keyword);
        if (keywordTokens.isEmpty() || !StringUtils.hasText(sourceText)) {
            return false;
        }

        Set<String> sourceTokens = coreTokens(sourceText);
        long matchCount = keywordTokens.stream()
                .filter(sourceTokens::contains)
                .count();
        if (matchCount < 1) {
            return false;
        }

        if (keywordTokens.size() == 1) {
            String token = keywordTokens.iterator().next();
            return token.length() >= 3 && sourceTokens.contains(token);
        }
        return (double) matchCount / keywordTokens.size() >= MIN_KEYWORD_TOKEN_MATCH_RATIO;
    }

    private static Set<String> coreTokens(String value) {
        Set<String> tokens = new LinkedHashSet<>();
        if (!StringUtils.hasText(value)) {
            return tokens;
        }

        Matcher matcher = TOKEN_PATTERN.matcher(value.toLowerCase(Locale.ROOT));
        while (matcher.find()) {
            String token = stripKoreanSuffix(matcher.group());
            if (isCoreToken(token)) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    private static boolean isCoreToken(String token) {
        return StringUtils.hasText(token)
                && token.length() >= 2
                && !STOP_WORDS.contains(token);
    }

    private static String stripKoreanSuffix(String token) {
        if (token == null || !token.matches("[가-힣]+")) {
            return token == null ? "" : token;
        }
        String result = token;
        String[] suffixes = {"으로", "에서", "하며", "하고", "하는", "까지", "부터", "에게", "보다",
                "은", "는", "이", "가", "을", "를", "와", "과", "의", "에", "로", "한"};
        boolean changed;
        do {
            changed = false;
            for (String suffix : suffixes) {
                if (result.length() > suffix.length() + 1 && result.endsWith(suffix)) {
                    result = result.substring(0, result.length() - suffix.length());
                    changed = true;
                    break;
                }
            }
        } while (changed);
        return result;
    }

    private static boolean containsNormalized(String sourceText, String keyword) {
        return StringUtils.hasText(sourceText)
                && StringUtils.hasText(keyword)
                && normalize(sourceText).contains(normalize(keyword));
    }

    private static boolean equalsNormalized(String left, String right) {
        return StringUtils.hasText(left)
                && StringUtils.hasText(right)
                && normalize(left).equals(normalize(right));
    }

    private static boolean isCopiedFromAnotherAnswerSentence(String sentence, String answer, String improvement) {
        if (!StringUtils.hasText(answer) || !StringUtils.hasText(improvement)) {
            return false;
        }
        return containsNormalized(answer, improvement) && !equalsNormalized(sentence, improvement);
    }

    private static boolean isMetaImprovement(String improvement) {
        String normalized = normalize(improvement);
        for (String term : META_IMPROVEMENT_TERMS) {
            if (normalized.contains(normalize(term))) {
                return true;
            }
        }
        return false;
    }

    private static boolean changesSentenceTense(String sentence, String improvement) {
        return isPastSentence(sentence) && isFutureSentence(improvement)
                || isFutureSentence(sentence) && isPastSentence(improvement);
    }

    private static boolean isPastSentence(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        String normalized = normalize(value);
        return normalized.contains("했습니다")
                || normalized.contains("였습니다")
                || normalized.contains("수행했습니다")
                || normalized.contains("개선했습니다")
                || normalized.contains("달성했습니다")
                || normalized.contains("근무했습니다")
                || normalized.contains("담당했습니다");
    }

    private static boolean isFutureSentence(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        String normalized = normalize(value);
        return normalized.contains("하겠습니다")
                || normalized.contains("되겠습니다")
                || normalized.contains("기여하겠습니다")
                || normalized.contains("노력하겠습니다")
                || normalized.contains("성장하겠습니다")
                || normalized.contains("싶습니다");
    }

    private static String normalize(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "").toLowerCase();
    }
}
