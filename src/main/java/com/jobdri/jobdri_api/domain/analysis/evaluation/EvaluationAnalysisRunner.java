package com.jobdri.jobdri_api.domain.analysis.evaluation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
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
@RequiredArgsConstructor
@Slf4j
// 수동 실행 예:
// ./gradlew bootRun --args='--spring.profiles.active=analysis-eval --evaluation.input=/path/evaluation_cases_검수.csv --evaluation.output=/path/evaluation_ai_results.csv --evaluation.confirm-openai-cost=true'
public class EvaluationAnalysisRunner implements ApplicationRunner {

    @Value("${evaluation.input:}")
    private String inputPath;

    @Value("${evaluation.output:}")
    private String outputPath;

    @Value("${evaluation.confirm-openai-cost:false}")
    private boolean confirmOpenAiCost;

    @Value("${openai.api.key:}")
    private String openAiApiKey;

    @Value("${openai.model.cover-letter-analysis:gpt-4o-mini}")
    private String analysisModel;

    private final EvaluationAnalysisBatchService evaluationAnalysisBatchService;
    private final ConfigurableApplicationContext applicationContext;
    private final Environment environment;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        try {
            validateProfiles();
            validateExecutionProperties();
            log.info("평가용 자소서 분석을 시작합니다. input={}, output={}, model={}", inputPath, outputPath, analysisModel);
            EvaluationAnalysisBatchService.EvaluationBatchSummary summary =
                    evaluationAnalysisBatchService.run(Path.of(inputPath), Path.of(outputPath));
            log.info(
                    "평가용 자소서 분석이 완료되었습니다. total={}, success={}, failure={}, output={}",
                    summary.totalCount(),
                    summary.successCount(),
                    summary.failureCount(),
                    summary.outputPath()
            );
            exitApplication(0);
        } catch (Exception e) {
            log.error("평가용 자소서 분석 실행에 실패했습니다. message={}", e.getMessage(), e);
            throw e;
        }
    }

    private void exitApplication(int exitCode) {
        Thread shutdownThread = new Thread(() -> SpringApplication.exit(applicationContext, () -> exitCode));
        shutdownThread.setDaemon(false);
        shutdownThread.start();
    }

    void validateProfiles() {
        List<String> profiles = Arrays.asList(environment.getActiveProfiles());
        if (profiles.contains("prod")) {
            throw new IllegalStateException("analysis-eval must not run with prod profile.");
        }
        if (!profiles.contains("analysis-eval")) {
            throw new IllegalStateException("Evaluation runner requires analysis-eval profile.");
        }
    }

    void validateExecutionProperties() {
        if (!StringUtils.hasText(inputPath)) {
            throw new IllegalArgumentException("evaluation.input 값을 지정해야 합니다.");
        }
        if (!StringUtils.hasText(outputPath)) {
            throw new IllegalArgumentException("evaluation.output 값을 지정해야 합니다.");
        }
        if (!Files.isRegularFile(Path.of(inputPath))) {
            throw new IllegalArgumentException("evaluation.input 파일을 찾을 수 없습니다. path=" + inputPath);
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
}
