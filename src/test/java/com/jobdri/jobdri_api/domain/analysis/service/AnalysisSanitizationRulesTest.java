package com.jobdri.jobdri_api.domain.analysis.service;

import com.jobdri.jobdri_api.domain.analysis.dto.response.MissingKeywordSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AnalysisSanitizationRulesTest {

    @Test
    @DisplayName("PROVEN 모순 reason은 결핍 구문 기준으로만 판단한다")
    void detectsContradictoryProvenReasonByDefectPhrase() {
        assertThat(AnalysisSanitizationRules.isContradictoryProvenReason("필요한 역량을 보여줍니다."))
                .isFalse();
        assertThat(AnalysisSanitizationRules.isContradictoryProvenReason("성과 수치가 부족합니다."))
                .isTrue();
        assertThat(AnalysisSanitizationRules.isContradictoryProvenReason("추가 보완이 필요합니다."))
                .isTrue();
    }

    @Test
    @DisplayName("unsafe improvement를 제거한다")
    void removesUnsafeImprovement() {
        String answer = "API 응답 속도를 개선했습니다. 장애 로그를 분석했습니다.";

        assertThat(AnalysisSanitizationRules.normalizeImprovement(
                "API 응답 속도를 개선했습니다.",
                answer,
                "API 응답 속도를 개선했습니다.",
                false
        )).isEmpty();
        assertThat(AnalysisSanitizationRules.normalizeImprovement(
                "API 응답 속도를 개선했습니다.",
                answer,
                "장애 로그를 분석했습니다.",
                false
        )).isEmpty();
        assertThat(AnalysisSanitizationRules.normalizeImprovement(
                "API 응답 속도를 개선했습니다.",
                answer,
                "성과를 강조했습니다.",
                false
        )).isEmpty();
        assertThat(AnalysisSanitizationRules.normalizeImprovement(
                "API 응답 속도를 개선했습니다.",
                answer,
                "입사 후 API 운영 안정화에 기여하겠습니다.",
                false
        )).isEmpty();
    }

    @Test
    @DisplayName("정형 자격요건은 제외하고 경험형 키워드는 유지한다")
    void filtersStructuredQualificationKeywordsConservatively() {
        assertThat(AnalysisSanitizationRules.isStructuredQualificationKeyword("영어 공인성적")).isTrue();
        assertThat(AnalysisSanitizationRules.isStructuredQualificationKeyword("영어 커뮤니케이션 경험")).isFalse();
        assertThat(AnalysisSanitizationRules.isStructuredQualificationKeyword("반도체 관련 전공")).isTrue();
        assertThat(AnalysisSanitizationRules.isStructuredQualificationKeyword("반도체 공정 분석 경험")).isFalse();
        assertThat(AnalysisSanitizationRules.isStructuredQualificationKeyword("경력 3년 이상")).isTrue();
        assertThat(AnalysisSanitizationRules.isStructuredQualificationKeyword("장애 대응 경험")).isFalse();
    }

    @Test
    @DisplayName("missingKeyword는 source JD 영역의 핵심 토큰 근거로 검증한다")
    void validatesMissingKeywordByCoreTokens() {
        assertThat(AnalysisSanitizationRules.isValidMissingKeyword(
                "재고 관리 및 분석 경험",
                MissingKeywordSource.MAIN_TASK,
                "재고 현황을 분석하고 적정 재고를 관리",
                ""
        )).isTrue();
        assertThat(AnalysisSanitizationRules.isValidMissingKeyword(
                "SQL 활용 경험",
                MissingKeywordSource.QUALIFICATION,
                "재고 현황을 분석하고 적정 재고를 관리",
                "Spring Boot API 개발 경험"
        )).isFalse();
        assertThat(AnalysisSanitizationRules.isValidMissingKeyword(
                "영어 커뮤니케이션 경험",
                MissingKeywordSource.QUALIFICATION,
                "",
                "영어 커뮤니케이션 경험"
        )).isTrue();
        assertThat(AnalysisSanitizationRules.isValidMissingKeyword(
                "온라인 쇼핑몰 근무 경험자",
                MissingKeywordSource.PREFERENCE,
                "고객 데이터 분석",
                "고객 데이터 분석"
        )).isFalse();
    }
}
