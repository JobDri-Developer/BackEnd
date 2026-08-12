package com.jobdri.jobdri_api.domain.evaluation.analysis;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EvaluationAnalysisPackageDependencyTest {

    private static final Path EVALUATION_ANALYSIS_ROOT = Path.of(
            "src/main/java/com/jobdri/jobdri_api/domain/evaluation/analysis"
    );
    private static final List<String> FORBIDDEN_IMPORT_PREFIXES = List.of(
            "import com.jobdri.jobdri_api.domain.analysis.dto.",
            "import com.jobdri.jobdri_api.domain.analysis.service."
    );

    @Test
    @DisplayName("evaluation analysis 패키지는 runtime analysis dto/service에 직접 의존하지 않는다")
    void evaluationAnalysisPackageDoesNotDependOnRuntimeAnalysisDtoOrService() throws IOException {
        List<String> violations = new ArrayList<>();

        try (var paths = Files.walk(EVALUATION_ANALYSIS_ROOT)) {
            paths.filter(path -> path.toString().endsWith(".java"))
                    .forEach(path -> collectViolations(path, violations));
        }

        assertThat(violations).isEmpty();
    }

    private void collectViolations(Path file, List<String> violations) {
        try {
            List<String> lines = Files.readAllLines(file);
            for (String forbiddenPrefix : FORBIDDEN_IMPORT_PREFIXES) {
                boolean matched = lines.stream().anyMatch(line -> line.startsWith(forbiddenPrefix));
                if (matched) {
                    violations.add(file + " -> " + forbiddenPrefix);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to inspect file: " + file, e);
        }
    }
}
