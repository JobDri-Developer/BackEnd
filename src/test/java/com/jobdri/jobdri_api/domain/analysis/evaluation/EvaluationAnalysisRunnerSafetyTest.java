package com.jobdri.jobdri_api.domain.analysis.evaluation;

import com.jobdri.jobdri_api.domain.corpus.service.CorpusAdminRunner;
import com.jobdri.jobdri_api.global.scheduling.AsyncTaskSweepScheduler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EvaluationAnalysisRunnerSafetyTest {

    @TempDir
    Path tempDir;

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
    @DisplayName("NLG judge 활성화 시 기본 평가 Runner는 실행되지 않는다")
    void evaluationRunnerIsDisabledWhenNlgJudgeIsEnabled() {
        ConditionalOnProperty condition = EvaluationAnalysisRunner.class.getAnnotation(ConditionalOnProperty.class);

        assertThat(condition).isNotNull();
        assertThat(condition.name()).containsExactly("evaluation.nlg-judge.enabled");
        assertThat(condition.havingValue()).isEqualTo("false");
        assertThat(condition.matchIfMissing()).isTrue();
    }

    @Test
    @DisplayName("NLG judge Runner는 명시적으로 활성화한 경우에만 실행된다")
    void nlgJudgeRunnerRequiresEnabledFlag() {
        ConditionalOnProperty condition = NlgEvaluationRunner.class.getAnnotation(ConditionalOnProperty.class);

        assertThat(condition).isNotNull();
        assertThat(condition.name()).containsExactly("evaluation.nlg-judge.enabled");
        assertThat(condition.havingValue()).isEqualTo("true");
        assertThat(condition.matchIfMissing()).isFalse();
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
                mock(ConfigurableApplicationContext.class),
                environment
        );
    }

    private NlgEvaluationRunner nlgRunnerWithProfiles(String... profiles) {
        Environment environment = mock(Environment.class);
        when(environment.getActiveProfiles()).thenReturn(profiles);
        return new NlgEvaluationRunner(
                mock(NlgEvaluationBatchService.class),
                mock(ConfigurableApplicationContext.class),
                environment
        );
    }

    private Properties loadAnalysisEvalProperties() {
        YamlPropertiesFactoryBean factoryBean = new YamlPropertiesFactoryBean();
        factoryBean.setResources(new ClassPathResource("application-analysis-eval.yaml"));
        Properties properties = factoryBean.getObject();
        return properties == null ? new Properties() : properties;
    }
}
