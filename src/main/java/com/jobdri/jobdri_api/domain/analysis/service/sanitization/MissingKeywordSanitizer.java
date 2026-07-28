package com.jobdri.jobdri_api.domain.analysis.service.sanitization;

import com.jobdri.jobdri_api.domain.analysis.dto.llm.AnalysisCandidateResponse;
import com.jobdri.jobdri_api.domain.analysis.dto.response.MissingKeywordSource;
import com.jobdri.jobdri_api.domain.analysis.service.ai.AnalysisPromptInput;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class MissingKeywordSanitizer {
    private static final int MAX_ACCEPTED_COUNT = 3;

    private MissingKeywordSanitizer() {
    }

    public static MissingKeywordSanitizationResult sanitize(
            AnalysisPromptInput promptInput,
            AnalysisCandidateResponse candidates
    ) {
        return sanitize(
                promptInput == null ? "" : promptInput.mainTasks(),
                promptInput == null ? "" : promptInput.qualifications(),
                "",
                candidates == null ? null : candidates.missingKeywordCandidates()
        );
    }

    public static MissingKeywordSanitizationResult sanitize(
            String mainTasks,
            String qualifications,
            String answer,
            List<AnalysisCandidateResponse.MissingKeywordCandidate> candidates
    ) {
        if (candidates == null) {
            return new MissingKeywordSanitizationResult(List.of(), List.of());
        }

        List<AnalysisCandidateResponse.MissingKeywordCandidate> acceptedCandidates = new ArrayList<>();
        List<MissingKeywordSanitizationDecision> decisions = new ArrayList<>();
        Map<String, IndexedKeyword> seen = new HashMap<>();

        for (int i = 0; i < candidates.size(); i++) {
            AnalysisCandidateResponse.MissingKeywordCandidate candidate = candidates.get(i);
            MissingKeywordSanitizationDecision decision = decide(
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

        return new MissingKeywordSanitizationResult(List.copyOf(acceptedCandidates), List.copyOf(decisions));
    }

    private static MissingKeywordSanitizationDecision decide(
            int index,
            AnalysisCandidateResponse.MissingKeywordCandidate candidate,
            String mainTasks,
            String qualifications,
            String answer,
            Map<String, IndexedKeyword> seen,
            int acceptedCount
    ) {
        if (candidate == null) {
            return rejected(index, null, "", answer, false, null, MissingKeywordRejectionReason.NULL_CANDIDATE);
        }
        String keyword = candidate.keyword();
        String normalizedKeyword = AnalysisSanitizationRules.normalizeText(keyword);
        if (!StringUtils.hasText(keyword)) {
            return rejected(index, candidate, normalizedKeyword, answer, false, null, MissingKeywordRejectionReason.BLANK_KEYWORD);
        }
        if (acceptedCount >= MAX_ACCEPTED_COUNT) {
            return rejected(index, candidate, normalizedKeyword, answer, false, null, MissingKeywordRejectionReason.MAX_ACCEPTED_LIMIT);
        }

        Optional<MissingKeywordSource> source = parseCandidateSource(candidate.source());
        if (source.isEmpty()) {
            return rejected(index, candidate, normalizedKeyword, answer, false, null, MissingKeywordRejectionReason.INVALID_FORMAT);
        }
        if (source.get() == MissingKeywordSource.PREFERENCE) {
            return rejected(index, candidate, normalizedKeyword, answer, false, null, MissingKeywordRejectionReason.UNSUPPORTED_KEYWORD);
        }
        if (AnalysisSanitizationRules.isStructuredQualificationKeyword(keyword)) {
            return rejected(index, candidate, normalizedKeyword, answer, false, null, MissingKeywordRejectionReason.CERTIFICATE_OR_QUANTITATIVE_NOISE);
        }
        if (!AnalysisSanitizationRules.hasMissingKeywordCoreTokens(keyword)) {
            return rejected(index, candidate, normalizedKeyword, answer, false, null, MissingKeywordRejectionReason.TOO_GENERIC);
        }

        boolean grounded = AnalysisSanitizationRules.isGroundedMissingKeyword(keyword, source.get(), mainTasks, qualifications);
        if (!grounded) {
            return rejected(index, candidate, normalizedKeyword, answer, false, null, MissingKeywordRejectionReason.NOT_RELATED_TO_JD);
        }

        IndexedKeyword duplicate = seen.get(normalizedKeyword);
        if (duplicate != null) {
            MissingKeywordRejectionReason reason = duplicate.keyword().trim().equals(keyword.trim())
                    ? MissingKeywordRejectionReason.DUPLICATE_KEYWORD
                    : MissingKeywordRejectionReason.NORMALIZATION_COLLISION;
            return rejected(index, candidate, normalizedKeyword, answer, grounded, duplicate.index(), reason);
        }

        return new MissingKeywordSanitizationDecision(
                index,
                candidate,
                normalizedKeyword,
                true,
                MissingKeywordRejectionReason.ACCEPTED,
                containsExact(answer, keyword),
                containsNormalized(answer, keyword),
                true,
                null
        );
    }

    private static MissingKeywordSanitizationDecision rejected(
            int index,
            AnalysisCandidateResponse.MissingKeywordCandidate candidate,
            String normalizedKeyword,
            String answer,
            boolean jdRequirementMatched,
            Integer duplicateOfCandidateIndex,
            MissingKeywordRejectionReason reason
    ) {
        String keyword = candidate == null ? "" : candidate.keyword();
        return new MissingKeywordSanitizationDecision(
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

    private static Optional<MissingKeywordSource> parseCandidateSource(String source) {
        if ("MAIN_TASK".equalsIgnoreCase(defaultString(source))) {
            return Optional.of(MissingKeywordSource.MAIN_TASK);
        }
        if ("QUALIFICATION".equalsIgnoreCase(defaultString(source))) {
            return Optional.of(MissingKeywordSource.QUALIFICATION);
        }
        if ("PREFERENCE".equalsIgnoreCase(defaultString(source))) {
            return Optional.of(MissingKeywordSource.PREFERENCE);
        }
        return MissingKeywordSource.from(source);
    }

    private static boolean containsExact(String sourceText, String value) {
        return StringUtils.hasText(sourceText)
                && StringUtils.hasText(value)
                && sourceText.contains(value);
    }

    private static boolean containsNormalized(String sourceText, String value) {
        return StringUtils.hasText(sourceText)
                && StringUtils.hasText(value)
                && AnalysisSanitizationRules.normalizeText(sourceText)
                .contains(AnalysisSanitizationRules.normalizeText(value));
    }

    private static String defaultString(String value) {
        return value == null ? "" : value.trim();
    }

    private record IndexedKeyword(
            int index,
            String keyword
    ) {
    }
}
