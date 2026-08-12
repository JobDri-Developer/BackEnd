package com.jobdri.jobdri_api.domain.analysis.service.ai;

import com.jobdri.jobdri_api.domain.analysis.dto.external.llm.AnalysisCandidateResponse;
import com.jobdri.jobdri_api.domain.analysis.dto.external.llm.AnalysisLlmResponse;
import com.jobdri.jobdri_api.domain.analysis.dto.external.llm.CandidateRecheckResponse;
import com.jobdri.jobdri_api.domain.analysis.dto.external.llm.CandidateRecheckResponse.RecheckDecision;
import com.jobdri.jobdri_api.domain.analysis.dto.external.llm.CandidateReviewResponse;
import com.jobdri.jobdri_api.domain.analysis.dto.external.llm.CandidateReviewResponse.RejectionCode;
import com.jobdri.jobdri_api.domain.analysis.dto.internal.criteria.JobCategoryEvaluationCriteria;
import com.jobdri.jobdri_api.domain.analysis.dto.response.MissingKeywordSource;
import com.jobdri.jobdri_api.domain.analysis.infrastructure.ai.OpenAiAnalysisAdapter;
import com.jobdri.jobdri_api.domain.analysis.type.QuestionAnalysisStatus;
import com.jobdri.jobdri_api.domain.analysis.entity.Question;
import com.jobdri.jobdri_api.domain.analysis.application.model.AnalysisExecutionPayload;
import com.jobdri.jobdri_api.domain.analysis.service.sanitization.AnalysisSanitizationRules;
import com.jobdri.jobdri_api.domain.analysis.service.sanitization.MissingKeywordSanitizer;
import com.jobdri.jobdri_api.domain.corpus.service.CorpusRetrievalService;
import com.jobdri.jobdri_api.domain.corpus.service.CorpusRetrievalService.RetrievalContext;
import com.jobdri.jobdri_api.domain.jobposting.entity.JobPosting;
import com.jobdri.jobdri_api.global.apiPayload.code.GeneralErrorCode;
import com.jobdri.jobdri_api.global.apiPayload.exception.GeneralException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.ArrayList;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@Slf4j
@RequiredArgsConstructor
// 자소서 분석에 필요한 프롬프트를 만들고 LLM 호출을 수행하는 클라이언트다.
public class AnalysisAiClient {
    private static final int MAX_CANDIDATES_PER_QUESTION = 3;
    private static final int RECHECK_MIN_PROBLEM_CLARITY = 4;
    private static final int RECHECK_MIN_JOB_RELEVANCE = 4;
    private static final int RECHECK_MIN_IMPROVEMENT_USEFULNESS = 4;
    private static final int RECHECK_MIN_FABRICATION_CONFIDENCE = 4;
    private final CorpusRetrievalService corpusRetrievalService;
    private final AnalysisPromptBuilder analysisPromptBuilder;
    private final AnalysisResponseParser analysisResponseParser;
    private final OpenAiAnalysisAdapter openAiAnalysisAdapter;

    @Value("${openai.model.cover-letter-analysis:gpt-4o-mini}")
    private String analysisModel;

    @Value("${analysis.two-pass.enabled:false}")
    private boolean twoPassEnabled;

    @Value("${analysis.mode:}")
    private String analysisMode;

    @PostConstruct
    void validateAnalysisModeProperty() {
        resolveAnalysisMode();
    }

    public AnalysisLlmResponse analyze(AnalysisExecutionPayload payload) {
        return analyze(
                payload.jobPosting(),
                payload.answeredQuestions(),
                payload.jobCategoryEvaluationCriteria(),
                payload.retrievalContext()
        );
    }

    public AnalysisLlmResponse analyze(JobPosting jobPosting, List<Question> questions) {
        return analyze(jobPosting, questions, null, null);
    }

    public AnalysisLlmResponse analyze(
            JobPosting jobPosting,
            List<Question> questions,
            JobCategoryEvaluationCriteria jobCategoryEvaluationCriteria
    ) {
        return analyze(jobPosting, questions, jobCategoryEvaluationCriteria, null);
    }

    public AnalysisLlmResponse analyze(
            JobPosting jobPosting,
            List<Question> questions,
            JobCategoryEvaluationCriteria jobCategoryEvaluationCriteria,
            RetrievalContext precomputedReferenceContext
    ) {
        RetrievalContext referenceContext = resolveReferenceContext(jobPosting, questions, precomputedReferenceContext);
        try {
            AnalysisPromptInput promptInput = AnalysisPromptInput.from(jobPosting, questions);
            return switch (resolveAnalysisMode()) {
                case TWO_PASS -> analyzeTwoPass(
                        promptInput,
                        referenceContext,
                        jobCategoryEvaluationCriteria,
                        "cover-letter-analysis",
                        null
                ).response();
                case HYBRID_EXACT -> analyzeHybridExact(
                        promptInput,
                        referenceContext,
                        jobCategoryEvaluationCriteria,
                        "cover-letter-analysis",
                        null
                ).response();
                case SINGLE_PASS -> analyzeSinglePass(
                        promptInput,
                        referenceContext,
                        jobCategoryEvaluationCriteria,
                        "cover-letter-analysis",
                        null
                ).response();
            };
        } catch (GeneralException e) {
            throw e;
        } catch (Exception e) {
            log.error("자소서 분석 OpenAI API 호출 오류: {}", e.getMessage(), e);
            throw new GeneralException(
                    GeneralErrorCode.SERVICE_UNAVAILABLE,
                    "자소서 분석 AI 호출에 실패했습니다."
            );
        }
    }

    private RetrievalContext resolveReferenceContext(
            JobPosting jobPosting,
            List<Question> questions,
            RetrievalContext precomputedReferenceContext
    ) {
        if (precomputedReferenceContext != null) {
            return precomputedReferenceContext;
        }

        RetrievalContext referenceContext = emptyContext();
        try {
            referenceContext = corpusRetrievalService.retrieveForAnalysis(jobPosting, questions);
        } catch (Exception e) {
            log.warn("자소서 분석 retrieval 실패. mock analysis will continue without references. message={}", e.getMessage());
            log.debug("analysis retrieval exception", e);
        }
        return referenceContext;
    }

    public AnalysisLlmResponse analyzeForEvaluation(
            AnalysisPromptInput promptInput,
            JobCategoryEvaluationCriteria jobCategoryEvaluationCriteria
    ) {
        return analyzeForEvaluationResult(promptInput, jobCategoryEvaluationCriteria).response();
    }

    public AnalysisAiCallResult analyzeForEvaluationResult(
            AnalysisPromptInput promptInput,
            JobCategoryEvaluationCriteria jobCategoryEvaluationCriteria
    ) {
        return analyzeForEvaluationResult(promptInput, jobCategoryEvaluationCriteria, null);
    }

    public AnalysisAiCallResult analyzeForEvaluationResult(
            AnalysisPromptInput promptInput,
            JobCategoryEvaluationCriteria jobCategoryEvaluationCriteria,
            Instant deadline
    ) {
        try {
            return switch (resolveAnalysisMode()) {
                case TWO_PASS -> analyzeTwoPass(
                        promptInput,
                        emptyContext(),
                        jobCategoryEvaluationCriteria,
                        "cover-letter-analysis-evaluation",
                        deadline
                );
                case HYBRID_EXACT -> analyzeHybridExact(
                        promptInput,
                        emptyContext(),
                        jobCategoryEvaluationCriteria,
                        "cover-letter-analysis-evaluation",
                        deadline
                );
                case SINGLE_PASS -> analyzeSinglePass(
                        promptInput,
                        emptyContext(),
                        jobCategoryEvaluationCriteria,
                        "cover-letter-analysis-evaluation",
                        deadline
                );
            };
        } catch (GeneralException e) {
            throw e;
        } catch (Exception e) {
            log.error("평가용 자소서 분석 OpenAI API 호출 오류: {}", e.getMessage(), e);
            throw new GeneralException(
                    GeneralErrorCode.SERVICE_UNAVAILABLE,
                    "평가용 자소서 분석 AI 호출에 실패했습니다."
            );
        }
    }

    private AnalysisAiCallResult analyzeSinglePass(
            AnalysisPromptInput promptInput,
            RetrievalContext referenceContext,
            JobCategoryEvaluationCriteria jobCategoryEvaluationCriteria,
            String operationName,
            Instant deadline
    ) {
        long startedAt = System.nanoTime();
        AnalysisLlmResponse response = createStructuredResponse(
                operationName,
                buildPrompt(promptInput, referenceContext, jobCategoryEvaluationCriteria),
                AnalysisLlmResponse.class,
                deadline
        );
        response = analysisResponseParser.sanitizeSinglePassSubheadings(promptInput, response);
        return AnalysisAiCallResult.singlePass(response, elapsedMillis(startedAt));
    }

