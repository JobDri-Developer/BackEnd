package com.jobdri.jobdri_api.domain.evaluation.analysis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobdri.jobdri_api.domain.analysis.dto.external.llm.AnalysisCandidateResponse;
import com.jobdri.jobdri_api.domain.analysis.service.sanitization.MissingKeywordSanitizationDecision;
import com.jobdri.jobdri_api.domain.analysis.service.sanitization.MissingKeywordSanitizationResult;
import com.jobdri.jobdri_api.domain.analysis.service.sanitization.MissingKeywordSanitizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
class MissingKeywordSanitizerReplayService {
    private static final List<String> REQUIRED_HEADERS = List.of(
            "caseId",
            "mainTasks",
            "qualifications",
            "question",
            "answer",
            "rawCandidateResponseJson",
            "sanitizedCandidateResponseJson"
    );
    private static final List<String> REPLAY_HEADERS = List.of(
            "caseId",
            "candidateIndex",
            "rawCandidateCount",
            "acceptedCandidateCount",
            "rejectedCandidateCount",
            "keyword",
            "normalizedKeyword",
            "relatedRequirement",
            "accepted",
            "rejectionReason",
            "answerExactMatch",
            "answerNormalizedMatch",
            "jdRequirementMatched",
            "duplicateOfCandidateIndex"
    );
    private static final List<String> REVIEW_HEADERS = List.of(
            "caseId",
            "candidateIndex",
            "question",
            "answer",
            "mainTasks",
            "qualifications",
            "keyword",
            "relatedRequirement",
            "accepted",
            "rejectionReason",
            "manualVerdict",
            "manualNote"
    );
    private static final List<String> SUMMARY_HEADERS = List.of(
            "totalCases",
            "casesWithRawCandidates",
            "rawCandidateCount",
            "acceptedCandidateCount",
            "rejectedCandidateCount",
            "rejectionReason",
            "rejectionReasonCount"
    );

    private final ObjectMapper objectMapper;

    ReplaySummary replay(Path input, Path output, Path reviewOutput) throws IOException {
        List<String> headers = EvaluationCsvSupport.readHeaders(input);
        validateHeaders(headers);

        List<Map<String, String>> rows = EvaluationCsvSupport.read(input);
        List<Map<String, String>> replayRows = new ArrayList<>();
        List<Map<String, String>> reviewRows = new ArrayList<>();
        Map<String, Long> reasonCounts = new LinkedHashMap<>();

        int casesWithRawCandidates = 0;
        int rawCandidateTotal = 0;
        int acceptedTotal = 0;
        int rejectedTotal = 0;
        Set<String> caseIds = new HashSet<>();

        for (Map<String, String> row : rows) {
            String caseId = value(row, "caseId");
            if (!StringUtils.hasText(caseId)) {
                throw new IllegalArgumentException("Missing keyword replay input CSV has blank caseId.");
            }
            if (!caseIds.add(caseId)) {
                throw new IllegalArgumentException("Missing keyword replay input CSV has duplicate caseId: " + caseId);
            }
            AnalysisCandidateResponse rawResponse = readCandidateResponse(
                    value(row, "rawCandidateResponseJson"),
                    "rawCandidateResponseJson",
                    caseId
            );
            AnalysisCandidateResponse existingSanitized = readCandidateResponse(
                    value(row, "sanitizedCandidateResponseJson"),
                    "sanitizedCandidateResponseJson",
                    caseId
            );
            List<AnalysisCandidateResponse.MissingKeywordCandidate> rawCandidates =
                    safeMissingKeywordCandidates(rawResponse);
            MissingKeywordSanitizationResult replayResult = MissingKeywordSanitizer.sanitize(
                    value(row, "mainTasks"),
                    value(row, "qualifications"),
                    value(row, "answer"),
                    rawCandidates
            );
            validateExistingSanitized(caseId, replayResult.acceptedCandidates(), safeMissingKeywordCandidates(existingSanitized));

            int rawCandidateCount = rawCandidates.size();
            int acceptedCount = replayResult.acceptedCandidates().size();
            int rejectedCount = replayResult.decisions().size() - acceptedCount;
            if (rawCandidateCount > 0) {
                casesWithRawCandidates++;
            }
            rawCandidateTotal += rawCandidateCount;
            acceptedTotal += acceptedCount;
            rejectedTotal += rejectedCount;

            for (MissingKeywordSanitizationDecision decision : replayResult.decisions()) {
                reasonCounts.merge(decision.rejectionReason().name(), 1L, Long::sum);
                replayRows.add(toReplayRow(caseId, rawCandidateCount, acceptedCount, rejectedCount, decision));
                if (rawCandidateCount > 0) {
                    reviewRows.add(toReviewRow(caseId, row, decision));
                }
            }
        }

        EvaluationCsvSupport.writeRows(output, REPLAY_HEADERS, replayRows);
        EvaluationCsvSupport.writeRows(reviewOutput, REVIEW_HEADERS, reviewRows);
        Path summaryOutput = summaryPath(output);
        EvaluationCsvSupport.writeRows(
                summaryOutput,
                SUMMARY_HEADERS,
                toSummaryRows(rows.size(), casesWithRawCandidates, rawCandidateTotal, acceptedTotal, rejectedTotal, reasonCounts)
        );

        log.info(
                "Missing keyword sanitizer replay completed. rows={}, rawCandidates={}, acceptedCandidates={}, rejectedCandidates={}, output={}",
                rows.size(),
                rawCandidateTotal,
                acceptedTotal,
                rejectedTotal,
                output
        );
        return new ReplaySummary(
                rows.size(),
                casesWithRawCandidates,
                rawCandidateTotal,
                acceptedTotal,
                rejectedTotal,
                output,
                reviewOutput,
                summaryOutput,
                Map.copyOf(reasonCounts)
        );
    }

