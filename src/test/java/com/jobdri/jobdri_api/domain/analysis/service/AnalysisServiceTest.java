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
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
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

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

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
        assertThat(response.score()).isEqualTo(78);
        assertThat(response.jobFit()).isEqualTo(82);
        assertThat(response.impact()).isEqualTo(71);
        assertThat(response.completeness()).isEqualTo(80);
        assertThat(response.missingKeywords()).isEmpty();
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
    @DisplayName("총점은 하위 점수 가중합으로 계산한다")
    void analyzeCalculatesScoreFromDimensionScores() {
        User user = saveUser("analysis-score-calculation@example.com");
        MockApply mockApply = saveMockApply(user);
        Question question = saveQuestion(mockApply, "지원 직무 경험을 작성해주세요.", "Spring Boot API를 개발했습니다.");
        when(analysisAiClient.analyze(any(), any())).thenReturn(new AnalysisLlmResponse(
                100,
                0,
                0,
                "총점 서버 계산입니다.",
                List.of(new AnalysisLlmResponse.QuestionAnalysisItem(
                        question.getId(),
                        "Spring Boot API를 개발했습니다.",
                        "mentioned",
                        "성과 지표가 부족합니다.",
                        "Spring Boot API를 개발해 응답 시간을 약 X% 개선했습니다."
                ))
        ));

        AnalysisResponse response = analysisService.analyze(user, mockApply.getId());

        assertThat(response.score()).isEqualTo(50);
        assertThat(response.jobFit()).isEqualTo(100);
        assertThat(response.impact()).isZero();
        assertThat(response.completeness()).isZero();
    }

    @Test
    @DisplayName("하위 점수가 0~100 범위를 벗어나면 분석을 실패 처리하고 크레딧을 환불한다")
    void analyzeThrowsAndRefundsWhenDimensionScoreOutOfRange() {
        User user = saveUser("analysis-score-range@example.com");
        MockApply mockApply = saveMockApply(user);
        saveQuestion(mockApply, "지원 직무 경험을 작성해주세요.", "Spring Boot API를 개발했습니다.");
        int initialCredit = userRepository.findById(user.getId()).orElseThrow().getCredit();
        when(analysisAiClient.analyze(any(), any())).thenReturn(new AnalysisLlmResponse(
                101,
                70,
                70,
                "잘못된 점수입니다.",
                List.of()
        ));

        assertThatThrownBy(() -> analysisService.analyze(user, mockApply.getId()))
                .isInstanceOf(GeneralException.class)
                .extracting("code")
                .isEqualTo(GeneralErrorCode.SERVICE_UNAVAILABLE);
        assertThat(userRepository.findById(user.getId()).orElseThrow().getCredit()).isEqualTo(initialCredit);
        assertThat(analysisRepository.findByMockApplyId(mockApply.getId())).isEmpty();
    }

    @Test
    @DisplayName("missingKeywords는 검증 후 최대 3개까지 응답에 포함한다")
    void analyzeReturnsValidatedMissingKeywords() {
        User user = saveUser("analysis-missing-keywords@example.com");
        JobPosting jobPosting = saveJobPosting(
                user,
                "테스트 자동화 경험",
                "SQL 활용 경험",
                "대용량 트래픽 처리 경험"
        );
        MockApply mockApply = mockApplyRepository.save(MockApply.create(user, jobPosting, ApplyType.ACTUAL));
        saveQuestion(mockApply, "지원 직무 경험", "Spring Boot API를 개발했습니다.");
        when(analysisAiClient.analyze(any(), any())).thenReturn(new AnalysisLlmResponse(
                80,
                70,
                60,
                "누락 키워드 검증입니다.",
                List.of(
                        new AnalysisLlmResponse.HighlightItem(
                                "직무 경험이 실제 구현 사례로 드러나요",
                                "Spring Boot API를 개발했습니다."
                        ),
                        new AnalysisLlmResponse.HighlightItem(" ", "Spring Boot API를 개발했습니다."),
                        new AnalysisLlmResponse.HighlightItem(
                                "이 강점 제목은 너무 길어서 응답에서 제외되어야 하는 매우 긴 문장이며 허용 길이를 명확하게 초과하는 잘못된 핵심 강점 카드 제목입니다. 화면 카드 제목으로 사용할 수 없는 수준의 장문입니다.",
                                "Spring Boot API를 개발했습니다."
                        ),
                        new AnalysisLlmResponse.HighlightItem("협업 기반 문제 해결이 보여요", "팀원들과 함께 일주일 동안 상권으로 나갔습니다."),
                        new AnalysisLlmResponse.HighlightItem("실행력이 구체적으로 드러나요", "직접 심층 인터뷰를 진행하고"),
                        new AnalysisLlmResponse.HighlightItem("네 번째 강점은 최대 개수 제한으로 제외돼요", "최우수상을 수상할 수 있었습니다.")
                ),
                List.of(
                        new AnalysisLlmResponse.HighlightItem(
                                "SQL 활용 경험 보강이 필요해요",
                                "SQL 활용 경험"
                        ),
                        new AnalysisLlmResponse.HighlightItem(
                                "SQL 활용 경험 보강이 필요해요",
                                "SQL 활용 경험"
                        ),
                        new AnalysisLlmResponse.HighlightItem(" ", "테스트 자동화 경험"),
                        new AnalysisLlmResponse.HighlightItem("대용량 트래픽 경험을 더 보여주세요", "대용량 트래픽 처리 경험"),
                        new AnalysisLlmResponse.HighlightItem("테스트 자동화 경험을 보강하세요", "테스트 자동화 경험"),
                        new AnalysisLlmResponse.HighlightItem("네 번째 약점은 최대 개수 제한으로 제외돼요", "성능 최적화 경험")
                ),
                List.of(
                        new AnalysisLlmResponse.MissingKeywordItem("SQL 활용 경험", "qualification"),
                        new AnalysisLlmResponse.MissingKeywordItem(" ", "qualification"),
                        new AnalysisLlmResponse.MissingKeywordItem("SQL 활용 경험", "preference"),
                        new AnalysisLlmResponse.MissingKeywordItem("잘못된 출처", "unknown"),
                        new AnalysisLlmResponse.MissingKeywordItem(
                                "이 키워드는 너무 길어서 응답에서 제외되어야 하는 매우 긴 역량 문구이며 허용 길이를 명확하게 초과하는 잘못된 누락 키워드입니다",
                                "mainTask"
                        ),
                        new AnalysisLlmResponse.MissingKeywordItem("대용량 트래픽 처리 경험", "preference"),
                        new AnalysisLlmResponse.MissingKeywordItem("테스트 자동화 경험", "mainTask")
                ),
                List.of()
        ));

        AnalysisResponse response = analysisService.analyze(user, mockApply.getId());

        assertThat(response.keyStrengths()).hasSize(3);
        assertThat(response.keyStrengths()).extracting("title")
                .containsExactly("직무 경험이 실제 구현 사례로 드러나요", "협업 기반 문제 해결이 보여요", "실행력이 구체적으로 드러나요");
        assertThat(response.keyStrengths()).extracting("quote")
                .containsExactly("Spring Boot API를 개발했습니다.", "팀원들과 함께 일주일 동안 상권으로 나갔습니다.", "직접 심층 인터뷰를 진행하고");
        assertThat(response.keyWeaknesses()).hasSize(3);
        assertThat(response.keyWeaknesses()).extracting("title")
                .containsExactly("SQL 활용 경험 보강이 필요해요", "대용량 트래픽 경험을 더 보여주세요", "테스트 자동화 경험을 보강하세요");
        assertThat(response.keyWeaknesses()).extracting("quote")
                .containsExactly("SQL 활용 경험", "대용량 트래픽 처리 경험", "테스트 자동화 경험");
        assertThat(response.missingKeywords()).hasSize(2);
        assertThat(response.missingKeywords()).extracting("keyword")
                .containsExactly("SQL 활용 경험", "테스트 자동화 경험");
        assertThat(response.missingKeywords()).extracting(keyword -> keyword.source().value())
                .containsExactly("qualification", "mainTask");

        Analysis analysis = analysisRepository.findByMockApplyId(mockApply.getId()).orElseThrow();
        assertThat(analysis.getKeyStrengthsJson())
                .contains("\"title\":\"직무 경험이 실제 구현 사례로 드러나요\"")
                .contains("\"quote\":\"Spring Boot API를 개발했습니다.\"")
                .contains("\"title\":\"협업 기반 문제 해결이 보여요\"")
                .contains("\"title\":\"실행력이 구체적으로 드러나요\"")
                .doesNotContain("너무 길어서")
                .doesNotContain("네 번째 강점");
        assertThat(analysis.getKeyWeaknessesJson())
                .contains("\"title\":\"SQL 활용 경험 보강이 필요해요\"", "\"quote\":\"SQL 활용 경험\"")
                .contains("\"title\":\"대용량 트래픽 경험을 더 보여주세요\"")
                .contains("\"title\":\"테스트 자동화 경험을 보강하세요\"")
                .doesNotContain("네 번째 약점");
        assertThat(analysis.getMissingKeywordsJson())
                .contains("\"keyword\":\"SQL 활용 경험\"", "\"source\":\"qualification\"")
                .contains("\"keyword\":\"테스트 자동화 경험\"", "\"source\":\"mainTask\"")
                .doesNotContain("대용량 트래픽 처리 경험")
                .doesNotContain("잘못된 출처")
                .doesNotContain("이 키워드는 너무 길어서");
    }

    @Test
    @DisplayName("정형 자격요건과 JD에 없는 missingKeywords를 제외한다")
    void analyzeSkipsStructuredAndNonJdMissingKeywords() {
        User user = saveUser("analysis-missing-keywords-strict@example.com");
        JobPosting jobPosting = saveJobPosting(
                user,
                "장비 설치 및 유지보수 경험",
                "데이터 분석 관련 Tool 활용 경험",
                "온라인 쇼핑몰 근무 경험자"
        );
        MockApply mockApply = mockApplyRepository.save(MockApply.create(user, jobPosting, ApplyType.ACTUAL));
        saveQuestion(mockApply, "지원 직무 경험", "장비 운용 경험이 있습니다.");
        when(analysisAiClient.analyze(any(), any())).thenReturn(new AnalysisLlmResponse(
                80,
                70,
                60,
                "엄격한 누락 키워드 검증입니다.",
                List.of(
                        new AnalysisLlmResponse.MissingKeywordItem("영어 공인성적", "qualification"),
                        new AnalysisLlmResponse.MissingKeywordItem("반도체 관련 전공", "qualification"),
                        new AnalysisLlmResponse.MissingKeywordItem("SQL 활용 경험", "qualification"),
                        new AnalysisLlmResponse.MissingKeywordItem("온라인 쇼핑몰 근무 경험자", "preference"),
                        new AnalysisLlmResponse.MissingKeywordItem("장비 설치 및 유지보수 경험", "mainTask"),
                        new AnalysisLlmResponse.MissingKeywordItem("데이터 분석 관련 Tool 활용 경험", "qualification")
                ),
                List.of()
        ));

        AnalysisResponse response = analysisService.analyze(user, mockApply.getId());

        assertThat(response.missingKeywords()).extracting("keyword")
                .containsExactly("장비 설치 및 유지보수 경험", "데이터 분석 관련 Tool 활용 경험");
    }

    @Test
    @DisplayName("improvement가 원문 동일, 다른 원문 문장 복사, 메타 조언이면 제거한다")
    void analyzeRemovesUnsafeImprovements() {
        User user = saveUser("analysis-unsafe-improvement@example.com");
        MockApply mockApply = saveMockApply(user);
        Question question = saveQuestion(
                mockApply,
                "문제 해결 경험",
                "첫 번째 문장입니다. 두 번째 문장입니다. 세 번째 문장입니다."
        );
        when(analysisAiClient.analyze(any(), any())).thenReturn(new AnalysisLlmResponse(
                70,
                70,
                70,
                "improvement 검증입니다.",
                List.of(
                        new AnalysisLlmResponse.QuestionAnalysisItem(
                                question.getId(),
                                "첫 번째 문장입니다.",
                                "mentioned",
                                "구체성이 부족합니다.",
                                "첫 번째 문장입니다."
                        ),
                        new AnalysisLlmResponse.QuestionAnalysisItem(
                                question.getId(),
                                "두 번째 문장입니다.",
                                "mentioned",
                                "결과가 부족합니다.",
                                "세 번째 문장입니다."
                        ),
                        new AnalysisLlmResponse.QuestionAnalysisItem(
                                question.getId(),
                                "세 번째 문장입니다.",
                                "mentioned",
                                "직무 연결이 부족합니다.",
                                "직무 연결성을 구체적으로 설명했습니다."
                        )
                )
        ));

        AnalysisResponse response = analysisService.analyze(user, mockApply.getId());

        assertThat(response.questions().get(0).analyses()).hasSize(3);
        assertThat(response.questions().get(0).analyses()).extracting("improvement")
                .containsExactly("", "", "");
    }

    @Test
    @DisplayName("PROVEN인데 reason이 부족이나 보완 필요를 말하면 모순 결과로 제외한다")
    void analyzeSkipsProvenWithContradictoryReason() {
        User user = saveUser("analysis-proven-contradictory-reason@example.com");
        MockApply mockApply = saveMockApply(user);
        Question question = saveQuestion(mockApply, "성과 경험", "평균 거칠기(Ra)를 1.5nm 감소시켰습니다.");
        when(analysisAiClient.analyze(any(), any())).thenReturn(new AnalysisLlmResponse(
                80,
                80,
                80,
                "PROVEN 모순 검증입니다.",
                List.of(
                        new AnalysisLlmResponse.QuestionAnalysisItem(
                                question.getId(),
                                "평균 거칠기(Ra)를 1.5nm 감소시켰습니다.",
                                "proven",
                                "구체적인 성과 보완이 필요합니다.",
                                "평균 거칠기(Ra)를 1.5nm 감소시킨 성과를 강조했습니다."
                        )
                )
        ));

        AnalysisResponse response = analysisService.analyze(user, mockApply.getId());

        assertThat(response.questions().get(0).analyses()).isEmpty();
    }

    @Test
    @DisplayName("PROVEN questionAnalysis는 최종 결과에서 제외한다")
    void analyzeRemovesImprovementForProvenStatus() {
        User user = saveUser("analysis-proven-improvement-empty@example.com");
        MockApply mockApply = saveMockApply(user);
        Question question = saveQuestion(mockApply, "성과 경험", "평균 거칠기(Ra)를 1.5nm 감소시켰습니다.");
        when(analysisAiClient.analyze(any(), any())).thenReturn(new AnalysisLlmResponse(
                80,
                80,
                80,
                "PROVEN improvement 검증입니다.",
                List.of(new AnalysisLlmResponse.QuestionAnalysisItem(
                        question.getId(),
                        "평균 거칠기(Ra)를 1.5nm 감소시켰습니다.",
                        "proven",
                        "수치와 결과가 구체적으로 제시되어 있습니다.",
                        "평균 거칠기(Ra)를 1.5nm 감소시킨 성과를 강조했습니다."
                ))
        ));

        AnalysisResponse response = analysisService.analyze(user, mockApply.getId());

        assertThat(response.questions().get(0).analyses()).isEmpty();
    }

    @Test
    @DisplayName("PROVEN reason이 긍정 문맥이어도 questionAnalyses에서는 제외한다")
    void analyzeKeepsValidProvenReasonWithPositiveNeedContext() {
        User user = saveUser("analysis-valid-proven-need-context@example.com");
        MockApply mockApply = saveMockApply(user);
        Question question = saveQuestion(mockApply, "성과 경험", "Spring Boot API를 개발해 장애 대응 시간을 단축했습니다.");
        when(analysisAiClient.analyze(any(), any())).thenReturn(new AnalysisLlmResponse(
                80,
                80,
                80,
                "PROVEN 긍정 문맥 검증입니다.",
                List.of(new AnalysisLlmResponse.QuestionAnalysisItem(
                        question.getId(),
                        "Spring Boot API를 개발해 장애 대응 시간을 단축했습니다.",
                        "proven",
                        "직무에 필요한 역량을 보여줍니다.",
                        "Spring Boot API 개발 경험을 더 구체적으로 작성했습니다."
                ))
        ));

        AnalysisResponse response = analysisService.analyze(user, mockApply.getId());

        assertThat(response.questions().get(0).analyses()).isEmpty();
    }

    @Test
    @DisplayName("questionAnalyses 빈 배열도 유효한 분석 결과로 허용한다")
    void analyzeAllowsEmptyQuestionAnalyses() {
        User user = saveUser("analysis-empty-question-analyses@example.com");
        MockApply mockApply = saveMockApply(user);
        saveQuestion(mockApply, "성과 경험", "Spring Boot API를 개발해 장애 대응 시간을 단축했습니다.");
        when(analysisAiClient.analyze(any(), any())).thenReturn(new AnalysisLlmResponse(
                80,
                80,
                80,
                "보완 대상이 없습니다.",
                List.of()
        ));

        AnalysisResponse response = analysisService.analyze(user, mockApply.getId());

        assertThat(response.questions().get(0).analyses()).isEmpty();
    }

    @Test
    @DisplayName("유효한 문장이 2개면 두 분석 모두 저장한다")
    void analyzeKeepsTwoValidQuestionAnalyses() {
        User user = saveUser("analysis-two-valid-items@example.com");
        MockApply mockApply = saveMockApply(user);
        Question question = saveQuestion(mockApply, "성과 경험", "첫 번째 문장입니다. 두 번째 문장입니다.");
        when(analysisAiClient.analyze(any(), any())).thenReturn(new AnalysisLlmResponse(
                80,
                80,
                80,
                "두 문장 검증입니다.",
                List.of(
                        new AnalysisLlmResponse.QuestionAnalysisItem(
                                question.getId(),
                                "첫 번째 문장입니다.",
                                "mentioned",
                                "실행 방법이 부족합니다.",
                                null
                        ),
                        new AnalysisLlmResponse.QuestionAnalysisItem(
                                question.getId(),
                                "두 번째 문장입니다.",
                                "fabricated",
                                "확인된 사실과 직접 충돌합니다.",
                                null
                        )
                )
        ));

        AnalysisResponse response = analysisService.analyze(user, mockApply.getId());

        assertThat(response.questions().get(0).analyses()).hasSize(2);
        assertThat(response.questions().get(0).analyses())
                .extracting("status")
                .containsExactly("mentioned", "fabricated");
    }

    @Test
    @DisplayName("reason이 비어 있는 FABRICATED는 최종 결과에서 제외한다")
    void analyzeSkipsFabricatedWithoutReason() {
        User user = saveUser("analysis-fabricated-empty-reason@example.com");
        MockApply mockApply = saveMockApply(user);
        Question question = saveQuestion(mockApply, "성과 경험", "답변 내부에서 서로 다른 경력을 주장했습니다.");
        when(analysisAiClient.analyze(any(), any())).thenReturn(new AnalysisLlmResponse(
                80,
                80,
                80,
                "FABRICATED reason 검증입니다.",
                List.of(new AnalysisLlmResponse.QuestionAnalysisItem(
                        question.getId(),
                        "답변 내부에서 서로 다른 경력을 주장했습니다.",
                        "fabricated",
                        "",
                        null
                ))
        ));

        AnalysisResponse response = analysisService.analyze(user, mockApply.getId());

        assertThat(response.questions().get(0).analyses()).isEmpty();
    }

    @Test
    @DisplayName("keyStrengths quote와 동일한 questionAnalysis sentence는 제외한다")
    void analyzeSkipsQuestionAnalysisDuplicatedWithKeyStrength() {
        User user = saveUser("analysis-strength-analysis-duplicate@example.com");
        MockApply mockApply = saveMockApply(user);
        Question question = saveQuestion(mockApply, "성과 경험", "API 응답 시간을 300ms 단축했습니다.");
        when(analysisAiClient.analyze(any(), any())).thenReturn(new AnalysisLlmResponse(
                80,
                80,
                80,
                "강점과 분석 중복 검증입니다.",
                List.of(new AnalysisLlmResponse.HighlightItem(
                        "성능 개선 근거가 구체적입니다.",
                        "API 응답 시간을 300ms 단축했습니다."
                )),
                List.of(),
                List.of(),
                List.of(new AnalysisLlmResponse.QuestionAnalysisItem(
                        question.getId(),
                        "API 응답 시간을 300ms 단축했습니다.",
                        "mentioned",
                        "구체성이 부족합니다.",
                        null
                ))
        ));

        AnalysisResponse response = analysisService.analyze(user, mockApply.getId());

        assertThat(response.keyStrengths()).hasSize(1);
        assertThat(response.questions().get(0).analyses()).isEmpty();
    }

    @Test
    @DisplayName("missingKeywords가 null이면 빈 배열로 응답한다")
    void analyzeReturnsEmptyMissingKeywordsWhenLlmMissingKeywordsIsNull() {
        User user = saveUser("analysis-missing-keywords-null@example.com");
        MockApply mockApply = saveMockApply(user);
        saveQuestion(mockApply, "지원 직무 경험", "Spring Boot API를 개발했습니다.");
        when(analysisAiClient.analyze(any(), any())).thenReturn(new AnalysisLlmResponse(
                80,
                70,
                60,
                "누락 키워드가 없습니다.",
                null,
                List.of()
        ));

        AnalysisResponse response = analysisService.analyze(user, mockApply.getId());

        assertThat(response.missingKeywords()).isEmpty();
        Analysis analysis = analysisRepository.findByMockApplyId(mockApply.getId()).orElseThrow();
        assertThat(analysis.getMissingKeywordsJson()).isEqualTo("[]");
    }

    @Test
    @DisplayName("keyStrengths와 keyWeaknesses가 null이면 빈 배열로 응답한다")
    void analyzeReturnsEmptyHighlightsWhenLlmHighlightsAreNull() {
        User user = saveUser("analysis-highlights-null@example.com");
        MockApply mockApply = saveMockApply(user);
        saveQuestion(mockApply, "지원 직무 경험", "Spring Boot API를 개발했습니다.");
        when(analysisAiClient.analyze(any(), any())).thenReturn(new AnalysisLlmResponse(
                80,
                70,
                60,
                "핵심 강약점이 없습니다.",
                null,
                null,
                List.of(),
                List.of()
        ));

        AnalysisResponse response = analysisService.analyze(user, mockApply.getId());

        assertThat(response.keyStrengths()).isEmpty();
        assertThat(response.keyWeaknesses()).isEmpty();
        Analysis analysis = analysisRepository.findByMockApplyId(mockApply.getId()).orElseThrow();
        assertThat(analysis.getKeyStrengthsJson()).isEqualTo("[]");
        assertThat(analysis.getKeyWeaknessesJson()).isEqualTo("[]");
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
    @DisplayName("invalid status 문항 분석은 MENTIONED로 변환하지 않고 제외한다")
    void analyzeSkipsInvalidStatus() {
        User user = saveUser("analysis-invalid-status@example.com");
        MockApply mockApply = saveMockApply(user);
        Question question = saveQuestion(mockApply, "문제 해결 경험", "장애 로그를 분석해 원인을 찾았습니다.");
        when(analysisAiClient.analyze(any(), any())).thenReturn(new AnalysisLlmResponse(
                70,
                70,
                70,
                "잘못된 status 항목은 제외됩니다.",
                List.of(new AnalysisLlmResponse.QuestionAnalysisItem(
                        question.getId(),
                        "장애 로그를 분석해 원인을 찾았습니다.",
                        "uncertain",
                        "status가 잘못되었습니다.",
                        "장애 로그를 분석해 원인을 찾고 복구 시간을 약 X분 단축했습니다."
                ))
        ));

        AnalysisResponse response = analysisService.analyze(user, mockApply.getId());

        assertThat(response.questions().get(0).analyses()).isEmpty();
    }

    @Test
    @DisplayName("구버전 worker status는 백엔드 분석 상태로 매핑해 저장한다")
    void analyzeMapsLegacyWorkerStatuses() {
        User user = saveUser("analysis-legacy-status@example.com");
        MockApply mockApply = saveMockApply(user);
        Question question = saveQuestion(
                mockApply,
                "문제 해결 경험",
                "장애 로그를 분석해 원인을 찾았습니다. 배포 장애를 줄였습니다. 검증되지 않은 성과를 주장했습니다."
        );
        when(analysisAiClient.analyze(any(), any())).thenReturn(new AnalysisLlmResponse(
                80,
                70,
                60,
                "구버전 worker status 매핑입니다.",
                List.of(new AnalysisLlmResponse.QuestionAnalysisItem(
                        question.getId(),
                        "장애 로그를 분석해 원인을 찾았습니다.",
                        "GOOD",
                        "근거가 충분합니다.",
                        "성과를 조금 더 구체화하세요."
                ), new AnalysisLlmResponse.QuestionAnalysisItem(
                        question.getId(),
                        "배포 장애를 줄였습니다.",
                        "NEEDS_IMPROVEMENT",
                        "성과 수치가 부족합니다.",
                        "정량 지표를 추가하세요."
                ), new AnalysisLlmResponse.QuestionAnalysisItem(
                        question.getId(),
                        "검증되지 않은 성과를 주장했습니다.",
                        "RISK",
                        "답변 내부의 명시적 사실과 직접 충돌합니다.",
                        "검증 가능한 표현으로 낮추세요."
                ))
        ));

        AnalysisResponse response = analysisService.analyze(user, mockApply.getId());

        assertThat(response.questions().get(0).analyses())
                .extracting("status")
                .containsExactly("mentioned", "fabricated");
    }

    @Test
    @DisplayName("missing status는 원문 sentence 저장이 안전하지 않아 문항 분석에서 제외한다")
    void analyzeSkipsMissingStatus() {
        User user = saveUser("analysis-missing-status@example.com");
        MockApply mockApply = saveMockApply(user);
        Question question = saveQuestion(mockApply, "지원 직무 경험", "Spring Boot API를 개발했습니다.");
        when(analysisAiClient.analyze(any(), any())).thenReturn(new AnalysisLlmResponse(
                70,
                70,
                70,
                "missing 항목은 제외됩니다.",
                List.of(new AnalysisLlmResponse.QuestionAnalysisItem(
                        question.getId(),
                        "Spring Boot API를 개발했습니다.",
                        "missing",
                        "SQL 활용 경험이 드러나지 않습니다.",
                        "Spring Boot API 개발 과정에서 SQL 쿼리를 분석해 응답 시간을 약 X% 개선했습니다."
                ))
        ));

        AnalysisResponse response = analysisService.analyze(user, mockApply.getId());

        assertThat(response.questions().get(0).analyses()).isEmpty();
    }

    @Test
    @DisplayName("동일 sentence가 중복되면 첫 번째 유효 항목만 저장한다")
    void analyzeDeduplicatesSameSentence() {
        User user = saveUser("analysis-deduplicate-sentence@example.com");
        MockApply mockApply = saveMockApply(user);
        Question question = saveQuestion(mockApply, "성과 경험", "API 응답 속도를 개선했습니다.");
        when(analysisAiClient.analyze(any(), any())).thenReturn(new AnalysisLlmResponse(
                70,
                70,
                70,
                "중복 문장 제거입니다.",
                List.of(
                        new AnalysisLlmResponse.QuestionAnalysisItem(
                                question.getId(),
                                "API 응답 속도를 개선했습니다.",
                                "mentioned",
                                "개선 전후 수치가 부족합니다.",
                                "API 응답 속도를 약 X% 개선했습니다."
                        ),
                        new AnalysisLlmResponse.QuestionAnalysisItem(
                                question.getId(),
                                "API 응답 속도를 개선했습니다.",
                                "mentioned",
                                "중복 항목입니다.",
                                "API 응답 속도를 약 Nms 단축했습니다."
                        )
                )
        ));

        AnalysisResponse response = analysisService.analyze(user, mockApply.getId());

        assertThat(response.questions().get(0).analyses()).hasSize(1);
        assertThat(response.questions().get(0).analyses().get(0).reason()).isEqualTo("개선 전후 수치가 부족합니다.");
    }

    @Test
    @DisplayName("questionAnalyses는 문항당 최대 3개까지만 저장한다")
    void analyzeLimitsQuestionAnalysesPerQuestion() {
        User user = saveUser("analysis-limit-per-question@example.com");
        MockApply mockApply = saveMockApply(user);
        Question question = saveQuestion(
                mockApply,
                "성과 경험",
                "첫 번째 문장입니다. 두 번째 문장입니다. 세 번째 문장입니다. 네 번째 문장입니다."
        );
        when(analysisAiClient.analyze(any(), any())).thenReturn(new AnalysisLlmResponse(
                70,
                70,
                70,
                "최대 개수 제한입니다.",
                List.of(
                        analysisItem(question.getId(), "첫 번째 문장입니다."),
                        analysisItem(question.getId(), "두 번째 문장입니다."),
                        analysisItem(question.getId(), "세 번째 문장입니다."),
                        analysisItem(question.getId(), "네 번째 문장입니다.")
                )
        ));

        AnalysisResponse response = analysisService.analyze(user, mockApply.getId());

        assertThat(response.questions().get(0).analyses()).hasSize(3);
        assertThat(response.questions().get(0).analyses())
                .extracting("sentence")
                .containsExactly("첫 번째 문장입니다.", "두 번째 문장입니다.", "세 번째 문장입니다.");
    }

    @Test
    @DisplayName("분석 응답은 저장된 지원 순번을 우선 반환한다")
    void analyzeReturnsStoredSequence() {
        User user = saveUser("analysis-stored-sequence@example.com");
        JobPosting jobPosting = saveJobPosting(user);
        MockApply mockApply = mockApplyRepository.save(MockApply.create(user, jobPosting, ApplyType.ACTUAL, 4));
        Question question = saveQuestion(mockApply, "재지원 분석 문항입니다.", "Spring Boot API를 개발했습니다.");
        when(analysisAiClient.analyze(any(), any())).thenReturn(new AnalysisLlmResponse(
                81,
                82,
                83,
                "저장 순번 분석입니다.",
                List.of(new AnalysisLlmResponse.QuestionAnalysisItem(
                        question.getId(),
                        "Spring Boot API를 개발했습니다.",
                        "mentioned",
                        "성과 지표가 부족합니다.",
                        "Spring Boot API를 개발해 응답 시간을 개선했습니다."
                ))
        ));

        AnalysisResponse response = analysisService.analyze(user, mockApply.getId());

        assertThat(response.mockApplyId()).isEqualTo(mockApply.getId());
        assertThat(response.sequence()).isEqualTo(4);
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
    @DisplayName("분석 실행 payload는 공고 중분류에 맞는 직무별 평가 기준을 포함한다")
    void prepareAnalysisExecutionIncludesJobCategoryEvaluationCriteria() {
        User user = saveUser("analysis-criteria-payload@example.com");
        JobPosting jobPosting = saveJobPosting(
                user,
                "AI·개발·데이터",
                "백엔드 개발",
                "API 개발",
                "Spring Boot 경험",
                "대용량 트래픽 경험"
        );
        MockApply mockApply = mockApplyRepository.save(MockApply.create(user, jobPosting, ApplyType.ACTUAL));
        saveQuestion(mockApply, "지원 직무 경험", "Spring Boot API를 개발했습니다.");

        AnalysisExecutionPayload payload = analysisService.prepareAnalysisExecution(user, mockApply.getId());

        assertThat(payload.jobCategoryEvaluationCriteria()).isNotNull();
        assertThat(payload.jobCategoryEvaluationCriteria().jobCategoryMiddle()).isEqualTo("AI·개발·데이터");
    }

    @Test
    @DisplayName("공고 중분류에 맞는 직무별 평가 기준이 없으면 payload 기준은 null이다")
    void prepareAnalysisExecutionKeepsCriteriaNullWhenMiddleNameIsUnknown() {
        User user = saveUser("analysis-criteria-missing-payload@example.com");
        MockApply mockApply = saveMockApply(user);
        saveQuestion(mockApply, "지원 직무 경험", "Spring Boot API를 개발했습니다.");

        AnalysisExecutionPayload payload = analysisService.prepareAnalysisExecution(user, mockApply.getId());

        assertThat(payload.jobCategoryEvaluationCriteria()).isNull();
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
    @DisplayName("띄어쓰기가 포함된 첨삭 지시문도 빈 값으로 저장한다")
    void analyzeNormalizesSpacedInstructionLikeImprovement() {
        User user = saveUser("analysis-spaced-instruction-improvement@example.com");
        MockApply mockApply = saveMockApply(user);
        Question question = saveQuestion(mockApply, "문제 해결 경험", "저는 로그를 확인했습니다.");
        when(analysisAiClient.analyze(any(), any())).thenReturn(new AnalysisLlmResponse(
                70,
                55,
                67,
                "띄어쓰기 지시문 검증입니다.",
                List.of(new AnalysisLlmResponse.QuestionAnalysisItem(
                        question.getId(),
                        "저는 로그를 확인했습니다.",
                        "mentioned",
                        "성과가 부족합니다.",
                        "성과 수치를 추가해 주세요."
                ))
        ));

        AnalysisResponse response = analysisService.analyze(user, mockApply.getId());

        assertThat(response.questions().get(0).analyses()).hasSize(1);
        assertThat(response.questions().get(0).analyses().get(0).improvement()).isEmpty();
    }

    @Test
    @DisplayName("하십시오와 해주십시오 형태의 첨삭 지시문도 빈 값으로 저장한다")
    void analyzeNormalizesFormalInstructionLikeImprovement() {
        User user = saveUser("analysis-formal-instruction-improvement@example.com");
        MockApply mockApply = saveMockApply(user);
        Question firstQuestion = saveQuestion(mockApply, "문제 해결 경험", "저는 로그를 확인했습니다.");
        Question secondQuestion = saveQuestion(mockApply, "성과 경험", "저는 API 응답 속도를 개선했습니다.");
        when(analysisAiClient.analyze(any(), any())).thenReturn(new AnalysisLlmResponse(
                70,
                55,
                67,
                "격식체 지시문 검증입니다.",
                List.of(
                        new AnalysisLlmResponse.QuestionAnalysisItem(
                                firstQuestion.getId(),
                                "저는 로그를 확인했습니다.",
                                "mentioned",
                                "성과가 부족합니다.",
                                "문장을 명확히 하십시오."
                        ),
                        new AnalysisLlmResponse.QuestionAnalysisItem(
                                secondQuestion.getId(),
                                "저는 API 응답 속도를 개선했습니다.",
                                "mentioned",
                                "성과 수치가 부족합니다.",
                                "수정해주십시오."
                        )
                )
        ));

        AnalysisResponse response = analysisService.analyze(user, mockApply.getId());

        assertThat(response.questions()).hasSize(2);
        assertThat(response.questions().get(0).analyses().get(0).improvement()).isEmpty();
        assertThat(response.questions().get(1).analyses().get(0).improvement()).isEmpty();
    }

    @Test
    @DisplayName("재분석 시 기존 분석과 문항 분석을 새 결과로 교체한다")
    void analyzeReplacesExistingAnalysis() {
        User user = saveUser("analysis-replace@example.com");
        MockApply mockApply = saveMockApply(user);
        Question question = saveQuestion(mockApply, "성과 경험", "가입 완료율을 개선했습니다. API 응답 속도를 개선했습니다.");
        when(analysisAiClient.analyze(any(), any()))
                .thenReturn(new AnalysisLlmResponse(
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
                        89,
                        90,
                        91,
                        "두 번째 분석",
                        List.of(new AnalysisLlmResponse.QuestionAnalysisItem(
                                question.getId(),
                                "API 응답 속도를 개선했습니다.",
                                "proven",
                                "성과가 구체적으로 제시되어 있습니다.",
                                "API 응답 속도를 300ms 단축했습니다."
                        ))
                ));

        AnalysisResponse first = analysisService.analyze(user, mockApply.getId());
        AnalysisResponse second = analysisService.analyze(user, mockApply.getId());

        assertThat(second.analysisId()).isNotEqualTo(first.analysisId());
        assertThat(second.score()).isEqualTo(90);
        assertThat(second.feedback()).isEqualTo("두 번째 분석");
        assertThat(second.questions().get(0).analyses()).isEmpty();
        assertThat(analysisRepository.findByMockApplyId(mockApply.getId()).orElseThrow().getScore()).isEqualTo(90);
        assertThat(questionAnalysisRepository.findAllByAnalysisId(second.analysisId())).isEmpty();
        assertThat(questionAnalysisRepository.findAllByAnalysisId(first.analysisId())).isEmpty();
    }

    @Test
    @DisplayName("저장된 분석 결과를 조회한다")
    void getAnalysis() {
        User user = saveUser("analysis-get@example.com");
        MockApply mockApply = saveMockApply(user);
        Question question = saveQuestion(mockApply, "지원 동기", "서비스 개선 경험이 있습니다.");
        when(analysisAiClient.analyze(any(), any())).thenReturn(new AnalysisLlmResponse(
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
        assertThat(response.score()).isEqualTo(77);
        assertThat(response.questions()).hasSize(1);
        assertThat(response.questions().get(0).analyses()).hasSize(1);
    }

    @Test
    @DisplayName("조회 응답은 JD와 답변으로 재계산하지 않고 DB에 저장된 missingKeywords를 반환한다")
    void getAnalysisReturnsPersistedMissingKeywordsWithoutRecalculation() {
        User user = saveUser("analysis-get-missing-keywords@example.com");
        JobPosting jobPosting = saveJobPosting(
                user,
                "- 테스트 자동화 경험",
                "- SQL 활용 경험",
                "- 대용량 트래픽 처리 경험"
        );
        MockApply mockApply = mockApplyRepository.save(MockApply.create(user, jobPosting, ApplyType.ACTUAL));
        saveQuestion(mockApply, "지원 직무 경험", "Spring Boot API를 개발했습니다.");
        when(analysisAiClient.analyze(any(), any())).thenReturn(new AnalysisLlmResponse(
                80,
                70,
                60,
                "저장된 분석입니다.",
                List.of(new AnalysisLlmResponse.HighlightItem("저장된 강점", "Spring Boot API를 개발했습니다.")),
                List.of(new AnalysisLlmResponse.HighlightItem("저장된 약점", "SQL 활용 경험")),
                List.of(new AnalysisLlmResponse.MissingKeywordItem("SQL 활용 경험", "qualification")),
                List.of()
        ));
        AnalysisResponse saved = analysisService.analyze(user, mockApply.getId());
        entityManager.clear();

        Analysis persisted = analysisRepository.findByMockApplyId(mockApply.getId()).orElseThrow();
        assertThat(persisted.getKeyStrengthsJson())
                .contains("\"title\":\"저장된 강점\"", "\"quote\":\"Spring Boot API를 개발했습니다.\"");
        assertThat(persisted.getKeyWeaknessesJson())
                .contains("\"title\":\"저장된 약점\"", "\"quote\":\"SQL 활용 경험\"");
        assertThat(persisted.getMissingKeywordsJson())
                .contains("\"keyword\":\"SQL 활용 경험\"", "\"source\":\"qualification\"");
        entityManager.clear();

        AnalysisResponse response = analysisService.getAnalysis(user, mockApply.getId());

        assertThat(saved.missingKeywords()).extracting("keyword")
                .containsExactly("SQL 활용 경험");
        assertThat(response.keyStrengths()).extracting("title")
                .containsExactly("저장된 강점");
        assertThat(response.keyWeaknesses()).extracting("title")
                .containsExactly("저장된 약점");
        assertThat(response.missingKeywords()).extracting("keyword")
                .containsExactly("SQL 활용 경험");
        assertThat(response.missingKeywords()).extracting(keyword -> keyword.source().value())
                .containsExactly("qualification");
    }

    @Test
    @DisplayName("저장된 missingKeywords JSON이 깨져 있어도 조회 응답은 빈 배열로 fallback한다")
    void getAnalysisReturnsEmptyMissingKeywordsWhenPersistedJsonIsMalformed() {
        User user = saveUser("analysis-get-malformed-missing-keywords@example.com");
        MockApply mockApply = saveMockApply(user);
        saveQuestion(mockApply, "지원 직무 경험", "Spring Boot API를 개발했습니다.");
        when(analysisAiClient.analyze(any(), any())).thenReturn(new AnalysisLlmResponse(
                80,
                70,
                60,
                "저장된 분석입니다.",
                List.of(new AnalysisLlmResponse.MissingKeywordItem("SQL 활용 경험", "qualification")),
                List.of()
        ));
        AnalysisResponse saved = analysisService.analyze(user, mockApply.getId());

        jdbcTemplate.update(
                "UPDATE analyses SET missing_keywords = ? WHERE id = ?",
                "not-json",
                saved.analysisId()
        );
        entityManager.clear();

        AnalysisResponse response = analysisService.getAnalysis(user, mockApply.getId());

        assertThat(response.analysisId()).isEqualTo(saved.analysisId());
        assertThat(response.missingKeywords()).isEmpty();
    }

    @Test
    @DisplayName("저장된 핵심 강약점 JSON이 깨져 있어도 조회 응답은 빈 배열로 fallback한다")
    void getAnalysisReturnsEmptyHighlightsWhenPersistedJsonIsMalformed() {
        User user = saveUser("analysis-get-malformed-highlights@example.com");
        MockApply mockApply = saveMockApply(user);
        saveQuestion(mockApply, "지원 직무 경험", "Spring Boot API를 개발했습니다.");
        when(analysisAiClient.analyze(any(), any())).thenReturn(new AnalysisLlmResponse(
                80,
                70,
                60,
                "저장된 분석입니다.",
                List.of(new AnalysisLlmResponse.HighlightItem("저장된 강점", "Spring Boot API를 개발했습니다.")),
                List.of(new AnalysisLlmResponse.HighlightItem("저장된 약점", "SQL 활용 경험")),
                List.of(),
                List.of()
        ));
        AnalysisResponse saved = analysisService.analyze(user, mockApply.getId());

        jdbcTemplate.update(
                "UPDATE analyses SET key_strengths = ?, key_weaknesses = ? WHERE id = ?",
                "not-json",
                "not-json",
                saved.analysisId()
        );
        entityManager.clear();

        AnalysisResponse response = analysisService.getAnalysis(user, mockApply.getId());

        assertThat(response.analysisId()).isEqualTo(saved.analysisId());
        assertThat(response.keyStrengths()).isEmpty();
        assertThat(response.keyWeaknesses()).isEmpty();
    }

    @Test
    @DisplayName("jobPosting 기준 sequence로 특정 회차 분석 결과를 조회한다")
    void getAnalysisByJobPostingSequence() {
        User user = saveUser("analysis-get-sequence@example.com");
        JobPosting jobPosting = saveJobPosting(user);
        MockApply firstMockApply = mockApplyRepository.save(MockApply.create(user, jobPosting, ApplyType.ACTUAL));
        MockApply secondMockApply = mockApplyRepository.save(MockApply.create(user, jobPosting, ApplyType.ACTUAL));
        saveQuestion(firstMockApply, "첫 번째 지원 동기", "첫 번째 답변입니다.");
        Question secondQuestion = saveQuestion(secondMockApply, "두 번째 지원 동기", "두 번째 답변입니다.");
        when(analysisAiClient.analyze(any(), any()))
                .thenReturn(new AnalysisLlmResponse(
                        62,
                        63,
                        64,
                        "첫 번째 분석 결과",
                        List.of()
                ))
                .thenReturn(new AnalysisLlmResponse(
                        82,
                        83,
                        84,
                        "두 번째 분석 결과",
                        List.of(new AnalysisLlmResponse.QuestionAnalysisItem(
                                secondQuestion.getId(),
                                "두 번째 답변입니다.",
                                "mentioned",
                                "근거가 더 필요합니다.",
                                "두 번째 답변에 구체적인 성과를 추가했습니다."
                        ))
                ));

        analysisService.analyze(user, firstMockApply.getId());
        AnalysisResponse saved = analysisService.analyze(user, secondMockApply.getId());

        AnalysisResponse response = analysisService.getAnalysisByJobPostingSequence(user, jobPosting.getId(), 2);

        assertThat(response.analysisId()).isEqualTo(saved.analysisId());
        assertThat(response.mockApplyId()).isEqualTo(secondMockApply.getId());
        assertThat(response.sequence()).isEqualTo(2);
        assertThat(response.feedback()).isEqualTo("두 번째 분석 결과");
    }

    @Test
    @DisplayName("jobPosting 기준 sequence 조회는 저장된 지원 순번을 우선 사용한다")
    void getAnalysisByJobPostingStoredSequence() {
        User user = saveUser("analysis-get-stored-sequence@example.com");
        JobPosting jobPosting = saveJobPosting(user);
        MockApply firstMockApply = mockApplyRepository.save(MockApply.create(user, jobPosting, ApplyType.ACTUAL));
        MockApply secondMockApply = mockApplyRepository.save(MockApply.create(user, jobPosting, ApplyType.ACTUAL, 4));
        saveQuestion(firstMockApply, "첫 번째 지원 동기", "첫 번째 답변입니다.");
        Question secondQuestion = saveQuestion(secondMockApply, "두 번째 지원 동기", "두 번째 답변입니다.");
        when(analysisAiClient.analyze(any(), any()))
                .thenReturn(new AnalysisLlmResponse(
                        62,
                        63,
                        64,
                        "첫 번째 분석 결과",
                        List.of()
                ))
                .thenReturn(new AnalysisLlmResponse(
                        82,
                        83,
                        84,
                        "저장 순번 분석 결과",
                        List.of(new AnalysisLlmResponse.QuestionAnalysisItem(
                                secondQuestion.getId(),
                                "두 번째 답변입니다.",
                                "mentioned",
                                "근거가 더 필요합니다.",
                                "두 번째 답변에 구체적인 성과를 추가했습니다."
                        ))
                ));

        analysisService.analyze(user, firstMockApply.getId());
        AnalysisResponse saved = analysisService.analyze(user, secondMockApply.getId());

        AnalysisResponse response = analysisService.getAnalysisByJobPostingSequence(user, jobPosting.getId(), 4);

        assertThat(response.analysisId()).isEqualTo(saved.analysisId());
        assertThat(response.mockApplyId()).isEqualTo(secondMockApply.getId());
        assertThat(response.sequence()).isEqualTo(4);
        assertThat(response.feedback()).isEqualTo("저장 순번 분석 결과");
    }

    @Test
    @DisplayName("존재하지 않는 sequence로 jobPosting 분석 결과 조회 시 예외를 던진다")
    void getAnalysisByJobPostingThrowsWhenSequenceDoesNotExist() {
        User user = saveUser("analysis-get-sequence-missing@example.com");
        MockApply mockApply = saveMockApply(user);

        assertThatThrownBy(() -> analysisService.getAnalysisByJobPostingSequence(
                user,
                mockApply.getJobPosting().getId(),
                2
        ))
                .isInstanceOf(GeneralException.class)
                .extracting("code")
                .isEqualTo(GeneralErrorCode.MOCK_APPLY_NOT_FOUND);
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

    private AnalysisLlmResponse.QuestionAnalysisItem analysisItem(Long questionId, String sentence) {
        return new AnalysisLlmResponse.QuestionAnalysisItem(
                questionId,
                sentence,
                "mentioned",
                "구체적 결과가 부족합니다.",
                sentence.replace("입니다.", "이며 관련 결과를 약 X% 개선했습니다.")
        );
    }

    private JobPosting saveJobPosting(User user) {
        return saveJobPosting(user, "주요 업무", "자격 요건", "우대 사항");
    }

    private JobPosting saveJobPosting(User user, String task, String requirement, String preferred) {
        Company company = companyRepository.save(Company.create("분석 테스트 기업", CompanySize.MEDIUM));
        DetailClassification detailClassification = saveDetailClassification();
        return jobPostingRepository.save(JobPosting.create(
                user,
                company,
                detailClassification,
                task,
                requirement,
                preferred
        ));
    }

    private JobPosting saveJobPosting(
            User user,
            String middleName,
            String detailName,
            String task,
            String requirement,
            String preferred
    ) {
        Company company = companyRepository.save(Company.create("분석 테스트 기업", CompanySize.MEDIUM));
        DetailClassification detailClassification = saveDetailClassification(middleName, detailName);
        return jobPostingRepository.save(JobPosting.create(
                user,
                company,
                detailClassification,
                task,
                requirement,
                preferred
        ));
    }

    private DetailClassification saveDetailClassification() {
        return saveDetailClassification("분석 테스트 중분류", "분석 테스트 소분류");
    }

    private DetailClassification saveDetailClassification(String middleName, String detailName) {
        Classification classification = Classification.create("분석 테스트 대분류 " + System.nanoTime());
        MiddleClassification middleClassification = classification.addMiddleClassification(middleName);
        DetailClassification detailClassification = middleClassification.addDetailClassification(detailName);
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