    private AnalysisAiCallResult analyzeTwoPass(
            AnalysisPromptInput promptInput,
            RetrievalContext referenceContext,
            JobCategoryEvaluationCriteria jobCategoryEvaluationCriteria,
            String operationName,
            Instant deadline
    ) {
        long candidateStartedAt = System.nanoTime();
        AnalysisCandidateResponse rawCandidates = createStructuredResponse(
                operationName + "-candidates",
                buildCandidatePrompt(promptInput, referenceContext, jobCategoryEvaluationCriteria),
                AnalysisCandidateResponse.class,
                deadline
        );
        long candidateLatencyMs = elapsedMillis(candidateStartedAt);
        AnalysisCandidateResponse sanitizedCandidates = sanitizeCandidates(promptInput, rawCandidates);
        log.debug(
                "analysis two-pass candidate result. enabled={}, rawAnalysisCount={}, sanitizedAnalysisCount={}, rawStrengthCount={}, sanitizedStrengthCount={}, rawMissingKeywordCount={}, sanitizedMissingKeywordCount={}, model={}, latencyMs={}",
                true,
                size(rawCandidates == null ? null : rawCandidates.analysisCandidates()),
                size(sanitizedCandidates.analysisCandidates()),
                size(rawCandidates == null ? null : rawCandidates.strengthCandidates()),
                size(sanitizedCandidates.strengthCandidates()),
                size(rawCandidates == null ? null : rawCandidates.missingKeywordCandidates()),
                size(sanitizedCandidates.missingKeywordCandidates()),
                analysisModel,
                candidateLatencyMs
        );

        long finalStartedAt = System.nanoTime();
        CandidateReviewResponse reviewResponse = createStructuredResponse(
                operationName + "-final",
                buildFinalPrompt(promptInput, referenceContext, jobCategoryEvaluationCriteria, sanitizedCandidates),
                CandidateReviewResponse.class,
                deadline
        );
        CandidateReviewResponse validatedReviewResponse = validateCandidateReview(
                promptInput,
                sanitizedCandidates,
                reviewResponse
        );
        log.debug(
                "analysis two-pass review validation. firstPassCandidates={}, rawDecisions={}, validatedDecisions={}, secondPassAccepted={}, secondPassRejected={}, rejectionCodeCounts={}",
                size(sanitizedCandidates.analysisCandidates()),
                size(reviewResponse == null ? null : reviewResponse.decisions()),
                size(validatedReviewResponse.decisions()),
                acceptedDecisionCount(validatedReviewResponse),
                rejectedDecisionCount(validatedReviewResponse),
                rejectionCodeCounts(validatedReviewResponse)
        );
        CandidateReviewResponse recheckedReviewResponse = recheckWhenAllCandidatesRejected(
                promptInput,
                referenceContext,
                jobCategoryEvaluationCriteria,
                sanitizedCandidates,
                validatedReviewResponse,
                operationName,
                deadline
        );
        AnalysisLlmResponse response = buildFinalResponse(promptInput, sanitizedCandidates, recheckedReviewResponse);
        long finalLatencyMs = elapsedMillis(finalStartedAt);
        logQuestionFlowStats(sanitizedCandidates, recheckedReviewResponse, response);
        log.debug(
                "Two-pass missing keyword flow. twoPassRawMissingKeywordCount={}, twoPassParsedMissingKeywordCount={}, twoPassSanitizedMissingKeywordCount={}, twoPassReviewMissingKeywordCount={}, twoPassFinalMissingKeywordCount={}",
                size(rawCandidates == null ? null : rawCandidates.missingKeywordCandidates()),
                size(rawCandidates == null ? null : rawCandidates.missingKeywordCandidates()),
                size(sanitizedCandidates.missingKeywordCandidates()),
                size(recheckedReviewResponse == null ? null : recheckedReviewResponse.missingKeywords()),
                size(response == null ? null : response.missingKeywords())
        );
        log.debug(
                "analysis two-pass final result. enabled={}, firstPassCandidates={}, secondPassAccepted={}, finalAnalysisCount={}, removedByRejected={}, model={}, latencyMs={}",
                true,
                size(sanitizedCandidates.analysisCandidates()),
                acceptedDecisionCount(recheckedReviewResponse),
                size(response == null ? null : response.questionAnalyses()),
                rejectedDecisionCount(recheckedReviewResponse),
                analysisModel,
                finalLatencyMs
        );
        return AnalysisAiCallResult.twoPass(
                response,
                rawCandidates,
                sanitizedCandidates,
                recheckedReviewResponse,
                candidateLatencyMs,
                finalLatencyMs
        );
    }

    private AnalysisAiCallResult analyzeHybridExact(
            AnalysisPromptInput promptInput,
            RetrievalContext referenceContext,
            JobCategoryEvaluationCriteria jobCategoryEvaluationCriteria,
            String operationName,
            Instant deadline
    ) {
        AnalysisAiCallResult singlePassResult = analyzeSinglePass(
                promptInput,
                referenceContext,
                jobCategoryEvaluationCriteria,
                operationName + "-single-pass",
                deadline
        );
        AnalysisAiCallResult twoPassResult = analyzeTwoPass(
                promptInput,
                referenceContext,
                jobCategoryEvaluationCriteria,
                operationName + "-two-pass",
                deadline
        );
        AnalysisLlmResponse merged = mergeHybridExact(
                singlePassResult.response(),
                twoPassResult.response()
        );
        log.debug(
                "Hybrid exact response merged. questionAnalysesSource=single-pass, missingKeywordsSource=two-pass, scoreSource=single-pass, singlePassQuestionAnalyses={}, twoPassQuestionAnalyses={}, mergedQuestionAnalyses={}, singlePassMissingKeywords={}, hybridInputMissingKeywordCount={}, hybridMergedMissingKeywordCount={}",
                size(singlePassResult.response() == null ? null : singlePassResult.response().questionAnalyses()),
                size(twoPassResult.response() == null ? null : twoPassResult.response().questionAnalyses()),
                size(merged == null ? null : merged.questionAnalyses()),
                size(singlePassResult.response() == null ? null : singlePassResult.response().missingKeywords()),
                size(twoPassResult.response() == null ? null : twoPassResult.response().missingKeywords()),
                size(merged == null ? null : merged.missingKeywords())
        );
        return AnalysisAiCallResult.hybridExact(
                merged,
                twoPassResult.rawCandidateResponse(),
                twoPassResult.sanitizedCandidateResponse(),
                twoPassResult.candidateReviewResponse(),
                twoPassResult.candidateCallLatencyMs(),
                singlePassResult.finalCallLatencyMs() + twoPassResult.finalCallLatencyMs()
        );
    }

    private <T> T createStructuredResponse(String operationName, String prompt, Class<T> responseType) {
        return openAiAnalysisAdapter.createStructuredResponse(operationName, prompt, responseType);
    }

    private <T> T createStructuredResponse(
            String operationName,
            String prompt,
            Class<T> responseType,
            Instant deadline
    ) {
        if (deadline == null) {
            return openAiAnalysisAdapter.createStructuredResponse(operationName, prompt, responseType);
        }
        Duration remaining = Duration.between(Instant.now(), deadline);
        if (remaining.isZero() || remaining.isNegative()) {
            throw new GeneralException(
                    GeneralErrorCode.EXTERNAL_SERVICE_TIMEOUT,
                    "평가 사례 처리 시간이 제한을 초과했습니다."
            );
        }
        return openAiAnalysisAdapter.createStructuredResponse(operationName, prompt, responseType, remaining);
    }

    private <T> T createStructuredResponse(String operationName, String prompt, Class<T> responseType, Duration timeout) {
        return openAiAnalysisAdapter.createStructuredResponse(operationName, prompt, responseType);
    }

    String buildPrompt(
            JobPosting jobPosting,
            List<Question> questions,
            RetrievalContext referenceContext,
            JobCategoryEvaluationCriteria jobCategoryEvaluationCriteria
    ) {
        return analysisPromptBuilder.buildPrompt(jobPosting, questions, referenceContext, jobCategoryEvaluationCriteria);
    }

    String buildPrompt(
            AnalysisPromptInput promptInput,
            RetrievalContext referenceContext,
            JobCategoryEvaluationCriteria jobCategoryEvaluationCriteria
    ) {
        return analysisPromptBuilder.buildPrompt(promptInput, referenceContext, jobCategoryEvaluationCriteria);
    }

    String buildCandidatePrompt(
            AnalysisPromptInput promptInput,
            RetrievalContext referenceContext,
            JobCategoryEvaluationCriteria jobCategoryEvaluationCriteria
    ) {
        return analysisPromptBuilder.buildCandidatePrompt(promptInput, referenceContext, jobCategoryEvaluationCriteria);
    }

    String buildFinalPrompt(
            AnalysisPromptInput promptInput,
            RetrievalContext referenceContext,
            JobCategoryEvaluationCriteria jobCategoryEvaluationCriteria,
            AnalysisCandidateResponse candidates
    ) {
        return analysisPromptBuilder.buildFinalPrompt(promptInput, referenceContext, jobCategoryEvaluationCriteria, candidates);
    }

