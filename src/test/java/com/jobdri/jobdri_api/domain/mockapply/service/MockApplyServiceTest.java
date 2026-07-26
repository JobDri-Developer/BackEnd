package com.jobdri.jobdri_api.domain.mockapply.service;

import com.jobdri.jobdri_api.domain.analysis.entity.Analysis;
import com.jobdri.jobdri_api.domain.analysis.entity.Question;
import com.jobdri.jobdri_api.domain.analysis.entity.QuestionAnalysis;
import com.jobdri.jobdri_api.domain.analysis.entity.QuestionAnalysisStatus;
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
import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingMockGenerateResponse;
import com.jobdri.jobdri_api.domain.jobposting.entity.JobPosting;
import com.jobdri.jobdri_api.domain.jobposting.entity.JobPostingProfileColor;
import com.jobdri.jobdri_api.domain.jobposting.repository.JobPostingRepository;
import com.jobdri.jobdri_api.domain.jobposting.service.MockJobPostingGenerationService;
import com.jobdri.jobdri_api.domain.mockapply.dto.request.MockApplyCreateMockRequest;
import com.jobdri.jobdri_api.domain.mockapply.dto.response.MockApplyCreateResponse;
import com.jobdri.jobdri_api.domain.mockapply.dto.response.MockApplyHomeItemResponse;
import com.jobdri.jobdri_api.domain.mockapply.dto.response.MockApplyHomeResponse;
import com.jobdri.jobdri_api.domain.mockapply.dto.response.MockApplyRetryResponse;
import com.jobdri.jobdri_api.domain.mockapply.dto.response.MockApplySequenceResponse;
import com.jobdri.jobdri_api.domain.mockapply.dto.response.MockApplyUpdateNameResponse;
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
import org.springframework.data.domain.Page;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MockApplyServiceTest {

    @Autowired
    private MockApplyService mockApplyService;

    @Autowired
    private MockApplyRepository mockApplyRepository;

    @Autowired
    private AnalysisRepository analysisRepository;

    @Autowired
    private QuestionAnalysisRepository questionAnalysisRepository;

    @Autowired
    private QuestionRepository questionRepository;

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
    private PlatformTransactionManager transactionManager;

    @MockBean
    private MockJobPostingGenerationService mockJobPostingGenerationService;

    @Test
    @DisplayName("기존 공고를 기준으로 ACTUAL 타입 모의 서류 지원을 생성한다")
    void createActualApply() {
        User user = saveUser("actual-apply@example.com");
        JobPosting jobPosting = saveJobPosting(user, "백엔드 개발");

        MockApplyCreateResponse response = mockApplyService.createActualApply(user, jobPosting.getId());

        MockApply mockApply = mockApplyRepository.findById(response.mockApplyId()).orElseThrow();
        assertThat(response.jobPostingId()).isEqualTo(jobPosting.getId());
        assertThat(response.applyType()).isEqualTo(ApplyType.ACTUAL);
        assertThat(response.sequence()).isEqualTo(1);
        assertThat(mockApply.getUser().getId()).isEqualTo(user.getId());
        assertThat(mockApply.getJobPosting().getId()).isEqualTo(jobPosting.getId());
        assertThat(mockApply.getApplyType()).isEqualTo(ApplyType.ACTUAL);
        assertThat(mockApply.getStatus()).isEqualTo(MockApplyStatus.APPLICATION_CREATED);
        assertThat(mockApply.getSequence()).isEqualTo(1);
    }

    @Test
    @DisplayName("요청 순번이 있으면 ACTUAL 타입 지원에 저장한다")
    void createActualApplyWithRequestedSequence() {
        User user = saveUser("actual-apply-sequence@example.com");
        JobPosting jobPosting = saveJobPosting(user, "백엔드 개발");

        MockApplyCreateResponse response = mockApplyService.createActualApply(user, jobPosting.getId(), 3);

        MockApply mockApply = mockApplyRepository.findById(response.mockApplyId()).orElseThrow();
        MockApplySequenceResponse sequenceResponse = mockApplyService.getMockApplySequence(user, mockApply.getId());
        assertThat(response.sequence()).isEqualTo(3);
        assertThat(mockApply.getSequence()).isEqualTo(3);
        assertThat(sequenceResponse.sequence()).isEqualTo(3);
        assertThat(sequenceResponse.totalCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("같은 회사와 직무여도 다른 공고이면 순번을 따로 계산한다")
    void createActualApplySequencesByJobPosting() {
        User user = saveUser("actual-apply-retry-sequence@example.com");
        Company company = saveCompany("재지원 기업 " + UUID.randomUUID(), CompanySize.MEDIUM);
        DetailClassification detailClassification = saveDetailClassification("백엔드 개발");
        JobPosting firstJobPosting = saveJobPosting(user, company, detailClassification, "첫 번째 JD");
        JobPosting secondJobPosting = saveJobPosting(user, company, detailClassification, "복제된 JD");

        MockApplyCreateResponse firstResponse = mockApplyService.createActualApply(user, firstJobPosting.getId());
        MockApplyCreateResponse secondResponse = mockApplyService.createActualApply(user, secondJobPosting.getId());

        MockApply secondMockApply = mockApplyRepository.findById(secondResponse.mockApplyId()).orElseThrow();
        assertThat(firstResponse.sequence()).isEqualTo(1);
        assertThat(secondResponse.sequence()).isEqualTo(1);
        assertThat(secondMockApply.getSequence()).isEqualTo(1);
    }

    @Test
    @DisplayName("요청 순번이 0 이하이면 다음 유효 순번을 저장한다")
    void createActualApplyIgnoresNonPositiveRequestedSequence() {
        User user = saveUser("actual-apply-non-positive-sequence@example.com");
        JobPosting jobPosting = saveJobPosting(user, "백엔드 개발");
        saveMockApply(user, jobPosting, ApplyType.ACTUAL, 1);
        saveMockApply(user, jobPosting, ApplyType.ACTUAL, 3);

        MockApplyCreateResponse zeroResponse = mockApplyService.createActualApply(user, jobPosting.getId(), 0);
        MockApplyCreateResponse negativeResponse = mockApplyService.createActualApply(user, jobPosting.getId(), -1);

        MockApply zeroSequenceApply = mockApplyRepository.findById(zeroResponse.mockApplyId()).orElseThrow();
        MockApply negativeSequenceApply = mockApplyRepository.findById(negativeResponse.mockApplyId()).orElseThrow();
        assertThat(zeroResponse.sequence()).isEqualTo(4);
        assertThat(negativeResponse.sequence()).isEqualTo(5);
        assertThat(zeroSequenceApply.getSequence()).isEqualTo(4);
        assertThat(negativeSequenceApply.getSequence()).isEqualTo(5);
    }

    @Test
    @DisplayName("같은 공고에 이미 사용 중인 순번을 명시하면 예외를 던진다")
    void createActualApplyThrowsWhenRequestedSequenceDuplicated() {
        User user = saveUser("actual-apply-sequence-duplicate@example.com");
        JobPosting jobPosting = saveJobPosting(user, "백엔드 개발");
        mockApplyService.createActualApply(user, jobPosting.getId(), 2);

        assertThatThrownBy(() -> mockApplyService.createActualApply(user, jobPosting.getId(), 2))
                .isInstanceOf(GeneralException.class)
                .extracting("code")
                .isEqualTo(GeneralErrorCode.INVALID_PARAMETER);
    }

    @Test
    @DisplayName("소분류를 기준으로 가상 공고와 MOCK 타입 모의 서류 지원을 생성한다")
    void createMockApply() {
        User user = saveUser("mock-apply@example.com");
        String companyName = "선택 기업 " + UUID.randomUUID();
        Company company = saveCompany(companyName, CompanySize.MEDIUM);
        DetailClassification detailClassification = saveDetailClassification("프론트엔드 개발");
        Long middleClassificationId = detailClassification.getMiddleClassification().getId();
        MockApplyCreateMockRequest request = new MockApplyCreateMockRequest(
                company.getId(),
                middleClassificationId,
                detailClassification.getId()
        );
        when(mockJobPostingGenerationService.generate(any()))
                .thenReturn(new JobPostingMockGenerateResponse(
                        companyName,
                        "프론트엔드 개발자",
                        "웹 프론트엔드 개발 및 운영",
                        "HTML/CSS/JavaScript 기본기",
                        "React 경험 우대",
                        "프론트엔드 직무 대상 모의 공고입니다.",
                        List.of("질문 1", "질문 2")
                ));

        MockApplyCreateResponse response = mockApplyService.createMockApply(user, request);

        MockApply mockApply = mockApplyRepository.findById(response.mockApplyId()).orElseThrow();
        JobPosting jobPosting = jobPostingRepository.findById(response.jobPostingId()).orElseThrow();
        assertThat(response.applyType()).isEqualTo(ApplyType.MOCK);
        assertThat(response.sequence()).isEqualTo(1);
        assertThat(mockApply.getUser().getId()).isEqualTo(user.getId());
        assertThat(mockApply.getApplyType()).isEqualTo(ApplyType.MOCK);
        assertThat(mockApply.getStatus()).isEqualTo(MockApplyStatus.APPLICATION_CREATED);
        assertThat(mockApply.getSequence()).isEqualTo(1);
        assertThat(jobPosting.getCompany().getId()).isEqualTo(company.getId());
        assertThat(jobPosting.getCompany().getName()).isEqualTo(companyName);
        assertThat(jobPosting.getCompany().getSize()).isEqualTo(CompanySize.MEDIUM);
        assertThat(jobPosting.getDetailClassification().getId()).isEqualTo(detailClassification.getId());
        assertThat(jobPosting.getTask()).isEqualTo("웹 프론트엔드 개발 및 운영");
        assertThat(jobPosting.getRequirement()).isEqualTo("HTML/CSS/JavaScript 기본기");
        assertThat(jobPosting.getPreferred()).isEqualTo("React 경험 우대");
    }

    @Test
    @DisplayName("저장된 공고를 기준으로 MOCK 타입 모의 서류 지원을 생성한다")
    void createMockApplyFromJobPosting() {
        User user = saveUser("mock-from-job-posting@example.com");
        JobPosting jobPosting = saveJobPosting(user, "백엔드 개발");

        MockApplyCreateResponse response = mockApplyService.createMockApplyFromJobPosting(user, jobPosting.getId());

        MockApply mockApply = mockApplyRepository.findById(response.mockApplyId()).orElseThrow();
        assertThat(response.jobPostingId()).isEqualTo(jobPosting.getId());
        assertThat(response.applyType()).isEqualTo(ApplyType.MOCK);
        assertThat(response.sequence()).isEqualTo(1);
        assertThat(mockApply.getUser().getId()).isEqualTo(user.getId());
        assertThat(mockApply.getJobPosting().getId()).isEqualTo(jobPosting.getId());
        assertThat(mockApply.getApplyType()).isEqualTo(ApplyType.MOCK);
        assertThat(mockApply.getSequence()).isEqualTo(1);
    }

    @Test
    @DisplayName("기존 지원의 공고에 새 회차와 문항을 생성한다")
    void retryMockApply() {
        User user = saveUser("retry-mock-apply@example.com");
        JobPosting jobPosting = saveJobPosting(user, "백엔드 개발");
        MockApply sourceMockApply = saveMockApply(user, jobPosting, ApplyType.MOCK, 1);
        saveQuestion(sourceMockApply, "지원 동기와 입사 후 목표를 작성해주세요.", 700, "기존 답변");
        saveQuestion(sourceMockApply, "직접 추가한 문항입니다.", 1000, "기존 직접 추가 답변");

        MockApplyRetryResponse response = mockApplyService.retryMockApply(user, sourceMockApply.getId());

        MockApply retryMockApply = mockApplyRepository.findById(response.mockApplyId()).orElseThrow();
        JobPosting retryJobPosting = jobPostingRepository.findById(response.jobPostingId()).orElseThrow();
        List<Question> retryQuestions = questionRepository.findAllByMockApplyIdOrderByIdAsc(response.mockApplyId());
        assertThat(response.sourceMockApplyId()).isEqualTo(sourceMockApply.getId());
        assertThat(response.jobPostingId()).isEqualTo(jobPosting.getId());
        assertThat(response.sequence()).isEqualTo(2);
        assertThat(response.status()).isEqualTo(MockApplyStatus.ANSWER_WRITE);
        assertThat(retryMockApply.getApplyType()).isEqualTo(ApplyType.MOCK);
        assertThat(retryMockApply.getJobPosting().getId()).isEqualTo(jobPosting.getId());
        assertThat(retryMockApply.getSequence()).isEqualTo(2);
        assertThat(retryMockApply.getStatus()).isEqualTo(MockApplyStatus.ANSWER_WRITE);
        assertThat(retryJobPosting.getId()).isEqualTo(jobPosting.getId());
        assertThat(retryJobPosting.getCompany().getId()).isEqualTo(jobPosting.getCompany().getId());
        assertThat(retryJobPosting.getDetailClassification().getId()).isEqualTo(jobPosting.getDetailClassification().getId());
        assertThat(retryJobPosting.getTask()).isEqualTo(jobPosting.getTask());
        assertThat(retryQuestions).hasSize(2);
        assertThat(retryQuestions)
                .extracting(Question::getContent)
                .containsExactly(
                        "지원 동기와 입사 후 목표를 작성해주세요.",
                        "직접 추가한 문항입니다."
                );
        assertThat(retryQuestions)
                .extracting(Question::getAnswer)
                .containsExactly("", "");
    }

    @Test
    @DisplayName("mockApplyId로 생성된 모의 공고를 조회한다")
    void getMockApplyJobPosting() {
        User user = saveUser("mock-job-posting@example.com");
        JobPosting jobPosting = saveJobPosting(user, "백엔드 개발");
        MockApply mockApply = mockApplyRepository.save(MockApply.create(user, jobPosting, ApplyType.MOCK));

        var response = mockApplyService.getMockApplyJobPosting(user, mockApply.getId());

        assertThat(response.getJobPostingId()).isEqualTo(jobPosting.getId());
        assertThat(response.getCompanyName()).isEqualTo(jobPosting.getCompany().getName());
    }

    @Test
    @DisplayName("같은 공고 기준 자소서 총 개수와 현재 순번을 조회한다")
    void getMockApplySequence() {
        User user = saveUser("sequence@example.com");
        JobPosting jobPosting = saveJobPosting(user, "백엔드 개발");
        MockApply first = mockApplyRepository.save(MockApply.create(user, jobPosting, ApplyType.MOCK));
        MockApply second = mockApplyRepository.save(MockApply.create(user, jobPosting, ApplyType.MOCK));
        MockApply third = mockApplyRepository.save(MockApply.create(user, jobPosting, ApplyType.ACTUAL));

        LocalDateTime baseTime = LocalDateTime.of(2026, 1, 1, 12, 0);
        ReflectionTestUtils.setField(first, "createdAt", baseTime);
        ReflectionTestUtils.setField(second, "createdAt", baseTime.plusMinutes(1));
        ReflectionTestUtils.setField(third, "createdAt", baseTime.plusMinutes(2));
        mockApplyRepository.saveAndFlush(first);
        mockApplyRepository.saveAndFlush(second);
        mockApplyRepository.saveAndFlush(third);

        MockApplySequenceResponse response = mockApplyService.getMockApplySequence(user, second.getId());

        assertThat(response.jobPostingId()).isEqualTo(jobPosting.getId());
        assertThat(response.mockApplyId()).isEqualTo(second.getId());
        assertThat(response.totalCount()).isEqualTo(3);
        assertThat(response.sequence()).isEqualTo(2);
    }

    @Test
    @DisplayName("홈 화면에서 이어쓰기와 완료 결과 카드 목록을 최신순 페이지로 조회한다")
    void getMyMockApplies() {
        User user = saveUser("home-list@example.com");
        User otherUser = saveUser("home-list-other@example.com");
        JobPosting backendPosting = saveJobPosting(user, "백엔드 개발");
        JobPosting dataPosting = saveJobPosting(user, "데이터 분석");
        JobPosting otherPosting = saveJobPosting(otherUser, "프론트엔드 개발");
        ReflectionTestUtils.setField(backendPosting, "profileColor", JobPostingProfileColor.BLUE);
        ReflectionTestUtils.setField(dataPosting, "profileColor", JobPostingProfileColor.GREEN);
        jobPostingRepository.saveAndFlush(backendPosting);
        jobPostingRepository.saveAndFlush(dataPosting);

        MockApply inProgress = mockApplyRepository.save(MockApply.create(user, backendPosting, ApplyType.ACTUAL));
        inProgress.updateStatus(MockApplyStatus.ANSWER_WRITE);
        inProgress.updateDisplayName("카카오 백엔드 지원 연습");
        MockApply completedFirst = mockApplyRepository.save(MockApply.create(user, dataPosting, ApplyType.MOCK));
        completedFirst.updateStatus(MockApplyStatus.COMPLETED);
        MockApply completedSecond = mockApplyRepository.save(MockApply.create(user, dataPosting, ApplyType.ACTUAL));
        completedSecond.updateStatus(MockApplyStatus.COMPLETED);
        mockApplyRepository.save(MockApply.create(otherUser, otherPosting, ApplyType.MOCK));
        Analysis firstAnalysis = analysisRepository.save(Analysis.create(completedFirst, 71, 72, 73, 74, "첫 완료 분석입니다."));
        completedFirst.assignAnalysis(firstAnalysis);
        Analysis secondAnalysis = analysisRepository.save(Analysis.create(completedSecond, 81, 82, 83, 84, "둘째 완료 분석입니다."));
        completedSecond.assignAnalysis(secondAnalysis);

        LocalDateTime baseTime = LocalDateTime.of(2026, 1, 1, 12, 0);
        ReflectionTestUtils.setField(inProgress, "createdAt", baseTime);
        ReflectionTestUtils.setField(completedFirst, "createdAt", baseTime.plusMinutes(1));
        ReflectionTestUtils.setField(completedSecond, "createdAt", baseTime.plusMinutes(2));
        mockApplyRepository.saveAndFlush(inProgress);
        mockApplyRepository.saveAndFlush(completedFirst);
        mockApplyRepository.saveAndFlush(completedSecond);

        MockApplyHomeResponse response = mockApplyService.getMyMockApplies(user, 0, 9);

        assertThat(response.inProgress()).hasSize(1);
        assertThat(response.completed().getContent()).hasSize(2);
        assertThat(response.inProgress().get(0).mockApplyId()).isEqualTo(inProgress.getId());
        assertThat(response.inProgress().get(0).jobPostingId()).isEqualTo(backendPosting.getId());
        assertThat(response.inProgress().get(0).displayName()).isEqualTo("카카오 백엔드 지원 연습");
        assertThat(response.inProgress().get(0).sequence()).isEqualTo(1);
        assertThat(response.inProgress().get(0).status()).isEqualTo(MockApplyStatus.ANSWER_WRITE);
        assertThat(response.inProgress().get(0).companyName()).isEqualTo("테스트 기업");
        assertThat(response.inProgress().get(0).detailClassificationName()).isEqualTo("백엔드 개발");
        assertThat(response.inProgress().get(0).jobTitle()).isEqualTo("백엔드 개발");
        assertThat(response.inProgress().get(0).profileColor()).isEqualTo(JobPostingProfileColor.BLUE);
        assertThat(response.inProgress().get(0).createdAt()).isEqualTo(baseTime);
        assertThat(response.inProgress().get(0).applyType()).isEqualTo(ApplyType.ACTUAL);
        assertThat(response.inProgress().get(0).score()).isNull();
        assertThat(response.inProgress().get(0).resumePath()).isEqualTo("/mock-applies/" + inProgress.getId() + "/answers");
        assertThat(response.completed().getContent()).extracting(MockApplyHomeItemResponse::mockApplyId)
                .containsExactly(completedSecond.getId(), completedFirst.getId());
        assertThat(response.completed().getTotalElements()).isEqualTo(2);
        assertThat(response.completed().getTotalPages()).isEqualTo(1);
        assertThat(response.completed().getSize()).isEqualTo(9);
        assertThat(response.completed().getNumber()).isEqualTo(0);
        assertThat(response.completed().getContent().get(0).createdAt()).isEqualTo(baseTime.plusMinutes(2));
        assertThat(response.completed().getContent().get(0).profileColor()).isEqualTo(JobPostingProfileColor.GREEN);
        assertThat(response.completed().getContent().get(0).displayName()).isNull();
        assertThat(response.completed().getContent().get(0).score()).isEqualTo(81);
        assertThat(response.completed().getContent().get(0).applyType()).isEqualTo(ApplyType.ACTUAL);
        assertThat(response.completed().getContent().get(0).resumePath()).isEqualTo("/mock-applies/" + completedSecond.getId() + "/analysis");
    }

    @Test
    @DisplayName("완료된 분석 결과 카드는 9개 기준으로 페이지 조회한다")
    void getMyMockAppliesCompletedResultsPaged() {
        User user = saveUser("home-page@example.com");
        JobPosting posting = saveJobPosting(user, "백엔드 개발");
        LocalDateTime baseTime = LocalDateTime.of(2026, 1, 1, 12, 0);
        List<Long> createdIds = new java.util.ArrayList<>();

        for (int i = 0; i < 10; i++) {
            MockApply completed = mockApplyRepository.save(MockApply.create(user, posting, ApplyType.MOCK, i + 1));
            completed.updateStatus(MockApplyStatus.COMPLETED);
            Analysis analysis = analysisRepository.save(Analysis.create(completed, 70 + i, 70, 70, 70, "완료 분석 " + i));
            completed.assignAnalysis(analysis);
            ReflectionTestUtils.setField(completed, "createdAt", baseTime.plusMinutes(i));
            mockApplyRepository.saveAndFlush(completed);
            createdIds.add(completed.getId());
        }

        MockApplyHomeResponse firstPage = mockApplyService.getMyMockApplies(user, 0, 9);
        MockApplyHomeResponse secondPage = mockApplyService.getMyMockApplies(user, 1, 9);

        assertThat(firstPage.completed().getContent()).hasSize(9);
        assertThat(firstPage.completed().getContent()).extracting(MockApplyHomeItemResponse::mockApplyId)
                .containsExactly(
                        createdIds.get(9),
                        createdIds.get(8),
                        createdIds.get(7),
                        createdIds.get(6),
                        createdIds.get(5),
                        createdIds.get(4),
                        createdIds.get(3),
                        createdIds.get(2),
                        createdIds.get(1)
                );
        assertThat(firstPage.completed().getTotalElements()).isEqualTo(10);
        assertThat(firstPage.completed().getTotalPages()).isEqualTo(2);
        assertThat(firstPage.completed().hasNext()).isTrue();
        assertThat(secondPage.completed().getContent()).hasSize(1);
        assertThat(secondPage.completed().getContent()).extracting(MockApplyHomeItemResponse::mockApplyId)
                .containsExactly(createdIds.get(0));
        assertThat(secondPage.completed().getNumber()).isEqualTo(1);
        assertThat(secondPage.completed().hasNext()).isFalse();
    }

    @Test
    @DisplayName("모의 서류 지원 이름을 변경한다")
    void updateMockApplyName() {
        User user = saveUser("mock-apply-name@example.com");
        JobPosting jobPosting = saveJobPosting(user, "백엔드 개발");
        MockApply mockApply = saveMockApply(user, jobPosting, ApplyType.MOCK, 1);

        MockApplyUpdateNameResponse response = mockApplyService.updateMockApplyName(
                user,
                mockApply.getId(),
                "  카카오 백엔드 지원 연습  "
        );
        mockApplyRepository.flush();

        MockApply updated = mockApplyRepository.findById(mockApply.getId()).orElseThrow();
        assertThat(response.mockApplyId()).isEqualTo(mockApply.getId());
        assertThat(response.name()).isEqualTo("카카오 백엔드 지원 연습");
        assertThat(response.updatedAt()).isNotNull();
        assertThat(updated.getDisplayName()).isEqualTo("카카오 백엔드 지원 연습");
    }

    @Test
    @DisplayName("모의 서류 지원 이름은 여러 번 변경할 수 있다")
    void updateMockApplyNameAgain() {
        User user = saveUser("mock-apply-name-again@example.com");
        JobPosting jobPosting = saveJobPosting(user, "백엔드 개발");
        MockApply mockApply = saveMockApply(user, jobPosting, ApplyType.MOCK, 1);

        mockApplyService.updateMockApplyName(user, mockApply.getId(), "첫 번째 이름");
        MockApplyUpdateNameResponse response = mockApplyService.updateMockApplyName(user, mockApply.getId(), "두 번째 이름");

        assertThat(response.name()).isEqualTo("두 번째 이름");
        assertThat(mockApplyRepository.findById(mockApply.getId()).orElseThrow().getDisplayName())
                .isEqualTo("두 번째 이름");
    }

    @Test
    @DisplayName("빈 이름으로 모의 서류 지원 이름을 변경할 수 없다")
    void updateMockApplyNameRejectsBlankName() {
        User user = saveUser("mock-apply-name-blank@example.com");
        JobPosting jobPosting = saveJobPosting(user, "백엔드 개발");
        MockApply mockApply = saveMockApply(user, jobPosting, ApplyType.MOCK, 1);

        assertThatThrownBy(() -> mockApplyService.updateMockApplyName(user, mockApply.getId(), "   "))
                .isInstanceOf(GeneralException.class)
                .extracting("code")
                .isEqualTo(GeneralErrorCode.INVALID_PARAMETER);
    }

    @Test
    @DisplayName("100자를 초과한 이름으로 모의 서류 지원 이름을 변경할 수 없다")
    void updateMockApplyNameRejectsTooLongName() {
        User user = saveUser("mock-apply-name-too-long@example.com");
        JobPosting jobPosting = saveJobPosting(user, "백엔드 개발");
        MockApply mockApply = saveMockApply(user, jobPosting, ApplyType.MOCK, 1);

        assertThatThrownBy(() -> mockApplyService.updateMockApplyName(user, mockApply.getId(), "가".repeat(101)))
                .isInstanceOf(GeneralException.class)
                .extracting("code")
                .isEqualTo(GeneralErrorCode.INVALID_PARAMETER);
    }

    @Test
    @DisplayName("다른 사용자의 모의 서류 지원 이름은 변경할 수 없다")
    void updateMockApplyNameRejectsOtherUserMockApply() {
        User user = saveUser("mock-apply-name-owner@example.com");
        User otherUser = saveUser("mock-apply-name-other@example.com");
        JobPosting jobPosting = saveJobPosting(otherUser, "백엔드 개발");
        MockApply mockApply = saveMockApply(otherUser, jobPosting, ApplyType.MOCK, 1);

        assertThatThrownBy(() -> mockApplyService.updateMockApplyName(user, mockApply.getId(), "변경 이름"))
                .isInstanceOf(GeneralException.class)
                .extracting("code")
                .isEqualTo(GeneralErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("없는 모의 서류 지원 이름은 변경할 수 없다")
    void updateMockApplyNameRejectsMissingMockApply() {
        User user = saveUser("mock-apply-name-missing@example.com");

        assertThatThrownBy(() -> mockApplyService.updateMockApplyName(user, 999_999L, "변경 이름"))
                .isInstanceOf(GeneralException.class)
                .extracting("code")
                .isEqualTo(GeneralErrorCode.MOCK_APPLY_NOT_FOUND);
    }

    @Test
    @DisplayName("모의 서류 지원을 삭제하면 해당 지원의 문항과 분석만 삭제한다")
    void deleteMockApplyDeletesOnlyTargetMockApplyResults() {
        User user = saveUser("mock-apply-delete@example.com");
        JobPosting jobPosting = saveJobPosting(user, "백엔드 개발");
        MockApply target = saveMockApply(user, jobPosting, ApplyType.MOCK, 1);
        MockApply remaining = saveMockApply(user, jobPosting, ApplyType.MOCK, 2);
        Question targetQuestion = saveQuestion(target, "삭제 대상 문항", 1000, "삭제 대상 답변");
        Question remainingQuestion = saveQuestion(remaining, "유지 대상 문항", 1000, "유지 대상 답변");
        Analysis targetAnalysis = saveAnalysis(target, 70);
        Analysis remainingAnalysis = saveAnalysis(remaining, 80);
        QuestionAnalysis targetQuestionAnalysis = saveQuestionAnalysis(targetQuestion, targetAnalysis);
        QuestionAnalysis remainingQuestionAnalysis = saveQuestionAnalysis(remainingQuestion, remainingAnalysis);

        mockApplyService.deleteMockApply(user, target.getId());
        mockApplyRepository.flush();

        assertThat(mockApplyRepository.findById(target.getId())).isEmpty();
        assertThat(questionRepository.findById(targetQuestion.getId())).isEmpty();
        assertThat(analysisRepository.findById(targetAnalysis.getId())).isEmpty();
        assertThat(questionAnalysisRepository.findById(targetQuestionAnalysis.getId())).isEmpty();
        assertThat(jobPostingRepository.findById(jobPosting.getId())).isPresent();
        assertThat(mockApplyRepository.findById(remaining.getId())).isPresent();
        assertThat(questionRepository.findById(remainingQuestion.getId())).isPresent();
        assertThat(analysisRepository.findById(remainingAnalysis.getId())).isPresent();
        assertThat(questionAnalysisRepository.findById(remainingQuestionAnalysis.getId())).isPresent();
    }

    @Test
    @DisplayName("존재하지 않는 공고 ID로 ACTUAL 타입 지원 생성 시 예외를 던진다")
    void createActualApplyThrowsWhenJobPostingNotFound() {
        User user = saveUser("missing-job-posting@example.com");

        assertThatThrownBy(() -> mockApplyService.createActualApply(user, 9999L))
                .isInstanceOf(GeneralException.class)
                .extracting("code")
                .isEqualTo(GeneralErrorCode.JOB_POSTING_NOT_FOUND);
    }

    @Test
    @DisplayName("다른 사용자의 공고로 MOCK 타입 지원 생성 시 예외를 던진다")
    void createMockApplyFromJobPostingThrowsWhenForbidden() {
        User owner = saveUser("mock-owner@example.com");
        User otherUser = saveUser("mock-other@example.com");
        JobPosting jobPosting = saveJobPosting(owner, "프론트엔드 개발");

        assertThatThrownBy(() -> mockApplyService.createMockApplyFromJobPosting(otherUser, jobPosting.getId()))
                .isInstanceOf(GeneralException.class)
                .extracting("code")
                .isEqualTo(GeneralErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("존재하지 않는 소분류 ID로 MOCK 타입 지원 생성 시 예외를 던진다")
    void createMockApplyThrowsWhenDetailClassificationNotFound() {
        User user = saveUser("missing-detail-classification@example.com");
        Company company = saveCompany("선택 기업 " + UUID.randomUUID(), CompanySize.MEDIUM);
        MockApplyCreateMockRequest request = new MockApplyCreateMockRequest(company.getId(), 1L, 9999L);
        when(mockJobPostingGenerationService.generate(any()))
                .thenThrow(new GeneralException(
                        GeneralErrorCode.CLASSIFICATION_NOT_FOUND,
                        "해당 소분류를 찾을 수 없습니다. detailClassificationId=9999"
                ));

        assertThatThrownBy(() -> mockApplyService.createMockApply(user, request))
                .isInstanceOf(GeneralException.class)
                .extracting("code")
                .isEqualTo(GeneralErrorCode.CLASSIFICATION_NOT_FOUND);
    }

    @Test
    @DisplayName("존재하지 않는 회사 ID로 MOCK 타입 지원 생성 시 예외를 던진다")
    void createMockApplyThrowsWhenCompanyNotFound() {
        User user = saveUser("missing-company@example.com");
        MockApplyCreateMockRequest request = new MockApplyCreateMockRequest(9999L, 1L, 1L);

        assertThatThrownBy(() -> mockApplyService.createMockApply(user, request))
                .isInstanceOf(GeneralException.class)
                .extracting("code")
                .isEqualTo(GeneralErrorCode.COMPANY_NOT_FOUND);
    }

    @Test
    @DisplayName("소분류가 중분류에 속하지 않으면 MOCK 타입 지원 생성 시 예외를 던진다")
    void createMockApplyThrowsWhenMiddleClassificationMismatched() {
        User user = saveUser("middle-mismatch@example.com");
        Company company = saveCompany("선택 기업 " + UUID.randomUUID(), CompanySize.MEDIUM);
        DetailClassification detailClassification = saveDetailClassification("데이터 분석");
        MockApplyCreateMockRequest request = new MockApplyCreateMockRequest(company.getId(), 9999L, detailClassification.getId());
        when(mockJobPostingGenerationService.generate(any()))
                .thenThrow(new GeneralException(
                        GeneralErrorCode.CLASSIFICATION_NOT_FOUND,
                        "해당 소분류가 중분류에 속하지 않습니다. middleClassificationId=9999, detailClassificationId=" + detailClassification.getId()
                ));

        assertThatThrownBy(() -> mockApplyService.createMockApply(user, request))
                .isInstanceOf(GeneralException.class)
                .extracting("code")
                .isEqualTo(GeneralErrorCode.CLASSIFICATION_NOT_FOUND);
    }

    @Test
    @DisplayName("다른 사용자의 mockApplyId로 공고 조회 시 예외를 던진다")
    void getMockApplyJobPostingThrowsWhenForbidden() {
        User owner = saveUser("owner@example.com");
        User otherUser = saveUser("other@example.com");
        JobPosting jobPosting = saveJobPosting(owner, "데이터 분석");
        MockApply mockApply = mockApplyRepository.save(MockApply.create(owner, jobPosting, ApplyType.MOCK));

        assertThatThrownBy(() -> mockApplyService.getMockApplyJobPosting(otherUser, mockApply.getId()))
                .isInstanceOf(GeneralException.class)
                .extracting("code")
                .isEqualTo(GeneralErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("다른 사용자의 모의 서류 지원은 삭제할 수 없다")
    void deleteMockApplyThrowsWhenForbidden() {
        User owner = saveUser("delete-owner@example.com");
        User otherUser = saveUser("delete-other@example.com");
        JobPosting jobPosting = saveJobPosting(owner, "데이터 분석");
        MockApply mockApply = saveMockApply(owner, jobPosting, ApplyType.MOCK, 1);

        assertThatThrownBy(() -> mockApplyService.deleteMockApply(otherUser, mockApply.getId()))
                .isInstanceOf(GeneralException.class)
                .extracting("code")
                .isEqualTo(GeneralErrorCode.FORBIDDEN);

        assertThat(mockApplyRepository.findById(mockApply.getId())).isPresent();
    }

    private User saveUser(String email) {
        return inNewTransaction(() -> userRepository.save(User.signup("테스트 사용자", email, "encoded-password")));
    }

    private JobPosting saveJobPosting(User user, String detailName) {
        return inNewTransaction(() -> {
            Company company = companyRepository.save(Company.create("테스트 기업", CompanySize.MEDIUM));
            DetailClassification detailClassification = saveDetailClassificationInCurrentTransaction(detailName);
            return jobPostingRepository.save(JobPosting.create(
                    user,
                    company,
                    detailClassification,
                    "주요 업무",
                    "자격 요건",
                    "우대 사항"
            ));
        });
    }

    private JobPosting saveJobPosting(
            User user,
            Company company,
            DetailClassification detailClassification,
            String task
    ) {
        return inNewTransaction(() -> jobPostingRepository.save(JobPosting.create(
                userRepository.findById(user.getId()).orElseThrow(),
                companyRepository.findById(company.getId()).orElseThrow(),
                detailClassificationRepository.findById(detailClassification.getId()).orElseThrow(),
                task,
                "자격 요건",
                "우대 사항"
        )));
    }

    private DetailClassification saveDetailClassification(String detailName) {
        return inNewTransaction(() -> saveDetailClassificationInCurrentTransaction(detailName));
    }

    private DetailClassification saveDetailClassificationInCurrentTransaction(String detailName) {
        Classification classification = Classification.create("테스트 대분류 " + detailName + " " + UUID.randomUUID());
        MiddleClassification middleClassification = classification.addMiddleClassification("테스트 중분류 " + detailName);
        DetailClassification detailClassification = middleClassification.addDetailClassification(detailName);
        classificationRepository.save(classification);
        return detailClassificationRepository.findById(detailClassification.getId()).orElseThrow();
    }

    private Company saveCompany(String name, CompanySize size) {
        return inNewTransaction(() -> companyRepository.save(Company.create(name, size)));
    }

    private MockApply saveMockApply(User user, JobPosting jobPosting, ApplyType applyType, Integer sequence) {
        return inNewTransaction(() -> mockApplyRepository.saveAndFlush(
                MockApply.create(user, jobPosting, applyType, sequence)
        ));
    }

    private Question saveQuestion(MockApply mockApply, String content, int limit, String answer) {
        return inNewTransaction(() -> questionRepository.save(Question.create(
                mockApplyRepository.findById(mockApply.getId()).orElseThrow(),
                content,
                limit,
                answer
        )));
    }

    private Analysis saveAnalysis(MockApply mockApply, int score) {
        return inNewTransaction(() -> analysisRepository.save(Analysis.create(
                mockApplyRepository.findById(mockApply.getId()).orElseThrow(),
                score,
                score,
                score,
                score,
                "분석 결과입니다."
        )));
    }

    private QuestionAnalysis saveQuestionAnalysis(Question question, Analysis analysis) {
        return inNewTransaction(() -> questionAnalysisRepository.save(QuestionAnalysis.create(
                questionRepository.findById(question.getId()).orElseThrow(),
                analysisRepository.findById(analysis.getId()).orElseThrow(),
                "답변입니다.",
                "근거가 부족합니다.",
                "구체적인 성과를 포함해 답변했습니다.",
                QuestionAnalysisStatus.MENTIONED,
                0,
                5
        )));
    }

    private <T> T inNewTransaction(Supplier<T> action) {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return transactionTemplate.execute(status -> action.get());
    }
}
