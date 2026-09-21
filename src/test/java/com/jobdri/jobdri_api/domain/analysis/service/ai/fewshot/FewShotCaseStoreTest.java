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
    @DisplayName("운영 검수 소스를 켜면 평가 소스와 분리해 승인 후보를 적재한다")
    void loadsReviewedProductionCasesIndependently() {
        FewShotProperties properties = new FewShotProperties();
        properties.getSource().setFixedEnabled(false);
        properties.getSource().setCuratedEnabled(false);
        properties.getSource().setReviewedEvaluationEnabled(false);
        properties.getSource().setReviewedProductionEnabled(true);
        properties.setReviewedProductionResource(
                "analysis/fewshot/reviewed-fewshot-cases-pm-20260914-v2.json"
        );

        var loaded = new FewShotCaseStore(new FewShotPromptProvider(), properties, new ObjectMapper())
                .loadActiveCases();

        assertThat(loaded).hasSize(5);
        assertThat(loaded)
                .extracting(FewShotCase::source)
                .containsOnly(FewShotSource.REVIEWED_PRODUCTION);
        assertThat(loaded)
                .extracting(FewShotCase::id)
                .containsExactly("FS-02", "FS-03", "FS-05", "FS-08", "FS-09");
    }

    @Test
    void rejectsAmbiguousCsvHeadersAndMalformedRows() throws Exception {
        String header = "caseId,mainTasks,question,sanitizedAnswer,approvedAnalysisJson,fewShotEnabled,reviewStatus";
        var properties = new FewShotProperties();
        properties.getSource().setFixedEnabled(false);
        properties.getSource().setCuratedEnabled(false);
        properties.getSource().setReviewedEvaluationEnabled(true);
        properties.setReviewedEvaluationResource("");
        Path csv = tempDir.resolve("malformed.csv");
        properties.setReviewedEvaluationCsvPath(csv.toString());
        var store = new FewShotCaseStore(new FewShotPromptProvider(), properties, new ObjectMapper());
        for (String content : java.util.List.of(
                header + ",caseId\nEV-01,API,경험,답변,{},true,APPROVED,EV-02",
                header + "\nEV-01,API,경험,답변,{},true,APPROVED,extra",
                header + "\nEV-01,API,경험,\"unclosed")) {
            Files.writeString(csv, content);
            assertThat(store.loadActiveCases()).isEmpty();
        }
    }

    @Test
    @DisplayName("무효 행은 ID를 선점하지 않고 같은 ID의 첫 유효 행만 적재한다")
    void retainsFirstValidRowAfterInvalidRowsWithSameCaseId() throws Exception {
        Path csv = tempDir.resolve("duplicate-id.csv");
        Files.writeString(csv, """
                caseId,mainTasks,question,sanitizedAnswer,approvedAnalysisJson,fewShotEnabled,reviewStatus
                EV-01,API 개발,경험,비활성 답변,"{""keyStrengths"":[],""missingKeywords"":[],""questionAnalyses"":[]}",false,APPROVED
                EV-01,API 개발,경험,미승인 답변,"{""keyStrengths"":[],""missingKeywords"":[],""questionAnalyses"":[]}",true,IN_REVIEW
                EV-01,API 개발,경험,,"{""keyStrengths"":[],""missingKeywords"":[],""questionAnalyses"":[]}",true,APPROVED
                EV-01,API 개발,경험,잘못된 JSON 답변,{,true,APPROVED
                EV-01,API 개발,경험,배열 JSON 답변,[],true,APPROVED
                EV-01,API 개발,경험,첫 유효 답변,"{""keyStrengths"":[],""missingKeywords"":[],""questionAnalyses"":[]}",true,APPROVED
                EV-01,API 개발,경험,중복 유효 답변,"{""keyStrengths"":[],""missingKeywords"":[],""questionAnalyses"":[]}",true,APPROVED
                """);
        FewShotProperties properties = new FewShotProperties();
        properties.getSource().setFixedEnabled(false);
        properties.getSource().setCuratedEnabled(false);
        properties.getSource().setReviewedEvaluationEnabled(true);
        properties.setReviewedEvaluationResource("");
        properties.setReviewedEvaluationCsvPath(csv.toString());

        var loaded = new FewShotCaseStore(new FewShotPromptProvider(), properties, new ObjectMapper())
                .loadActiveCases();

        assertThat(loaded).extracting(FewShotCase::id).containsExactly("EV-01");
        assertThat(loaded.getFirst().sanitizedAnswer()).isEqualTo("첫 유효 답변");
    }

    @Test
    void duplicateInputDoesNotReserveIdOfLaterDistinctInput() throws Exception {
        Path csv = tempDir.resolve("duplicate-input.csv");
        String analysis = "\"{\"\"keyStrengths\"\":[],\"\"missingKeywords\"\":[],\"\"questionAnalyses\"\":[]}\"";
        Files.writeString(csv,
                "caseId,mainTasks,question,sanitizedAnswer,approvedAnalysisJson,fewShotEnabled,reviewStatus\n"
                        + "FIRST,API,경험,답변," + analysis + ",true,APPROVED\n"
                        + "SECOND,API,경험, 답변 ," + analysis + ",true,APPROVED\n"
                        + "SECOND,API,경험,다른 답변," + analysis + ",true,APPROVED\n");
        var properties = new FewShotProperties();
        properties.getSource().setFixedEnabled(false);
        properties.getSource().setCuratedEnabled(false);
        properties.getSource().setReviewedEvaluationEnabled(true);
        properties.setReviewedEvaluationResource("");
        properties.setReviewedEvaluationCsvPath(csv.toString());
        var loaded = new FewShotCaseStore(new FewShotPromptProvider(), properties, new ObjectMapper()).loadActiveCases();
        assertThat(loaded).extracting(FewShotCase::id).containsExactly("FIRST", "SECOND");
        assertThat(loaded.getLast().sanitizedAnswer()).isEqualTo("다른 답변");
    }

    @Test
    void preservesOptionalJdSectionsAndSkipsMalformedJsonPerRow() throws Exception {
        Path csv = tempDir.resolve("optional-jd.csv");
        Files.writeString(csv, """
                caseId,jobCategorySmall,jobTitle,mainTasks,qualifications,preferences,question,sanitizedAnswer,approvedAnalysisJson,fewShotEnabled,reviewStatus
                FS-05,PA,Project Assistant,,협업 능력,Adobe,경험,답변,"{""keyStrengths"":[],""missingKeywords"":[],""questionAnalyses"":[]}",true,APPROVED
                FS-09,BX,Brand Designer,IP 관리,,,경험,답변,"{""keyStrengths"":[],""missingKeywords"":[],""questionAnalyses"":[]}",true,APPROVED
                PREF,디자인,,, ,Adobe,경험,답변,"{""keyStrengths"":[],""missingKeywords"":[],""questionAnalyses"":[]}",true,APPROVED
                EMPTY,디자인,,,,,경험,답변,"{""keyStrengths"":[],""missingKeywords"":[],""questionAnalyses"":[]}",true,APPROVED
                BROKEN,디자인,,IP 관리,,,경험,답변,{,true,APPROVED
                ARRAY,디자인,,IP 관리,,,경험,답변,[],true,APPROVED
                TRAILING,디자인,,IP 관리,,,경험,답변,{} garbage,true,APPROVED
                LAST,개발,,API 개발,,,경험,답변,"{""keyStrengths"":[],""missingKeywords"":[],""questionAnalyses"":[]}",true,APPROVED
                """);
        FewShotProperties properties = new FewShotProperties();
        properties.getSource().setFixedEnabled(false);
        properties.getSource().setCuratedEnabled(false);
        properties.getSource().setReviewedEvaluationEnabled(true);
        properties.setReviewedEvaluationResource("");
        properties.setReviewedEvaluationCsvPath(csv.toString());
        var loaded = new FewShotCaseStore(new FewShotPromptProvider(), properties, new ObjectMapper())
                .loadActiveCases();
        assertThat(loaded).extracting(FewShotCase::id).containsExactly("FS-05", "FS-09", "PREF", "LAST");
        assertThat(loaded.getFirst().jobTitle()).isEqualTo("Project Assistant");
        assertThat(loaded.getFirst().mainTasks()).isEmpty();
        assertThat(loaded.getFirst().promptBlock()).contains("- preference: Adobe");
        assertThat(loaded.get(1).qualifications()).isEmpty();
        assertThat(loaded.getLast().jobTitle()).isEqualTo("개발");
    }

    @Test
    @DisplayName("reviewed evaluation CSV는 승인, 활성, 비식별 답변, 승인 분석이 있는 행만 후보로 적재한다")
    void loadsOnlyApprovedReviewedEvaluationRows() throws Exception {
        Path csv = tempDir.resolve("reviewed.csv");
        Files.writeString(csv, """
                caseId,jobCategorySmall,mainTasks,qualifications,question,answer,sanitizedAnswer,approvedAnalysisJson,fewShotEnabled,reviewStatus,fewShotPriority,fewShotTags
                EV-01,백엔드,API 개발,Spring Boot,직무 경험,원본 답변,비식별 답변,"{""keyStrengths"":[],""missingKeywords"":[],""questionAnalyses"":[]}",true,APPROVED,100,"spring,api"
                EV-02,백엔드,API 개발,Spring Boot,직무 경험,원본 답변,,"{""keyStrengths"":[],""missingKeywords"":[],""questionAnalyses"":[]}",true,APPROVED,100,spring
                EV-03,백엔드,API 개발,Spring Boot,직무 경험,원본 답변,비식별 답변,"{""keyStrengths"":[],""missingKeywords"":[],""questionAnalyses"":[]}",false,APPROVED,100,spring
                EV-04,백엔드,API 개발,Spring Boot,직무 경험,원본 답변,비식별 답변,"{""keyStrengths"":[],""missingKeywords"":[],""questionAnalyses"":[]}",true,IN_REVIEW,100,spring
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