    String buildRecheckPrompt(
            AnalysisPromptInput promptInput,
            RetrievalContext referenceContext,
            JobCategoryEvaluationCriteria jobCategoryEvaluationCriteria,
            AnalysisCandidateResponse candidates,
            CandidateReviewResponse reviewResponse
    ) {
        return analysisPromptBuilder.buildRecheckPrompt(
                promptInput,
                referenceContext,
                jobCategoryEvaluationCriteria,
                candidates,
                reviewResponse
        );
    }

    AnalysisCandidateResponse sanitizeCandidates(
            AnalysisPromptInput promptInput,
            AnalysisCandidateResponse candidates
    ) {
        Map<Long, String> answerByQuestionId = promptInput.questions().stream()
                .collect(Collectors.toMap(AnalysisPromptInput.QuestionAnswer::questionId, AnalysisPromptInput.QuestionAnswer::answer));
        List<AnalysisCandidateResponse.StrengthCandidate> strengthCandidates = sanitizeStrengthCandidates(candidates, answerByQuestionId);
        List<AnalysisCandidateResponse.AnalysisCandidate> analysisCandidates = sanitizeAnalysisCandidates(candidates, answerByQuestionId);
        List<AnalysisCandidateResponse.MissingKeywordCandidate> missingKeywordCandidates = sanitizeMissingKeywordCandidates(promptInput, candidates);
        return new AnalysisCandidateResponse(strengthCandidates, analysisCandidates, missingKeywordCandidates);
    }

    private List<AnalysisCandidateResponse.StrengthCandidate> sanitizeStrengthCandidates(
            AnalysisCandidateResponse candidates,
            Map<Long, String> answerByQuestionId
    ) {
        if (candidates == null || candidates.strengthCandidates() == null) {
            return List.of();
        }
        List<AnalysisCandidateResponse.StrengthCandidate> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        Map<Long, Integer> countByQuestionId = new HashMap<>();
        for (AnalysisCandidateResponse.StrengthCandidate candidate : candidates.strengthCandidates()) {
            if (candidate == null || candidate.questionId() == null || !StringUtils.hasText(candidate.quote())) {
                continue;
            }
            String answer = answerByQuestionId.get(candidate.questionId());
            if (!containsExact(answer, candidate.quote())
                    || isBracketedSubheading(answer, candidate.quote())
                    || !isPrimarySource(candidate.relatedSource())) {
                continue;
            }
            String dedupeKey = candidate.questionId() + ":" + normalize(candidate.quote());
            if (!seen.add(dedupeKey)) {
                continue;
            }
            int currentCount = countByQuestionId.getOrDefault(candidate.questionId(), 0);
            if (currentCount >= MAX_CANDIDATES_PER_QUESTION) {
                continue;
            }
            result.add(candidate);
            countByQuestionId.put(candidate.questionId(), currentCount + 1);
        }
        return result;
    }

    private List<AnalysisCandidateResponse.AnalysisCandidate> sanitizeAnalysisCandidates(
            AnalysisCandidateResponse candidates,
            Map<Long, String> answerByQuestionId
    ) {
        if (candidates == null || candidates.analysisCandidates() == null) {
            return List.of();
        }
        List<AnalysisCandidateResponse.AnalysisCandidate> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        Map<Long, Integer> countByQuestionId = new HashMap<>();
        for (AnalysisCandidateResponse.AnalysisCandidate candidate : candidates.analysisCandidates()) {
            if (candidate == null || candidate.questionId() == null || !StringUtils.hasText(candidate.sentence())) {
                continue;
            }
            String answer = answerByQuestionId.get(candidate.questionId());
            if (!containsExact(answer, candidate.sentence())
                    || isBracketedSubheading(answer, candidate.sentence())
                    || !isPrimarySource(candidate.relatedSource())) {
                continue;
            }
            QuestionAnalysisStatus status = parseQuestionAnalysisStatus(candidate.status());
            if (status != QuestionAnalysisStatus.MENTIONED && status != QuestionAnalysisStatus.FABRICATED) {
                continue;
            }
            if (status == QuestionAnalysisStatus.FABRICATED
                    && !AnalysisSanitizationRules.hasFabricatedDirectConflictEvidence(
                            candidate.sentence(),
                            candidate.reasonBasis()
                    )) {
                continue;
            }
            if (!StringUtils.hasText(candidate.candidateId())) {
                continue;
            }
            String dedupeKey = candidate.questionId() + ":" + normalize(candidate.sentence());
            if (!seen.add(dedupeKey)) {
                continue;
            }
            int currentCount = countByQuestionId.getOrDefault(candidate.questionId(), 0);
            if (currentCount >= MAX_CANDIDATES_PER_QUESTION) {
                continue;
            }
            result.add(new AnalysisCandidateResponse.AnalysisCandidate(
                    candidate.candidateId().trim(),
                    candidate.questionId(),
                    candidate.sentence(),
                    contextBefore(answer, candidate.sentence()),
                    contextAfter(answer, candidate.sentence()),
                    candidate.sentenceType(),
                    candidate.relatedSource(),
                    candidate.relatedRequirement(),
                    candidate.status(),
                    candidate.issueType(),
                    candidate.reasonBasis()
            ));
            countByQuestionId.put(candidate.questionId(), currentCount + 1);
        }
        return result;
    }

    private List<AnalysisCandidateResponse.MissingKeywordCandidate> sanitizeMissingKeywordCandidates(
            AnalysisPromptInput promptInput,
            AnalysisCandidateResponse candidates
    ) {
        return MissingKeywordSanitizer.sanitize(
                promptInput == null ? "" : promptInput.mainTasks(),
                promptInput == null ? "" : promptInput.qualifications(),
                "",
                candidates == null ? null : candidates.missingKeywordCandidates()
        ).acceptedCandidates();
    }

