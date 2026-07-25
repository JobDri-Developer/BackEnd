package com.jobdri.jobdri_api.domain.jobposting.service;

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
import com.jobdri.jobdri_api.domain.jobposting.dto.request.JobPostingCreateRequest;
import com.jobdri.jobdri_api.domain.jobposting.dto.request.JobPostingUpdateRequest;
import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingResponse;
import com.jobdri.jobdri_api.domain.jobposting.entity.JobPosting;
import com.jobdri.jobdri_api.domain.jobposting.entity.JobPostingProfileColor;
import com.jobdri.jobdri_api.domain.jobposting.repository.JobPostingRepository;
import com.jobdri.jobdri_api.domain.mockapply.entity.ApplyType;
import com.jobdri.jobdri_api.domain.mockapply.entity.MockApply;
import com.jobdri.jobdri_api.domain.mockapply.entity.MockApplySequence;
import com.jobdri.jobdri_api.domain.mockapply.repository.MockApplyRepository;
import com.jobdri.jobdri_api.domain.mockapply.repository.MockApplySequenceRepository;
import com.jobdri.jobdri_api.domain.user.entity.User;
import com.jobdri.jobdri_api.domain.user.repository.UserRepository;
import com.jobdri.jobdri_api.global.apiPayload.code.GeneralErrorCode;
import com.jobdri.jobdri_api.global.apiPayload.exception.GeneralException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class JobPostingServiceTest {

    @Autowired
    private JobPostingService jobPostingService;

    @Autowired
    private JobPostingRepository jobPostingRepository;

    @Autowired
    private MockApplyRepository mockApplyRepository;

    @Autowired
    private MockApplySequenceRepository mockApplySequenceRepository;

    @Autowired
    private QuestionRepository questionRepository;

    @Autowired
    private AnalysisRepository analysisRepository;

    @Autowired
    private QuestionAnalysisRepository questionAnalysisRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private ClassificationRepository classificationRepository;

    @Autowired
    private DetailClassificationRepository detailClassificationRepository;

    @Test
    @DisplayName("채용 공고 프로필 색상, 공고명, 직무명을 생성하고 조회한다")
    void createAndGetJobPostingIncludesEditableProfileFields() {
        User user = saveUser("job-posting-create@example.com");
        DetailClassification detailClassification = saveDetailClassification();

        JobPostingResponse created = jobPostingService.createJobPosting(
                user,
                new JobPostingCreateRequest(
                        JobPostingProfileColor.LIGHTBLUE,
                        "여름 인턴 채용",
                        "생성 테스트 기업",
                        CompanySize.LARGE,
                        "백엔드 엔지니어",
                        detailClassification.getId(),
                        "주요 업무",
                        "자격 요건",
                        "우대 사항"
                )
        );

        JobPostingResponse found = jobPostingService.getJobPosting(user, created.getJobPostingId());

        assertThat(found.getProfileColor()).isEqualTo(JobPostingProfileColor.LIGHTBLUE);
        assertThat(found.getPostingName()).isEqualTo("여름 인턴 채용");
        assertThat(found.getJobTitle()).isEqualTo("백엔드 엔지니어");
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("채용 공고 프로필 색상, 공고명, 직무명을 수정한다")
    void updateJobPostingUpdatesEditableProfileFields() {
        User user = saveUser("job-posting-update@example.com");
        JobPosting jobPosting = saveJobPosting(user);
        DetailClassification detailClassification = jobPosting.getDetailClassification();

        JobPostingResponse updated = jobPostingService.updateJobPosting(
                user,
                jobPosting.getId(),
                new JobPostingUpdateRequest(
                        JobPostingProfileColor.PINK,
                        "수정된 공고명",
                        jobPosting.getCompany().getName(),
                        jobPosting.getCompany().getSize(),
                        "수정된 직무명",
                        detailClassification.getId(),
                        "수정된 주요 업무",
                        "수정된 자격 요건",
                        "수정된 우대 사항"
                )
        );

        assertThat(updated.getProfileColor()).isEqualTo(JobPostingProfileColor.PINK);
        assertThat(updated.getPostingName()).isEqualTo("수정된 공고명");
        assertThat(updated.getJobTitle()).isEqualTo("수정된 직무명");
    }

    @Test
    @DisplayName("채용 공고를 삭제하면 연결된 모의 서류 결과도 함께 삭제한다")
    void deleteJobPostingDeletesMockApplyResults() {
        User user = saveUser("job-posting-delete@example.com");
        JobPosting jobPosting = saveJobPosting(user);
        MockApply mockApply = mockApplyRepository.save(MockApply.create(user, jobPosting, ApplyType.MOCK, 1));
        Question question = questionRepository.save(Question.create(mockApply, "지원 동기", 1000, "답변입니다."));
        Analysis analysis = analysisRepository.save(Analysis.create(mockApply, 80, 81, 82, 83, "분석 결과입니다."));
        QuestionAnalysis questionAnalysis = questionAnalysisRepository.save(QuestionAnalysis.create(
                question,
                analysis,
                "답변입니다.",
                "근거가 부족합니다.",
                "구체적인 성과를 포함해 답변했습니다.",
                QuestionAnalysisStatus.MENTIONED,
                0,
                5
        ));
        mockApplySequenceRepository.save(MockApplySequence.create(user.getId(), jobPosting.getId(), 1));

        jobPostingService.deleteJobPosting(user, jobPosting.getId());
        jobPostingRepository.flush();

        assertThat(jobPostingRepository.findById(jobPosting.getId())).isEmpty();
        assertThat(mockApplyRepository.findById(mockApply.getId())).isEmpty();
        assertThat(questionRepository.findById(question.getId())).isEmpty();
        assertThat(analysisRepository.findById(analysis.getId())).isEmpty();
        assertThat(questionAnalysisRepository.findById(questionAnalysis.getId())).isEmpty();
        assertThat(mockApplySequenceRepository.findByKeyForUpdate(user.getId(), jobPosting.getId())).isEmpty();
    }

    @Test
    @DisplayName("다른 사용자의 채용 공고는 삭제할 수 없다")
    void deleteJobPostingThrowsWhenUserDoesNotOwnJobPosting() {
        User owner = saveUser("job-posting-delete-owner@example.com");
        User other = saveUser("job-posting-delete-other@example.com");
        JobPosting jobPosting = saveJobPosting(owner);

        assertThatThrownBy(() -> jobPostingService.deleteJobPosting(other, jobPosting.getId()))
                .isInstanceOf(GeneralException.class)
                .extracting("code")
                .isEqualTo(GeneralErrorCode.FORBIDDEN);

        assertThat(jobPostingRepository.findById(jobPosting.getId())).isPresent();
    }

    @Test
    @DisplayName("내 채용 공고 목록은 페이지네이션으로 최신순 조회된다")
    void getAllJobPostingsReturnsPagedNewestFirst() {
        User user = saveUser("job-posting-list@example.com");
        DetailClassification detailClassification = saveDetailClassification();

        JobPostingResponse first = jobPostingService.createJobPosting(
                user,
                new JobPostingCreateRequest(
                        JobPostingProfileColor.DEFAULT,
                        "첫 번째 공고",
                        "목록 테스트 기업 A",
                        CompanySize.SMALL,
                        "백엔드 엔지니어",
                        detailClassification.getId(),
                        "주요 업무 A",
                        "자격 요건 A",
                        "우대 사항 A"
                )
        );

        JobPostingResponse second = jobPostingService.createJobPosting(
                user,
                new JobPostingCreateRequest(
                        JobPostingProfileColor.BLUE,
                        "두 번째 공고",
                        "목록 테스트 기업 B",
                        CompanySize.MEDIUM,
                        "프론트엔드 엔지니어",
                        detailClassification.getId(),
                        "주요 업무 B",
                        "자격 요건 B",
                        "우대 사항 B"
                )
        );

        JobPostingResponse third = jobPostingService.createJobPosting(
                user,
                new JobPostingCreateRequest(
                        JobPostingProfileColor.PINK,
                        "세 번째 공고",
                        "목록 테스트 기업 C",
                        CompanySize.LARGE,
                        "데이터 엔지니어",
                        detailClassification.getId(),
                        "주요 업무 C",
                        "자격 요건 C",
                        "우대 사항 C"
                )
        );

        JobPostingResponse fourth = jobPostingService.createJobPosting(
                user,
                new JobPostingCreateRequest(
                        JobPostingProfileColor.GREEN,
                        "네 번째 공고",
                        "목록 테스트 기업 D",
                        CompanySize.MEDIUM,
                        "모바일 엔지니어",
                        detailClassification.getId(),
                        "주요 업무 D",
                        "자격 요건 D",
                        "우대 사항 D"
                )
        );

        Page<JobPostingResponse> firstPage = jobPostingService.getAllJobPostings(user, 0, 3);
        Page<JobPostingResponse> secondPage = jobPostingService.getAllJobPostings(user, 1, 3);

        assertThat(firstPage.getContent()).extracting(JobPostingResponse::getJobPostingId)
                .containsExactly(fourth.getJobPostingId(), third.getJobPostingId(), second.getJobPostingId());
        assertThat(firstPage.getTotalElements()).isEqualTo(4);
        assertThat(firstPage.getTotalPages()).isEqualTo(2);
        assertThat(firstPage.getSize()).isEqualTo(3);
        assertThat(firstPage.getNumber()).isEqualTo(0);
        assertThat(firstPage.hasNext()).isTrue();

        assertThat(secondPage.getContent()).extracting(JobPostingResponse::getJobPostingId)
                .containsExactly(first.getJobPostingId());
        assertThat(secondPage.getNumber()).isEqualTo(1);
        assertThat(secondPage.hasNext()).isFalse();
        assertThat(firstPage.getContent()).allSatisfy(response -> {
            assertThat(response.getCreatedAt()).isNotNull();
            assertThat(response.getUpdatedAt()).isNotNull();
        });
        assertThat(secondPage.getContent()).allSatisfy(response -> {
            assertThat(response.getCreatedAt()).isNotNull();
            assertThat(response.getUpdatedAt()).isNotNull();
        });
    }

    @Test
    @DisplayName("채용 공고 목록 페이지 크기는 최대값으로 제한된다")
    void getAllJobPostingsCapsOversizedPageSize() {
        User user = saveUser("job-posting-max-page-size@example.com");
        DetailClassification detailClassification = saveDetailClassification();

        for (int i = 0; i < 4; i++) {
            jobPostingService.createJobPosting(
                    user,
                    new JobPostingCreateRequest(
                            JobPostingProfileColor.DEFAULT,
                            "공고 " + i,
                            "목록 테스트 기업 " + i,
                            CompanySize.SMALL,
                            "백엔드 엔지니어 " + i,
                            detailClassification.getId(),
                            "주요 업무 " + i,
                            "자격 요건 " + i,
                            "우대 사항 " + i
                    )
            );
        }

        Page<JobPostingResponse> page = jobPostingService.getAllJobPostings(user, 0, JobPostingService.MAX_PAGE_SIZE + 50);

        assertThat(page.getSize()).isEqualTo(JobPostingService.MAX_PAGE_SIZE);
        assertThat(page.getContent()).hasSize(4);
        assertThat(page.getTotalElements()).isEqualTo(4);
    }

    private User saveUser(String email) {
        return userRepository.save(User.signup("테스트 사용자", email, "encoded-password"));
    }

    private JobPosting saveJobPosting(User user) {
        Company company = companyRepository.save(Company.create("삭제 테스트 기업 " + UUID.randomUUID(), CompanySize.MEDIUM));
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
        Classification classification = Classification.create("삭제 테스트 대분류 " + UUID.randomUUID());
        MiddleClassification middleClassification = classification.addMiddleClassification("삭제 테스트 중분류");
        DetailClassification detailClassification = middleClassification.addDetailClassification("삭제 테스트 소분류");
        classificationRepository.save(classification);
        return detailClassificationRepository.findById(detailClassification.getId()).orElseThrow();
    }
}
