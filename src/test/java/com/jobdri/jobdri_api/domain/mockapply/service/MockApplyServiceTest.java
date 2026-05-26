package com.jobdri.jobdri_api.domain.mockapply.service;

import com.jobdri.jobdri_api.domain.analysis.entity.Analysis;
import com.jobdri.jobdri_api.domain.analysis.repository.AnalysisRepository;
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
import com.jobdri.jobdri_api.domain.jobposting.repository.JobPostingRepository;
import com.jobdri.jobdri_api.domain.jobposting.service.MockJobPostingGenerationService;
import com.jobdri.jobdri_api.domain.mockapply.dto.request.MockApplyCreateMockRequest;
import com.jobdri.jobdri_api.domain.mockapply.dto.response.MockApplyCreateResponse;
import com.jobdri.jobdri_api.domain.mockapply.dto.response.MockApplyHomeResponse;
import com.jobdri.jobdri_api.domain.mockapply.dto.response.MockApplySequenceResponse;
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
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

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
    @DisplayName("요청 순번이 0 이하이면 다음 유효 순번을 저장한다")
    void createActualApplyIgnoresNonPositiveRequestedSequence() {
        User user = saveUser("actual-apply-non-positive-sequence@example.com");
        JobPosting jobPosting = saveJobPosting(user, "백엔드 개발");
        mockApplyService.createActualApply(user, jobPosting.getId());

        MockApplyCreateResponse zeroResponse = mockApplyService.createActualApply(user, jobPosting.getId(), 0);
        MockApplyCreateResponse negativeResponse = mockApplyService.createActualApply(user, jobPosting.getId(), -1);

        MockApply zeroSequenceApply = mockApplyRepository.findById(zeroResponse.mockApplyId()).orElseThrow();
        MockApply negativeSequenceApply = mockApplyRepository.findById(negativeResponse.mockApplyId()).orElseThrow();
        assertThat(zeroResponse.sequence()).isEqualTo(2);
        assertThat(negativeResponse.sequence()).isEqualTo(3);
        assertThat(zeroSequenceApply.getSequence()).isEqualTo(2);
        assertThat(negativeSequenceApply.getSequence()).isEqualTo(3);
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
        Company company = companyRepository.save(Company.create("선택 기업", CompanySize.MEDIUM));
        DetailClassification detailClassification = saveDetailClassification("프론트엔드 개발");
        Long middleClassificationId = detailClassification.getMiddleClassification().getId();
        MockApplyCreateMockRequest request = new MockApplyCreateMockRequest(
                company.getId(),
                middleClassificationId,
                detailClassification.getId()
        );
        when(mockJobPostingGenerationService.generate(any()))
                .thenReturn(new JobPostingMockGenerateResponse(
                        "선택 기업",
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
        assertThat(jobPosting.getCompany().getName()).isEqualTo("선택 기업");
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
    @DisplayName("홈 화면에서 이어쓰기와 완료 결과 카드 목록을 조회한다")
    void getMyMockApplies() {
        User user = saveUser("home-list@example.com");
        User otherUser = saveUser("home-list-other@example.com");
        JobPosting backendPosting = saveJobPosting(user, "백엔드 개발");
        JobPosting dataPosting = saveJobPosting(user, "데이터 분석");
        JobPosting otherPosting = saveJobPosting(otherUser, "프론트엔드 개발");

        MockApply inProgress = mockApplyRepository.save(MockApply.create(user, backendPosting, ApplyType.ACTUAL));
        inProgress.updateStatus(MockApplyStatus.ANSWER_WRITE);
        MockApply completed = mockApplyRepository.save(MockApply.create(user, dataPosting, ApplyType.MOCK));
        completed.updateStatus(MockApplyStatus.COMPLETED);
        mockApplyRepository.save(MockApply.create(otherUser, otherPosting, ApplyType.MOCK));
        Analysis analysis = analysisRepository.save(Analysis.create(completed, 71, 72, 73, 74, "완료 분석입니다."));
        completed.assignAnalysis(analysis);

        MockApplyHomeResponse response = mockApplyService.getMyMockApplies(user);

        assertThat(response.inProgress()).hasSize(1);
        assertThat(response.completed()).hasSize(1);
        assertThat(response.inProgress().get(0).mockApplyId()).isEqualTo(inProgress.getId());
        assertThat(response.inProgress().get(0).jobPostingId()).isEqualTo(backendPosting.getId());
        assertThat(response.inProgress().get(0).status()).isEqualTo(MockApplyStatus.ANSWER_WRITE);
        assertThat(response.inProgress().get(0).companyName()).isEqualTo("테스트 기업");
        assertThat(response.inProgress().get(0).detailClassificationName()).isEqualTo("백엔드 개발");
        assertThat(response.inProgress().get(0).jobTitle()).isEqualTo("백엔드 개발");
        assertThat(response.inProgress().get(0).applyType()).isEqualTo(ApplyType.ACTUAL);
        assertThat(response.inProgress().get(0).score()).isNull();
        assertThat(response.inProgress().get(0).resumePath()).isEqualTo("/mock-applies/" + inProgress.getId() + "/answers");
        assertThat(response.completed().get(0).mockApplyId()).isEqualTo(completed.getId());
        assertThat(response.completed().get(0).score()).isEqualTo(71);
        assertThat(response.completed().get(0).applyType()).isEqualTo(ApplyType.MOCK);
        assertThat(response.completed().get(0).resumePath()).isEqualTo("/mock-applies/" + completed.getId() + "/analysis");
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
        Company company = companyRepository.save(Company.create("선택 기업", CompanySize.MEDIUM));
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
        Company company = companyRepository.save(Company.create("선택 기업", CompanySize.MEDIUM));
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

    private User saveUser(String email) {
        return userRepository.save(User.signup("테스트 사용자", email, "encoded-password"));
    }

    private JobPosting saveJobPosting(User user, String detailName) {
        Company company = companyRepository.save(Company.create("테스트 기업", CompanySize.MEDIUM));
        DetailClassification detailClassification = saveDetailClassification(detailName);
        return jobPostingRepository.save(JobPosting.create(
                user,
                company,
                detailClassification,
                "주요 업무",
                "자격 요건",
                "우대 사항"
        ));
    }

    private DetailClassification saveDetailClassification(String detailName) {
        Classification classification = Classification.create("테스트 대분류 " + detailName);
        MiddleClassification middleClassification = classification.addMiddleClassification("테스트 중분류 " + detailName);
        DetailClassification detailClassification = middleClassification.addDetailClassification(detailName);
        classificationRepository.save(classification);
        return detailClassificationRepository.findById(detailClassification.getId()).orElseThrow();
    }
}
