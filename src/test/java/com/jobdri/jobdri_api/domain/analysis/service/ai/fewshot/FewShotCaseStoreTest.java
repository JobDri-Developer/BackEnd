package com.jobdri.jobdri_api.domain.analysis.service.ai.fewshot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobdri.jobdri_api.domain.analysis.service.ai.FewShotPromptProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class FewShotCaseStoreTest {
    @TempDir
    Path tempDir;

    @Test
    @DisplayName("reviewed evaluation CSV는 승인, 활성, 비식별 답변, 승인 분석이 있는 행만 후보로 적재한다")
    void loadsOnlyApprovedReviewedEvaluationRows() throws Exception {
        Path csv = tempDir.resolve("reviewed.csv");
        Files.writeString(csv, """
                caseId,jobCategorySmall,mainTasks,qualifications,question,answer,sanitizedAnswer,approvedAnalysisJson,fewShotEnabled,reviewStatus,fewShotPriority,fewShotTags
                EV-01,백엔드,API 개발,Spring Boot,직무 경험,원본 답변,비식별 답변,"{""questionAnalyses"":[]}",true,APPROVED,100,"spring,api"
                EV-02,백엔드,API 개발,Spring Boot,직무 경험,원본 답변,,"{""questionAnalyses"":[]}",true,APPROVED,100,spring
                EV-03,백엔드,API 개발,Spring Boot,직무 경험,원본 답변,비식별 답변,"{""questionAnalyses"":[]}",false,APPROVED,100,spring
                EV-04,백엔드,API 개발,Spring Boot,직무 경험,원본 답변,비식별 답변,"{""questionAnalyses"":[]}",true,IN_REVIEW,100,spring
                """);
        FewShotProperties properties = new FewShotProperties();
        properties.getSource().setFixedEnabled(false);
        properties.getSource().setCuratedEnabled(false);
        properties.getSource().setReviewedEvaluationEnabled(true);
        properties.setReviewedEvaluationResource("");
        properties.setReviewedEvaluationCsvPath(csv.toString());

        FewShotCaseStore store = new FewShotCaseStore(new FewShotPromptProvider(), properties, new ObjectMapper());

        assertThat(store.loadActiveCases())
                .hasSize(1)
                .first()
                .satisfies(fewShotCase -> {
                    assertThat(fewShotCase.id()).isEqualTo("EV-01");
                    assertThat(fewShotCase.source()).isEqualTo(FewShotSource.REVIEWED_EVALUATION);
                    assertThat(fewShotCase.sanitizedAnswer()).isEqualTo("비식별 답변");
                    assertThat(fewShotCase.promptBlock()).contains("비식별 답변").doesNotContain("원본 답변");
                });
    }

    @Test
    @DisplayName("기존 evaluation_cases_reviewed 형식처럼 승인 컬럼이 없으면 자동 few-shot 후보로 편입하지 않는다")
    void doesNotAutoPromoteReviewedEvaluationCsvWithoutApprovalColumns() throws Exception {
        Path csv = tempDir.resolve("reviewed.csv");
        Files.writeString(csv, """
                caseId,jobCategorySmall,mainTasks,qualifications,question,answer
                EV-01,백엔드,API 개발,Spring Boot,직무 경험,원본 답변
                """);
        FewShotProperties properties = new FewShotProperties();
        properties.getSource().setFixedEnabled(false);
        properties.getSource().setCuratedEnabled(false);
        properties.getSource().setReviewedEvaluationEnabled(true);
        properties.setReviewedEvaluationResource("");
        properties.setReviewedEvaluationCsvPath(csv.toString());

        FewShotCaseStore store = new FewShotCaseStore(new FewShotPromptProvider(), properties, new ObjectMapper());

        assertThat(store.loadActiveCases()).isEmpty();
    }
}
