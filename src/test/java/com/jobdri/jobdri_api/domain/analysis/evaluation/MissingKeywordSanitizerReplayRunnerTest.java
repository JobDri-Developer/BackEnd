package com.jobdri.jobdri_api.domain.analysis.evaluation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class MissingKeywordSanitizerReplayRunnerTest {
    @TempDir
    Path tempDir;

    @Test
    @DisplayName("정규화된 input과 output 경로가 같으면 fail-fast 한다")
    void rejectsSameNormalizedInputAndOutputPath() throws Exception {
        Path input = tempDir.resolve("input.csv");
        Files.writeString(input, "caseId\n");
        MissingKeywordSanitizerReplayRunner runner = runner(
                input.toString(),
                tempDir.resolve(".").resolve("input.csv").toString(),
                tempDir.resolve("review.csv").toString()
        );

        assertThatThrownBy(runner::validateExecutionProperties)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("evaluation.missing-keyword-replay.input")
                .hasMessageContaining("output")
                .hasMessageContaining("서로 다른 파일");
    }

    @Test
    @DisplayName("정규화된 output과 review-output 경로가 같으면 fail-fast 한다")
    void rejectsSameNormalizedOutputAndReviewOutputPath() throws Exception {
        Path input = tempDir.resolve("input.csv");
        Files.writeString(input, "caseId\n");
        MissingKeywordSanitizerReplayRunner runner = runner(
                input.toString(),
                tempDir.resolve("result.csv").toString(),
                tempDir.resolve(".").resolve("result.csv").toString()
        );

        assertThatThrownBy(runner::validateExecutionProperties)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("evaluation.missing-keyword-replay.output")
                .hasMessageContaining("review-output")
                .hasMessageContaining("서로 다른 파일");
    }

    private MissingKeywordSanitizerReplayRunner runner(
            String input,
            String output,
            String reviewOutput
    ) {
        MissingKeywordSanitizerReplayRunner runner = new MissingKeywordSanitizerReplayRunner(
                mock(MissingKeywordSanitizerReplayService.class),
                mock(EvaluationExitCoordinator.class),
                new MockEnvironment()
        );
        ReflectionTestUtils.setField(runner, "inputPath", input);
        ReflectionTestUtils.setField(runner, "outputPath", output);
        ReflectionTestUtils.setField(runner, "reviewOutputPath", reviewOutput);
        return runner;
    }
}