    private Optional<MissingKeywordSource> parseCandidateSource(String source) {
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

    AnalysisLlmResponse buildFinalResponse(
            AnalysisPromptInput promptInput,
            AnalysisCandidateResponse sanitizedCandidates,
            CandidateReviewResponse reviewResponse
    ) {
        List<AnalysisLlmResponse.QuestionAnalysisItem> questionAnalyses = buildAcceptedQuestionAnalyses(
                promptInput,
                sanitizedCandidates,
                reviewResponse
        );
        List<AnalysisLlmResponse.HighlightItem> keyStrengths = buildFinalStrengths(
                sanitizedCandidates,
                reviewResponse,
                questionAnalyses
        );
        List<AnalysisLlmResponse.MissingKeywordItem> missingKeywords = buildFinalMissingKeywords(
                promptInput,
                sanitizedCandidates
        );
        return new AnalysisLlmResponse(
                reviewResponse == null ? null : reviewResponse.jobFit(),
                reviewResponse == null ? null : reviewResponse.impact(),
                reviewResponse == null ? null : reviewResponse.completeness(),
                reviewResponse == null ? null : reviewResponse.feedback(),
                keyStrengths,
                List.of(),
                missingKeywords,
                questionAnalyses
        );
    }

    CandidateReviewResponse recheckWhenAllCandidatesRejected(
            AnalysisPromptInput promptInput,
            RetrievalContext referenceContext,
            JobCategoryEvaluationCriteria jobCategoryEvaluationCriteria,
            AnalysisCandidateResponse sanitizedCandidates,
            CandidateReviewResponse reviewResponse,
            String operationName,
            Instant deadline
    ) {
        int firstPassCandidates = sanitizedCandidates == null || sanitizedCandidates.analysisCandidates() == null
                ? 0
                : sanitizedCandidates.analysisCandidates().size();
        int acceptedCandidates = acceptedDecisionCount(reviewResponse);
        if (firstPassCandidates == 0 || acceptedCandidates > 0) {
            return reviewResponse;
        }

        log.debug(
                "analysis two-pass recheck triggered. firstPassCandidates={}, secondPassAccepted={}, secondPassRejected={}, rejectionCodeCounts={}",
                firstPassCandidates,
                acceptedCandidates,
                rejectedDecisionCount(reviewResponse),
                rejectionCodeCounts(reviewResponse)
        );
        CandidateRecheckResponse recheckResponse = createStructuredResponse(
                operationName + "-recheck",
                buildRecheckPrompt(promptInput, referenceContext, jobCategoryEvaluationCriteria, sanitizedCandidates, reviewResponse),
                CandidateRecheckResponse.class,
                deadline
        );
        CandidateReviewResponse rechecked = applyRecheckResponse(promptInput, sanitizedCandidates, reviewResponse, recheckResponse);
        int recoveredMentionedCount = recoveredDecisionCount(rechecked, QuestionAnalysisStatus.MENTIONED);
        int recoveredFabricatedCount = recoveredDecisionCount(rechecked, QuestionAnalysisStatus.FABRICATED);
        boolean keepRequested = recheckResponse != null && recheckResponse.decision() == RecheckDecision.KEEP_BEST_CANDIDATE;
        boolean recovered = acceptedDecisionCount(rechecked) > acceptedCandidates;
        log.debug(
                "analysis two-pass recheck aggregate. recheckTriggeredCount=1, recheckKeepCount={}, recheckNoCorrectionCount={}, recheckValidationRejectedCount={}, recoveredCandidateCount={}, recoveredMentionedCount={}, recoveredFabricatedCount={}, decision={}, candidateIdPresent={}, finalRejectedCandidateCount={}",
                keepRequested ? 1 : 0,
                recheckResponse != null && recheckResponse.decision() == RecheckDecision.NO_CORRECTION_NEEDED ? 1 : 0,
                keepRequested && !recovered ? 1 : 0,
                recovered ? 1 : 0,
                recoveredMentionedCount,
                recoveredFabricatedCount,
                recheckResponse == null ? null : recheckResponse.decision(),
                recheckResponse != null && StringUtils.hasText(recheckResponse.candidateId()),
                rejectedDecisionCount(rechecked)
        );
        return rechecked;
    }

    CandidateReviewResponse applyRecheckResponse(
            AnalysisPromptInput promptInput,
            AnalysisCandidateResponse sanitizedCandidates,
            CandidateReviewResponse reviewResponse,
            CandidateRecheckResponse recheckResponse
    ) {
        if (reviewResponse == null || recheckResponse == null
                || recheckResponse.decision() != RecheckDecision.KEEP_BEST_CANDIDATE
                || sanitizedCandidates == null
                || sanitizedCandidates.analysisCandidates() == null
                || !StringUtils.hasText(recheckResponse.candidateId())) {
            return reviewResponse;
        }

        Map<String, AnalysisCandidateResponse.AnalysisCandidate> candidateById = sanitizedCandidates.analysisCandidates().stream()
                .filter(candidate -> StringUtils.hasText(candidate.candidateId()))
                .collect(Collectors.toMap(
                        candidate -> candidate.candidateId().trim(),
                        candidate -> candidate,
                        (left, right) -> left
                ));
        String candidateId = recheckResponse.candidateId().trim();
        AnalysisCandidateResponse.AnalysisCandidate candidate = candidateById.get(candidateId);
        Optional<RecheckValidationFailureReason> validationFailure = recheckValidationFailure(
                promptInput,
                candidate,
                recheckResponse
        );
        if (candidate == null || validationFailure.isPresent()) {
            log.debug(
                    "analysis two-pass recheck rejected. candidateIdPresent={}, failureReason={}",
                    candidate != null,
                    validationFailure.map(Enum::name).orElse(RecheckValidationFailureReason.UNKNOWN_CANDIDATE.name())
            );
            return reviewResponse;
        }
        Map<Long, String> answerByQuestionId = promptInput.questions().stream()
                .collect(Collectors.toMap(AnalysisPromptInput.QuestionAnswer::questionId, AnalysisPromptInput.QuestionAnswer::answer));
        CandidateReviewResponse.CandidateDecision accepted = validateAcceptedDecision(
                new CandidateReviewResponse.CandidateDecision(
                        candidateId,
                        true,
                        RejectionCode.NONE,
                        recheckResponse.status(),
                        recheckResponse.reason(),
                        recheckResponse.improvement()
                ),
                candidate,
                answerByQuestionId.get(candidate.questionId())
        );
        if (accepted == null) {
            return reviewResponse;
        }

        List<CandidateReviewResponse.CandidateDecision> decisions = new ArrayList<>();
        if (reviewResponse.decisions() != null) {
            for (CandidateReviewResponse.CandidateDecision decision : reviewResponse.decisions()) {
                if (decision == null || !StringUtils.hasText(decision.candidateId())
                        || candidateId.equals(decision.candidateId().trim())) {
                    continue;
                }
                decisions.add(decision);
            }
        }
        decisions.add(0, accepted);
        return new CandidateReviewResponse(
                decisions,
                reviewResponse.strengths() == null ? List.of() : reviewResponse.strengths(),
                reviewResponse.missingKeywords() == null ? List.of() : reviewResponse.missingKeywords(),
                reviewResponse.jobFit(),
                reviewResponse.impact(),
                reviewResponse.completeness(),
                reviewResponse.feedback()
        );
    }

    private Optional<RecheckValidationFailureReason> recheckValidationFailure(
            AnalysisPromptInput promptInput,
            AnalysisCandidateResponse.AnalysisCandidate candidate,
            CandidateRecheckResponse response
    ) {
        if (candidate == null) {
            return Optional.of(RecheckValidationFailureReason.UNKNOWN_CANDIDATE);
        }
        String answer = promptInput.questions().stream()
                .filter(question -> Objects.equals(question.questionId(), candidate.questionId()))
                .map(AnalysisPromptInput.QuestionAnswer::answer)
                .findFirst()
                .orElse(null);
        if (isBracketedSubheading(answer, candidate.sentence())) {
            return Optional.of(RecheckValidationFailureReason.SUBHEADING);
        }
        if (!validRecheckScore(response.problemClarity()) || response.problemClarity() < RECHECK_MIN_PROBLEM_CLARITY) {
            return Optional.of(RecheckValidationFailureReason.LOW_PROBLEM_CLARITY);
        }
        if (!validRecheckScore(response.jobRelevance()) || response.jobRelevance() < RECHECK_MIN_JOB_RELEVANCE) {
            return Optional.of(RecheckValidationFailureReason.LOW_JOB_RELEVANCE);
        }
        if (!validRecheckScore(response.evidenceGap())) {
            return Optional.of(RecheckValidationFailureReason.LOW_EVIDENCE_GAP);
        }
        if (!validRecheckScore(response.improvementUsefulness())
                || response.improvementUsefulness() < RECHECK_MIN_IMPROVEMENT_USEFULNESS) {
            return Optional.of(RecheckValidationFailureReason.LOW_IMPROVEMENT_USEFULNESS);
        }
        if (!validRecheckScore(response.fabricationConfidence())) {
            return Optional.of(RecheckValidationFailureReason.LOW_FABRICATION_CONFIDENCE);
        }
        if (!Boolean.TRUE.equals(response.questionTypeMatched())) {
            return Optional.of(RecheckValidationFailureReason.QUESTION_TYPE_MISMATCH);
        }
        if (!Boolean.TRUE.equals(response.contextConsistent())) {
            return Optional.of(RecheckValidationFailureReason.CONTEXT_MISMATCH);
        }
        if (!Boolean.TRUE.equals(response.reasonSpecific())
                || !isSpecificRecheckReason(candidate, response.reason())) {
            return Optional.of(RecheckValidationFailureReason.GENERIC_REASON);
        }
        if (!Boolean.TRUE.equals(response.improvementActionable())
                || !isActionableRecheckImprovement(candidate, response.improvement())) {
            return Optional.of(RecheckValidationFailureReason.NON_ACTIONABLE_IMPROVEMENT);
        }
        QuestionAnalysisStatus status = parseQuestionAnalysisStatus(response.status());
        if (status == QuestionAnalysisStatus.FABRICATED
                && (response.fabricationConfidence() < RECHECK_MIN_FABRICATION_CONFIDENCE
                || !Boolean.TRUE.equals(response.directContradiction()))) {
            return Optional.of(RecheckValidationFailureReason.INVALID_FABRICATED);
        }
        Map<Long, String> answerByQuestionId = promptInput.questions().stream()
                .collect(Collectors.toMap(AnalysisPromptInput.QuestionAnswer::questionId, AnalysisPromptInput.QuestionAnswer::answer));
        if (!containsExact(answerByQuestionId.get(candidate.questionId()), candidate.sentence())) {
            return Optional.of(RecheckValidationFailureReason.SENTENCE_NOT_FOUND);
        }
        if (sentenceTypeCriterionMismatch(candidate, response.reason())) {
            return Optional.of(RecheckValidationFailureReason.QUESTION_TYPE_MISMATCH);
        }
        return Optional.empty();
    }

    private boolean validRecheckScore(Integer score) {
        return score != null && score >= 1 && score <= 5;
    }

    private boolean isSpecificRecheckReason(
            AnalysisCandidateResponse.AnalysisCandidate candidate,
            String reason
    ) {
        if (!StringUtils.hasText(reason) || reason.trim().length() < 30) {
            return false;
        }
        String normalizedReason = normalize(reason);
        Set<String> genericReasons = Set.of(
                normalize("구체성이 부족합니다."),
                normalize("성과를 명확히 작성해야 합니다."),
                normalize("직무 연관성을 강화해야 합니다."),
                normalize("내용을 더 구체적으로 작성해야 합니다."),
                normalize("설명이 부족합니다."),
                normalize("구체적인 방법론이 부족하여 개선이 필요합니다."),
                normalize("구체적인 행동이나 방법이 부족함."),
                normalize("구체적인 실행 방법이 부족합니다.")
        );
        if (genericReasons.contains(normalizedReason)) {
            return false;
        }
        boolean referencesSentence = meaningfulTokens(candidate.sentence()).stream()
                .anyMatch(token -> normalizedReason.contains(normalize(token)));
        boolean namesMissingElement = List.of(
                "행동",
                "역할",
                "결과",
                "방법",
                "과정",
                "기여",
                "직무",
                "문항",
                "근거",
                "연결",
                "계획",
                "실행",
                "갈등",
                "조율",
                "충돌",
                "사실",
                "모순"
        ).stream().anyMatch(reason::contains);
        return referencesSentence && namesMissingElement;
    }

    private boolean isActionableRecheckImprovement(
            AnalysisCandidateResponse.AnalysisCandidate candidate,
            String improvement
    ) {
        if (!StringUtils.hasText(improvement)) {
            return false;
        }
        String normalizedImprovement = normalize(improvement);
        if (normalizedImprovement.equals(normalize(candidate.sentence()))) {
            return false;
        }
        List<String> nonActionablePatterns = List.of(
                "더구체적으로작성하겠습니다",
                "직무역량을강화하겠습니다",
                "성과를명확히보여주었습니다",
                "개선할수있습니다",
                "추가하면좋겠습니다",
                "설명할수있습니다",
                "구체적인설명을추가",
                "방법론에대한구체적인설명"
        );
        if (nonActionablePatterns.stream().anyMatch(normalizedImprovement::contains)) {
            return false;
        }
        Set<String> sentenceNumbers = numberTokens(candidate.sentence());
        Set<String> improvementNumbers = numberTokens(improvement);
        if (!sentenceNumbers.containsAll(improvementNumbers)) {
            return false;
        }
        boolean keepsOriginalFact = meaningfulTokens(candidate.sentence()).stream()
                .anyMatch(token -> normalizedImprovement.contains(normalize(token)));
        boolean hasActionDetail = List.of(
                "분석",
                "검토",
                "조율",
                "관리",
                "설계",
                "수행",
                "개선",
                "확인",
                "점검",
                "기록",
                "비교",
                "정리",
                "전달"
        ).stream().anyMatch(improvement::contains);
        return keepsOriginalFact && hasActionDetail;
    }

    private Set<String> numberTokens(String value) {
        if (!StringUtils.hasText(value)) {
            return Set.of();
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\d+(?:\\.\\d+)?%?").matcher(value);
        Set<String> numbers = new HashSet<>();
        while (matcher.find()) {
            numbers.add(matcher.group());
        }
        return numbers;
    }

    private boolean sentenceTypeCriterionMismatch(
            AnalysisCandidateResponse.AnalysisCandidate candidate,
            String reason
    ) {
        String sentenceType = defaultString(candidate.sentenceType()).trim().toUpperCase();
        if (!StringUtils.hasText(reason)) {
            return false;
        }
        if ("PLAN".equals(sentenceType) || "MOTIVATION".equals(sentenceType)) {
            return reason.contains("성과 수치")
                    || reason.contains("정량")
                    || reason.contains("과거 성과")
                    || reason.contains("Before-After")
                    || reason.contains("STAR");
        }
        return false;
    }

    private List<String> meaningfulTokens(String value) {
        if (!StringUtils.hasText(value)) {
            return List.of();
        }
        return java.util.Arrays.stream(value.split("[^가-힣A-Za-z0-9]+"))
                .map(String::trim)
                .map(this::stripCommonKoreanSuffix)
                .filter(token -> token.length() >= 2)
                .filter(token -> !Set.of("있습니다", "했습니다", "합니다", "대한", "통해").contains(token))
                .limit(8)
                .toList();
    }

    private String stripCommonKoreanSuffix(String token) {
        if (!StringUtils.hasText(token)) {
            return "";
        }
        return token.replaceAll("(은|는|이|가|을|를|과|와|로|으로|에서|에게|부터|까지)$", "");
    }

    private List<AnalysisLlmResponse.QuestionAnalysisItem> buildAcceptedQuestionAnalyses(
            AnalysisPromptInput promptInput,
            AnalysisCandidateResponse sanitizedCandidates,
            CandidateReviewResponse reviewResponse
    ) {
        if (reviewResponse == null || sanitizedCandidates == null) {
            return List.of();
        }
        Map<String, AnalysisCandidateResponse.AnalysisCandidate> candidateById = sanitizedCandidates.analysisCandidates().stream()
                .filter(candidate -> StringUtils.hasText(candidate.candidateId()))
                .collect(Collectors.toMap(
                        candidate -> candidate.candidateId().trim(),
                        candidate -> candidate,
                        (left, right) -> left
                ));
        Map<Long, String> answerByQuestionId = promptInput.questions().stream()
                .collect(Collectors.toMap(AnalysisPromptInput.QuestionAnswer::questionId, AnalysisPromptInput.QuestionAnswer::answer));

        List<AnalysisLlmResponse.QuestionAnalysisItem> result = new ArrayList<>();
        Map<Long, Integer> countByQuestionId = new HashMap<>();
        Set<String> seenSentences = new HashSet<>();

        Map<String, String> reviewedStrengthReasonByQuote = reviewResponse.strengths() == null
                ? Map.of()
                : reviewResponse.strengths().stream()
                        .filter(strength -> strength != null && StringUtils.hasText(strength.quote()))
                        .collect(Collectors.toMap(
                                strength -> normalize(strength.quote()),
                                strength -> defaultString(strength.title()).trim(),
                                (left, right) -> left
                        ));
        for (AnalysisCandidateResponse.StrengthCandidate strength : sanitizedCandidates.strengthCandidates()) {
            if (strength == null || strength.questionId() == null || !StringUtils.hasText(strength.quote())) {
                continue;
            }
            String normalizedQuote = normalize(strength.quote());
            if (!reviewedStrengthReasonByQuote.containsKey(normalizedQuote)) {
                continue;
            }
            String answer = answerByQuestionId.get(strength.questionId());
            if (!containsExact(answer, strength.quote()) || isBracketedSubheading(answer, strength.quote())) {
                continue;
            }
            int currentCount = countByQuestionId.getOrDefault(strength.questionId(), 0);
            if (currentCount >= MAX_CANDIDATES_PER_QUESTION) {
                continue;
            }
            String reason = AnalysisSanitizationRules.hasValidProvenReason(strength.reasonBasis())
                    ? strength.reasonBasis().trim()
                    : reviewedStrengthReasonByQuote.get(normalizedQuote);
            if (!AnalysisSanitizationRules.hasValidProvenReason(reason)) {
                continue;
            }
            String dedupeKey = strength.questionId() + ":" + normalizedQuote;
            if (!seenSentences.add(dedupeKey)) {
                continue;
            }
            result.add(new AnalysisLlmResponse.QuestionAnalysisItem(
                    strength.questionId(),
                    strength.quote(),
                    QuestionAnalysisStatus.PROVEN.name().toLowerCase(),
                    reason,
                    null
            ));
            countByQuestionId.put(strength.questionId(), currentCount + 1);
        }

        Set<String> seenCandidateIds = new HashSet<>();
        List<CandidateReviewResponse.CandidateDecision> decisions = reviewResponse.decisions() == null
                ? List.of()
                : reviewResponse.decisions();
        for (CandidateReviewResponse.CandidateDecision decision : decisions) {
            if (decision == null || !StringUtils.hasText(decision.candidateId())) {
                continue;
            }
            String candidateId = decision.candidateId().trim();
            if (!seenCandidateIds.add(candidateId)) {
                continue;
            }
            AnalysisCandidateResponse.AnalysisCandidate candidate = candidateById.get(candidateId);
            if (candidate == null || !Boolean.TRUE.equals(decision.accepted())) {
                continue;
            }
            if (decision.rejectionCode() != RejectionCode.NONE) {
                continue;
            }
            QuestionAnalysisStatus status = parseQuestionAnalysisStatus(decision.status());
            if (status != QuestionAnalysisStatus.MENTIONED && status != QuestionAnalysisStatus.FABRICATED) {
                continue;
            }
            if (!StringUtils.hasText(decision.reason())) {
                continue;
            }
            if (status == QuestionAnalysisStatus.MENTIONED
                    && AnalysisSanitizationRules.isPositiveMentionedReason(decision.reason())) {
                continue;
            }
            if (status == QuestionAnalysisStatus.FABRICATED
                    && !AnalysisSanitizationRules.hasFabricatedDirectConflictEvidence(
                            candidate.sentence(),
                            decision.reason()
                    )) {
                continue;
            }
            String answer = answerByQuestionId.get(candidate.questionId());
            if (!containsExact(answer, candidate.sentence())
                    || isBracketedSubheading(answer, candidate.sentence())) {
                continue;
            }
            int currentCount = countByQuestionId.getOrDefault(candidate.questionId(), 0);
            if (currentCount >= MAX_CANDIDATES_PER_QUESTION) {
                continue;
            }
            String dedupeKey = candidate.questionId() + ":" + normalize(candidate.sentence());
            if (!seenSentences.add(dedupeKey)) {
                continue;
            }
            String improvement = AnalysisSanitizationRules.normalizeImprovement(
                    candidate.sentence(),
                    answer,
                    decision.improvement(),
                    false
            );
            result.add(new AnalysisLlmResponse.QuestionAnalysisItem(
                    candidate.questionId(),
                    candidate.sentence(),
                    status.name().toLowerCase(),
                    decision.reason().trim(),
                    StringUtils.hasText(improvement) ? improvement : null
            ));
            countByQuestionId.put(candidate.questionId(), currentCount + 1);
        }
        return result;
    }

    CandidateReviewResponse validateCandidateReview(
            AnalysisPromptInput promptInput,
            AnalysisCandidateResponse sanitizedCandidates,
            CandidateReviewResponse reviewResponse
    ) {
        if (reviewResponse == null) {
            return new CandidateReviewResponse(List.of(), List.of(), List.of(), null, null, null, null);
        }
        List<AnalysisCandidateResponse.AnalysisCandidate> analysisCandidates =
                sanitizedCandidates == null || sanitizedCandidates.analysisCandidates() == null
                        ? List.of()
                        : sanitizedCandidates.analysisCandidates();
        List<CandidateReviewResponse.FinalStrengthCandidate> strengths = validateReviewStrengths(
                promptInput,
                sanitizedCandidates,
                reviewResponse.strengths()
        );
        List<CandidateReviewResponse.FinalMissingKeywordCandidate> missingKeywords = validateReviewMissingKeywords(
                promptInput,
                sanitizedCandidates,
                reviewResponse.missingKeywords()
        );
        if (analysisCandidates.isEmpty()) {
            logDroppedDecisionsWithoutInput(promptInput, reviewResponse.decisions());
            return new CandidateReviewResponse(
                    List.of(),
                    strengths,
                    missingKeywords,
                    reviewResponse.jobFit(),
                    reviewResponse.impact(),
                    reviewResponse.completeness(),
                    reviewResponse.feedback()
            );
        }

        Map<String, AnalysisCandidateResponse.AnalysisCandidate> candidateById = analysisCandidates.stream()
                .filter(candidate -> StringUtils.hasText(candidate.candidateId()))
                .collect(Collectors.toMap(
                        candidate -> candidate.candidateId().trim(),
                        candidate -> candidate,
                        (left, right) -> left
                ));
        Map<Long, String> answerByQuestionId = promptInput.questions().stream()
                .collect(Collectors.toMap(AnalysisPromptInput.QuestionAnswer::questionId, AnalysisPromptInput.QuestionAnswer::answer));

        List<CandidateReviewResponse.CandidateDecision> decisions = new ArrayList<>();
        Set<String> seenCandidateIds = new HashSet<>();
        if (reviewResponse.decisions() != null) {
            for (CandidateReviewResponse.CandidateDecision decision : reviewResponse.decisions()) {
                if (decision == null || !StringUtils.hasText(decision.candidateId())) {
                    continue;
                }
                String candidateId = decision.candidateId().trim();
                if (!seenCandidateIds.add(candidateId)) {
                    logReviewDrop(promptInput, "review_unknown_candidate_id", candidateId, null, null);
                    continue;
                }
                AnalysisCandidateResponse.AnalysisCandidate candidate = candidateById.get(candidateId);
                if (candidate == null || decision.accepted() == null || decision.rejectionCode() == null) {
                    logReviewDrop(promptInput, "review_unknown_candidate_id", candidateId, null, null);
                    continue;
                }
                if (Boolean.TRUE.equals(decision.accepted())) {
                    CandidateReviewResponse.CandidateDecision accepted = validateAcceptedDecision(
                            decision,
                            candidate,
                            answerByQuestionId.get(candidate.questionId())
                    );
                    if (accepted != null) {
                        decisions.add(accepted);
                    }
                    continue;
                }
                CandidateReviewResponse.CandidateDecision rejected = validateRejectedDecision(decision);
                if (rejected != null) {
                    decisions.add(rejected);
                }
            }
        }
        return new CandidateReviewResponse(
                decisions,
                strengths,
                missingKeywords,
                reviewResponse.jobFit(),
                reviewResponse.impact(),
                reviewResponse.completeness(),
                reviewResponse.feedback()
        );
    }

    private void logDroppedDecisionsWithoutInput(
            AnalysisPromptInput promptInput,
            List<CandidateReviewResponse.CandidateDecision> decisions
    ) {
        if (decisions == null) {
            return;
        }
        for (CandidateReviewResponse.CandidateDecision decision : decisions) {
            if (decision != null) {
                logReviewDrop(
                        promptInput,
                        "review_output_without_input_candidate",
                        decision.candidateId(),
                        null,
                        null
                );
            }
        }
    }

    private List<CandidateReviewResponse.FinalStrengthCandidate> validateReviewStrengths(
            AnalysisPromptInput promptInput,
            AnalysisCandidateResponse sanitizedCandidates,
            List<CandidateReviewResponse.FinalStrengthCandidate> reviewStrengths
    ) {
        if (reviewStrengths == null || sanitizedCandidates == null || sanitizedCandidates.strengthCandidates() == null) {
            if (reviewStrengths != null) {
                logReviewStrengthsWithoutInput(promptInput, reviewStrengths);
            }
            return List.of();
        }
        Set<String> allowedQuotes = sanitizedCandidates.strengthCandidates().stream()
                .filter(candidate -> isPrimarySource(candidate.relatedSource()))
                .map(candidate -> normalize(candidate.quote()))
                .collect(Collectors.toSet());
        if (allowedQuotes.isEmpty()) {
            logReviewStrengthsWithoutInput(promptInput, reviewStrengths);
            return List.of();
        }

        List<CandidateReviewResponse.FinalStrengthCandidate> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (CandidateReviewResponse.FinalStrengthCandidate strength : reviewStrengths) {
            if (strength == null || !StringUtils.hasText(strength.quote())) {
                logReviewDrop(promptInput, "review_strength_without_candidate", null, null, null);
                continue;
            }
            String normalizedQuote = normalize(strength.quote());
            if (!allowedQuotes.contains(normalizedQuote) || !seen.add(normalizedQuote)) {
                logReviewDrop(promptInput, "review_strength_without_candidate", null, null, null);
                continue;
            }
            result.add(strength);
        }
        return List.copyOf(result);
    }

    private void logReviewStrengthsWithoutInput(
            AnalysisPromptInput promptInput,
            List<CandidateReviewResponse.FinalStrengthCandidate> strengths
    ) {
        for (CandidateReviewResponse.FinalStrengthCandidate strength : strengths) {
            logReviewDrop(promptInput, "review_strength_without_candidate", null, null, null);
        }
    }

    private List<CandidateReviewResponse.FinalMissingKeywordCandidate> validateReviewMissingKeywords(
            AnalysisPromptInput promptInput,
            AnalysisCandidateResponse sanitizedCandidates,
            List<CandidateReviewResponse.FinalMissingKeywordCandidate> reviewMissingKeywords
    ) {
        if (reviewMissingKeywords == null || sanitizedCandidates == null || sanitizedCandidates.missingKeywordCandidates() == null) {
            if (reviewMissingKeywords != null) {
                logReviewMissingKeywordsWithoutInput(promptInput, reviewMissingKeywords);
            }
            return List.of();
        }
        Set<String> allowedKeywords = sanitizedCandidates.missingKeywordCandidates().stream()
                .filter(candidate -> StringUtils.hasText(candidate.keyword()))
                .map(candidate -> missingKeywordProvenanceKey(candidate.keyword(), candidate.source()))
                .collect(Collectors.toSet());
        if (allowedKeywords.isEmpty()) {
            logReviewMissingKeywordsWithoutInput(promptInput, reviewMissingKeywords);
            return List.of();
        }

        List<CandidateReviewResponse.FinalMissingKeywordCandidate> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (CandidateReviewResponse.FinalMissingKeywordCandidate keyword : reviewMissingKeywords) {
            if (keyword == null || !StringUtils.hasText(keyword.keyword())) {
                logReviewDrop(promptInput, "review_missing_keyword_without_candidate", null, null, null);
                continue;
            }
            String key = missingKeywordProvenanceKey(keyword.keyword(), keyword.source());
            if (!allowedKeywords.contains(key) || !seen.add(key)) {
                logReviewDrop(
                        promptInput,
                        "review_missing_keyword_without_candidate",
                        null,
                        keyword.keyword(),
                        keyword.source()
                );
                continue;
            }
            result.add(keyword);
        }
        return List.copyOf(result);
    }

    private void logReviewMissingKeywordsWithoutInput(
            AnalysisPromptInput promptInput,
            List<CandidateReviewResponse.FinalMissingKeywordCandidate> keywords
    ) {
        for (CandidateReviewResponse.FinalMissingKeywordCandidate keyword : keywords) {
            logReviewDrop(
                    promptInput,
                    "review_missing_keyword_without_candidate",
                    null,
                    keyword == null ? null : keyword.keyword(),
                    keyword == null ? null : keyword.source()
            );
        }
    }

    private String missingKeywordProvenanceKey(String keyword, String source) {
        String normalizedKeyword = AnalysisSanitizationRules.normalizeText(keyword);
        String normalizedSource = parseCandidateSource(source)
                .map(Enum::name)
                .orElseGet(() -> AnalysisSanitizationRules.normalizeText(source));
        return normalizedKeyword + ":" + normalizedSource;
    }

    private void logReviewDrop(
            AnalysisPromptInput promptInput,
            String reason,
            String candidateId,
            String keyword,
            String source
    ) {
        log.warn(
                "Candidate review output removed. reason={}, companyName={}, jobName={}, candidateId={}, keyword={}, source={}",
                reason,
                promptInput == null ? null : promptInput.companyName(),
                promptInput == null ? null : promptInput.jobName(),
                candidateId,
                keyword,
                source
        );
    }

    private CandidateReviewResponse.CandidateDecision validateAcceptedDecision(
            CandidateReviewResponse.CandidateDecision decision,
            AnalysisCandidateResponse.AnalysisCandidate candidate,
            String answer
    ) {
        if (decision.rejectionCode() != RejectionCode.NONE) {
            return null;
        }
        QuestionAnalysisStatus status = parseQuestionAnalysisStatus(decision.status());
        if (status != QuestionAnalysisStatus.MENTIONED && status != QuestionAnalysisStatus.FABRICATED) {
            return null;
        }
        if (!StringUtils.hasText(decision.reason())
                || !containsExact(answer, candidate.sentence())
                || isBracketedSubheading(answer, candidate.sentence())) {
            return null;
        }
        if (status == QuestionAnalysisStatus.MENTIONED
                && AnalysisSanitizationRules.isPositiveMentionedReason(decision.reason())) {
            return null;
        }
        if (status == QuestionAnalysisStatus.FABRICATED
                && !AnalysisSanitizationRules.hasFabricatedDirectConflictEvidence(
                        candidate.sentence(),
                        decision.reason()
                )) {
            return null;
        }
        String improvement = AnalysisSanitizationRules.normalizeImprovement(
                candidate.sentence(),
                answer,
                decision.improvement(),
                false
        );
        return new CandidateReviewResponse.CandidateDecision(
                decision.candidateId().trim(),
                true,
                RejectionCode.NONE,
                status.name(),
                decision.reason().trim(),
                StringUtils.hasText(improvement) ? improvement : null
        );
    }

    private CandidateReviewResponse.CandidateDecision validateRejectedDecision(
            CandidateReviewResponse.CandidateDecision decision
    ) {
        if (decision.rejectionCode() == RejectionCode.NONE) {
            return null;
        }
        return new CandidateReviewResponse.CandidateDecision(
                decision.candidateId().trim(),
                false,
                decision.rejectionCode(),
                null,
                defaultString(decision.reason()).trim(),
                null
        );
    }

    private List<AnalysisLlmResponse.HighlightItem> buildFinalStrengths(
            AnalysisCandidateResponse sanitizedCandidates,
            CandidateReviewResponse reviewResponse,
            List<AnalysisLlmResponse.QuestionAnalysisItem> questionAnalyses
    ) {
        if (reviewResponse == null || reviewResponse.strengths() == null || sanitizedCandidates == null) {
            return List.of();
        }
        Set<String> allowedQuotes = sanitizedCandidates.strengthCandidates().stream()
                .filter(candidate -> isPrimarySource(candidate.relatedSource()))
                .map(candidate -> normalize(candidate.quote()))
                .collect(Collectors.toSet());
        Set<String> nonProvenAnalysisSentences = questionAnalyses.stream()
                .filter(item -> !QuestionAnalysisStatus.PROVEN.name().equalsIgnoreCase(defaultString(item.status())))
                .map(item -> normalize(item.sentence()))
                .collect(Collectors.toSet());
        List<AnalysisLlmResponse.HighlightItem> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (CandidateReviewResponse.FinalStrengthCandidate strength : reviewResponse.strengths()) {
            if (strength == null || !StringUtils.hasText(strength.title()) || !StringUtils.hasText(strength.quote())) {
                continue;
            }
            String normalizedQuote = normalize(strength.quote());
            if (!allowedQuotes.contains(normalizedQuote)
                    || nonProvenAnalysisSentences.contains(normalizedQuote)
                    || !seen.add(normalizedQuote)) {
                continue;
            }
            result.add(new AnalysisLlmResponse.HighlightItem(strength.title().trim(), strength.quote().trim()));
            if (result.size() >= 3) {
                break;
            }
        }
        return result;
    }

    private List<AnalysisLlmResponse.MissingKeywordItem> buildFinalMissingKeywords(
            AnalysisPromptInput promptInput,
            AnalysisCandidateResponse sanitizedCandidates
    ) {
        if (sanitizedCandidates == null || sanitizedCandidates.missingKeywordCandidates() == null) {
            return List.of();
        }
        List<AnalysisLlmResponse.MissingKeywordItem> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (AnalysisCandidateResponse.MissingKeywordCandidate keyword : sanitizedCandidates.missingKeywordCandidates()) {
            if (keyword == null || !StringUtils.hasText(keyword.keyword())) {
                continue;
            }
            Optional<MissingKeywordSource> source = parseCandidateSource(keyword.source());
            if (source.isEmpty()) {
                continue;
            }
            if (!AnalysisSanitizationRules.isValidMissingKeyword(
                    keyword.keyword(),
                    source.get(),
                    promptInput.mainTasks(),
                    promptInput.qualifications()
            )) {
                continue;
            }
            String dedupeKey = normalize(keyword.keyword());
            if (!seen.add(dedupeKey)) {
                continue;
            }
            result.add(new AnalysisLlmResponse.MissingKeywordItem(keyword.keyword().trim(), source.get().value()));
            if (result.size() >= 3) {
                break;
            }
        }
        return result;
    }

    AnalysisLlmResponse mergeHybridExact(
            AnalysisLlmResponse singlePassResponse,
            AnalysisLlmResponse twoPassResponse
    ) {
        if (singlePassResponse == null) {
            return null;
        }
        return new AnalysisLlmResponse(
                singlePassResponse.jobFit(),
                singlePassResponse.impact(),
                singlePassResponse.completeness(),
                singlePassResponse.feedback(),
                singlePassResponse.keyStrengths() == null ? List.of() : List.copyOf(singlePassResponse.keyStrengths()),
                singlePassResponse.keyWeaknesses() == null ? List.of() : List.copyOf(singlePassResponse.keyWeaknesses()),
                twoPassResponse == null || twoPassResponse.missingKeywords() == null
                        ? List.of()
                        : List.copyOf(twoPassResponse.missingKeywords()),
                singlePassResponse.questionAnalyses() == null
                        ? List.of()
                        : List.copyOf(singlePassResponse.questionAnalyses())
        );
    }

    private int acceptedDecisionCount(CandidateReviewResponse reviewResponse) {
        if (reviewResponse == null || reviewResponse.decisions() == null) {
            return 0;
        }
        return (int) reviewResponse.decisions().stream()
                .filter(decision -> decision != null && Boolean.TRUE.equals(decision.accepted()))
                .count();
    }

    private int rejectedDecisionCount(CandidateReviewResponse reviewResponse) {
        if (reviewResponse == null || reviewResponse.decisions() == null) {
            return 0;
        }
        return (int) reviewResponse.decisions().stream()
                .filter(decision -> decision != null && !Boolean.TRUE.equals(decision.accepted()))
                .count();
    }

    private int recoveredDecisionCount(CandidateReviewResponse reviewResponse, QuestionAnalysisStatus status) {
        if (reviewResponse == null || reviewResponse.decisions() == null || status == null) {
            return 0;
        }
        return (int) reviewResponse.decisions().stream()
                .filter(decision -> decision != null && Boolean.TRUE.equals(decision.accepted()))
                .filter(decision -> parseQuestionAnalysisStatus(decision.status()) == status)
                .count();
    }

    private Map<String, Long> rejectionCodeCounts(CandidateReviewResponse reviewResponse) {
        if (reviewResponse == null || reviewResponse.decisions() == null) {
            return Map.of();
        }
        return reviewResponse.decisions().stream()
                .filter(decision -> decision != null && decision.rejectionCode() != null && decision.rejectionCode() != RejectionCode.NONE)
                .collect(Collectors.groupingBy(
                        decision -> decision.rejectionCode().name(),
                        java.util.TreeMap::new,
                        Collectors.counting()
                ));
    }

    private void logQuestionFlowStats(
            AnalysisCandidateResponse sanitizedCandidates,
            CandidateReviewResponse reviewResponse,
            AnalysisLlmResponse response
    ) {
        if (!log.isDebugEnabled() || sanitizedCandidates == null || sanitizedCandidates.analysisCandidates() == null) {
            return;
        }
        Map<String, AnalysisCandidateResponse.AnalysisCandidate> candidateById = sanitizedCandidates.analysisCandidates().stream()
                .filter(candidate -> StringUtils.hasText(candidate.candidateId()))
                .collect(Collectors.toMap(
                        candidate -> candidate.candidateId().trim(),
                        candidate -> candidate,
                        (left, right) -> left
                ));
        Set<Long> questionIds = sanitizedCandidates.analysisCandidates().stream()
                .map(AnalysisCandidateResponse.AnalysisCandidate::questionId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(java.util.TreeSet::new));
        if (response != null && response.questionAnalyses() != null) {
            response.questionAnalyses().stream()
                    .map(AnalysisLlmResponse.QuestionAnalysisItem::questionId)
                    .filter(Objects::nonNull)
                    .forEach(questionIds::add);
        }
        for (Long questionId : questionIds) {
            long firstPassCandidates = sanitizedCandidates.analysisCandidates().stream()
                    .filter(candidate -> Objects.equals(questionId, candidate.questionId()))
                    .count();
            long secondPassAccepted = reviewResponse == null || reviewResponse.decisions() == null ? 0 : reviewResponse.decisions().stream()
                    .filter(decision -> decision != null && Boolean.TRUE.equals(decision.accepted()))
                    .map(decision -> candidateById.get(defaultString(decision.candidateId()).trim()))
                    .filter(candidate -> candidate != null && Objects.equals(questionId, candidate.questionId()))
                    .count();
            long secondPassRejected = reviewResponse == null || reviewResponse.decisions() == null ? 0 : reviewResponse.decisions().stream()
                    .filter(decision -> decision != null && !Boolean.TRUE.equals(decision.accepted()))
                    .map(decision -> candidateById.get(defaultString(decision.candidateId()).trim()))
                    .filter(candidate -> candidate != null && Objects.equals(questionId, candidate.questionId()))
                    .count();
            long finalAnalyses = response == null || response.questionAnalyses() == null ? 0 : response.questionAnalyses().stream()
                    .filter(item -> Objects.equals(questionId, item.questionId()))
                    .count();
            log.debug(
                    "analysis two-pass question flow. questionId={}, firstPassCandidates={}, secondPassAccepted={}, secondPassRejected={}, finalAnalyses={}",
                    questionId,
                    firstPassCandidates,
                    secondPassAccepted,
                    secondPassRejected,
                    finalAnalyses
            );
        }
    }

    private String contextBefore(String answer, String sentence) {
        int start = StringUtils.hasText(answer) && StringUtils.hasText(sentence) ? answer.indexOf(sentence) : -1;
        if (start <= 0) {
            return "";
        }
        int previousEnd = Math.max(answer.lastIndexOf('.', start - 1), answer.lastIndexOf('。', start - 1));
        int from = previousEnd < 0 ? 0 : previousEnd + 1;
        return answer.substring(from, start).trim();
    }

    private String contextAfter(String answer, String sentence) {
        int start = StringUtils.hasText(answer) && StringUtils.hasText(sentence) ? answer.indexOf(sentence) : -1;
        if (start < 0) {
            return "";
        }
        int from = start + sentence.length();
        if (from >= answer.length()) {
            return "";
        }
        int nextEnd = answer.indexOf('.', from);
        if (nextEnd < 0) {
            nextEnd = answer.length();
        }
        return answer.substring(from, Math.min(answer.length(), nextEnd + 1)).trim();
    }

    private boolean isPrimarySource(String relatedSource) {
        return "MAIN_TASK".equalsIgnoreCase(defaultString(relatedSource))
                || "QUALIFICATION".equalsIgnoreCase(defaultString(relatedSource));
    }

    private boolean containsExact(String source, String part) {
        return StringUtils.hasText(source)
                && StringUtils.hasText(part)
                && source.contains(part);
    }

    AnalysisLlmResponse sanitizeSinglePassSubheadings(
            AnalysisPromptInput promptInput,
            AnalysisLlmResponse response
    ) {
        if (response == null || promptInput == null || promptInput.questions() == null) {
            return response;
        }
        Map<Long, String> answerByQuestionId = promptInput.questions().stream()
                .collect(Collectors.toMap(
                        AnalysisPromptInput.QuestionAnswer::questionId,
                        AnalysisPromptInput.QuestionAnswer::answer
                ));
        List<String> answers = new ArrayList<>(answerByQuestionId.values());
        List<AnalysisLlmResponse.HighlightItem> keyStrengths = response.keyStrengths() == null
                ? null
                : response.keyStrengths().stream()
                .filter(item -> item != null && !isBracketedSubheadingInAnyAnswer(answers, item.quote()))
                .toList();
        List<AnalysisLlmResponse.HighlightItem> keyWeaknesses = response.keyWeaknesses() == null
                ? null
                : response.keyWeaknesses().stream()
                .filter(item -> item != null && !isBracketedSubheadingInAnyAnswer(answers, item.quote()))
                .toList();
        List<AnalysisLlmResponse.QuestionAnalysisItem> questionAnalyses = response.questionAnalyses() == null
                ? null
                : response.questionAnalyses().stream()
                .filter(item -> item != null
                        && !isBracketedSubheading(answerByQuestionId.get(item.questionId()), item.sentence()))
                .toList();
        return new AnalysisLlmResponse(
                response.jobFit(),
                response.impact(),
                response.completeness(),
                response.feedback(),
                keyStrengths,
                keyWeaknesses,
                response.missingKeywords(),
                questionAnalyses
        );
    }

    private boolean isBracketedSubheadingInAnyAnswer(List<String> answers, String candidateText) {
        return answers.stream().anyMatch(answer -> isBracketedSubheading(answer, candidateText));
    }

    private boolean isBracketedSubheading(String answer, String candidateText) {
        if (!StringUtils.hasText(answer) || !StringUtils.hasText(candidateText)) {
            return false;
        }
        String trimmedCandidate = candidateText.trim();
        if (trimmedCandidate.indexOf('\n') >= 0
                || trimmedCandidate.indexOf('\r') >= 0
                || !trimmedCandidate.startsWith("[")
                || !trimmedCandidate.endsWith("]")
                || trimmedCandidate.length() <= 2) {
            return false;
        }
        return answer.lines().map(String::trim).anyMatch(trimmedCandidate::equals);
    }

    private QuestionAnalysisStatus parseQuestionAnalysisStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return null;
        }
        try {
            return QuestionAnalysisStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    private int size(List<?> values) {
        return values == null ? 0 : values.size();
    }

    private String normalize(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "").toLowerCase();
    }

    private String defaultString(String value) {
        return value == null ? "" : value;
    }

    private RetrievalContext emptyContext() {
        return new RetrievalContext(List.of(), List.of());
    }

    public AnalysisMode resolveAnalysisMode() {
        if (StringUtils.hasText(analysisMode)) {
            String normalized = analysisMode.trim().replace('-', '_').toUpperCase(java.util.Locale.ROOT);
            try {
                return AnalysisMode.valueOf(normalized);
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException("Unsupported analysis mode: " + analysisMode, e);
            }
        }
        return twoPassEnabled ? AnalysisMode.TWO_PASS : AnalysisMode.SINGLE_PASS;
    }

    public enum AnalysisMode {
        SINGLE_PASS,
        TWO_PASS,
        HYBRID_EXACT
    }

    private enum RecheckValidationFailureReason {
        UNKNOWN_CANDIDATE,
        SUBHEADING,
        LOW_PROBLEM_CLARITY,
        LOW_JOB_RELEVANCE,
        LOW_EVIDENCE_GAP,
        LOW_IMPROVEMENT_USEFULNESS,
        LOW_FABRICATION_CONFIDENCE,
        QUESTION_TYPE_MISMATCH,
        CONTEXT_MISMATCH,
        GENERIC_REASON,
        NON_ACTIONABLE_IMPROVEMENT,
        SENTENCE_NOT_FOUND,
        INVALID_FABRICATED
    }

    public record AnalysisAiCallResult(
            AnalysisLlmResponse response,
            AnalysisCandidateResponse rawCandidateResponse,
            AnalysisCandidateResponse sanitizedCandidateResponse,
            CandidateReviewResponse candidateReviewResponse,
            boolean twoPassEnabled,
            long candidateCallLatencyMs,
            long finalCallLatencyMs
    ) {
        static AnalysisAiCallResult singlePass(AnalysisLlmResponse response, long latencyMs) {
            return new AnalysisAiCallResult(response, null, null, null, false, 0, latencyMs);
        }

        static AnalysisAiCallResult twoPass(
                AnalysisLlmResponse response,
                AnalysisCandidateResponse rawCandidateResponse,
                AnalysisCandidateResponse sanitizedCandidateResponse,
                CandidateReviewResponse candidateReviewResponse,
                long candidateCallLatencyMs,
                long finalCallLatencyMs
        ) {
            return new AnalysisAiCallResult(
                    response,
                    rawCandidateResponse,
                    sanitizedCandidateResponse,
                    candidateReviewResponse,
                    true,
                    candidateCallLatencyMs,
                    finalCallLatencyMs
            );
        }

        static AnalysisAiCallResult hybridExact(
                AnalysisLlmResponse response,
                AnalysisCandidateResponse rawCandidateResponse,
                AnalysisCandidateResponse sanitizedCandidateResponse,
                CandidateReviewResponse candidateReviewResponse,
                long candidateCallLatencyMs,
                long finalCallLatencyMs
        ) {
            return new AnalysisAiCallResult(
                    response,
                    rawCandidateResponse,
                    sanitizedCandidateResponse,
                    candidateReviewResponse,
                    true,
                    candidateCallLatencyMs,
                    finalCallLatencyMs
            );
        }
    }
}
