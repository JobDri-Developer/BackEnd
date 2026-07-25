package com.jobdri.jobdri_api.domain.analysis.evaluation;

import com.jobdri.jobdri_api.domain.corpus.service.CorpusAdminRunner;
import com.jobdri.jobdri_api.global.scheduling.AsyncTaskSweepScheduler;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobdri.jobdri_api.domain.analysis.dto.llm.AnalysisLlmResponse;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.DefaultApplicationArguments;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EvaluationAnalysisRunnerSafetyTest {

    @TempDir
    Path tempDir;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("prod profile과 analysis-eval을 함께 활성화하면 fail-fast 한다")
    void validateProfilesFailsWhenProdProfileIsActive() {
        EvaluationAnalysisRunner runner = runnerWithProfiles("prod", "analysis-eval");

        assertThatThrownBy(runner::validateProfiles)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("analysis-eval must not run with prod profile");
    }

    @Test
    @DisplayName("analysis-eval profile이 없으면 fail-fast 한다")
    void validateProfilesFailsWhenAnalysisEvalProfileIsMissing() {
        EvaluationAnalysisRunner runner = runnerWithProfiles("dev");

        assertThatThrownBy(runner::validateProfiles)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Evaluation runner requires analysis-eval profile");
    }

    @Test
    @DisplayName("analysis-eval profile만 활성화되면 프로필 검증에 성공한다")
    void validateProfilesSucceedsWhenOnlyAnalysisEvalProfileIsActive() {
        EvaluationAnalysisRunner runner = runnerWithProfiles("analysis-eval");

        runner.validateProfiles();
    }

    @Test
    @DisplayName("confirm-openai-cost=false이면 실행 속성 검증에서 실패한다")
    void validateExecutionPropertiesFailsWhenCostConfirmationIsFalse() throws Exception {
        EvaluationAnalysisRunner runner = runnerWithProfiles("analysis-eval");
        Path input = tempDir.resolve("evaluation_cases.csv");
        Files.writeString(input, "caseId\nEV-01\n");
        ReflectionTestUtils.setField(runner, "inputPath", input.toString());
        ReflectionTestUtils.setField(runner, "outputPath", tempDir.resolve("evaluation_ai_results.csv").toString());
        ReflectionTestUtils.setField(runner, "openAiApiKey", "test-key");
        ReflectionTestUtils.setField(runner, "confirmOpenAiCost", false);

        assertThatThrownBy(runner::validateExecutionProperties)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("evaluation.confirm-openai-cost=true");
    }

    @Test
    @DisplayName("운영 profile에서는 NLG judge Runner가 실행되지 않도록 fail-fast 한다")
    void nlgJudgeRunnerFailsWhenProdProfileIsActive() {
        NlgEvaluationRunner runner = nlgRunnerWithProfiles("prod", "analysis-eval");

        assertThatThrownBy(runner::validateProfiles)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must not run with prod profile");
    }

    @Test
    @DisplayName("분석 평가 Runner는 evaluation.analysis.enabled=true일 때만 실행된다")
    void evaluationRunnerRequiresAnalysisEnabledFlag() {
        ConditionalOnProperty condition = EvaluationAnalysisRunner.class.getAnnotation(ConditionalOnProperty.class);

        assertThat(condition).isNotNull();
        assertThat(condition.prefix()).isEqualTo("evaluation.analysis");
        assertThat(condition.name()).containsExactly("enabled");
        assertThat(condition.havingValue()).isEqualTo("true");
        assertThat(condition.matchIfMissing()).isFalse();
    }

    @Test
    @DisplayName("NLG judge Runner는 명시적으로 활성화한 경우에만 실행된다")
    void nlgJudgeRunnerRequiresEnabledFlag() {
        ConditionalOnProperty condition = NlgEvaluationRunner.class.getAnnotation(ConditionalOnProperty.class);

        assertThat(condition).isNotNull();
        assertThat(condition.prefix()).isEqualTo("evaluation.nlg-judge");
        assertThat(condition.name()).containsExactly("enabled");
        assertThat(condition.havingValue()).isEqualTo("true");
        assertThat(condition.matchIfMissing()).isFalse();
    }

    @Test
    @DisplayName("NLG judge Runner는 component scan에서 발견 가능한 public 컴포넌트다")
    void nlgJudgeRunnerIsPublicScannableComponent() {
        assertThat(Modifier.isPublic(NlgEvaluationRunner.class.getModifiers())).isTrue();

        scannedRunnerContext()
                .withPropertyValues(
                        "spring.profiles.active=analysis-eval",
                        "evaluation.analysis.enabled=false",
                        "evaluation.nlg-judge.enabled=true"
                )
                .run(context -> {
                    assertThat(context).doesNotHaveBean(EvaluationAnalysisRunner.class);
                    assertThat(context).hasSingleBean(NlgEvaluationRunner.class);
                    assertThat(context.getBeanNamesForType(ApplicationRunner.class))
                            .containsExactly("nlgEvaluationRunner");
                });
    }

    @Test
    @DisplayName("analysis-eval + analysis.enabled=false이면 분석 Runner가 생성되지 않는다")
    void analysisRunnerIsNotCreatedWhenAnalysisFlagIsFalse() {
        runnerContext()
                .withPropertyValues(
                        "spring.profiles.active=analysis-eval",
                        "evaluation.analysis.enabled=false",
                        "evaluation.nlg-judge.enabled=false"
                )
                .run(context -> {
                    assertThat(context).doesNotHaveBean(EvaluationAnalysisRunner.class);
                    assertThat(context).doesNotHaveBean(NlgEvaluationRunner.class);
                    assertThat(context.getBeanNamesForType(ApplicationRunner.class)).isEmpty();
                });
    }

    @Test
    @DisplayName("analysis-eval + analysis.enabled=true이면 분석 Runner만 생성된다")
    void analysisRunnerIsCreatedWhenAnalysisFlagIsTrue() {
        scannedRunnerContext()
                .withPropertyValues(
                        "spring.profiles.active=analysis-eval",
                        "evaluation.analysis.enabled=true",
                        "evaluation.nlg-judge.enabled=false"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(EvaluationAnalysisRunner.class);
                    assertThat(context).doesNotHaveBean(NlgEvaluationRunner.class);
                    assertThat(context.getBeanNamesForType(ApplicationRunner.class))
                            .containsExactly("evaluationAnalysisRunner");
                });
    }

    @Test
    @DisplayName("analysis-eval + nlg-judge.enabled=true이면 NLG Runner만 생성된다")
    void nlgRunnerIsCreatedWhenNlgJudgeFlagIsTrue() {
        scannedRunnerContext()
                .withPropertyValues(
                        "spring.profiles.active=analysis-eval",
                        "evaluation.analysis.enabled=false",
                        "evaluation.nlg-judge.enabled=true"
                )
                .run(context -> {
                    assertThat(context).doesNotHaveBean(EvaluationAnalysisRunner.class);
                    assertThat(context).hasSingleBean(NlgEvaluationRunner.class);
                    assertThat(context.getBeanNamesForType(ApplicationRunner.class))
                            .containsExactly("nlgEvaluationRunner");
                    verifyNoInteractions(context.getBean(EvaluationExitCoordinator.class));
                });
    }

    @Test
    @DisplayName("두 플래그가 모두 false이면 어느 Runner도 생성되지 않는다")
    void noRunnerIsCreatedWhenBothFlagsAreFalse() {
        runnerContext()
                .withPropertyValues(
                        "spring.profiles.active=analysis-eval",
                        "evaluation.analysis.enabled=false",
                        "evaluation.nlg-judge.enabled=false"
                )
                .run(context -> {
                    assertThat(context).doesNotHaveBean(EvaluationAnalysisRunner.class);
                    assertThat(context).doesNotHaveBean(NlgEvaluationRunner.class);
                    assertThat(context.getBeanNamesForType(ApplicationRunner.class)).isEmpty();
                });
    }

    @Test
    @DisplayName("두 플래그가 모두 true이면 설정 오류로 fail-fast 한다")
    void bothFlagsTrueFailsFast() {
        runnerContext()
                .withPropertyValues(
                        "spring.profiles.active=analysis-eval",
                        "evaluation.analysis.enabled=true",
                        "evaluation.nlg-judge.enabled=true"
                )
                .run(context -> assertThat(context.getStartupFailure())
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("동시에 true"));
    }

    @Test
    @DisplayName("NLG judge 비교 모드는 OpenAI 키와 비용 확인 없이 입력 파일과 출력 경로만 검증한다")
    void nlgJudgeComparisonModeDoesNotRequireOpenAiProperties() throws Exception {
        NlgEvaluationRunner runner = nlgRunnerWithProfiles("analysis-eval");
        Path first = tempDir.resolve("judge-a.csv");
        Path second = tempDir.resolve("judge-b.csv");
        Files.writeString(first, "caseId\nEV-01\n");
        Files.writeString(second, "caseId\nEV-02\n");
        ReflectionTestUtils.setField(runner, "compareInputPaths", first + "," + second);
        ReflectionTestUtils.setField(runner, "outputPath", tempDir.resolve("comparison.csv").toString());
        ReflectionTestUtils.setField(runner, "openAiApiKey", "");
        ReflectionTestUtils.setField(runner, "confirmOpenAiCost", false);
        ReflectionTestUtils.setField(runner, "analysisEvaluationEnabled", false);

        runner.validateComparisonProperties();
    }

    @Test
    @DisplayName("NLG judge 비교 모드는 output 절대 경로에 파일을 생성한 뒤 정상 종료한다")
    void nlgJudgeComparisonRunCreatesResolvedOutputCsv() throws Exception {
        NlgEvaluationBatchService batchService = mock(NlgEvaluationBatchService.class);
        EvaluationExitCoordinator exitCoordinator = mock(EvaluationExitCoordinator.class);
        Environment environment = mock(Environment.class);
        when(environment.getActiveProfiles()).thenReturn(new String[]{"analysis-eval"});
        Path first = tempDir.resolve("judge-a.csv");
        Path second = tempDir.resolve("judge-b.csv");
        Files.writeString(first, "caseId,failureStage\nEV-01,\n");
        Files.writeString(second, "caseId,failureStage\nEV-02,\n");
        Path output = tempDir.resolve("nested").resolve("comparison.csv");
        when(batchService.compare(any(), any())).thenAnswer(invocation -> {
            Path resolvedOutput = invocation.getArgument(1);
            Files.createDirectories(resolvedOutput.getParent());
            Files.writeString(resolvedOutput, "sourceResultFile,caseCount\njudge-a.csv,1\n");
            return new NlgEvaluationBatchService.NlgEvaluationComparisonSummary(
                    2,
                    resolvedOutput,
                    2,
                    Files.size(resolvedOutput)
            );
        });
        NlgEvaluationRunner runner = new NlgEvaluationRunner(batchService, exitCoordinator, environment);
        ReflectionTestUtils.setField(runner, "compareInputPaths", first + "," + second);
        ReflectionTestUtils.setField(runner, "outputPath", output.toString());
        ReflectionTestUtils.setField(runner, "analysisEvaluationEnabled", false);

        runner.run(new DefaultApplicationArguments());

        assertThat(output).isRegularFile();
        assertThat(Files.size(output)).isGreaterThan(0);
        var outputCaptor = org.mockito.ArgumentCaptor.forClass(Path.class);
        verify(batchService).compare(any(), outputCaptor.capture());
        assertThat(outputCaptor.getValue()).isEqualTo(output.toAbsolutePath().normalize());
        verify(exitCoordinator).exit("nlg-judge", 0);
    }

    @Test
    @DisplayName("분석 평가 Runner는 analysis-evaluation source로 정상 종료를 요청한다")
    void analysisEvaluationRunRequestsExitWithSource() throws Exception {
        EvaluationAnalysisBatchService batchService = mock(EvaluationAnalysisBatchService.class);
        EvaluationExitCoordinator exitCoordinator = mock(EvaluationExitCoordinator.class);
        Environment environment = mock(Environment.class);
        when(environment.getActiveProfiles()).thenReturn(new String[]{"analysis-eval"});
        Path input = tempDir.resolve("evaluation-cases.csv");
        Path output = tempDir.resolve("analysis-output.csv");
        Files.writeString(input, "caseId\nEV-01\n");
        when(batchService.run(input, output)).thenReturn(new EvaluationAnalysisBatchService.EvaluationBatchSummary(
                1,
                1,
                0,
                output
        ));
        EvaluationAnalysisRunner runner = new EvaluationAnalysisRunner(batchService, exitCoordinator, environment);
        ReflectionTestUtils.setField(runner, "inputPath", input.toString());
        ReflectionTestUtils.setField(runner, "outputPath", output.toString());
        ReflectionTestUtils.setField(runner, "openAiApiKey", "test-key");
        ReflectionTestUtils.setField(runner, "confirmOpenAiCost", true);
        ReflectionTestUtils.setField(runner, "analysisModel", "gpt-4o-mini");
        ReflectionTestUtils.setField(runner, "nlgJudgeEnabled", false);

        runner.run(new DefaultApplicationArguments());

        var order = inOrder(batchService, exitCoordinator);
        order.verify(batchService).run(input, output);
        order.verify(exitCoordinator).exit("analysis-evaluation", 0);
    }

    @Test
    @DisplayName("NLG judge 평가 모드는 run에 진입해 output CSV를 생성하고 정상 종료를 요청한다")
    void nlgJudgeRunCreatesOutputCsv() throws Exception {
        NlgEvaluationAiClient aiClient = mock(NlgEvaluationAiClient.class);
        when(aiClient.evaluate(any())).thenReturn(new NlgEvaluationAiClient.JudgeCallResult(
                new NlgEvaluationResponse(
                        "EV-01",
                        List.of(),
                        5,
                        5,
                        5,
                        5,
                        5,
                        5,
                        List.of(NlgEvaluationErrorCode.NONE),
                        "문제 없는 평가 결과입니다."
                ),
                10L,
                null,
                null
        ));
        NlgEvaluationBatchService batchService = org.mockito.Mockito.spy(
                new NlgEvaluationBatchService(aiClient, objectMapper)
        );
        EvaluationExitCoordinator exitCoordinator = mock(EvaluationExitCoordinator.class);
        Environment environment = mock(Environment.class);
        when(environment.getActiveProfiles()).thenReturn(new String[]{"analysis-eval"});
        NlgEvaluationRunner runner = new NlgEvaluationRunner(batchService, exitCoordinator, environment);
        Path input = writeNlgJudgeInput();
        Path output = tempDir.resolve("nlg-judge-output.csv");
        ReflectionTestUtils.setField(runner, "inputPath", input.toString());
        ReflectionTestUtils.setField(runner, "outputPath", output.toString());
        ReflectionTestUtils.setField(runner, "compareInputPaths", "");
        ReflectionTestUtils.setField(runner, "openAiApiKey", "test-key");
        ReflectionTestUtils.setField(runner, "confirmOpenAiCost", true);
        ReflectionTestUtils.setField(runner, "judgeModel", "gpt-4o-mini");
        ReflectionTestUtils.setField(runner, "analysisEvaluationEnabled", false);

        runner.run(new DefaultApplicationArguments());

        assertThat(output).isRegularFile();
        assertThat(Files.size(output)).isGreaterThan(0);
        var order = inOrder(batchService, exitCoordinator);
        order.verify(batchService).run(input, output);
        order.verify(exitCoordinator).exit("nlg-judge", 0);
    }

    @Test
    @DisplayName("NLG judge Runner는 output CSV가 없으면 성공 종료하지 않는다")
    void nlgJudgeRunFailsWhenOutputCsvIsMissing() throws Exception {
        NlgEvaluationBatchService batchService = mock(NlgEvaluationBatchService.class);
        EvaluationExitCoordinator exitCoordinator = mock(EvaluationExitCoordinator.class);
        Environment environment = mock(Environment.class);
        when(environment.getActiveProfiles()).thenReturn(new String[]{"analysis-eval"});
        Path input = writeNlgJudgeInput();
        Path output = tempDir.resolve("missing-output.csv");
        when(batchService.run(any(), any())).thenReturn(new NlgEvaluationBatchService.NlgEvaluationSummary(
                1,
                1,
                0,
                output
        ));
        NlgEvaluationRunner runner = new NlgEvaluationRunner(batchService, exitCoordinator, environment);
        ReflectionTestUtils.setField(runner, "inputPath", input.toString());
        ReflectionTestUtils.setField(runner, "outputPath", output.toString());
        ReflectionTestUtils.setField(runner, "compareInputPaths", "");
        ReflectionTestUtils.setField(runner, "openAiApiKey", "test-key");
        ReflectionTestUtils.setField(runner, "confirmOpenAiCost", true);
        ReflectionTestUtils.setField(runner, "judgeModel", "gpt-4o-mini");
        ReflectionTestUtils.setField(runner, "analysisEvaluationEnabled", false);

        assertThatThrownBy(() -> runner.run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("output CSV");
        verify(exitCoordinator).exit("nlg-judge", 1);
    }

    @Test
    @DisplayName("EvaluationExitCoordinator는 동일한 종료 요청을 한 번만 전달한다")
    void evaluationExitCoordinatorIgnoresDuplicateExitRequests() throws Exception {
        var applicationContext = mock(org.springframework.context.ConfigurableApplicationContext.class);
        AtomicInteger springExitCount = new AtomicInteger();
        AtomicInteger systemExitCount = new AtomicInteger();
        AtomicInteger systemExitCode = new AtomicInteger(-1);
        CountDownLatch exited = new CountDownLatch(1);
        EvaluationExitCoordinator coordinator = new EvaluationExitCoordinator(
                applicationContext,
                (context, exitCode) -> {
                    springExitCount.incrementAndGet();
                    return 17;
                },
                exitCode -> {
                    systemExitCount.incrementAndGet();
                    systemExitCode.set(exitCode);
                    exited.countDown();
                }
        );

        coordinator.exit("nlg-judge", 0);
        coordinator.exit("nlg-judge", 0);

        assertThat(exited.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(springExitCount).hasValue(1);
        assertThat(systemExitCount).hasValue(1);
        assertThat(systemExitCode).hasValue(17);
    }

    @Test
    @DisplayName("EvaluationExitCoordinator는 Spring 종료 실패 시에도 System.exit를 호출한다")
    void evaluationExitCoordinatorCallsSystemExitWhenSpringExitFails() throws Exception {
        var applicationContext = mock(org.springframework.context.ConfigurableApplicationContext.class);
        AtomicInteger systemExitCode = new AtomicInteger(-1);
        CountDownLatch exited = new CountDownLatch(1);
        EvaluationExitCoordinator coordinator = new EvaluationExitCoordinator(
                applicationContext,
                (context, exitCode) -> {
                    throw new IllegalStateException("shutdown failed");
                },
                exitCode -> {
                    systemExitCode.set(exitCode);
                    exited.countDown();
                }
        );

        coordinator.exit("nlg-judge", 1);

        assertThat(exited.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(systemExitCode).hasValue(1);
    }

    @Test
    @DisplayName("다른 profile에서는 두 Runner 모두 생성되지 않는다")
    void noRunnerIsCreatedOutsideAnalysisEvalProfile() {
        runnerContext()
                .withPropertyValues(
                        "spring.profiles.active=dev",
                        "evaluation.analysis.enabled=true",
                        "evaluation.nlg-judge.enabled=true"
                )
                .run(context -> {
                    assertThat(context).doesNotHaveBean(EvaluationAnalysisRunner.class);
                    assertThat(context).doesNotHaveBean(NlgEvaluationRunner.class);
                    assertThat(context.getBeanNamesForType(ApplicationRunner.class)).isEmpty();
                });
    }

    @Test
    @DisplayName("analysis-eval 설정은 DB DDL과 schema.sql 실행을 비활성화한다")
    void analysisEvalYamlDisablesDatabaseSideEffects() {
        Properties properties = loadAnalysisEvalProperties();

        assertThat(properties.getProperty("spring.main.web-application-type")).isEqualTo("none");
        assertThat(properties.getProperty("spring.autoconfigure.exclude[0]"))
                .isEqualTo("org.springframework.boot.devtools.autoconfigure.DevToolsDataSourceAutoConfiguration");
        assertThat(properties.getProperty("spring.devtools.add-properties")).isEqualTo("false");
        assertThat(properties.getProperty("spring.devtools.restart.enabled")).isEqualTo("false");
        assertThat(properties.getProperty("spring.sql.init.mode")).isEqualTo("never");
        assertThat(properties.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("none");
        assertThat(properties.getProperty("spring.jpa.defer-datasource-initialization")).isEqualTo("false");
        assertThat(properties.getProperty("spring.datasource.url"))
                .contains("EVAL_DB_URL")
                .contains("DB_CLOSE_ON_EXIT=FALSE")
                .doesNotContain("${DB_URL");
        assertThat(properties.getProperty("spring.mail.host")).isEqualTo("localhost");
        assertThat(properties.getProperty("spring.mail.port")).isEqualTo("2525");
        assertThat(properties.getProperty("spring.mail.username")).isEqualTo("dummy");
        assertThat(properties.getProperty("spring.security.oauth2.client.registration.google.client-id"))
                .isEqualTo("dummy-google-client-id");
        assertThat(properties.getProperty("spring.cloud.aws.region.static")).isEqualTo("ap-northeast-2");
        assertThat(properties.getProperty("spring.cloud.aws.s3.bucket")).isEqualTo("dummy-bucket");
        assertThat(properties.getProperty("app.admin.bootstrap-emails")).isEmpty();
        assertThat(properties.getProperty("app.corpus.import.run-on-startup")).isEqualTo("false");
        assertThat(properties.getProperty("app.corpus.embedding.sync-on-startup")).isEqualTo("false");
        assertThat(properties.getProperty("evaluation.analysis.enabled")).isEqualTo("false");
        assertThat(properties.getProperty("evaluation.nlg-judge.enabled")).isEqualTo("false");
        assertThat(properties.getProperty("payment.toss.client-key")).contains("dummy-evaluation-client-key");
    }

    @Test
    @DisplayName("CorpusAdminRunner는 analysis-eval profile에서 bean 등록되지 않는다")
    void corpusAdminRunnerIsExcludedFromAnalysisEvalProfile() {
        Profile profile = CorpusAdminRunner.class.getAnnotation(Profile.class);

        assertThat(profile).isNotNull();
        assertThat(profile.value()).containsExactly("!analysis-eval");
    }

    @Test
    @DisplayName("AsyncTaskSweepScheduler는 analysis-eval profile에서 bean 등록되지 않는다")
    void asyncTaskSweepSchedulerIsExcludedFromAnalysisEvalProfile() {
        Profile profile = AsyncTaskSweepScheduler.class.getAnnotation(Profile.class);

        assertThat(profile).isNotNull();
        assertThat(profile.value()).containsExactly("!analysis-eval");
    }

    private EvaluationAnalysisRunner runnerWithProfiles(String... profiles) {
        Environment environment = mock(Environment.class);
        when(environment.getActiveProfiles()).thenReturn(profiles);
        return new EvaluationAnalysisRunner(
                mock(EvaluationAnalysisBatchService.class),
                mock(EvaluationExitCoordinator.class),
                environment
        );
    }

    private NlgEvaluationRunner nlgRunnerWithProfiles(String... profiles) {
        Environment environment = mock(Environment.class);
        when(environment.getActiveProfiles()).thenReturn(profiles);
        return new NlgEvaluationRunner(
                mock(NlgEvaluationBatchService.class),
                mock(EvaluationExitCoordinator.class),
                environment
        );
    }

    private Properties loadAnalysisEvalProperties() {
        YamlPropertiesFactoryBean factoryBean = new YamlPropertiesFactoryBean();
        factoryBean.setResources(new ClassPathResource("application-analysis-eval.yaml"));
        Properties properties = factoryBean.getObject();
        return properties == null ? new Properties() : properties;
    }

    private Path writeNlgJudgeInput() throws Exception {
        Path input = tempDir.resolve("nlg-judge-input.csv");
        String rawLlmResponseJson = objectMapper.writeValueAsString(new AnalysisLlmResponse(
                70,
                60,
                65,
                "피드백",
                List.of(new AnalysisLlmResponse.HighlightItem("강점", "좋은 문장")),
                List.of(),
                List.of(),
                List.of()
        ));
        Files.writeString(
                input,
                "caseId,mainTasks,qualifications,preferences,question,answer,aiQuestionAnalysesJson,aiMissingKeywordsJson,rawLlmResponseJson\n"
                        + csv("EV-01") + ","
                        + csv("재고 분석") + ","
                        + csv("장애 대응 경험") + ","
                        + csv("SQL 우대") + ","
                        + csv("지원 동기") + ","
                        + csv("좋은 문장") + ","
                        + csv("[]") + ","
                        + csv("[]") + ","
                        + csv(rawLlmResponseJson) + "\n"
        );
        return input;
    }

    private String csv(String value) {
        String safeValue = value == null ? "" : value;
        if (safeValue.contains(",") || safeValue.contains("\"") || safeValue.contains("\n") || safeValue.contains("\r")) {
            return "\"" + safeValue.replace("\"", "\"\"") + "\"";
        }
        return safeValue;
    }

    private ApplicationContextRunner runnerContext() {
        return new ApplicationContextRunner()
                .withUserConfiguration(RunnerConditionTestConfig.class);
    }

    private ApplicationContextRunner scannedRunnerContext() {
        return new ApplicationContextRunner()
                .withUserConfiguration(ScannedRunnerConditionTestConfig.class);
    }

    @Configuration
    @Import({EvaluationAnalysisRunner.class, NlgEvaluationRunner.class, EvaluationRunnerFlagValidator.class})
    static class RunnerConditionTestConfig {
        @Bean
        EvaluationAnalysisBatchService evaluationAnalysisBatchService() {
            return mock(EvaluationAnalysisBatchService.class);
        }

        @Bean
        NlgEvaluationBatchService nlgEvaluationBatchService() {
            return mock(NlgEvaluationBatchService.class);
        }

        @Bean
        EvaluationExitCoordinator evaluationExitCoordinator() {
            return mock(EvaluationExitCoordinator.class);
        }
    }

    @Configuration
    @ComponentScan(
            basePackageClasses = NlgEvaluationRunner.class,
            useDefaultFilters = false,
            includeFilters = @ComponentScan.Filter(
                    type = FilterType.ASSIGNABLE_TYPE,
                    classes = {
                            EvaluationAnalysisRunner.class,
                            NlgEvaluationRunner.class,
                            EvaluationRunnerFlagValidator.class
                    }
            )
    )
    static class ScannedRunnerConditionTestConfig {
        @Bean
        EvaluationAnalysisBatchService evaluationAnalysisBatchService() {
            return mock(EvaluationAnalysisBatchService.class);
        }

        @Bean
        NlgEvaluationBatchService nlgEvaluationBatchService() {
            return mock(NlgEvaluationBatchService.class);
        }

        @Bean
        EvaluationExitCoordinator evaluationExitCoordinator() {
            return mock(EvaluationExitCoordinator.class);
        }
    }
}
