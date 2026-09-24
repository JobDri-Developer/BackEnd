package com.jobdri.jobdri_api.domain.jobapplication.service;

import com.jobdri.jobdri_api.domain.classification.entity.Classification;
import com.jobdri.jobdri_api.domain.classification.entity.DetailClassification;
import com.jobdri.jobdri_api.domain.classification.entity.MiddleClassification;
import com.jobdri.jobdri_api.domain.classification.repository.ClassificationRepository;
import com.jobdri.jobdri_api.domain.company.entity.CompanySize;
import com.jobdri.jobdri_api.domain.jobapplication.dto.request.JobApplicationCreateRequest;
import com.jobdri.jobdri_api.domain.jobapplication.dto.request.JobApplicationDetailUpdateRequest;
import com.jobdri.jobdri_api.domain.jobapplication.dto.response.JobApplicationMockApplyResponse;
import com.jobdri.jobdri_api.domain.jobapplication.dto.response.JobApplicationNotReadyResponse;
import com.jobdri.jobdri_api.domain.jobapplication.dto.response.JobApplicationResponse;
import com.jobdri.jobdri_api.domain.jobapplication.entity.JobApplicationStage;
import com.jobdri.jobdri_api.domain.jobapplication.repository.JobApplicationRepository;
import com.jobdri.jobdri_api.domain.jobposting.entity.JobPosting;
import com.jobdri.jobdri_api.domain.jobposting.repository.JobPostingRepository;
import com.jobdri.jobdri_api.domain.mockapply.entity.ApplyType;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class JobApplicationMockApplyServiceTest {
    @Autowired JobApplicationMockApplyService conversionService;
    @Autowired JobApplicationService applicationService;
    @Autowired JobApplicationDetailService detailService;
    @Autowired JobApplicationRepository applicationRepository;
    @Autowired JobPostingRepository jobPostingRepository;
    @Autowired MockApplyRepository mockApplyRepository;
    @Autowired UserRepository userRepository;
    @Autowired ClassificationRepository classificationRepository;

    @Test
    @DisplayName("분석 준비 필드가 부족하면 정확한 missingFields를 반환하고 아무것도 생성하지 않는다")
    void rejectNotReadyWithoutPartialCreation() {
        User user = saveUser("not-ready");
        JobApplicationResponse card = applicationService.create(user, new JobApplicationCreateRequest(
                "잡드리", "백엔드 채용", "백엔드 엔지니어", null, null,
                null, null, null, null, null, null, null, null
        ));
        long postingsBefore = jobPostingRepository.count();
        long mockAppliesBefore = mockApplyRepository.count();

        assertThatThrownBy(() -> conversionService.createOrGet(user, card.getJobApplicationId()))
                .isInstanceOf(GeneralException.class)
                .satisfies(error -> {
                    GeneralException exception = (GeneralException) error;
                    assertThat(exception.getCode()).isEqualTo(GeneralErrorCode.JOB_APPLICATION_NOT_READY);
                    assertThat(exception.getError()).isEqualTo(new JobApplicationNotReadyResponse(List.of(
                            "detailClassificationId", "task", "requirement", "preferred"
                    )));
                });
        assertThat(jobPostingRepository.count()).isEqualTo(postingsBefore);
        assertThat(mockApplyRepository.count()).isEqualTo(mockAppliesBefore);
    }

    @Test
    @DisplayName("준비된 카드 스냅샷으로 JobPosting과 ACTUAL MockApply를 한 번만 생성한다")
    void convertReadyCardIdempotently() {
        User user = saveUser("success");
        DetailClassification detail = saveClassification();
        JobApplicationResponse card = createReadyCard(user, detail, "원본");

        JobApplicationMockApplyResponse first = conversionService.createOrGet(user, card.getJobApplicationId());
        long postingCount = jobPostingRepository.count();
        long mockApplyCount = mockApplyRepository.count();
        JobApplicationMockApplyResponse replay = conversionService.createOrGet(user, card.getJobApplicationId());

        assertThat(first.created()).isTrue();
        assertThat(first.status()).isEqualTo(MockApplyStatus.APPLICATION_CREATED);
        assertThat(replay.created()).isFalse();
        assertThat(replay.jobPostingId()).isEqualTo(first.jobPostingId());
        assertThat(replay.mockApplyId()).isEqualTo(first.mockApplyId());
        assertThat(jobPostingRepository.count()).isEqualTo(postingCount);
        assertThat(mockApplyRepository.count()).isEqualTo(mockApplyCount);
        var mockApply = mockApplyRepository.findById(first.mockApplyId()).orElseThrow();
        assertThat(mockApply.getApplyType()).isEqualTo(ApplyType.ACTUAL);
        assertThat(mockApply.getSequence()).isEqualTo(1);
        assertThat(applicationService.get(user, card.getJobApplicationId()).getMockApplyId())
                .isEqualTo(first.mockApplyId());
    }

    @Test
    @DisplayName("전환 후 카드 스냅샷을 수정해도 생성된 분석 공고는 바뀌지 않는다")
    void convertedPostingIsIsolatedFromCardUpdates() {
        User user = saveUser("snapshot");
        DetailClassification detail = saveClassification();
        JobApplicationResponse card = createReadyCard(user, detail, "원본");
        JobApplicationMockApplyResponse converted = conversionService.createOrGet(user, card.getJobApplicationId());
        var current = detailService.get(user, card.getJobApplicationId());

        detailService.update(user, card.getJobApplicationId(), new JobApplicationDetailUpdateRequest(
                current.getUpdatedAt(), "수정 기업", "수정 공고", "수정 직무", CompanySize.LARGE, detail.getId(),
                "수정 업무", "수정 자격", "수정 우대", List.of("Kotlin"), null,
                null, null, null, null, null, List.of(), List.of(), List.of()
        ));

        JobPosting posting = jobPostingRepository.findById(converted.jobPostingId()).orElseThrow();
        assertThat(posting.getCompany().getName()).isEqualTo("원본 기업");
        assertThat(posting.getPostingName()).isEqualTo("원본 공고");
        assertThat(posting.getJobTitle()).isEqualTo("원본 직무");
        assertThat(posting.getTask()).isEqualTo("원본 업무");
        assertThat(posting.getRequirement()).isEqualTo("원본 자격");
        assertThat(posting.getPreferred()).isEqualTo("원본 우대");
    }

    @Test
    @DisplayName("다른 사용자의 카드에서는 모의지원을 시작할 수 없다")
    void rejectOtherOwnersCard() {
        User owner = saveUser("owner");
        User other = saveUser("other");
        JobApplicationResponse card = createReadyCard(owner, saveClassification(), "소유");

        assertThatThrownBy(() -> conversionService.createOrGet(other, card.getJobApplicationId()))
                .isInstanceOf(GeneralException.class)
                .extracting("code")
                .isEqualTo(GeneralErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("동일 카드의 동시 전환 요청도 하나의 모의지원만 생성한다")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentConversionCreatesOnce() throws Exception {
        User user = userRepository.saveAndFlush(User.signup(
                "사용자", "conversion-concurrent-" + UUID.randomUUID() + "@example.com", "password"
        ));
        DetailClassification detail = saveClassification();
        JobApplicationResponse card = createReadyCard(user, detail, "동시");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<JobApplicationMockApplyResponse> first = executor.submit(
                    () -> convertAfterSignal(user, card.getJobApplicationId(), ready, start));
            Future<JobApplicationMockApplyResponse> second = executor.submit(
                    () -> convertAfterSignal(user, card.getJobApplicationId(), ready, start));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<JobApplicationMockApplyResponse> responses = List.of(
                    first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)
            );

            Long mockApplyId = responses.get(0).mockApplyId();
            assertThat(responses).extracting(JobApplicationMockApplyResponse::mockApplyId)
                    .containsOnly(mockApplyId);
            assertThat(responses).extracting(JobApplicationMockApplyResponse::created)
                    .containsExactlyInAnyOrder(true, false);
            assertThat(applicationRepository.findDetailedById(card.getJobApplicationId()).orElseThrow()
                    .getMockApply().getId()).isEqualTo(mockApplyId);
        } finally {
            start.countDown();
            executor.shutdownNow();
            executor.awaitTermination(10, TimeUnit.SECONDS);
        }
    }

    private JobApplicationMockApplyResponse convertAfterSignal(
            User user, Long applicationId, CountDownLatch ready, CountDownLatch start
    ) throws Exception {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("동시 전환 시작 대기 시간이 초과되었습니다.");
        }
        return conversionService.createOrGet(user, applicationId);
    }

    private JobApplicationResponse createReadyCard(User user, DetailClassification detail, String prefix) {
        return applicationService.create(user, new JobApplicationCreateRequest(
                prefix + " 기업", prefix + " 공고", prefix + " 직무", CompanySize.MEDIUM, detail.getId(),
                prefix + " 업무", prefix + " 자격", prefix + " 우대", List.of("Java"), null,
                JobApplicationStage.PLANNED, null, null
        ));
    }

    private User saveUser(String prefix) {
        return userRepository.save(User.signup(
                "사용자", "conversion-" + prefix + "-" + UUID.randomUUID() + "@example.com", "password"
        ));
    }

    private DetailClassification saveClassification() {
        Classification classification = Classification.create("전환 대분류 " + UUID.randomUUID());
        MiddleClassification middle = classification.addMiddleClassification("전환 중분류");
        DetailClassification detail = middle.addDetailClassification("전환 소분류");
        classificationRepository.saveAndFlush(classification);
        return detail;
    }
}
