package com.jobdri.jobdri_api.domain.analysis.evaluation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
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
@ConditionalOnProperty(name = "evaluation.nlg-judge.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
// 수동 실행 예:
// ./gradlew bootRun --args='--spring.profiles.active=analysis-eval --evaluation.nlg-judge.enabled=true --evaluation.nlg-judge.input=evaluation/evaluation_ai_results_two_pass_v2.csv --evaluation.nlg-judge.output=evaluation/evaluation_nlg_judge_two_pass_v2.csv --evaluation.confirm-openai-cost=true'
class NlgEvaluationRunner implements ApplicationRunner {

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

    private final NlgEvaluationBatchService nlgEvaluationBatchService;
    private final ConfigurableApplicationContext applicationContext;
    private final Environment environment;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        try {
            validateProfiles();
            if (StringUtils.hasText(compareInputPaths)) {
                validateComparisonProperties();
                List<Path> inputs = Arrays.stream(compareInputPaths.split(","))
                        .map(String::trim)
                        .filter(StringUtils::hasText)
                        .map(Path::of)
                        .toList();
                NlgEvaluationBatchService.NlgEvaluationComparisonSummary summary =
                        nlgEvaluationBatchService.compare(inputs, Path.of(outputPath));
                log.info("NLG judge 비교 리포트 생성 완료. files={}, output={}", summary.fileCount(), summary.outputPath());
            } else {
                validateJudgeProperties();
                log.info("NLG judge 평가를 시작합니다. input={}, output={}, model={}", inputPath, outputPath, judgeModel);
                NlgEvaluationBatchService.NlgEvaluationSummary summary =
                        nlgEvaluationBatchService.run(Path.of(inputPath), Path.of(outputPath));
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
            exitApplication(1);
            throw e;
        }
        exitApplication(0);
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

    private void exitApplication(int exitCode) {
        Thread shutdownThread = new Thread(() -> {
            int resolvedExitCode = SpringApplication.exit(applicationContext, () -> exitCode);
            System.exit(resolvedExitCode);
        });
        shutdownThread.setDaemon(false);
        shutdownThread.start();
    }
}
