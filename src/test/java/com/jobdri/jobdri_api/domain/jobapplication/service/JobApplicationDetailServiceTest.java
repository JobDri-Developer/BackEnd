package com.jobdri.jobdri_api.domain.jobapplication.service;

import com.jobdri.jobdri_api.domain.company.entity.CompanySize;
import com.jobdri.jobdri_api.domain.jobapplication.dto.request.JobApplicationChecklistItemRequest;
import com.jobdri.jobdri_api.domain.jobapplication.dto.request.JobApplicationCreateRequest;
import com.jobdri.jobdri_api.domain.jobapplication.dto.request.JobApplicationDetailUpdateRequest;
import com.jobdri.jobdri_api.domain.jobapplication.dto.request.JobApplicationEssayRequest;
import com.jobdri.jobdri_api.domain.jobapplication.dto.request.JobApplicationMetricRequest;
import com.jobdri.jobdri_api.domain.jobapplication.dto.response.JobApplicationDetailResponse;
import com.jobdri.jobdri_api.domain.jobapplication.dto.response.JobApplicationResponse;
import com.jobdri.jobdri_api.domain.jobapplication.entity.JobApplicationMetricType;
import com.jobdri.jobdri_api.domain.jobapplication.repository.JobApplicationRepository;
import com.jobdri.jobdri_api.domain.user.entity.User;
import com.jobdri.jobdri_api.domain.user.repository.UserRepository;
import com.jobdri.jobdri_api.global.apiPayload.code.GeneralErrorCode;
import com.jobdri.jobdri_api.global.apiPayload.exception.GeneralException;
import jakarta.persistence.EntityManager;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class JobApplicationDetailServiceTest {

    @Autowired JobApplicationDetailService detailService;
    @Autowired JobApplicationService applicationService;
    @Autowired UserRepository userRepository;
    @Autowired JobApplicationRepository jobApplicationRepository;
    @Autowired EntityManager entityManager;
    @Autowired Validator validator;

    @Test
    @DisplayName("세 탭 전체 데이터를 카드별로 격리하고 전달 순서대로 저장한다")
    void saveAndReadAllDetailTabsInOrder() {
        User user = saveUser();
        JobApplicationResponse target = createCard(user, "수정 대상");
        JobApplicationResponse untouched = createCard(user, "유지 대상");
        LocalDateTime currentAt = LocalDateTime.of(2026, 9, 20, 14, 0);

        detailService.update(user, target.getJobApplicationId(), request(
                target.getUpdatedAt(),
                List.of(
                        new JobApplicationChecklistItemRequest("서류 제출", true),
                        new JobApplicationChecklistItemRequest("면접 복기", false)
                ),
                List.of(
                        new JobApplicationMetricRequest(JobApplicationMetricType.LANGUAGE, "TOEIC", "950"),
                        new JobApplicationMetricRequest(JobApplicationMetricType.CERTIFICATE, "자격증", "정보처리기사")
                ),
                List.of(
                        new JobApplicationEssayRequest("첫 질문", "첫 답변"),
                        new JobApplicationEssayRequest("둘째 질문", "둘째 답변")
                ),
                currentAt
        ));
        entityManager.clear();

        JobApplicationDetailResponse found = detailService.get(user, target.getJobApplicationId());
        JobApplicationDetailResponse other = detailService.get(user, untouched.getJobApplicationId());

        assertThat(found.getCompanyName()).isEqualTo("수정 기업");
        assertThat(found.getRequiredSkills()).containsExactly("Java", "Spring");
        assertThat(found.getCurrentAt()).isEqualTo(currentAt);
        assertThat(found.getMemo()).isEqualTo("지원 및 면접 메모");
        assertThat(found.getGpa()).isEqualByComparingTo("4.1");
        assertThat(found.getMaxGpa()).isEqualByComparingTo("4.5");
        assertThat(found.getChecklistItems()).extracting(JobApplicationDetailResponse.ChecklistItem::content)
                .containsExactly("서류 제출", "면접 복기");
        assertThat(found.getChecklistItems()).extracting(JobApplicationDetailResponse.ChecklistItem::displayOrder)
                .containsExactly(0, 1);
        assertThat(found.getMetrics()).extracting(JobApplicationDetailResponse.Metric::type)
                .containsExactly(JobApplicationMetricType.LANGUAGE, JobApplicationMetricType.CERTIFICATE);
        assertThat(found.getEssays()).extracting(JobApplicationDetailResponse.Essay::question)
                .containsExactly("첫 질문", "둘째 질문");
        assertThat(other.getChecklistItems()).isEmpty();
        assertThat(other.getMetrics()).isEmpty();
        assertThat(other.getEssays()).isEmpty();
    }

    @Test
    @DisplayName("목록 전체 교체와 카드 삭제 시 자식이 orphanRemoval 및 cascade로 삭제된다")
    void replaceAllChildrenAndCascadeDelete() {
        User user = saveUser();
        JobApplicationResponse card = createCard(user, "전체 교체");
        JobApplicationDetailResponse first = detailService.update(user, card.getJobApplicationId(), request(
                card.getUpdatedAt(),
                List.of(new JobApplicationChecklistItemRequest("기존 할 일", false)),
                List.of(new JobApplicationMetricRequest(JobApplicationMetricType.AWARD, "기존 수상", "대상")),
                List.of(new JobApplicationEssayRequest("기존 질문", "기존 답변")),
                null
        ));
        Long oldChecklistId = first.getChecklistItems().getFirst().checklistItemId();
        Long oldMetricId = first.getMetrics().getFirst().metricId();
        Long oldEssayId = first.getEssays().getFirst().essayId();

        JobApplicationDetailResponse replaced = detailService.update(user, card.getJobApplicationId(), request(
                first.getUpdatedAt(),
                List.of(new JobApplicationChecklistItemRequest("새 할 일", true)),
                List.of(),
                List.of(new JobApplicationEssayRequest("새 질문", "새 답변")),
                null
        ));
        Long newChecklistId = replaced.getChecklistItems().getFirst().checklistItemId();
        Long newEssayId = replaced.getEssays().getFirst().essayId();
        entityManager.flush();
        entityManager.clear();

        assertThat(replaced.getChecklistItems()).extracting(JobApplicationDetailResponse.ChecklistItem::content)
                .containsExactly("새 할 일");
        assertThat(replaced.getMetrics()).isEmpty();
        assertThat(replaced.getEssays()).extracting(JobApplicationDetailResponse.Essay::question)
                .containsExactly("새 질문");
        assertThat(entityManager.find(
                com.jobdri.jobdri_api.domain.jobapplication.entity.JobApplicationChecklistItem.class,
                oldChecklistId
        )).isNull();
        assertThat(entityManager.find(
                com.jobdri.jobdri_api.domain.jobapplication.entity.JobApplicationMetric.class,
                oldMetricId
        )).isNull();
        assertThat(entityManager.find(
                com.jobdri.jobdri_api.domain.jobapplication.entity.JobApplicationEssay.class,
                oldEssayId
        )).isNull();

        jobApplicationRepository.delete(jobApplicationRepository.findById(card.getJobApplicationId()).orElseThrow());
        jobApplicationRepository.flush();
        entityManager.clear();

        assertThat(entityManager.find(
                com.jobdri.jobdri_api.domain.jobapplication.entity.JobApplicationChecklistItem.class,
                newChecklistId
        )).isNull();
        assertThat(entityManager.find(
                com.jobdri.jobdri_api.domain.jobapplication.entity.JobApplicationEssay.class,
                newEssayId
        )).isNull();
    }

    @Test
    @DisplayName("오래된 수정 시각과 다른 사용자 상세 접근을 차단한다")
    void rejectStaleUpdateAndCrossUserAccess() {
        User owner = saveUser();
        User other = saveUser();
        JobApplicationResponse card = createCard(owner, "소유 카드");
        JobApplicationDetailResponse updated = detailService.update(owner, card.getJobApplicationId(), request(
                card.getUpdatedAt(), List.of(), List.of(), List.of(), null));

        assertThat(updated.getUpdatedAt()).isAfter(card.getUpdatedAt());
        assertThatThrownBy(() -> detailService.update(owner, card.getJobApplicationId(), request(
                card.getUpdatedAt(), List.of(), List.of(), List.of(), null)))
                .isInstanceOf(GeneralException.class)
                .extracting("code")
                .isEqualTo(GeneralErrorCode.JOB_APPLICATION_UPDATE_CONFLICT);
        assertThatThrownBy(() -> detailService.get(other, card.getJobApplicationId()))
                .isInstanceOf(GeneralException.class)
                .extracting("code")
                .isEqualTo(GeneralErrorCode.FORBIDDEN);
        assertThatThrownBy(() -> detailService.update(other, card.getJobApplicationId(), request(
                updated.getUpdatedAt(), List.of(), List.of(), List.of(), null)))
                .isInstanceOf(GeneralException.class)
                .extracting("code")
                .isEqualTo(GeneralErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("목록 개수와 문자열 길이 및 학점 범위를 검증한다")
    void validateRequestBoundaries() {
        List<String> skills = IntStream.range(0, 21).mapToObj(index -> "기술" + index).toList();
        List<JobApplicationChecklistItemRequest> checklist = IntStream.range(0, 101)
                .mapToObj(index -> new JobApplicationChecklistItemRequest("할 일", false)).toList();
        List<JobApplicationEssayRequest> essays = IntStream.range(0, 21)
                .mapToObj(index -> new JobApplicationEssayRequest("질문", "답변")).toList();
        JobApplicationDetailUpdateRequest invalid = new JobApplicationDetailUpdateRequest(
                LocalDateTime.now(), "기업", "공고", "직무", null, null,
                null, null, null, skills, null, "진행", null, "메".repeat(10001),
                new BigDecimal("4.6"), new BigDecimal("4.5"), checklist, List.of(), essays
        );

        Set<ConstraintViolation<JobApplicationDetailUpdateRequest>> violations = validator.validate(invalid);

        assertThat(violations).extracting(violation -> violation.getPropertyPath().toString())
                .contains("requiredSkills", "memo", "checklistItems", "essays", "gpaRangeValid");
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("전체 저장 중 자식 저장이 실패하면 스냅샷과 모든 자식 변경을 롤백한다")
    void rollbackWholeUpdateWhenChildPersistenceFails() {
        User user = userRepository.saveAndFlush(User.signup(
                "롤백 사용자", "detail-rollback-" + UUID.randomUUID() + "@example.com", "encoded-password"));
        JobApplicationResponse card = createCard(user, "롤백 원본");
        JobApplicationDetailUpdateRequest invalid = request(
                LocalDateTime.now().plusYears(1),
                List.of(new JobApplicationChecklistItemRequest("저장되면 안 됨", true)),
                Collections.singletonList(null),
                List.of(new JobApplicationEssayRequest("저장되면 안 되는 질문", "답변")),
                null
        );

        assertThatThrownBy(() -> detailService.update(user, card.getJobApplicationId(), invalid))
                .isInstanceOf(NullPointerException.class);

        JobApplicationDetailResponse found = detailService.get(user, card.getJobApplicationId());
        assertThat(found.getCompanyName()).isEqualTo("테스트 기업");
        assertThat(found.getChecklistItems()).isEmpty();
        assertThat(found.getMetrics()).isEmpty();
        assertThat(found.getEssays()).isEmpty();
    }

    private JobApplicationDetailUpdateRequest request(
            LocalDateTime lastKnownUpdatedAt,
            List<JobApplicationChecklistItemRequest> checklistItems,
            List<JobApplicationMetricRequest> metrics,
            List<JobApplicationEssayRequest> essays,
            LocalDateTime currentAt
    ) {
        return new JobApplicationDetailUpdateRequest(
                lastKnownUpdatedAt,
                "수정 기업",
                "수정 공고",
                "수정 직무",
                CompanySize.LARGE,
                null,
                "수정 업무",
                "수정 자격 요건",
                "수정 우대 사항",
                List.of(" Java ", "Spring"),
                LocalDateTime.of(2026, 9, 30, 18, 0),
                "면접 예정",
                currentAt,
                "지원 및 면접 메모",
                new BigDecimal("4.1"),
                new BigDecimal("4.5"),
                checklistItems,
                metrics,
                essays
        );
    }

    private JobApplicationResponse createCard(User user, String postingName) {
        return applicationService.create(user, new JobApplicationCreateRequest(
                "테스트 기업", postingName, "서버 개발자", null, null,
                null, null, null, null, null, null, null, null
        ));
    }

    private User saveUser() {
        return userRepository.save(User.signup(
                "상세 테스트 사용자", "application-detail-" + UUID.randomUUID() + "@example.com", "encoded-password"));
    }
}
