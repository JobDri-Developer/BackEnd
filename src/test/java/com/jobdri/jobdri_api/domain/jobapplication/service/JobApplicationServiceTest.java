package com.jobdri.jobdri_api.domain.jobapplication.service;

import com.jobdri.jobdri_api.domain.classification.entity.Classification;
import com.jobdri.jobdri_api.domain.classification.entity.DetailClassification;
import com.jobdri.jobdri_api.domain.classification.entity.MiddleClassification;
import com.jobdri.jobdri_api.domain.classification.repository.ClassificationRepository;
import com.jobdri.jobdri_api.domain.classification.repository.DetailClassificationRepository;
import com.jobdri.jobdri_api.domain.company.entity.Company;
import com.jobdri.jobdri_api.domain.company.entity.CompanySize;
import com.jobdri.jobdri_api.domain.company.repository.CompanyRepository;
import com.jobdri.jobdri_api.domain.jobapplication.dto.request.JobApplicationCreateRequest;
import com.jobdri.jobdri_api.domain.jobapplication.dto.request.JobApplicationFromJobPostingRequest;
import com.jobdri.jobdri_api.domain.jobapplication.dto.response.JobApplicationResponse;
import com.jobdri.jobdri_api.domain.jobapplication.entity.JobApplicationStage;
import com.jobdri.jobdri_api.domain.jobposting.entity.JobPosting;
import com.jobdri.jobdri_api.domain.jobposting.repository.JobPostingRepository;
import com.jobdri.jobdri_api.domain.jobposting.service.JobPostingService;
import com.jobdri.jobdri_api.domain.mockapply.repository.MockApplyRepository;
import com.jobdri.jobdri_api.domain.user.entity.User;
import com.jobdri.jobdri_api.domain.user.repository.UserRepository;
import com.jobdri.jobdri_api.global.apiPayload.code.GeneralErrorCode;
import com.jobdri.jobdri_api.global.apiPayload.exception.GeneralException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class JobApplicationServiceTest {

    @Autowired JobApplicationService jobApplicationService;
    @Autowired JobPostingService jobPostingService;
    @Autowired JobPostingRepository jobPostingRepository;
    @Autowired MockApplyRepository mockApplyRepository;
    @Autowired UserRepository userRepository;
    @Autowired CompanyRepository companyRepository;
    @Autowired ClassificationRepository classificationRepository;
    @Autowired DetailClassificationRepository detailClassificationRepository;

    @Test
    @DisplayName("최소 정보만으로 분석 엔티티 없이 PLANNED 마지막에 지원 카드를 생성한다")
    void createMinimalCardWithoutAnalysisEntities() {
        User user = saveUser("application-minimal@example.com");
        long postingCount = jobPostingRepository.count();
        long mockApplyCount = mockApplyRepository.count();

        JobApplicationResponse first = jobApplicationService.create(user, minimalRequest("첫 공고"));
        JobApplicationResponse second = jobApplicationService.create(user, minimalRequest("둘째 공고"));

        assertThat(first.getStage()).isEqualTo(JobApplicationStage.PLANNED);
        assertThat(first.getStageOrder()).isZero();
        assertThat(first.getSourceJobPostingId()).isNull();
        assertThat(first.getMockApplyId()).isNull();
        assertThat(second.getStageOrder()).isEqualTo(1);
        assertThat(jobPostingRepository.count()).isEqualTo(postingCount);
        assertThat(mockApplyRepository.count()).isEqualTo(mockApplyCount);
    }

    @Test
    @DisplayName("선택 상세 정보와 기술 태그 순서를 카드 스냅샷에 저장한다")
    void createCardWithOptionalSnapshot() {
        User user = saveUser("application-snapshot@example.com");
        DetailClassification detail = saveDetailClassification();
        LocalDateTime deadlineAt = LocalDateTime.of(2026, 10, 1, 18, 0);

        JobApplicationResponse created = jobApplicationService.create(user, new JobApplicationCreateRequest(
                "스냅샷 기업", "경력 채용", "백엔드 엔지니어", CompanySize.LARGE, detail.getId(),
                "API 개발", "Java 경험", "대용량 처리", List.of(" Java ", "PostgreSQL"),
                deadlineAt, JobApplicationStage.DOCUMENT, "서류 제출 예정", deadlineAt.minusDays(1)
        ));
        JobApplicationResponse found = jobApplicationService.get(user, created.getJobApplicationId());

        assertThat(found.getDetailClassificationId()).isEqualTo(detail.getId());
        assertThat(found.getRequiredSkills()).containsExactly("Java", "PostgreSQL");
        assertThat(found.getDeadlineAt()).isEqualTo(deadlineAt);
        assertThat(found.getStage()).isEqualTo(JobApplicationStage.DOCUMENT);
        assertThat(found.getStageOrder()).isZero();
    }

    @Test
    @DisplayName("저장 공고는 여러 독립 카드로 복사되고 원본 삭제 후에도 유지된다")
    void copyPostingAllowsDuplicatesAndSurvivesSourceDeletion() {
        User user = saveUser("application-copy@example.com");
        JobPosting posting = saveJobPosting(user, "원본 기업", "원본 공고");

        JobApplicationResponse first = jobApplicationService.createFromJobPosting(
                user, new JobApplicationFromJobPostingRequest(posting.getId(), null));
        JobApplicationResponse second = jobApplicationService.createFromJobPosting(
                user, new JobApplicationFromJobPostingRequest(posting.getId(), null));

        assertThat(first.getJobApplicationId()).isNotEqualTo(second.getJobApplicationId());
        assertThat(first.getCompanyName()).isEqualTo("원본 기업");
        assertThat(first.getPostingName()).isEqualTo("원본 공고");
        assertThat(first.getSourceJobPostingId()).isEqualTo(posting.getId());

        jobPostingService.deleteJobPosting(user, posting.getId());
        jobPostingRepository.flush();

        JobApplicationResponse retained = jobApplicationService.get(user, first.getJobApplicationId());
        assertThat(retained.getSourceJobPostingId()).isNull();
        assertThat(retained.getCompanyName()).isEqualTo("원본 기업");
        assertThat(retained.getPostingName()).isEqualTo("원본 공고");
    }

    @Test
    @DisplayName("다른 사용자의 공고 복사와 지원 카드 조회를 차단한다")
    void rejectCrossUserAccess() {
        User owner = saveUser("application-owner@example.com");
        User other = saveUser("application-other@example.com");
        JobPosting posting = saveJobPosting(owner, "소유 기업", "소유 공고");
        JobApplicationResponse card = jobApplicationService.create(owner, minimalRequest("소유 카드"));

        assertThatThrownBy(() -> jobApplicationService.createFromJobPosting(
                other, new JobApplicationFromJobPostingRequest(posting.getId(), null)))
                .isInstanceOf(GeneralException.class)
                .extracting("code")
                .isEqualTo(GeneralErrorCode.FORBIDDEN);

        assertThatThrownBy(() -> jobApplicationService.get(other, card.getJobApplicationId()))
                .isInstanceOf(GeneralException.class)
                .extracting("code")
                .isEqualTo(GeneralErrorCode.FORBIDDEN);
    }

    private JobApplicationCreateRequest minimalRequest(String postingName) {
        return new JobApplicationCreateRequest(
                "테스트 기업", postingName, "서버 개발자", null, null,
                null, null, null, null, null, null, null, null
        );
    }

    private User saveUser(String email) {
        return userRepository.save(User.signup("테스트 사용자", email, "encoded-password"));
    }

    private JobPosting saveJobPosting(User user, String companyName, String postingName) {
        Company company = companyRepository.save(Company.create(companyName, CompanySize.MEDIUM));
        DetailClassification detail = saveDetailClassification();
        return jobPostingRepository.save(JobPosting.create(
                user, company, detail,
                com.jobdri.jobdri_api.domain.jobposting.entity.JobPostingProfileColor.DEFAULT,
                postingName, "백엔드 엔지니어", "주요 업무", "자격 요건", "우대 사항"
        ));
    }

    private DetailClassification saveDetailClassification() {
        Classification classification = Classification.create("지원관리 대분류 " + UUID.randomUUID());
        MiddleClassification middle = classification.addMiddleClassification("지원관리 중분류");
        DetailClassification detail = middle.addDetailClassification("지원관리 소분류");
        classificationRepository.save(classification);
        return detailClassificationRepository.findById(detail.getId()).orElseThrow();
    }
}
