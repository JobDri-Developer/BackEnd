package com.jobdri.jobdri_api.domain.evaluation.analysis.sanitization;

import com.jobdri.jobdri_api.domain.evaluation.analysis.model.EvaluationMissingKeywordCandidate;
import com.jobdri.jobdri_api.domain.evaluation.analysis.model.EvaluationMissingKeywordRejectionReason;
import com.jobdri.jobdri_api.domain.evaluation.analysis.model.EvaluationMissingKeywordSanitizationDecision;
import com.jobdri.jobdri_api.domain.evaluation.analysis.model.EvaluationMissingKeywordSanitizationResult;
import com.jobdri.jobdri_api.domain.evaluation.analysis.model.EvaluationMissingKeywordSource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class EvaluationSanitizationService {
    private static final int MAX_ACCEPTED_COUNT = 3;
    private static final double MIN_KEYWORD_TOKEN_MATCH_RATIO = 0.5;
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");
    private static final Pattern TOKEN_PATTERN = Pattern.compile("[a-zA-Z0-9+#.]+|[가-힣]+");
    private static final Pattern KOREAN_ONLY_PATTERN = Pattern.compile("[가-힣]+");
    private static final Pattern CAREER_YEAR_PATTERN = Pattern.compile("경력\\s*\\d+\\s*년|\\d+\\s*년\\s*(이상|이하|미만|초과)");
    private static final Set<String> STRUCTURED_QUALIFICATION_TERMS = normalizedSet(
            "자격증", "면허", "면허증", "공인성적", "어학성적", "토익", "toeic", "토플", "toefl",
            "opic", "ielts", "학위", "전공", "졸업", "학력", "신입", "근무 가능", "국적", "나이", "연령",
            "사회복지사", "청소년지도사", "청소년상담사", "직업상담사", "임상심리사",
            "대졸", "초대졸", "전문대졸", "고졸"
    );
    private static final Set<String> META_IMPROVEMENT_TERMS = normalizedSet(
            "구체적으로 작성했습니다", "명확히 설명했습니다", "성과를 강조했습니다", "수치로 명시했습니다",
            "더 설득력 있게 작성했습니다", "경험을 구체적으로 작성했습니다", "구체적으로 설명했습니다",
            "구체적으로 서술했습니다", "명확히 서술했습니다", "구체적으로 설명합니다",
            "구체적으로 서술합니다", "명확히 설명합니다", "명확히 서술합니다",
            "추가해 보", "추가하면 좋", "설명해 보", "구체적으로 작성", "구체적으로 설명",
            "강조하겠", "추가하겠", "명확히 작성", "작성할 수 있", "설명할 수 있",
            "보완하겠", "드러내겠", "제시하겠", "추가할 수 있", "수정할 수 있",
            "수정하는 방향", "강조하는 방향"
    );
    private static final String[] CONTRADICTORY_PROVEN_REASON_TERMS = {
            "근거가 부족", "성과가 부족", "수치가 부족", "구체성이 부족", "보완이 필요",
            "드러나지 않음", "확인하기 어려움"
    };
    private static final Set<String> FABRICATED_DIRECT_CONFLICT_TERMS = normalizedSet(
            "직접 충돌", "명시적 사실과 충돌", "사실과 충돌", "조건과 충돌", "요건과 충돌",
            "서로 충돌", "상충", "하지 않았다고", "수행하지 않았다고", "경험이 없다고",
            "없다고 밝혔", "실제로 하지 않았", "모순", "불일치", "일치하지 않", "앞뒤가 맞지",
            "다르게 서술"
    );
    private static final String TEAM_PROJECT_TERM = normalizeStatic("팀 프로젝트");
    private static final String TEAM_PROGRESS_TERM = normalizeStatic("팀으로 진행");
    private static final String INDIVIDUAL_PROJECT_TERM = normalizeStatic("개인 프로젝트");
    private static final String SOLO_EXECUTION_TERM = normalizeStatic("혼자 수행");
    private static final String SOLO_PROGRESS_TERM = normalizeStatic("혼자 진행");
    private static final Set<String> STOP_WORDS = Set.of(
            "경험", "역량", "업무", "관련", "가능", "보유", "필수", "우대", "자격", "요건",
            "사항", "직무", "수행", "활용", "사용", "기반", "중심", "대한", "통한", "등",
            "및", "또는", "위한", "있는", "없는"
    );
    private static final List<String> BANNED_IMPROVEMENT_PHRASES = compactList(
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
    private static final List<String> IMPERATIVE_ENDINGS = compactList(
            "하세요",
            "하십시오",
            "해주십시오",
            "해 주십시오"
    );
    private static final List<String> KOREAN_SUFFIXES = List.of(
            "했습니다", "았습니다", "었습니다", "으로", "에서", "하며", "하고", "하는", "까지", "부터", "에게", "보다",
            "은", "는", "이", "가", "을", "를", "와", "과", "의", "에", "로", "한"
    );

    public boolean isValidMissingKeyword(
            String keyword,
            EvaluationMissingKeywordSource source,
            String mainTasks,
            String qualifications
    ) {
        if (!StringUtils.hasText(keyword) || source == null) {
            return false;
        }
        if (source == EvaluationMissingKeywordSource.PREFERENCE) {
            return false;
        }
        if (isStructuredQualificationKeyword(keyword)) {
            return false;
        }
        if (source == EvaluationMissingKeywordSource.MAIN_TASK) {
            return isGroundedInSource(keyword, mainTasks);
        }
        if (source == EvaluationMissingKeywordSource.QUALIFICATION) {
            return isGroundedInSource(keyword, qualifications);
        }
        return false;
    }

    public boolean isMissingKeywordMentionedInAnswers(String keyword, String answer) {
        if (!StringUtils.hasText(keyword) || !StringUtils.hasText(answer)) {
            return false;
        }
        if (containsNormalized(answer, keyword)) {
            return true;
        }

        Set<String> keywordTokens = coreTokens(keyword);
        Set<String> answerTokens = coreTokens(answer);
        if (keywordTokens.isEmpty() || answerTokens.isEmpty()) {
            return false;
        }

        long matchCount = keywordTokens.stream()
                .filter(answerTokens::contains)
                .count();
        if (keywordTokens.size() == 1) {
            return matchCount == 1;
        }
        return (double) matchCount / keywordTokens.size() >= MIN_KEYWORD_TOKEN_MATCH_RATIO;
    }

    public boolean hasValidProvenReason(String reason) {
        if (!StringUtils.hasText(reason)) {
            return false;
        }
        String normalized = normalize(reason);
        for (String term : CONTRADICTORY_PROVEN_REASON_TERMS) {
            if (normalized.contains(normalize(term))) {
                return false;
            }
        }
        return true;
    }

    public boolean hasFabricatedDirectConflictEvidence(String sentence, String reason) {
        if (!StringUtils.hasText(reason)) {
            return false;
        }
        if (hasFabricatedDirectConflictReason(reason)) {
            return true;
        }
        return StringUtils.hasText(sentence)
                && hasFabricatedDirectConflictReason(sentence + " " + reason);
    }

    public String normalizeImprovement(String sentence, String answer, String improvement, boolean proven) {
        if (proven || !StringUtils.hasText(improvement) || isNullLikeImprovement(improvement)) {
            return "";
        }

        String normalized = improvement.trim();
        if (isInstructionLike(normalized)
                || equalsNormalized(sentence, normalized)
                || isCopiedFromAnotherAnswerSentence(sentence, answer, normalized)
                || isMetaImprovement(normalized)
                || changesSentenceTense(sentence, normalized)) {
            return "";
        }
        return normalized;
    }

    public boolean isStructuredQualificationKeyword(String value) {
        if (value == null) {
            return false;
        }
        String normalized = normalize(value);
        if (CAREER_YEAR_PATTERN.matcher(value).find()) {
            return true;
        }
        for (String term : STRUCTURED_QUALIFICATION_TERMS) {
            if (normalized.contains(term)) {
                return true;
            }
        }
        return false;
    }

    public String normalizeText(String value) {
        return normalize(value);
    }

    public EvaluationMissingKeywordSanitizationResult sanitizeMissingKeywordCandidates(
            String mainTasks,
            String qualifications,
            String answer,
            List<EvaluationMissingKeywordCandidate> candidates
    ) {
        if (candidates == null) {
            return new EvaluationMissingKeywordSanitizationResult(List.of(), List.of());
        }

        List<EvaluationMissingKeywordCandidate> acceptedCandidates = new ArrayList<>();
        List<EvaluationMissingKeywordSanitizationDecision> decisions = new ArrayList<>();
        Map<String, IndexedKeyword> seen = new HashMap<>();

        for (int i = 0; i < candidates.size(); i++) {
            EvaluationMissingKeywordCandidate candidate = candidates.get(i);
            EvaluationMissingKeywordSanitizationDecision decision = decide(
                    i,
                    candidate,
                    mainTasks,
                    qualifications,
                    answer,
                    seen,
                    acceptedCandidates.size()
            );
            decisions.add(decision);
            if (decision.accepted()) {
                acceptedCandidates.add(candidate);
                seen.put(decision.normalizedKeyword(), new IndexedKeyword(i, candidate.keyword()));
            }
        }

        return new EvaluationMissingKeywordSanitizationResult(List.copyOf(acceptedCandidates), List.copyOf(decisions));
    }

    private EvaluationMissingKeywordSanitizationDecision decide(
            int index,
            EvaluationMissingKeywordCandidate candidate,
            String mainTasks,
            String qualifications,
            String answer,
            Map<String, IndexedKeyword> seen,
            int acceptedCount
    ) {
        if (candidate == null) {
            return rejected(index, null, "", answer, false, null, EvaluationMissingKeywordRejectionReason.NULL_CANDIDATE);
        }
        String keyword = candidate.keyword();
        String normalizedKeyword = normalize(keyword);
        if (!StringUtils.hasText(keyword)) {
            return rejected(index, candidate, normalizedKeyword, answer, false, null, EvaluationMissingKeywordRejectionReason.BLANK_KEYWORD);
        }
        if (acceptedCount >= MAX_ACCEPTED_COUNT) {
            return rejected(index, candidate, normalizedKeyword, answer, false, null, EvaluationMissingKeywordRejectionReason.MAX_ACCEPTED_LIMIT);
        }
        if (candidate.source() == null) {
            return rejected(index, candidate, normalizedKeyword, answer, false, null, EvaluationMissingKeywordRejectionReason.INVALID_FORMAT);
        }
        if (candidate.source() == EvaluationMissingKeywordSource.PREFERENCE) {
            return rejected(index, candidate, normalizedKeyword, answer, false, null, EvaluationMissingKeywordRejectionReason.UNSUPPORTED_KEYWORD);
        }
        if (isStructuredQualificationKeyword(keyword)) {
            return rejected(index, candidate, normalizedKeyword, answer, false, null, EvaluationMissingKeywordRejectionReason.CERTIFICATE_OR_QUANTITATIVE_NOISE);
        }
        if (coreTokens(keyword).isEmpty()) {
            return rejected(index, candidate, normalizedKeyword, answer, false, null, EvaluationMissingKeywordRejectionReason.TOO_GENERIC);
        }

        boolean grounded = isValidMissingKeyword(keyword, candidate.source(), mainTasks, qualifications);
        if (!grounded) {
            return rejected(index, candidate, normalizedKeyword, answer, false, null, EvaluationMissingKeywordRejectionReason.NOT_RELATED_TO_JD);
        }

        IndexedKeyword duplicate = seen.get(normalizedKeyword);
        if (duplicate != null) {
            EvaluationMissingKeywordRejectionReason reason = duplicate.keyword().trim().equals(keyword.trim())
                    ? EvaluationMissingKeywordRejectionReason.DUPLICATE_KEYWORD
                    : EvaluationMissingKeywordRejectionReason.NORMALIZATION_COLLISION;
            return rejected(index, candidate, normalizedKeyword, answer, grounded, duplicate.index(), reason);
        }

        return new EvaluationMissingKeywordSanitizationDecision(
                index,
                candidate,
                normalizedKeyword,
                true,
                EvaluationMissingKeywordRejectionReason.ACCEPTED,
                containsExact(answer, keyword),
                containsNormalized(answer, keyword),
                true,
                null
        );
    }

    private EvaluationMissingKeywordSanitizationDecision rejected(
            int index,
            EvaluationMissingKeywordCandidate candidate,
            String normalizedKeyword,
            String answer,
            boolean jdRequirementMatched,
            Integer duplicateOfCandidateIndex,
            EvaluationMissingKeywordRejectionReason reason
    ) {
        String keyword = candidate == null ? "" : candidate.keyword();
        return new EvaluationMissingKeywordSanitizationDecision(
                index,
                candidate,
                normalizedKeyword,
                false,
                reason,
                containsExact(answer, keyword),
                containsNormalized(answer, keyword),
                jdRequirementMatched,
                duplicateOfCandidateIndex
        );
    }

    private boolean hasFabricatedDirectConflictReason(String reason) {
        if (!StringUtils.hasText(reason)) {
            return false;
        }
        String normalized = normalize(reason);
        for (String term : FABRICATED_DIRECT_CONFLICT_TERMS) {
            if (normalized.contains(term)) {
                return true;
            }
        }
        boolean teamClaim = normalized.contains(TEAM_PROJECT_TERM)
                || normalized.contains(TEAM_PROGRESS_TERM);
        boolean individualClaim = normalized.contains(INDIVIDUAL_PROJECT_TERM)
                || normalized.contains(SOLO_EXECUTION_TERM)
                || normalized.contains(SOLO_PROGRESS_TERM);
        return teamClaim && individualClaim;
    }

    private boolean isInstructionLike(String improvement) {
        if (improvement == null) {
            return false;
        }
        String compact = compactWhitespace(improvement);
        return BANNED_IMPROVEMENT_PHRASES.stream().anyMatch(compact::contains)
                || IMPERATIVE_ENDINGS.stream()
                .anyMatch(ending -> compact.endsWith(ending) || compact.endsWith(ending + "."));
    }

    private boolean isGroundedInSource(String keyword, String sourceText) {
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

    private Set<String> coreTokens(String value) {
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

    private boolean isCoreToken(String token) {
        return StringUtils.hasText(token)
                && token.length() >= 2
                && !STOP_WORDS.contains(token);
    }

    private String stripKoreanSuffix(String token) {
        if (token == null || !KOREAN_ONLY_PATTERN.matcher(token).matches()) {
            return token == null ? "" : token;
        }
        String result = token;
        boolean changed;
        do {
            changed = false;
            for (String suffix : KOREAN_SUFFIXES) {
                if (result.length() > suffix.length() + 1 && result.endsWith(suffix)) {
                    result = result.substring(0, result.length() - suffix.length());
                    changed = true;
                    break;
                }
            }
        } while (changed);
        return result;
    }

    private boolean containsExact(String sourceText, String value) {
        return StringUtils.hasText(sourceText)
                && StringUtils.hasText(value)
                && sourceText.contains(value);
    }

    private boolean containsNormalized(String sourceText, String keyword) {
        return StringUtils.hasText(sourceText)
                && StringUtils.hasText(keyword)
                && normalize(sourceText).contains(normalize(keyword));
    }

    private boolean equalsNormalized(String left, String right) {
        return StringUtils.hasText(left)
                && StringUtils.hasText(right)
                && normalize(left).equals(normalize(right));
    }

    private boolean isCopiedFromAnotherAnswerSentence(String sentence, String answer, String improvement) {
        if (!StringUtils.hasText(answer) || !StringUtils.hasText(improvement)) {
            return false;
        }
        return containsNormalized(answer, improvement) && !equalsNormalized(sentence, improvement);
    }

    private boolean isMetaImprovement(String improvement) {
        String normalized = normalize(improvement);
        for (String term : META_IMPROVEMENT_TERMS) {
            if (normalized.contains(term)) {
                return true;
            }
        }
        return false;
    }

    private boolean isNullLikeImprovement(String improvement) {
        String normalized = normalize(improvement);
        return "null".equals(normalized)
                || "n/a".equals(normalized)
                || "na".equals(normalized)
                || "없음".equals(normalized);
    }

    private boolean changesSentenceTense(String sentence, String improvement) {
        return isPastSentence(sentence) && isFutureSentence(improvement)
                || isFutureSentence(sentence) && isPastSentence(improvement);
    }

    private boolean isPastSentence(String value) {
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

    private boolean isFutureSentence(String value) {
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

    private String normalize(String value) {
        return normalizeStatic(value);
    }

    private static Set<String> normalizedSet(String... values) {
        return Arrays.stream(values)
                .map(EvaluationSanitizationService::normalizeStatic)
                .collect(LinkedHashSet::new, Set::add, Set::addAll);
    }

    private static List<String> compactList(String... values) {
        return Arrays.stream(values)
                .map(EvaluationSanitizationService::compactWhitespace)
                .toList();
    }

    private static String compactWhitespace(String value) {
        return value == null ? "" : WHITESPACE_PATTERN.matcher(value).replaceAll("");
    }

    private static String normalizeStatic(String value) {
        return compactWhitespace(value).toLowerCase(Locale.ROOT);
    }

    private record IndexedKeyword(
            int index,
            String keyword
    ) {
    }
}
