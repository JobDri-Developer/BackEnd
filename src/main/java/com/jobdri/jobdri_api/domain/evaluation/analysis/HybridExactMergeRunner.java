package com.jobdri.jobdri_api.domain.evaluation.analysis;

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
        prefix = "evaluation.hybrid-merge",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = false
)
@RequiredArgsConstructor
@Slf4j
public class HybridExactMergeRunner implements ApplicationRunner {

    @Value("${evaluation.hybrid-merge.single-pass-input:}")
    private String singlePassInputPath;

    @Value("${evaluation.hybrid-merge.two-pass-input:}")
    private String twoPassInputPath;

    @Value("${evaluation.hybrid-merge.output:}")
    private String outputPath;

    @Value("${evaluation.analysis.enabled:false}")
    private boolean analysisEvaluationEnabled;

    @Value("${evaluation.nlg-judge.enabled:false}")
    private boolean nlgJudgeEnabled;

    private final HybridExactMergeService hybridExactMergeService;
    private final EvaluationExitCoordinator evaluationExitCoordinator;
    private final Environment environment;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        log.info("HybridExactMergeRunner run entered.");
        try {
            validateProfiles();
            validateExecutionProperties();
            log.info(
                    "Hybrid exact offline merge를 시작합니다. singlePassInput={}, twoPassInput={}, output={}",
                    singlePassInputPath,
                    twoPassInputPath,
                    outputPath
            );
            HybridExactMergeService.HybridExactMergeSummary summary = hybridExactMergeService.merge(
                    Path.of(singlePassInputPath),
                    Path.of(twoPassInputPath),
                    Path.of(outputPath)
            );
            validateOutputFile(summary.outputPath(), summary.mergedCases());
            log.info(
                    "Hybrid exact offline merge 완료. singlePassCases={}, twoPassCases={}, mergedCases={}, output={}",
                    summary.singlePassCases(),
                    summary.twoPassCases(),
                    summary.mergedCases(),
                    summary.outputPath()
            );
        } catch (Exception e) {
            log.error("Hybrid exact offline merge 실행에 실패했습니다. message={}", e.getMessage(), e);
            evaluationExitCoordinator.exit("hybrid-exact-merge", 1);
            throw e;
        }
        evaluationExitCoordinator.exit("hybrid-exact-merge", 0);
    }

    void validateProfiles() {
        List<String> profiles = Arrays.asList(environment.getActiveProfiles());
        if (profiles.contains("prod")) {
            throw new IllegalStateException("Hybrid exact merge must not run with prod profile.");
        }
        if (!profiles.contains("analysis-eval")) {
            throw new IllegalStateException("Hybrid exact merge requires analysis-eval profile.");
        }
    }

    void validateExecutionProperties() {
        if (analysisEvaluationEnabled || nlgJudgeEnabled) {
            throw new IllegalArgumentException(
                    "evaluation.hybrid-merge.enabled는 evaluation.analysis.enabled 또는 evaluation.nlg-judge.enabled와 동시에 true로 설정할 수 없습니다."
            );
        }
        if (!StringUtils.hasText(singlePassInputPath)) {
            throw new IllegalArgumentException("evaluation.hybrid-merge.single-pass-input 값을 지정해야 합니다.");
        }
        if (!StringUtils.hasText(twoPassInputPath)) {
            throw new IllegalArgumentException("evaluation.hybrid-merge.two-pass-input 값을 지정해야 합니다.");
        }
        if (!StringUtils.hasText(outputPath)) {
            throw new IllegalArgumentException("evaluation.hybrid-merge.output 값을 지정해야 합니다.");
        }
        if (!Files.isRegularFile(Path.of(singlePassInputPath))) {
            throw new IllegalArgumentException(
                    "evaluation.hybrid-merge.single-pass-input 파일을 찾을 수 없습니다. path=" + singlePassInputPath
            );
        }
        if (!Files.isRegularFile(Path.of(twoPassInputPath))) {
            throw new IllegalArgumentException(
                    "evaluation.hybrid-merge.two-pass-input 파일을 찾을 수 없습니다. path=" + twoPassInputPath
            );
        }
    }

    private void validateOutputFile(Path output, int expectedRows) throws java.io.IOException {
        if (!Files.isRegularFile(output) || Files.size(output) == 0) {
            throw new IllegalStateException("Hybrid exact offline merge output CSV가 생성되지 않았거나 비어 있습니다. path=" + output);
        }
        int actualRows = EvaluationCsvSupport.read(output).size();
        if (actualRows != expectedRows) {
            throw new IllegalStateException(
                    "Hybrid exact offline merge output row count mismatch. expected="
                            + expectedRows
                            + ", actual="
                            + actualRows
                            + ", path="
                            + output
            );
        }
    }
}
