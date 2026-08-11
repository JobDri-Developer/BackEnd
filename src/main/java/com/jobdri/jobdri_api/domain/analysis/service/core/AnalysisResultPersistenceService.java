package com.jobdri.jobdri_api.domain.analysis.service.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobdri.jobdri_api.domain.analysis.dto.llm.AnalysisLlmResponse;
import com.jobdri.jobdri_api.domain.analysis.dto.response.AnalysisHighlightResponse;
import com.jobdri.jobdri_api.domain.analysis.dto.response.AnalysisQuestionResponse;
import com.jobdri.jobdri_api.domain.analysis.dto.response.AnalysisResponse;
import com.jobdri.jobdri_api.domain.analysis.dto.response.MissingKeywordResponse;
import com.jobdri.jobdri_api.domain.analysis.dto.response.MissingKeywordSource;
import com.jobdri.jobdri_api.domain.analysis.dto.response.QuestionAnalysisResponse;
import com.jobdri.jobdri_api.domain.analysis.entity.Analysis;
import com.jobdri.jobdri_api.domain.analysis.entity.Question;
import com.jobdri.jobdri_api.domain.analysis.entity.QuestionAnalysis;
import com.jobdri.jobdri_api.domain.analysis.entity.QuestionAnalysisStatus;
import com.jobdri.jobdri_api.domain.analysis.repository.AnalysisRepository;
import com.jobdri.jobdri_api.domain.analysis.repository.QuestionAnalysisRepository;
import com.jobdri.jobdri_api.domain.analysis.repository.QuestionRepository;
import com.jobdri.jobdri_api.domain.analysis.service.sanitization.AnalysisSanitizationRules;
import com.jobdri.jobdri_api.domain.jobposting.entity.JobPosting;
import com.jobdri.jobdri_api.domain.mockapply.entity.MockApply;
import com.jobdri.jobdri_api.domain.mockapply.entity.MockApplyStatus;
import com.jobdri.jobdri_api.domain.mockapply.repository.MockApplyRepository;
import com.jobdri.jobdri_api.global.apiPayload.code.GeneralErrorCode;
import com.jobdri.jobdri_api.global.apiPayload.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.jobdri.jobdri_api.domain.analysis.service.core.AnalysisResultConstants.COMPLETENESS_WEIGHT;
import static com.jobdri.jobdri_api.domain.analysis.service.core.AnalysisResultConstants.IMPACT_WEIGHT;
import static com.jobdri.jobdri_api.domain.analysis.service.core.AnalysisResultConstants.JOB_FIT_WEIGHT;
import static com.jobdri.jobdri_api.domain.analysis.service.core.AnalysisResultConstants.MAX_ANALYSES_PER_QUESTION;
import static com.jobdri.jobdri_api.domain.analysis.service.core.AnalysisResultConstants.MAX_HIGHLIGHTS;
import static com.jobdri.jobdri_api.domain.analysis.service.core.AnalysisResultConstants.MAX_HIGHLIGHT_QUOTE_LENGTH;
import static com.jobdri.jobdri_api.domain.analysis.service.core.AnalysisResultConstants.MAX_HIGHLIGHT_TITLE_LENGTH;
import static com.jobdri.jobdri_api.domain.analysis.service.core.AnalysisResultConstants.MAX_MISSING_KEYWORDS;
import static com.jobdri.jobdri_api.domain.analysis.service.core.AnalysisResultConstants.MAX_MISSING_KEYWORD_LENGTH;
import static com.jobdri.jobdri_api.domain.analysis.service.core.AnalysisResultConstants.MAX_SCORE;
import static com.jobdri.jobdri_api.domain.analysis.service.core.AnalysisResultConstants.MIN_SCORE;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AnalysisResultPersistenceService {
    private static final TypeReference<List<MissingKeywordResponse>> MISSING_KEYWORDS_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<AnalysisHighlightResponse>> HIGHLIGHTS_TYPE = new TypeReference<>() {
    };

    private final MockApplyRepository mockApplyRepository;
    private final QuestionRepository questionRepository;
    private final AnalysisRepository analysisRepository;
    private final QuestionAnalysisRepository questionAnalysisRepository;
    private final AnalysisInputFingerprintProvider analysisInputFingerprintProvider;
    private final ObjectMapper objectMapper;

    @Transactional
    public AnalysisResponse finalizeAnalysis(
            MockApply mockApply,
            List<Question> questions,
            List<AnalysisExecutionPayload.AnswerSnapshot> payloadSnapshots,
            AnalysisLlmResponse llmResponse,
            String inputFingerprint
    ) {
        VerifiedAnswerSnapshot answerSnapshot = verifyAnswerSnapshot(questions, payloadSnapshots);
        validateRequiredScores(llmResponse);
        int jobFit = validateScore("jobFit", llmResponse.jobFit());
        int impact = validateScore("impact", llmResponse.impact());
        int completeness = validateScore("completeness", llmResponse.completeness());
        List<AnalysisHighlightResponse> keyStrengths = buildHighlights(llmResponse.keyStrengths());
        List<AnalysisHighlightResponse> keyWeaknesses = buildNonOverlappingHighlights(llmResponse.keyWeaknesses(), keyStrengths);
        List<MissingKeywordResponse> missingKeywords = buildMissingKeywords(
                mockApply.getJobPosting(),
                answerSnapshot.combinedAnswers(),
                llmResponse
        );
        replaceExistingAnalysis(mockApply);

        Analysis analysis = analysisRepository.save(Analysis.create(
                mockApply,
                calculateScore(jobFit, impact, completeness),
                jobFit,
                impact,
                completeness,
                normalizeFeedback(llmResponse.feedback()),
                serializeMissingKeywords(missingKeywords),
                serializeHighlights(keyStrengths, "keyStrengths"),
                serializeHighlights(keyWeaknesses, "keyWeaknesses"),
                inputFingerprint
        ));

        List<QuestionAnalysis> questionAnalyses = buildQuestionAnalyses(
                analysis,
                questions,
                answerSnapshot.answerByQuestionId(),
                llmResponse
        );
        questionAnalysisRepository.saveAll(questionAnalyses);
        mockApply.updateStatus(MockApplyStatus.COMPLETED);

        return toResponse(mockApply, analysis, questions, questionAnalyses, analysisResultPayload(analysis));
    }

    @Transactional
    public AnalysisResponse getPersistedAnalysis(MockApply mockApply) {
        Analysis analysis = analysisRepository.findByMockApplyId(mockApply.getId())
                .orElseThrow(() -> new GeneralException(
                        GeneralErrorCode.ANALYSIS_NOT_FOUND,
                        "해당 모의 서류 지원의 분석 결과를 찾을 수 없습니다. mockApplyId=" + mockApply.getId()
                ));
        List<Question> questions = questionRepository.findAllByMockApplyIdOrderByIdAsc(mockApply.getId());
        List<QuestionAnalysis> questionAnalyses =
                questionAnalysisRepository.findAllByAnalysisIdOrderByQuestionIdAscIdAsc(analysis.getId());

        return toResponse(
                mockApply,
                analysis,
                questions,
                questionAnalyses,
                sanitizeAndPersistAnalysisPayload(analysis)
        );
    }

    private void replaceExistingAnalysis(MockApply mockApply) {
        Optional<Analysis> existingAnalysis = analysisRepository.findByMockApplyId(mockApply.getId());
        if (existingAnalysis.isEmpty()) {
            return;
        }

        Analysis analysis = existingAnalysis.get();
        mockApply.clearAnalysis();
        questionAnalysisRepository.deleteAllByAnalysisId(analysis.getId());
        analysisRepository.delete(analysis);
        analysisRepository.flush();
    }

    private VerifiedAnswerSnapshot verifyAnswerSnapshot(
            List<Question> databaseQuestions,
            List<AnalysisExecutionPayload.AnswerSnapshot> payloadSnapshots
    ) {
        String databaseFingerprint = analysisInputFingerprintProvider
                .createAnswerFingerprintFromQuestions(databaseQuestions);
        String payloadFingerprint = analysisInputFingerprintProvider
                .createAnswerFingerprint(payloadSnapshots);
        if (!databaseFingerprint.equals(payloadFingerprint)) {
            throw new GeneralException(
                    GeneralErrorCode.INVALID_PARAMETER,
                    "분석 실행 이후 자소서 답변이 변경되어 결과를 저장할 수 없습니다."
            );
        }

        List<AnalysisExecutionPayload.AnswerSnapshot> immutableSnapshots = List.copyOf(payloadSnapshots);
        Map<Long, String> answerByQuestionId = new LinkedHashMap<>();
        for (AnalysisExecutionPayload.AnswerSnapshot snapshot : immutableSnapshots) {
            if (snapshot == null || snapshot.questionId() == null || !StringUtils.hasText(snapshot.answer())) {
                continue;
            }
            if (answerByQuestionId.putIfAbsent(snapshot.questionId(), snapshot.answer()) != null) {
                throw new GeneralException(
                        GeneralErrorCode.INVALID_PARAMETER,
                        "분석 답변 snapshot에 중복된 questionId가 있습니다. questionId=" + snapshot.questionId()
                );
            }
        }
        return new VerifiedAnswerSnapshot(immutableSnapshots, Map.copyOf(answerByQuestionId));
    }

    private record VerifiedAnswerSnapshot(
            List<AnalysisExecutionPayload.AnswerSnapshot> answers,
            Map<Long, String> answerByQuestionId
    ) {
        private String combinedAnswers() {
            return answers.stream()
                    .map(AnalysisExecutionPayload.AnswerSnapshot::answer)
                    .filter(StringUtils::hasText)
                    .collect(Collectors.joining("\n"));
        }
    }

    private List<QuestionAnalysis> buildQuestionAnalyses(
            Analysis analysis,
            List<Question> questions,
            Map<Long, String> answerByQuestionId,
            AnalysisLlmResponse llmResponse
    ) {
        Map<Long, Question> questionMap = questions.stream()
                .collect(Collectors.toMap(Question::getId, Function.identity()));
        List<QuestionAnalysis> result = new ArrayList<>();
        Map<Long, Integer> analysisCountByQuestionId = new HashMap<>();
        Map<Long, Integer> nextSearchIndexByQuestionId = new HashMap<>();
        Set<String> seenSentences = new HashSet<>();
        Set<Long> fabricatedQuestionIds = new HashSet<>();
        Set<String> keyStrengthQuotes = normalizedKeyStrengthQuotes(llmResponse);

        if (llmResponse.questionAnalyses() == null) {
            return result;
        }

        for (AnalysisLlmResponse.QuestionAnalysisItem item : llmResponse.questionAnalyses()) {
            if (item == null || item.questionId() == null || !StringUtils.hasText(item.sentence())) {
                continue;
            }

            Question question = questionMap.get(item.questionId());
            if (question == null) {
                continue;
            }

            String answer = answerByQuestionId.get(item.questionId());
            if (!StringUtils.hasText(answer)) {
                continue;
            }
            QuestionAnalysisStatus status = parseStatus(item.status());
            if (status == null || status == QuestionAnalysisStatus.MISSING) {
                continue;
            }
            if (status == QuestionAnalysisStatus.PROVEN
                    && !AnalysisSanitizationRules.hasValidProvenReason(item.reason())) {
                continue;
            }
            if (status == QuestionAnalysisStatus.FABRICATED
                    && !AnalysisSanitizationRules.hasFabricatedDirectConflictEvidence(
                    item.sentence(),
                    item.reason()
            )) {
                continue;
            }
            int currentCount = analysisCountByQuestionId.getOrDefault(question.getId(), 0);
            if (currentCount >= MAX_ANALYSES_PER_QUESTION) {
                continue;
            }
            String sentence = item.sentence();
            if (status != QuestionAnalysisStatus.PROVEN
                    && keyStrengthQuotes.contains(normalizeKeyword(sentence))) {
                continue;
            }
            String dedupeKey = question.getId() + ":" + sentence.trim();
            if (!seenSentences.add(dedupeKey)) {
                continue;
            }
            int start = findNextSentenceStart(
                    answer,
                    sentence,
                    nextSearchIndexByQuestionId.getOrDefault(question.getId(), 0)
            );
            if (start < 0) {
                continue;
            }
            if (status == QuestionAnalysisStatus.FABRICATED
                    && !fabricatedQuestionIds.add(question.getId())) {
                continue;
            }
            nextSearchIndexByQuestionId.put(question.getId(), start + sentence.length());
            analysisCountByQuestionId.put(question.getId(), currentCount + 1);

            result.add(QuestionAnalysis.create(
                    question,
                    analysis,
                    sentence,
                    defaultString(item.reason()),
                    normalizeImprovement(sentence, answer, item.improvement(), status),
                    status,
                    start,
                    start + sentence.length()
            ));
        }

        return result;
    }

    private Set<String> normalizedKeyStrengthQuotes(AnalysisLlmResponse llmResponse) {
        if (llmResponse == null || llmResponse.keyStrengths() == null) {
            return Set.of();
        }
        return llmResponse.keyStrengths().stream()
                .filter(item -> item != null && StringUtils.hasText(item.quote()))
                .map(item -> normalizeKeyword(item.quote()))
                .collect(Collectors.toSet());
    }

    private AnalysisResponse toResponse(
            MockApply mockApply,
            Analysis analysis,
            List<Question> questions,
            List<QuestionAnalysis> questionAnalyses,
            AnalysisResultPayload resultPayload
    ) {
        Map<Long, Question> questionById = questions.stream()
                .collect(Collectors.toMap(Question::getId, Function.identity()));
        Map<Long, List<QuestionAnalysisResponse>> analysesByQuestionId = questionAnalyses.stream()
                .filter(questionAnalysis -> isValidQuestionAnalysisForResponse(
                        questionAnalysis,
                        questionById.get(questionAnalysis.getQuestion().getId())
                ))
                .collect(Collectors.groupingBy(
                        questionAnalysis -> questionAnalysis.getQuestion().getId(),
                        Collectors.mapping(QuestionAnalysisResponse::from, Collectors.toList())
                ));

        List<AnalysisQuestionResponse> questionResponses = questions.stream()
                .sorted(Comparator.comparing(Question::getId))
                .map(question -> AnalysisQuestionResponse.of(
                        question,
                        analysesByQuestionId.getOrDefault(question.getId(), List.of())
                ))
                .toList();

        return AnalysisResponse.of(
                analysis,
                mockApply.getStatus(),
                mockApplyRepository.calculateSequence(mockApply),
                resultPayload.keyStrengths(),
                resultPayload.keyWeaknesses(),
                resultPayload.missingKeywords(),
                questionResponses
        );
    }

    private boolean isValidQuestionAnalysisForResponse(QuestionAnalysis questionAnalysis, Question question) {
        if (questionAnalysis == null || question == null) {
            return false;
        }
        if (questionAnalysis.getStatus() == QuestionAnalysisStatus.MISSING) {
            return false;
        }
        String answer = question.getAnswer();
        String sentence = questionAnalysis.getSentence();
        int start = questionAnalysis.getStart();
        int end = questionAnalysis.getEnd();
        if (!StringUtils.hasText(answer) || !StringUtils.hasText(sentence)) {
            return false;
        }
        if (start < 0 || end <= start || end > answer.length()) {
            return false;
        }
        return answer.substring(start, end).equals(sentence);
    }

    private AnalysisResultPayload analysisResultPayload(Analysis analysis) {
        List<AnalysisHighlightResponse> keyStrengths =
                readHighlights(analysis, analysis == null ? null : analysis.getKeyStrengthsJson(), "keyStrengths");
        return new AnalysisResultPayload(
                keyStrengths,
                removeOverlappingHighlights(
                        readHighlights(analysis, analysis == null ? null : analysis.getKeyWeaknessesJson(), "keyWeaknesses"),
                        keyStrengths
                ),
                readMissingKeywords(analysis)
        );
    }

    private AnalysisResultPayload sanitizeAndPersistAnalysisPayload(Analysis analysis) {
        AnalysisResultPayload payload = analysisResultPayload(analysis);
        if (analysis != null) {
            analysis.updateHighlightsJson(
                    serializeHighlights(payload.keyStrengths(), "keyStrengths"),
                    serializeHighlights(payload.keyWeaknesses(), "keyWeaknesses")
            );
        }
        return payload;
    }

    private List<AnalysisHighlightResponse> buildHighlights(List<AnalysisLlmResponse.HighlightItem> items) {
        return sanitizeHighlights(items, AnalysisLlmResponse.HighlightItem::title, AnalysisLlmResponse.HighlightItem::quote);
    }

    private List<AnalysisHighlightResponse> buildNonOverlappingHighlights(
            List<AnalysisLlmResponse.HighlightItem> items,
            List<AnalysisHighlightResponse> existingHighlights
    ) {
        return sanitizeHighlights(
                removeOverlappingRawHighlights(items, existingHighlights),
                AnalysisLlmResponse.HighlightItem::title,
                AnalysisLlmResponse.HighlightItem::quote
        );
    }

    private List<MissingKeywordResponse> buildMissingKeywords(
            JobPosting jobPosting,
            String combinedAnswers,
            AnalysisLlmResponse llmResponse
    ) {
        if (llmResponse == null || llmResponse.missingKeywords() == null) {
            return List.of();
        }

        List<MissingKeywordResponse> result = new ArrayList<>();
        Set<String> seenKeywords = new HashSet<>();

        for (AnalysisLlmResponse.MissingKeywordItem item : llmResponse.missingKeywords()) {
            if (item == null || !StringUtils.hasText(item.keyword())) {
                continue;
            }

            String keyword = item.keyword().trim();
            if (keyword.length() > MAX_MISSING_KEYWORD_LENGTH) {
                continue;
            }

            Optional<MissingKeywordSource> source = MissingKeywordSource.from(item.source());
            if (source.isEmpty()) {
                continue;
            }
            if (!AnalysisSanitizationRules.isValidMissingKeyword(
                    keyword,
                    source.get(),
                    jobPosting == null ? "" : jobPosting.getTask(),
                    jobPosting == null ? "" : jobPosting.getRequirement()
            )) {
                continue;
            }
            if (AnalysisSanitizationRules.isMissingKeywordMentionedInAnswers(keyword, combinedAnswers)) {
                continue;
            }

            String dedupeKey = normalizeKeyword(keyword);
            if (!seenKeywords.add(dedupeKey)) {
                continue;
            }

            result.add(new MissingKeywordResponse(keyword, source.get()));
            if (result.size() >= MAX_MISSING_KEYWORDS) {
                break;
            }
        }

        return result;
    }

    private String normalizeKeyword(String keyword) {
        return keyword == null ? "" : keyword.replaceAll("\\s+", "").toLowerCase();
    }

    private String serializeMissingKeywords(List<MissingKeywordResponse> missingKeywords) {
        try {
            return objectMapper.writeValueAsString(missingKeywords == null ? List.of() : missingKeywords);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize missingKeywords. Fallback to empty array.", e);
            return "[]";
        }
    }

    private String serializeHighlights(List<AnalysisHighlightResponse> highlights, String fieldName) {
        try {
            return objectMapper.writeValueAsString(highlights == null ? List.of() : highlights);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize {}. Fallback to empty array.", fieldName, e);
            return "[]";
        }
    }

    private List<AnalysisHighlightResponse> readHighlights(Analysis analysis, String json, String fieldName) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }

        try {
            List<AnalysisHighlightResponse> highlights = objectMapper.readValue(json, HIGHLIGHTS_TYPE);
            return sanitizeStoredHighlights(highlights);
        } catch (Exception e) {
            log.warn(
                    "Failed to deserialize {}. analysisId={}, fallback to empty array.",
                    fieldName,
                    analysis == null ? null : analysis.getId(),
                    e
            );
            return List.of();
        }
    }

    private List<MissingKeywordResponse> readMissingKeywords(Analysis analysis) {
        if (analysis == null || !StringUtils.hasText(analysis.getMissingKeywordsJson())) {
            return List.of();
        }

        try {
            List<MissingKeywordResponse> missingKeywords = objectMapper.readValue(
                    analysis.getMissingKeywordsJson(),
                    MISSING_KEYWORDS_TYPE
            );
            return sanitizeStoredMissingKeywords(missingKeywords);
        } catch (Exception e) {
            log.warn(
                    "Failed to deserialize missingKeywords. analysisId={}, fallback to empty array.",
                    analysis.getId(),
                    e
            );
            return List.of();
        }
    }

    private List<MissingKeywordResponse> sanitizeStoredMissingKeywords(List<MissingKeywordResponse> missingKeywords) {
        if (missingKeywords == null) {
            return List.of();
        }

        List<MissingKeywordResponse> result = new ArrayList<>();
        Set<String> seenKeywords = new HashSet<>();

        for (MissingKeywordResponse item : missingKeywords) {
            if (item == null || !StringUtils.hasText(item.keyword()) || item.source() == null) {
                continue;
            }

            String keyword = item.keyword().trim();
            if (keyword.length() > MAX_MISSING_KEYWORD_LENGTH) {
                continue;
            }

            String dedupeKey = normalizeKeyword(keyword);
            if (!seenKeywords.add(dedupeKey)) {
                continue;
            }

            result.add(new MissingKeywordResponse(keyword, item.source()));
            if (result.size() >= MAX_MISSING_KEYWORDS) {
                break;
            }
        }

        return result;
    }

    private List<AnalysisHighlightResponse> sanitizeStoredHighlights(List<AnalysisHighlightResponse> highlights) {
        return sanitizeHighlights(highlights, AnalysisHighlightResponse::title, AnalysisHighlightResponse::quote);
    }

    private <T> List<AnalysisHighlightResponse> sanitizeHighlights(
            List<T> items,
            Function<T, String> titleExtractor,
            Function<T, String> quoteExtractor
    ) {
        if (items == null) {
            return List.of();
        }

        List<AnalysisHighlightResponse> result = new ArrayList<>();
        Set<String> seenHighlights = new HashSet<>();

        for (T item : items) {
            if (item == null) {
                continue;
            }

            String rawTitle = titleExtractor.apply(item);
            String rawQuote = quoteExtractor.apply(item);
            if (!StringUtils.hasText(rawTitle) || !StringUtils.hasText(rawQuote)) {
                continue;
            }

            String title = rawTitle.trim();
            String quote = rawQuote.trim();
            if (title.length() > MAX_HIGHLIGHT_TITLE_LENGTH || quote.length() > MAX_HIGHLIGHT_QUOTE_LENGTH) {
                continue;
            }

            String dedupeKey = normalizeKeyword(title) + ":" + normalizeKeyword(quote);
            if (!seenHighlights.add(dedupeKey)) {
                continue;
            }

            result.add(new AnalysisHighlightResponse(title, quote));
            if (result.size() >= MAX_HIGHLIGHTS) {
                break;
            }
        }

        return result;
    }

    private List<AnalysisHighlightResponse> removeOverlappingHighlights(
            List<AnalysisHighlightResponse> highlights,
            List<AnalysisHighlightResponse> existingHighlights
    ) {
        if (highlights == null || highlights.isEmpty()) {
            return List.of();
        }
        Set<String> existingQuotes = normalizedHighlightQuotes(existingHighlights);
        if (existingQuotes.isEmpty()) {
            return highlights;
        }

        return highlights.stream()
                .filter(highlight -> highlight != null && !existingQuotes.contains(normalizeKeyword(highlight.quote())))
                .toList();
    }

    private List<AnalysisLlmResponse.HighlightItem> removeOverlappingRawHighlights(
            List<AnalysisLlmResponse.HighlightItem> highlights,
            List<AnalysisHighlightResponse> existingHighlights
    ) {
        if (highlights == null || highlights.isEmpty()) {
            return List.of();
        }
        Set<String> existingQuotes = normalizedHighlightQuotes(existingHighlights);
        if (existingQuotes.isEmpty()) {
            return highlights;
        }
        return highlights.stream()
                .filter(highlight -> highlight != null && !existingQuotes.contains(normalizeKeyword(highlight.quote())))
                .toList();
    }

    private Set<String> normalizedHighlightQuotes(List<AnalysisHighlightResponse> highlights) {
        if (highlights == null || highlights.isEmpty()) {
            return Set.of();
        }
        return highlights.stream()
                .filter(highlight -> highlight != null && StringUtils.hasText(highlight.quote()))
                .map(highlight -> normalizeKeyword(highlight.quote()))
                .collect(Collectors.toSet());
    }

    private record AnalysisResultPayload(
            List<AnalysisHighlightResponse> keyStrengths,
            List<AnalysisHighlightResponse> keyWeaknesses,
            List<MissingKeywordResponse> missingKeywords
    ) {
    }

    private void validateRequiredScores(AnalysisLlmResponse llmResponse) {
        if (llmResponse == null
                || llmResponse.jobFit() == null
                || llmResponse.impact() == null
                || llmResponse.completeness() == null) {
            throw new GeneralException(
                    GeneralErrorCode.SERVICE_UNAVAILABLE,
                    "자소서 분석 AI 응답에 필수 점수 필드가 누락되었습니다."
            );
        }
    }

    private int validateScore(String fieldName, Integer score) {
        if (score == null || score < MIN_SCORE || score > MAX_SCORE) {
            throw new GeneralException(
                    GeneralErrorCode.SERVICE_UNAVAILABLE,
                    "자소서 분석 AI 응답의 " + fieldName + " 점수 범위가 올바르지 않습니다."
            );
        }
        return score;
    }

    private int calculateScore(int jobFit, int impact, int completeness) {
        return (int) Math.round(
                jobFit * JOB_FIT_WEIGHT
                        + impact * IMPACT_WEIGHT
                        + completeness * COMPLETENESS_WEIGHT
        );
    }

    private int findNextSentenceStart(String answer, String sentence, int fromIndex) {
        int start = answer.indexOf(sentence, Math.max(0, fromIndex));
        if (start >= 0) {
            return start;
        }
        return answer.indexOf(sentence);
    }

    private String normalizeFeedback(String feedback) {
        if (StringUtils.hasText(feedback)) {
            return feedback;
        }
        return "자소서 분석 결과를 확인해주세요.";
    }

    private String defaultString(String value) {
        return value == null ? "" : value;
    }

    private String normalizeImprovement(
            String sentence,
            String answer,
            String improvement,
            QuestionAnalysisStatus status
    ) {
        return AnalysisSanitizationRules.normalizeImprovement(
                sentence,
                answer,
                improvement,
                status == QuestionAnalysisStatus.PROVEN
        );
    }

    private QuestionAnalysisStatus parseStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return null;
        }

        String normalizedStatus = status.trim().toUpperCase();
        if ("GOOD".equals(normalizedStatus)) {
            return QuestionAnalysisStatus.PROVEN;
        }
        if ("NEEDS_IMPROVEMENT".equals(normalizedStatus)) {
            return QuestionAnalysisStatus.MENTIONED;
        }
        if ("RISK".equals(normalizedStatus)) {
            return QuestionAnalysisStatus.FABRICATED;
        }

        try {
            return QuestionAnalysisStatus.valueOf(normalizedStatus);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
