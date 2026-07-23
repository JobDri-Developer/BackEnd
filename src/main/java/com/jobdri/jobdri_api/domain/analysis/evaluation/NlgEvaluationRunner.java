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
        prefix = "evaluation.nlg-judge",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = false
)
@RequiredArgsConstructor
@Slf4j
// 수동 실행 예:
// ./gradlew bootRun --args='--spring.profiles.active=analysis-eval --evaluation.nlg-judge.enabled=true --evaluation.nlg-judge.input=evaluation/evaluation_ai_results_two_pass_v2.csv --evaluation.nlg-judge.output=evaluation/evaluation_nlg_judge_two_pass_v2.csv --evaluation.confirm-openai-cost=true'
public class NlgEvaluationRunner implements ApplicationRunner {

    @Value("${evaluation.nlg-judge.input:}")
    private String inputPath;

    @Value("${evaluation.nlg-judge.output:}")
    private String outputPath;

    @Value("${evaluation.nlg-judge.compare-inputs:}")
    private String compareInputPaths;

    @Value("${evaluation.confirm-openai-cost:false}")
    private boolean confirmOpenAiCost;

    @Value("${openai.api.key:}")
    private String openAiApiKey;

    @Value("${evaluation.nlg-judge.model:gpt-4o-mini}")
    private String judgeModel;

    @Value("${evaluation.analysis.enabled:false}")
    private boolean analysisEvaluationEnabled;

    private final NlgEvaluationBatchService nlgEvaluationBatchService;
    private final EvaluationExitCoordinator evaluationExitCoordinator;
    private final Environment environment;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        log.info(
                "NlgEvaluationRunner run entered. compareMode={}",
                StringUtils.hasText(compareInputPaths)
        );
        try {
            validateProfiles();
            log.info(
                    "NLG judge Runner가 시작되었습니다. compareMode={}, output={}",
                    StringUtils.hasText(compareInputPaths),
                    outputPath
            );
            if (StringUtils.hasText(compareInputPaths)) {
                validateComparisonProperties();
                List<Path> inputs = Arrays.stream(compareInputPaths.split(","))
                        .map(String::trim)
                        .filter(StringUtils::hasText)
                        .map(Path::of)
                        .toList();
                NlgEvaluationBatchService.NlgEvaluationComparisonSummary summary =
                        nlgEvaluationBatchService.compare(inputs, Path.of(outputPath));
                validateOutputFile(Path.of(outputPath));
                log.info("NLG judge 비교 리포트 생성 완료. files={}, output={}", summary.fileCount(), summary.outputPath());
            } else {
                validateJudgeProperties();
                log.info("NLG judge 평가를 시작합니다. input={}, output={}, model={}", inputPath, outputPath, judgeModel);
                NlgEvaluationBatchService.NlgEvaluationSummary summary =
                        nlgEvaluationBatchService.run(Path.of(inputPath), Path.of(outputPath));
                validateOutputFile(Path.of(outputPath));
                log.info(
                        "NLG judge 평가 완료. total={}, success={}, failure={}, output={}",
                        summary.totalCount(),
                        summary.successCount(),
                        summary.failureCount(),
                        summary.outputPath()
                );
            }
        } catch (Exception e) {
            log.error("NLG judge 실행에 실패했습니다. message={}", e.getMessage(), e);
            evaluationExitCoordinator.exit("nlg-judge", 1);
            throw e;
        }
        evaluationExitCoordinator.exit("nlg-judge", 0);
    }

    void validateProfiles() {
        List<String> profiles = Arrays.asList(environment.getActiveProfiles());
        if (profiles.contains("prod")) {
            throw new IllegalStateException("NLG judge must not run with prod profile.");
        }
        if (!profiles.contains("analysis-eval")) {
            throw new IllegalStateException("NLG judge requires analysis-eval profile.");
        }
    }

    void validateJudgeProperties() {
        validateMutuallyExclusiveRunner();
        validateCommonOutput();
        if (!StringUtils.hasText(inputPath)) {
            throw new IllegalArgumentException("evaluation.nlg-judge.input 값을 지정해야 합니다.");
        }
        if (!Files.isRegularFile(Path.of(inputPath))) {
            throw new IllegalArgumentException("evaluation.nlg-judge.input 파일을 찾을 수 없습니다. path=" + inputPath);
        }
        if (!StringUtils.hasText(openAiApiKey)) {
            throw new IllegalArgumentException("OPENAI_API_KEY 또는 openai.api.key 값을 지정해야 합니다.");
        }
        if (!confirmOpenAiCost) {
            throw new IllegalArgumentException(
                    "외부 OpenAI API 비용 발생을 확인한 뒤 evaluation.confirm-openai-cost=true 값을 지정해야 합니다."
            );
        }
    }

    void validateComparisonProperties() {
        validateMutuallyExclusiveRunner();
        validateCommonOutput();
        List<Path> inputs = Arrays.stream(compareInputPaths.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .map(Path::of)
                .toList();
        if (inputs.isEmpty()) {
            throw new IllegalArgumentException("evaluation.nlg-judge.compare-inputs 값을 지정해야 합니다.");
        }
        for (Path input : inputs) {
            if (!Files.isRegularFile(input)) {
                throw new IllegalArgumentException("비교 대상 judge CSV 파일을 찾을 수 없습니다. path=" + input);
            }
        }
    }

    private void validateCommonOutput() {
        if (!StringUtils.hasText(outputPath)) {
            throw new IllegalArgumentException("evaluation.nlg-judge.output 값을 지정해야 합니다.");
        }
    }

    private void validateMutuallyExclusiveRunner() {
        if (analysisEvaluationEnabled) {
            throw new IllegalArgumentException(
                    "evaluation.analysis.enabled와 evaluation.nlg-judge.enabled를 동시에 true로 설정할 수 없습니다."
            );
        }
    }

    private void validateOutputFile(Path output) throws java.io.IOException {
        if (!Files.isRegularFile(output) || Files.size(output) == 0) {
            throw new IllegalStateException("NLG judge output CSV가 생성되지 않았거나 비어 있습니다. path=" + output);
        }
    }

}