    private void validateHeaders(List<String> headers) {
        List<String> missing = REQUIRED_HEADERS.stream()
                .filter(header -> !headers.contains(header))
                .toList();
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("Missing keyword replay input CSV missing required headers: " + missing);
        }
    }

    private AnalysisCandidateResponse readCandidateResponse(String json, String fieldName, String caseId) {
        if (!StringUtils.hasText(json)) {
            return new AnalysisCandidateResponse(List.of(), List.of(), List.of());
        }
        try {
            AnalysisCandidateResponse response = objectMapper.readValue(json, AnalysisCandidateResponse.class);
            if (response == null) {
                return new AnalysisCandidateResponse(List.of(), List.of(), List.of());
            }
            return response;
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(fieldName + " is not valid candidate JSON. caseId=" + caseId, e);
        }
    }

    private List<AnalysisCandidateResponse.MissingKeywordCandidate> safeMissingKeywordCandidates(
            AnalysisCandidateResponse response
    ) {
        if (response == null || response.missingKeywordCandidates() == null) {
            return List.of();
        }
        return response.missingKeywordCandidates();
    }

    private void validateExistingSanitized(
            String caseId,
            List<AnalysisCandidateResponse.MissingKeywordCandidate> replayAccepted,
            List<AnalysisCandidateResponse.MissingKeywordCandidate> existingAccepted
    ) {
        if (!Objects.equals(replayAccepted, existingAccepted)) {
            throw new IllegalStateException(
                    "Missing keyword replay accepted candidates mismatch. caseId=" + caseId
            );
        }
    }

    private Map<String, String> toReplayRow(
            String caseId,
            int rawCandidateCount,
            int acceptedCandidateCount,
            int rejectedCandidateCount,
            MissingKeywordSanitizationDecision decision
    ) {
        AnalysisCandidateResponse.MissingKeywordCandidate candidate = decision.originalCandidate();
        Map<String, String> row = new LinkedHashMap<>();
        row.put("caseId", caseId);
        row.put("candidateIndex", String.valueOf(decision.candidateIndex()));
        row.put("rawCandidateCount", String.valueOf(rawCandidateCount));
        row.put("acceptedCandidateCount", String.valueOf(acceptedCandidateCount));
        row.put("rejectedCandidateCount", String.valueOf(rejectedCandidateCount));
        row.put("keyword", candidate == null ? "" : value(candidate.keyword()));
        row.put("normalizedKeyword", value(decision.normalizedKeyword()));
        row.put("relatedRequirement", candidate == null ? "" : value(candidate.relatedRequirement()));
        row.put("accepted", String.valueOf(decision.accepted()));
        row.put("rejectionReason", decision.rejectionReason().name());
        row.put("answerExactMatch", String.valueOf(decision.answerExactMatch()));
        row.put("answerNormalizedMatch", String.valueOf(decision.answerNormalizedMatch()));
        row.put("jdRequirementMatched", String.valueOf(decision.jdRequirementMatched()));
        row.put("duplicateOfCandidateIndex", decision.duplicateOfCandidateIndex() == null
                ? ""
                : String.valueOf(decision.duplicateOfCandidateIndex()));
        return row;
    }

    private Map<String, String> toReviewRow(
            String caseId,
            Map<String, String> inputRow,
            MissingKeywordSanitizationDecision decision
    ) {
        AnalysisCandidateResponse.MissingKeywordCandidate candidate = decision.originalCandidate();
        Map<String, String> row = new LinkedHashMap<>();
        row.put("caseId", caseId);
        row.put("candidateIndex", String.valueOf(decision.candidateIndex()));
        row.put("question", value(inputRow, "question"));
        row.put("answer", value(inputRow, "answer"));
        row.put("mainTasks", value(inputRow, "mainTasks"));
        row.put("qualifications", value(inputRow, "qualifications"));
        row.put("keyword", candidate == null ? "" : value(candidate.keyword()));
        row.put("relatedRequirement", candidate == null ? "" : value(candidate.relatedRequirement()));
        row.put("accepted", String.valueOf(decision.accepted()));
        row.put("rejectionReason", decision.rejectionReason().name());
        row.put("manualVerdict", "");
        row.put("manualNote", "");
        return row;
    }

    private List<Map<String, String>> toSummaryRows(
            int totalCases,
            int casesWithRawCandidates,
            int rawCandidateCount,
            int acceptedCandidateCount,
            int rejectedCandidateCount,
            Map<String, Long> reasonCounts
    ) {
        if (reasonCounts.isEmpty()) {
            return List.of(summaryRow(
                    totalCases,
                    casesWithRawCandidates,
                    rawCandidateCount,
                    acceptedCandidateCount,
                    rejectedCandidateCount,
                    "",
                    0
            ));
        }
        return reasonCounts.entrySet().stream()
                .map(entry -> summaryRow(
                        totalCases,
                        casesWithRawCandidates,
                        rawCandidateCount,
                        acceptedCandidateCount,
                        rejectedCandidateCount,
                        entry.getKey(),
                        entry.getValue()
                ))
                .collect(Collectors.toList());
    }

    private Map<String, String> summaryRow(
            int totalCases,
            int casesWithRawCandidates,
            int rawCandidateCount,
            int acceptedCandidateCount,
            int rejectedCandidateCount,
            String rejectionReason,
            long rejectionReasonCount
    ) {
        Map<String, String> row = new LinkedHashMap<>();
        row.put("totalCases", String.valueOf(totalCases));
        row.put("casesWithRawCandidates", String.valueOf(casesWithRawCandidates));
        row.put("rawCandidateCount", String.valueOf(rawCandidateCount));
        row.put("acceptedCandidateCount", String.valueOf(acceptedCandidateCount));
        row.put("rejectedCandidateCount", String.valueOf(rejectedCandidateCount));
        row.put("rejectionReason", rejectionReason);
        row.put("rejectionReasonCount", String.valueOf(rejectionReasonCount));
        return row;
    }

    private Path summaryPath(Path output) {
        String fileName = output.getFileName().toString();
        int extensionIndex = fileName.lastIndexOf('.');
        String summaryName = extensionIndex < 0
                ? fileName + "_summary.csv"
                : fileName.substring(0, extensionIndex) + "_summary" + fileName.substring(extensionIndex);
        Path parent = output.getParent();
        return parent == null ? Path.of(summaryName) : parent.resolve(summaryName);
    }

    private String value(Map<String, String> row, String key) {
        return row == null || row.get(key) == null ? "" : row.get(key).trim();
    }

    private String value(String value) {
        return value == null ? "" : value.trim();
    }

    record ReplaySummary(
            int totalCases,
            int casesWithRawCandidates,
            int rawCandidateCount,
            int acceptedCandidateCount,
            int rejectedCandidateCount,
            Path output,
            Path reviewOutput,
            Path summaryOutput,
            Map<String, Long> rejectionReasonCounts
    ) {
    }
}
