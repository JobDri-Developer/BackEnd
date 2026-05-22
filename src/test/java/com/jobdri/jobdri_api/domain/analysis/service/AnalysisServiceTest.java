package com.jobdri.jobdri_api.domain.analysis.service;

import com.jobdri.jobdri_api.domain.analysis.dto.llm.AnalysisLlmResponse;
import com.jobdri.jobdri_api.domain.analysis.dto.response.AnalysisResponse;
import com.jobdri.jobdri_api.domain.analysis.entity.Analysis;
import com.jobdri.jobdri_api.domain.analysis.entity.Question;
import com.jobdri.jobdri_api.domain.analysis.repository.AnalysisRepository;
import com.jobdri.jobdri_api.domain.analysis.repository.QuestionAnalysisRepository;
import com.jobdri.jobdri_api.domain.analysis.repository.QuestionRepository;
import com.jobdri.jobdri_api.domain.classification.entity.Classification;
import com.jobdri.jobdri_api.domain.classification.entity.DetailClassification;
import com.jobdri.jobdri_api.domain.classification.entity.MiddleClassification;
import com.jobdri.jobdri_api.domain.classification.repository.ClassificationRepository;
import com.jobdri.jobdri_api.domain.classification.repository.DetailClassificationRepository;
import com.jobdri.jobdri_api.domain.company.entity.Company;
import com.jobdri.jobdri_api.domain.company.entity.CompanySize;
import com.jobdri.jobdri_api.domain.company.repository.CompanyRepository;
import com.jobdri.jobdri_api.domain.jobposting.entity.JobPosting;
import com.jobdri.jobdri_api.domain.jobposting.repository.JobPostingRepository;
import com.jobdri.jobdri_api.domain.mockapply.entity.ApplyType;
import com.jobdri.jobdri_api.domain.mockapply.entity.MockApply;
import com.jobdri.jobdri_api.domain.mockapply.entity.MockApplyStatus;
import com.jobdri.jobdri_api.domain.mockapply.repository.MockApplyRepository;
import com.jobdri.jobdri_api.domain.user.entity.User;
import com.jobdri.jobdri_api.domain.user.repository.UserRepository;
import com.jobdri.jobdri_api.global.apiPayload.code.GeneralErrorCode;
import com.jobdri.jobdri_api.global.apiPayload.exception.GeneralException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AnalysisServiceTest {

    @Autowired
    private AnalysisService analysisService;

    @Autowired
    private AnalysisRepository analysisRepository;

    @Autowired
    private QuestionAnalysisRepository questionAnalysisRepository;

    @Autowired
    private QuestionRepository questionRepository;

    @Autowired
    private MockApplyRepository mockApplyRepository;

    @Autowired
    private JobPostingRepository jobPostingRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private ClassificationRepository classificationRepository;

    @Autowired
    private DetailClassificationRepository detailClassificationRepository;

    @Autowired
    private UserRepository userRepository;

    @MockBean
    private AnalysisAiClient analysisAiClient;

    @Test
    @DisplayName("자소서 분석을 실행하고 결과와 문항 분석을 저장한다")
    void analyzeSavesAnalysis() {
        User user = saveUser("analysis-save@example.com");
        MockApply mockApply = saveMockApply(user);
        Question question = saveQuestion(mockApply, "지원 직무 경험을 작성해주세요.", "Spring Boot API를 개발했습니다.");
        when(analysisAiClient.analyze(any(), any())).thenReturn(new AnalysisLlmResponse(
                120,
                82,
                71,
                80,
                "직무 경험은 좋지만 성과 근거 보완이 필요합니다.",
                List.of(
                        new AnalysisLlmResponse.QuestionAnalysisItem(
                                question.getId(),
                                "Spring Boot API를 개발했습니다.",
                                "mentioned",
                                "성과 지표가 없어 구체성이 약합니다.",
                                "Spring Boot API를 개발해 응답 시간을 20% 개선했습니다."
                        )
                )
        ));

        AnalysisResponse response = analysisService.analyze(user, mockApply.getId());

        assertThat(response.status()).isEqualTo(MockApplyStatus.COMPLETED);
        assertThat(response.score()).isEqualTo(100);
        assertThat(response.jobFit()).isEqualTo(82);
        assertThat(response.impact()).isEqualTo(71);
        assertThat(response.completeness()).isEqualTo(80);
        assertThat(response.questions()).hasSize(1);
        assertThat(response.questions().get(0).analyses()).hasSize(1);
        assertThat(response.questions().get(0).analyses().get(0).status()).isEqualTo("mentioned");
        assertThat(response.questions().get(0).analyses().get(0).start()).isEqualTo(0);
        assertThat(response.questions().get(0).analyses().get(0).end())
                .isEqualTo("Spring Boot API를 개발했습니다.".length());
        assertThat(mockApply.getStatus()).isEqualTo(MockApplyStatus.COMPLETED);
        assertThat(analysisRepository.findByMockApplyId(mockApply.getId())).isPresent();
    }

    @Test
    @DisplayName("답변이 없는 경우 분석을 실행할 수 없다")
    void analyzeThrowsWhenNoAnswers() {
        User user = saveUser("analysis-empty-answer@example.com");
        MockApply mockApply = saveMockApply(user);
        saveQuestion(mockApply, "지원 동기", "");

        assertThatThrownBy(() -> analysisService.analyze(user, mockApply.getId()))
                .isInstanceOf(GeneralException.class)
                .extracting("code")
                .isEqualTo(GeneralErrorCode.INVALID_PARAMETER);
    }

    @Test
    @DisplayName("다른 사용자의 지원서는 분석할 수 없다")
    void analyzeThrowsWhenUserDoesNotOwnMockApply() {
        User owner = saveUser("analysis-owner@example.com");
        User other = saveUser("analysis-other@example.com");
        MockApply mockApply = saveMockApply(owner);
        saveQuestion(mockApply, "지원 동기", "백엔드 개발 경험이 있습니다.");

        assertThatThrownBy(() -> analysisService.analyze(other, mockApply.getId()))
                .isInstanceOf(GeneralException.class)
                .extracting("code")
                .isEqualTo(GeneralErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("LLM sentence가 원문에 없으면 문항 분석 저장에서 제외한다")
    void analyzeSkipsSentenceNotInAnswer() {
        User user = saveUser("analysis-skip-sentence@example.com");
        MockApply mockApply = saveMockApply(user);
        Question question = saveQuestion(mockApply, "문제 해결 경험", "장애 로그를 분석해 원인을 찾았습니다.");
        when(analysisAiClient.analyze(any(), any())).thenReturn(new AnalysisLlmResponse(
                64,
                70,
                55,
                67,
                "원문 매칭 실패 문장은 제외됩니다.",
                List.of(
                        new AnalysisLlmResponse.QuestionAnalysisItem(
                                question.getId(),
                                "답변에 없는 문장입니다.",
                                "fabricated",
                                "원문에 없습니다.",
                                "원문 기반 문장으로 개선해야 합니다."
                        )
                )
        ));

        AnalysisResponse response = analysisService.analyze(user, mockApply.getId());

        assertThat(response.questions().get(0).analyses()).isEmpty();
        Analysis analysis = analysisRepository.findByMockApplyId(mockApply.getId()).orElseThrow();
        assertThat(questionAnalysisRepository.findAllByAnalysisId(analysis.getId())).isEmpty();
    }

    @Test
    @DisplayName("재분석 시 기존 분석과 문항 분석을 새 결과로 교체한다")
    void analyzeReplacesExistingAnalysis() {
        User user = saveUser("analysis-replace@example.com");
        MockApply mockApply = saveMockApply(user);
        Question question = saveQuestion(mockApply, "성과 경험", "가입 완료율을 개선했습니다. API 응답 속도를 개선했습니다.");
        when(analysisAiClient.analyze(any(), any()))
                .thenReturn(new AnalysisLlmResponse(
                        60,
                        61,
                        62,
                        63,
                        "첫 번째 분석",
                        List.of(new AnalysisLlmResponse.QuestionAnalysisItem(
                                question.getId(),
                                "가입 완료율을 개선했습니다.",
                                "mentioned",
                                "수치가 부족합니다.",
                                "가입 완료율을 12% 개선했습니다."
                        ))
                ))
                .thenReturn(new AnalysisLlmResponse(
                        88,
                        89,
                        90,
                        91,
                        "두 번째 분석",
                        List.of(new AnalysisLlmResponse.QuestionAnalysisItem(
                                question.getId(),
                                "API 응답 속도를 개선했습니다.",
                                "proven",
                                "성과 기준이 더 필요합니다.",
                                "API 응답 속도를 300ms 단축했습니다."
                        ))
                ));

        AnalysisResponse first = analysisService.analyze(user, mockApply.getId());
        AnalysisResponse second = analysisService.analyze(user, mockApply.getId());

        assertThat(second.analysisId()).isNotEqualTo(first.analysisId());
        assertThat(second.score()).isEqualTo(88);
        assertThat(second.feedback()).isEqualTo("두 번째 분석");
        assertThat(second.questions().get(0).analyses().get(0).status()).isEqualTo("proven");
        assertThat(analysisRepository.findByMockApplyId(mockApply.getId()).orElseThrow().getScore()).isEqualTo(88);
        assertThat(questionAnalysisRepository.findAllByAnalysisId(second.analysisId())).hasSize(1);
        assertThat(questionAnalysisRepository.findAllByAnalysisId(first.analysisId())).isEmpty();
    }

    @Test
    @DisplayName("저장된 분석 결과를 조회한다")
    void getAnalysis() {
        User user = saveUser("analysis-get@example.com");
        MockApply mockApply = saveMockApply(user);
        Question question = saveQuestion(mockApply, "지원 동기", "서비스 개선 경험이 있습니다.");
        when(analysisAiClient.analyze(any(), any())).thenReturn(new AnalysisLlmResponse(
                75,
                76,
                77,
                78,
                "저장된 분석 결과입니다.",
                List.of(new AnalysisLlmResponse.QuestionAnalysisItem(
                        question.getId(),
                        "서비스 개선 경험이 있습니다.",
                        "mentioned",
                        "구체성이 조금 부족합니다.",
                        "서비스 개선 경험으로 전환율을 10% 높였습니다."
                ))
        ));
        AnalysisResponse saved = analysisService.analyze(user, mockApply.getId());

        AnalysisResponse response = analysisService.getAnalysis(user, mockApply.getId());

        assertThat(response.analysisId()).isEqualTo(saved.analysisId());
        assertThat(response.score()).isEqualTo(75);
        assertThat(response.questions()).hasSize(1);
        assertThat(response.questions().get(0).analyses()).hasSize(1);
    }

    private User saveUser(String email) {
        return userRepository.save(User.signup("테스트 사용자", email, "encoded-password"));
    }

    private MockApply saveMockApply(User user) {
        JobPosting jobPosting = saveJobPosting(user);
        return mockApplyRepository.save(MockApply.create(user, jobPosting, ApplyType.ACTUAL));
    }

    private Question saveQuestion(MockApply mockApply, String content, String answer) {
        return questionRepository.save(Question.create(mockApply, content, 1000, answer));
    }

    private JobPosting saveJobPosting(User user) {
        Company company = companyRepository.save(Company.create("분석 테스트 기업", CompanySize.MEDIUM));
        DetailClassification detailClassification = saveDetailClassification();
        return jobPostingRepository.save(JobPosting.create(
                user,
                company,
                detailClassification,
                "주요 업무",
                "자격 요건",
                "우대 사항"
        ));
    }

    private DetailClassification saveDetailClassification() {
        Classification classification = Classification.create("분석 테스트 대분류 " + System.nanoTime());
        MiddleClassification middleClassification = classification.addMiddleClassification("분석 테스트 중분류");
        DetailClassification detailClassification = middleClassification.addDetailClassification("분석 테스트 소분류");
        classificationRepository.save(classification);
        return detailClassificationRepository.findById(detailClassification.getId()).orElseThrow();
    }
}
