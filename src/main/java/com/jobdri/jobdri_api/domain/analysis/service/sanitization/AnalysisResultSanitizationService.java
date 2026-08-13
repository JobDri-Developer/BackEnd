package com.jobdri.jobdri_api.domain.analysis.service.sanitization;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobdri.jobdri_api.domain.analysis.dto.external.llm.AnalysisLlmResponse;
import com.jobdri.jobdri_api.domain.analysis.dto.response.AnalysisHighlightResponse;
import com.jobdri.jobdri_api.domain.analysis.dto.response.MissingKeywordResponse;
import com.jobdri.jobdri_api.domain.analysis.dto.response.MissingKeywordSource;
import com.jobdri.jobdri_api.domain.analysis.entity.Analysis;
import com.jobdri.jobdri_api.domain.jobposting.entity.JobPosting;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.jobdri.jobdri_api.domain.analysis.service.core.AnalysisResultConstants.MAX_HIGHLIGHTS;
import static com.jobdri.jobdri_api.domain.analysis.service.core.AnalysisResultConstants.MAX_HIGHLIGHT_QUOTE_LENGTH;
import static com.jobdri.jobdri_api.domain.analysis.service.core.AnalysisResultConstants.MAX_HIGHLIGHT_TITLE_LENGTH;
import static com.jobdri.jobdri_api.domain.analysis.service.core.AnalysisResultConstants.MAX_MISSING_KEYWORDS;
import static com.jobdri.jobdri_api.domain.analysis.service.core.AnalysisResultConstants.MAX_MISSING_KEYWORD_LENGTH;

@Service
@Slf4j
@RequiredArgsConstructor
public class AnalysisResultSanitizationService {
    private static final TypeReference<List<MissingKeywordResponse>> MISSING_KEYWORDS_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<AnalysisHighlightResponse>> HIGHLIGHTS_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;

    public AnalysisResultPayload analysisResultPayload(Analysis analysis) {
        List<AnalysisHighlightResponse> keyStrengths = readHighlights(
                analysis,
                analysis.getKeyStrengthsJson(),
                "keyStrengths"
        );
        return new AnalysisResultPayload(
                keyStrengths,
                removeOverlappingHighlights(
                        readHighlights(analysis, analysis.getKeyWeaknessesJson(), "keyWeaknesses"),
                        keyStrengths
                ),
                readMissingKeywords(analysis)
        );
    }

    public AnalysisResultPayload sanitizeAndPersistAnalysisPayload(Analysis analysis, boolean persistIfChanged) {
        AnalysisResultPayload payload = analysisResultPayload(analysis);
        String sanitizedKeyStrengthsJson = serializeHighlights(payload.keyStrengths(), "keyStrengths");
        String sanitizedKeyWeaknessesJson = serializeHighlights(payload.keyWeaknesses(), "keyWeaknesses");
        if (persistIfChanged
                && (!sanitizedKeyStrengthsJson.equals(analysis.getKeyStrengthsJson())
                || !sanitizedKeyWeaknessesJson.equals(analysis.getKeyWeaknessesJson()))) {
            analysis.updateHighlightsJson(sanitizedKeyStrengthsJson, sanitizedKeyWeaknessesJson);
        }
        return payload;
    }

    public List<AnalysisHighlightResponse> buildHighlights(List<AnalysisLlmResponse.HighlightItem> items) {
        return sanitizeHighlights(items, AnalysisLlmResponse.HighlightItem::title, AnalysisLlmResponse.HighlightItem::quote);
    }

    public List<AnalysisHighlightResponse> buildNonOverlappingHighlights(
            List<AnalysisLlmResponse.HighlightItem> items,
            List<AnalysisHighlightResponse> existingHighlights
    ) {
        return sanitizeHighlights(
                removeOverlappingRawHighlights(items, existingHighlights),
                AnalysisLlmResponse.HighlightItem::title,
                AnalysisLlmResponse.HighlightItem::quote
        );
    }

    public List<MissingKeywordResponse> buildMissingKeywords(
            JobPosting jobPosting,
            String combinedAnswers,
            AnalysisLlmResponse llmResponse
    ) {
        if (llmResponse == null || llmResponse.missingKeywords() == null) {
            return List.of();
        }

        List<MissingKeywordResponse> result = new ArrayList<>();
        Set<String> seenKeywords = new java.util.HashSet<>();

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

    public String normalizeKeyword(String keyword) {
        return keyword == null ? "" : keyword.replaceAll("\\s+", "").toLowerCase();
    }

    public String serializeMissingKeywords(List<MissingKeywordResponse> missingKeywords) {
        try {
            return objectMapper.writeValueAsString(missingKeywords == null ? List.of() : missingKeywords);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize missingKeywords. Fallback to empty array.", e);
            return "[]";
        }
    }

    public String serializeHighlights(List<AnalysisHighlightResponse> highlights, String fieldName) {
        try {
            return objectMapper.writeValueAsString(highlights == null ? List.of() : highlights);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize {}. Fallback to empty array.", fieldName, e);
            return "[]";
        }
    }

    public List<AnalysisHighlightResponse> readHighlights(Analysis analysis, String json, String fieldName) {
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

    public List<MissingKeywordResponse> readMissingKeywords(Analysis analysis) {
        if (!StringUtils.hasText(analysis.getMissingKeywordsJson())) {
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
        Set<String> seenKeywords = new java.util.HashSet<>();

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
        Set<String> seenHighlights = new java.util.HashSet<>();

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

    public record AnalysisResultPayload(
            List<AnalysisHighlightResponse> keyStrengths,
            List<AnalysisHighlightResponse> keyWeaknesses,
            List<MissingKeywordResponse> missingKeywords
    ) {
    }
}
