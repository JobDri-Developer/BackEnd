package com.jobdri.jobdri_api.domain.analysis.evaluation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

@Component
@Profile("analysis-eval")
@ConditionalOnProperty(
        prefix = "evaluation.missing-keyword-replay",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = false
)
@RequiredArgsConstructor
@Slf4j
public class MissingKeywordSanitizerReplayRunner implements ApplicationRunner {
    @Value("${evaluation.missing-keyword-replay.input:}")
    private String inputPath;

    @Value("${evaluation.missing-keyword-replay.output:}")
    private String outputPath;

    @Value("${evaluation.missing-keyword-replay.review-output:}")
    private String reviewOutputPath;

    private final MissingKeywordSanitizerReplayService replayService;
    private final EvaluationExitCoordinator evaluationExitCoordinator;
    private final Environment environment;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        log.info("MissingKeywordSanitizerReplayRunner run entered.");
        try {
            validateProfiles();
            validateExecutionProperties();
            MissingKeywordSanitizerReplayService.ReplaySummary summary = replayService.replay(
                    Path.of(inputPath),
                    Path.of(outputPath),
                    Path.of(reviewOutputPath)
            );
            validateOutputFile(summary.output(), summary.rawCandidateCount());
            validateOutputFile(summary.reviewOutput(), summary.rawCandidateCount());
            log.info(
                    "Missing keyword sanitizer replay 완료. totalCases={}, casesWithRawCandidates={}, rawCandidates={}, acceptedCandidates={}, rejectedCandidates={}, output={}, reviewOutput={}, summaryOutput={}",
                    summary.totalCases(),
                    summary.casesWithRawCandidates(),
                    summary.rawCandidateCount(),
                    summary.acceptedCandidateCount(),
                    summary.rejectedCandidateCount(),
                    summary.output(),
                    summary.reviewOutput(),
                    summary.summaryOutput()
            );
        } catch (Exception e) {
            log.error("Missing keyword sanitizer replay 실행에 실패했습니다. message={}", e.getMessage(), e);
            evaluationExitCoordinator.exit("missing-keyword-replay", 1);
            throw e;
        }
        evaluationExitCoordinator.exit("missing-keyword-replay", 0);
    }

    void validateProfiles() {
        List<String> profiles = Arrays.asList(environment.getActiveProfiles());
        if (profiles.contains("prod")) {
            throw new IllegalStateException("Missing keyword sanitizer replay must not run with prod profile.");
        }
        if (!profiles.contains("analysis-eval")) {
            throw new IllegalStateException("Missing keyword sanitizer replay requires analysis-eval profile.");
        }
    }

    void validateExecutionProperties() {
        if (!StringUtils.hasText(inputPath)) {
            throw new IllegalArgumentException("evaluation.missing-keyword-replay.input 값을 지정해야 합니다.");
        }
        if (!StringUtils.hasText(outputPath)) {
            throw new IllegalArgumentException("evaluation.missing-keyword-replay.output 값을 지정해야 합니다.");
        }
        if (!StringUtils.hasText(reviewOutputPath)) {
            throw new IllegalArgumentException("evaluation.missing-keyword-replay.review-output 값을 지정해야 합니다.");
        }
        if (!Files.isRegularFile(Path.of(inputPath))) {
            throw new IllegalArgumentException(
                    "evaluation.missing-keyword-replay.input 파일을 찾을 수 없습니다. path=" + inputPath
            );
        }
    }

    private void validateOutputFile(Path output, int expectedRows) throws java.io.IOException {
        if (!Files.isRegularFile(output) || Files.size(output) == 0) {
            throw new IllegalStateException("Missing keyword sanitizer replay output CSV가 생성되지 않았거나 비어 있습니다. path=" + output);
        }
        int actualRows = EvaluationCsvSupport.read(output).size();
        if (actualRows != expectedRows) {
            throw new IllegalStateException(
                    "Missing keyword sanitizer replay output row count mismatch. expected="
                            + expectedRows
                            + ", actual="
                            + actualRows
                            + ", path="
                            + output
            );
        }
    }
}
