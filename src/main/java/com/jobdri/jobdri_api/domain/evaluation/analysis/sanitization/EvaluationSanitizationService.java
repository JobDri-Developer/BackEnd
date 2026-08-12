package com.jobdri.jobdri_api.domain.evaluation.analysis.sanitization;

import com.jobdri.jobdri_api.domain.analysis.dto.external.llm.AnalysisCandidateResponse;
import com.jobdri.jobdri_api.domain.analysis.dto.response.MissingKeywordSource;
import com.jobdri.jobdri_api.domain.analysis.service.sanitization.AnalysisSanitizationRules;
import com.jobdri.jobdri_api.domain.analysis.service.sanitization.MissingKeywordSanitizationResult;
import com.jobdri.jobdri_api.domain.analysis.service.sanitization.MissingKeywordSanitizer;
import com.jobdri.jobdri_api.domain.evaluation.analysis.mapper.EvaluationMissingKeywordSourceMapper;
import com.jobdri.jobdri_api.domain.evaluation.analysis.model.EvaluationMissingKeywordSource;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class EvaluationSanitizationService {

    public boolean isValidMissingKeyword(
            String keyword,
            EvaluationMissingKeywordSource source,
            String mainTasks,
            String qualifications
    ) {
        MissingKeywordSource analysisSource = EvaluationMissingKeywordSourceMapper.toAnalysisSource(source);
        return analysisSource != null && AnalysisSanitizationRules.isValidMissingKeyword(
                keyword,
                analysisSource,
                mainTasks,
                qualifications
        );
    }

    public boolean isValidMissingKeyword(
            String keyword,
            MissingKeywordSource source,
            String mainTasks,
            String qualifications
    ) {
        return source != null && AnalysisSanitizationRules.isValidMissingKeyword(
                keyword,
                source,
                mainTasks,
                qualifications
        );
    }

    public boolean isMissingKeywordMentionedInAnswers(String keyword, String answer) {
        return AnalysisSanitizationRules.isMissingKeywordMentionedInAnswers(keyword, answer);
    }

    public boolean hasValidProvenReason(String reason) {
        return AnalysisSanitizationRules.hasValidProvenReason(reason);
    }

    public boolean hasFabricatedDirectConflictEvidence(String sentence, String reason) {
        return AnalysisSanitizationRules.hasFabricatedDirectConflictEvidence(sentence, reason);
    }

    public String normalizeImprovement(String sentence, String answer, String improvement, boolean proven) {
        return AnalysisSanitizationRules.normalizeImprovement(sentence, answer, improvement, proven);
    }

    public boolean isStructuredQualificationKeyword(String value) {
        return AnalysisSanitizationRules.isStructuredQualificationKeyword(value);
    }

    public String normalizeText(String value) {
        return AnalysisSanitizationRules.normalizeText(value);
    }

    public MissingKeywordSanitizationResult sanitizeMissingKeywordCandidates(
            String mainTasks,
            String qualifications,
            String answer,
            List<AnalysisCandidateResponse.MissingKeywordCandidate> candidates
    ) {
        return MissingKeywordSanitizer.sanitize(mainTasks, qualifications, answer, candidates);
    }
}
