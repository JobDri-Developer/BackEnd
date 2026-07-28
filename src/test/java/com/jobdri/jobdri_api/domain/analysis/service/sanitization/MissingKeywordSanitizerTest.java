package com.jobdri.jobdri_api.domain.analysis.service.sanitization;

import com.jobdri.jobdri_api.domain.analysis.dto.llm.AnalysisCandidateResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MissingKeywordSanitizerTest {
    private static final String MAIN_TASKS = "재고 현황을 분석하고 적정 재고를 관리합니다. 장애 대응 경험을 활용합니다.";
    private static final String QUALIFICATIONS = "영어 고객 커뮤니케이션 경험과 Spring Boot API 개발 경험";

    @Test
    @DisplayName("기존 boolean sanitizer와 decision 기반 accepted 결과가 동일하다")
    void acceptedCandidatesMatchExistingValidationRules() {
        List<AnalysisCandidateResponse.MissingKeywordCandidate> candidates = List.of(
                candidate("재고 관리 및 분석 경험", "MAIN_TASK"),
                candidate("SQL 활용 경험", "MAIN_TASK"),
                candidate("영어 고객 커뮤니케이션 경험", "QUALIFICATION")
        );

        MissingKeywordSanitizationResult result = MissingKeywordSanitizer.sanitize(
                MAIN_TASKS,
                QUALIFICATIONS,
                "",
                candidates
        );

        List<AnalysisCandidateResponse.MissingKeywordCandidate> expected = candidates.stream()
                .filter(candidate -> AnalysisSanitizationRules.isValidMissingKeyword(
                        candidate.keyword(),
                        "MAIN_TASK".equals(candidate.source())
                                ? com.jobdri.jobdri_api.domain.analysis.dto.response.MissingKeywordSource.MAIN_TASK
                                : com.jobdri.jobdri_api.domain.analysis.dto.response.MissingKeywordSource.QUALIFICATION,
                        MAIN_TASKS,
                        QUALIFICATIONS
                ))
                .toList();
        assertThat(result.acceptedCandidates()).isEqualTo(expected);
    }

    @Test
    @DisplayName("빈 keyword는 BLANK_KEYWORD로 기록한다")
    void blankKeywordRejected() {
        MissingKeywordSanitizationResult result = sanitize(candidate(" ", "MAIN_TASK"));

        assertThat(result.decisions()).extracting(MissingKeywordSanitizationDecision::rejectionReason)
                .containsExactly(MissingKeywordRejectionReason.BLANK_KEYWORD);
    }

    @Test
    @DisplayName("중복 후보와 정규화 충돌 후보를 구분한다")
    void duplicateAndNormalizationCollisionRejected() {
        MissingKeywordSanitizationResult result = MissingKeywordSanitizer.sanitize(
                MAIN_TASKS,
                QUALIFICATIONS,
                "",
                List.of(
                        candidate("재고 관리 및 분석 경험", "MAIN_TASK"),
                        candidate("재고 관리 및 분석 경험", "MAIN_TASK"),
                        candidate("재고관리 및 분석 경험", "MAIN_TASK")
                )
        );

        assertThat(result.decisions()).extracting(MissingKeywordSanitizationDecision::rejectionReason)
                .containsExactly(
                        MissingKeywordRejectionReason.ACCEPTED,
                        MissingKeywordRejectionReason.DUPLICATE_KEYWORD,
                        MissingKeywordRejectionReason.NORMALIZATION_COLLISION
                );
        assertThat(result.decisions().get(1).duplicateOfCandidateIndex()).isZero();
        assertThat(result.decisions().get(2).duplicateOfCandidateIndex()).isZero();
    }

    @Test
    @DisplayName("정규화 후 답변에 존재하면 answerNormalizedMatch를 기록한다")
    void answerNormalizedMatchRecorded() {
        MissingKeywordSanitizationResult result = MissingKeywordSanitizer.sanitize(
                MAIN_TASKS,
                QUALIFICATIONS,
                "재고관리및분석경험을 쌓았습니다.",
                List.of(candidate("재고 관리 및 분석 경험", "MAIN_TASK"))
        );

        assertThat(result.decisions().getFirst().answerExactMatch()).isFalse();
        assertThat(result.decisions().getFirst().answerNormalizedMatch()).isTrue();
    }

    @Test
    @DisplayName("JD와 무관한 후보는 NOT_RELATED_TO_JD로 기록한다")
    void notRelatedToJdRejected() {
        MissingKeywordSanitizationResult result = sanitize(candidate("SQL 활용 경험", "MAIN_TASK"));

        assertThat(result.decisions()).extracting(MissingKeywordSanitizationDecision::rejectionReason)
                .containsExactly(MissingKeywordRejectionReason.NOT_RELATED_TO_JD);
    }

    @Test
    @DisplayName("일반어만 남는 후보는 TOO_GENERIC으로 기록한다")
    void genericKeywordRejected() {
        MissingKeywordSanitizationResult result = sanitize(candidate("직무 관련 경험", "MAIN_TASK"));

        assertThat(result.decisions()).extracting(MissingKeywordSanitizationDecision::rejectionReason)
                .containsExactly(MissingKeywordRejectionReason.TOO_GENERIC);
    }

    @Test
    @DisplayName("정형 자격요건 후보는 CERTIFICATE_OR_QUANTITATIVE_NOISE로 기록한다")
    void structuredQualificationRejected() {
        MissingKeywordSanitizationResult result = sanitize(candidate("TOEIC 공인성적", "QUALIFICATION"));

        assertThat(result.decisions()).extracting(MissingKeywordSanitizationDecision::rejectionReason)
                .containsExactly(MissingKeywordRejectionReason.CERTIFICATE_OR_QUANTITATIVE_NOISE);
    }

    @Test
    @DisplayName("유효 후보는 ACCEPTED로 기록한다")
    void validCandidateAccepted() {
        MissingKeywordSanitizationResult result = sanitize(candidate("장애 대응 경험", "MAIN_TASK"));

        assertThat(result.acceptedCandidates()).hasSize(1);
        assertThat(result.decisions()).extracting(MissingKeywordSanitizationDecision::rejectionReason)
                .containsExactly(MissingKeywordRejectionReason.ACCEPTED);
    }

    @Test
    @DisplayName("최초 실패 사유는 현재 검증 순서를 따른다")
    void firstFailureReasonFollowsValidationOrder() {
        MissingKeywordSanitizationResult result = sanitize(candidate("TOEIC 공인성적", "PREFERENCE"));

        assertThat(result.decisions()).extracting(MissingKeywordSanitizationDecision::rejectionReason)
                .containsExactly(MissingKeywordRejectionReason.UNSUPPORTED_KEYWORD);
    }

    private MissingKeywordSanitizationResult sanitize(AnalysisCandidateResponse.MissingKeywordCandidate candidate) {
        return MissingKeywordSanitizer.sanitize(MAIN_TASKS, QUALIFICATIONS, "", List.of(candidate));
    }

    private AnalysisCandidateResponse.MissingKeywordCandidate candidate(String keyword, String source) {
        return new AnalysisCandidateResponse.MissingKeywordCandidate(keyword, source, keyword);
    }
}
