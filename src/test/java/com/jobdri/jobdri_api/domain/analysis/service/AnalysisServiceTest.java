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
import com.jobdri.jobdri_api.domain.payment.entity.CreditTransactionType;
import com.jobdri.jobdri_api.domain.payment.repository.CreditTransactionRepository;
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

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
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

    @Autowired
    private CreditTransactionRepository creditTransactionRepository;

    @MockBean
    private AnalysisAiClient analysisAiClient;

    @Test
    @DisplayName("자소서 분석을 실행하고 결과와 문항 분석을 저장한다")
    void analyzeSavesAnalysis() {
        User user = saveUser("analysis-save@example.com");
        MockApply mockApply = saveMockApply(user);
        Question question = saveQuestion(mockApply, "지원 직무 경험을 작성해주세요.", "Spring Boot API를 개발했습니다.");
        int initialCredit = userRepository.findById(user.getId()).orElseThrow().getCredit();
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
        assertThat(response.sequence()).isEqualTo(1);
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
        assertThat(mockApplyRepository.findById(mockApply.getId()).orElseThrow().getStatus())
                .isEqualTo(MockApplyStatus.COMPLETED);
        assertThat(analysisRepository.findByMockApplyId(mockApply.getId())).isPresent();
        assertThat(userRepository.findById(user.getId()).orElseThrow().getCredit()).isEqualTo(initialCredit - 1);
        assertThat(creditTransactionRepository.findAllByUserIdAndTypeOrderByCreatedAtDescIdDesc(
                user.getId(),
                CreditTransactionType.USE
        )).hasSize(1);
    }

    @Test
    @DisplayName("분석 응답은 같은 공고 기준 현재 지원 순번을 반환한다")
    void analyzeReturnsSequence() {
        User user = saveUser("analysis-sequence@example.com");
        JobPosting jobPosting = saveJobPosting(user);
        mockApplyRepository.save(MockApply.create(user, jobPosting, ApplyType.ACTUAL));
        MockApply secondMockApply = mockApplyRepository.save(MockApply.create(user, jobPosting, ApplyType.ACTUAL));
        Question question = saveQuestion(secondMockApply, "재지원 분석 문항입니다.", "Spring Boot API를 개발했습니다.");
        when(analysisAiClient.analyze(any(), any())).thenReturn(new AnalysisLlmResponse(
                80,
                81,
                82,
                83,
                "재지원 분석입니다.",
                List.of(new AnalysisLlmResponse.QuestionAnalysisItem(
                        question.getId(),
                        "Spring Boot API를 개발했습니다.",
                        "mentioned",
                        "성과 지표가 부족합니다.",
                        "Spring Boot API를 개발해 응답 시간을 개선했습니다."
                ))
        ));

        AnalysisResponse response = analysisService.analyze(user, secondMockApply.getId());

        assertThat(response.mockApplyId()).isEqualTo(secondMockApply.getId());
        assertThat(response.sequence()).isEqualTo(2);
    }

    @Test
    @DisplayName("LLM 분석 실패 시 크레딧 차감과 분석 저장을 롤백한다")
    void analyzeRollsBackCreditWhenLlmFails() {
        User user = saveUser("analysis-credit-rollback@example.com");
        MockApply mockApply = saveMockApply(user);
        saveQuestion(mockApply, "지원 직무 경험을 작성해주세요.", "Spring Boot API를 개발했습니다.");
        int initialCredit = userRepository.findById(user.getId()).orElseThrow().getCredit();
        when(analysisAiClient.analyze(any(), any())).thenThrow(new RuntimeException("LLM timeout"));

        assertThatThrownBy(() -> analysisService.analyze(user, mockApply.getId()))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("LLM timeout");

        assertThat(userRepository.findById(user.getId()).orElseThrow().getCredit()).isEqualTo(initialCredit);
        assertThat(analysisRepository.findByMockApplyId(mockApply.getId())).isEmpty();
        assertThat(creditTransactionRepository.findAllByUserIdOrderByCreatedAtDescIdDesc(user.getId())).isEmpty();
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
    @DisplayName("LLM improvement가 첨삭 지시문이면 빈 값으로 저장한다")
    void analyzeNormalizesInstructionLikeImprovement() {
        User user = saveUser("analysis-instruction-improvement@example.com");
        MockApply mockApply = saveMockApply(user);
        Question question = saveQuestion(mockApply, "문제 해결 경험", "저는 로그를 확인하고 쿼리 실행 계획을 분석했습니다.");
        when(analysisAiClient.analyze(any(), any())).thenReturn(new AnalysisLlmResponse(
                64,
                70,
                55,
                67,
                "개선 예시 문장 검증입니다.",
                List.of(new AnalysisLlmResponse.QuestionAnalysisItem(
                        question.getId(),
                        "저는 로그를 확인하고 쿼리 실행 계획을 분석했습니다.",
                        "mentioned",
                        "성과 수치가 부족합니다.",
                        "성과 수치를 추가하여 문제 해결의 효과를 명확히 하세요."
                ))
        ));

        AnalysisResponse response = analysisService.analyze(user, mockApply.getId());

        assertThat(response.questions().get(0).analyses()).hasSize(1);
        assertThat(response.questions().get(0).analyses().get(0).improvement()).isEmpty();
    }

    @Test
    @DisplayName("완성된 평서문 improvement는 그대로 저장한다")
    void analyzePreservesDeclarativeImprovement() {
        User user = saveUser("analysis-declarative-improvement@example.com");
        MockApply mockApply = saveMockApply(user);
        Question question = saveQuestion(mockApply, "문제 해결 경험", "저는 로그를 확인했습니다.");
        String validImprovement = "저는 로그를 분석하고 누락된 인덱스를 추가하여 응답 시간을 1.8초에서 0.6초로 단축했습니다.";
        when(analysisAiClient.analyze(any(), any())).thenReturn(new AnalysisLlmResponse(
                70,
                75,
                65,
                70,
                "평서문 검증입니다.",
                List.of(new AnalysisLlmResponse.QuestionAnalysisItem(
                        question.getId(),
                        "저는 로그를 확인했습니다.",
                        "mentioned",
                        "성과가 부족합니다.",
                        validImprovement
                ))
        ));

        AnalysisResponse response = analysisService.analyze(user, mockApply.getId());

        assertThat(response.questions().get(0).analyses()).hasSize(1);
        assertThat(response.questions().get(0).analyses().get(0).improvement())
                .isEqualTo(validImprovement);
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

    @Test
    @DisplayName("크레딧 1개인 사용자가 동시에 분석을 요청해도 하나만 성공한다")
    void analyzeConcurrentlyUsesCreditOnlyOnce() throws Exception {
        User user = saveUserWithCredit("analysis-concurrent-credit@example.com", 1);
        MockApply firstMockApply = saveMockApply(user);
        MockApply secondMockApply = saveMockApply(user);
        Question firstQuestion = saveQuestion(firstMockApply, "지원 직무 경험을 작성해주세요.", "Spring Boot API를 개발했습니다.");
        Question secondQuestion = saveQuestion(secondMockApply, "문제 해결 경험을 작성해주세요.", "장애 로그를 분석했습니다.");
        when(analysisAiClient.analyze(any(), any()))
                .thenReturn(new AnalysisLlmResponse(
                        80,
                        81,
                        82,
                        83,
                        "첫 번째 분석",
                        List.of(new AnalysisLlmResponse.QuestionAnalysisItem(
                                firstQuestion.getId(),
                                "Spring Boot API를 개발했습니다.",
                                "mentioned",
                                "성과 지표가 부족합니다.",
                                "Spring Boot API를 개발해 응답 시간을 개선했습니다."
                        ))
                ))
                .thenReturn(new AnalysisLlmResponse(
                        70,
                        71,
                        72,
                        73,
                        "두 번째 분석",
                        List.of(new AnalysisLlmResponse.QuestionAnalysisItem(
                                secondQuestion.getId(),
                                "장애 로그를 분석했습니다.",
                                "mentioned",
                                "결과가 부족합니다.",
                                "장애 로그를 분석해 복구 시간을 단축했습니다."
                        ))
                ));

        List<Result> results = runConcurrently(
                List.of(
                        () -> analyzeSafely(user, firstMockApply.getId()),
                        () -> analyzeSafely(user, secondMockApply.getId())
                )
        );

        assertThat(results).filteredOn(Result::success).hasSize(1);
        assertThat(results).filteredOn(result -> !result.success()).hasSize(1);
        assertThat(userRepository.findById(user.getId()).orElseThrow().getCredit()).isZero();
        assertThat(creditTransactionRepository.findAllByUserIdAndTypeOrderByCreatedAtDescIdDesc(
                user.getId(),
                CreditTransactionType.USE
        )).hasSize(1);
    }

    private User saveUser(String email) {
        return saveUserWithCredit(email, 11);
    }

    private User saveUserWithCredit(String email, int credit) {
        User user = User.signup("테스트 사용자", email, "encoded-password");
        user.increaseCredit(credit - 1);
        return userRepository.save(user);
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

    private Result analyzeSafely(User user, Long mockApplyId) {
        try {
            analysisService.analyze(user, mockApplyId);
            return Result.ok();
        } catch (Exception e) {
            return Result.failure(e);
        }
    }

    private List<Result> runConcurrently(List<Callable<Result>> tasks) throws Exception {
        var ready = new CountDownLatch(tasks.size());
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(tasks.size());
        try {
            var futures = tasks.stream()
                    .map(task -> executor.submit(() -> {
                        ready.countDown();
                        start.await();
                        return task.call();
                    }))
                    .toList();
            ready.await();
            start.countDown();

            List<Result> results = new java.util.ArrayList<>();
            for (var future : futures) {
                results.add(future.get());
            }
            return results;
        } finally {
            executor.shutdownNow();
        }
    }

    private record Result(boolean success, Exception exception) {
        static Result ok() {
            return new Result(true, null);
        }

        static Result failure(Exception exception) {
            return new Result(false, exception);
        }
    }
}
