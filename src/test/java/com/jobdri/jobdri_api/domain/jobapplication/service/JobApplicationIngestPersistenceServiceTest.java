package com.jobdri.jobdri_api.domain.jobapplication.service;

import com.jobdri.jobdri_api.domain.analysis.repository.AnalysisRepository;
import com.jobdri.jobdri_api.domain.classification.entity.Classification;
import com.jobdri.jobdri_api.domain.classification.entity.DetailClassification;
import com.jobdri.jobdri_api.domain.classification.entity.MiddleClassification;
import com.jobdri.jobdri_api.domain.classification.repository.ClassificationRepository;
import com.jobdri.jobdri_api.domain.jobapplication.dto.response.JobApplicationResponse;
import com.jobdri.jobdri_api.domain.jobapplication.entity.JobApplicationStage;
import com.jobdri.jobdri_api.domain.jobapplication.repository.JobApplicationRepository;
import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingExtractResponse;
import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingGenerateResponse;
import com.jobdri.jobdri_api.domain.jobposting.repository.JobPostingRepository;
import com.jobdri.jobdri_api.domain.mockapply.repository.MockApplyRepository;
import com.jobdri.jobdri_api.domain.user.entity.User;
import com.jobdri.jobdri_api.domain.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class JobApplicationIngestPersistenceServiceTest {
    @Autowired JobApplicationIngestPersistenceService service;
    @Autowired JobApplicationRepository jobApplicationRepository;
    @Autowired UserRepository userRepository;
    @Autowired ClassificationRepository classificationRepository;
    @Autowired JobPostingRepository jobPostingRepository;
    @Autowired MockApplyRepository mockApplyRepository;
    @Autowired AnalysisRepository analysisRepository;

    @Test
    @DisplayName("추출 스냅샷을 PLANNED 마지막에 저장하고 다른 도메인 엔티티는 생성하지 않는다")
    void persistSnapshotWithoutAnalysisFlow() {
        User user = saveUser("clipper-boundary");
        DetailClassification detail = saveClassification();
        jobApplicationRepository.save(existingCard(user, detail, 0));
        long postingsBefore = jobPostingRepository.count();
        long mockAppliesBefore = mockApplyRepository.count();
        long analysesBefore = analysisRepository.count();

        JobApplicationIngestPersistenceService.PersistResult result = service.persist(
                user, " ingest-key ", detail.getId(), extracted(), generated()
        );
        JobApplicationResponse saved = result.jobApplication();

        assertThat(result.idempotentReplay()).isFalse();
        assertThat(saved.getStage()).isEqualTo(JobApplicationStage.PLANNED);
        assertThat(saved.getStageOrder()).isEqualTo(1);
        assertThat(saved.getRequiredSkills()).containsExactly("Java", "Spring");
        assertThat(saved.getDeadlineAt()).isEqualTo(LocalDateTime.of(2026, 10, 31, 18, 0));
        assertThat(saved.getSourceJobPostingId()).isNull();
        assertThat(saved.getMockApplyId()).isNull();
        assertThat(jobPostingRepository.count()).isEqualTo(postingsBefore);
        assertThat(mockApplyRepository.count()).isEqualTo(mockAppliesBefore);
        assertThat(analysisRepository.count()).isEqualTo(analysesBefore);
    }

    @Test
    @DisplayName("같은 사용자의 동일 멱등 키 동시 요청은 카드 하나만 생성한다")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentDuplicateRequestCreatesOneCard() throws Exception {
        User user = userRepository.saveAndFlush(User.signup(
                "사용자", "clipper-concurrent-" + UUID.randomUUID() + "@example.com", "password"
        ));
        DetailClassification detail = saveClassification();
        Long userId = user.getId();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<JobApplicationIngestPersistenceService.PersistResult> first = executor.submit(
                    () -> persistAfterSignal(user, detail.getId(), ready, start));
            Future<JobApplicationIngestPersistenceService.PersistResult> second = executor.submit(
                    () -> persistAfterSignal(user, detail.getId(), ready, start));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<JobApplicationIngestPersistenceService.PersistResult> results = List.of(
                    first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)
            );
            Long createdId = results.get(0).jobApplication().getJobApplicationId();
            assertThat(results).extracting(result -> result.jobApplication().getJobApplicationId())
                    .hasSize(2).containsOnly(createdId);
            assertThat(results).extracting(JobApplicationIngestPersistenceService.PersistResult::idempotentReplay)
                    .containsExactlyInAnyOrder(false, true);
            assertThat(jobApplicationRepository.findByUserIdAndIngestIdempotencyKey(userId, "same-key"))
                    .isPresent();
        } finally {
            start.countDown();
            executor.shutdownNow();
            executor.awaitTermination(10, TimeUnit.SECONDS);
        }
    }

    @Test
    @DisplayName("동일 멱등 키라도 사용자가 다르면 각각 카드를 생성한다")
    void idempotencyKeyIsScopedByOwner() {
        User first = saveUser("clipper-owner-a");
        User second = saveUser("clipper-owner-b");
        DetailClassification detail = saveClassification();

        var firstResult = service.persist(first, "shared-key", detail.getId(), extracted(), generated());
        var secondResult = service.persist(second, "shared-key", detail.getId(), extracted(), generated());

        assertThat(firstResult.jobApplication().getJobApplicationId())
                .isNotEqualTo(secondResult.jobApplication().getJobApplicationId());
        assertThat(firstResult.idempotentReplay()).isFalse();
        assertThat(secondResult.idempotentReplay()).isFalse();
    }

    private JobApplicationIngestPersistenceService.PersistResult persistAfterSignal(
            User user, Long detailId, CountDownLatch ready, CountDownLatch start
    ) throws Exception {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("동시 요청 시작 대기 시간이 초과되었습니다.");
        }
        return service.persist(user, "same-key", detailId, extracted(), generated());
    }

    private com.jobdri.jobdri_api.domain.jobapplication.entity.JobApplication existingCard(
            User user, DetailClassification detail, int order
    ) {
        return com.jobdri.jobdri_api.domain.jobapplication.entity.JobApplication.create(
                user, null, detail, "기존 기업", "기존 공고", "기존 직무", null,
                null, null, null, List.of(), null, JobApplicationStage.PLANNED, order, null, null
        );
    }

    private User saveUser(String prefix) {
        return userRepository.save(User.signup(
                "사용자", prefix + "-" + UUID.randomUUID() + "@example.com", "password"
        ));
    }

    private DetailClassification saveClassification() {
        Classification classification = Classification.create("대분류 " + UUID.randomUUID());
        MiddleClassification middle = classification.addMiddleClassification("중분류");
        DetailClassification detail = middle.addDetailClassification("소분류");
        classificationRepository.saveAndFlush(classification);
        return detail;
    }

    private JobPostingExtractResponse extracted() {
        return new JobPostingExtractResponse(
                "백엔드 채용", "잡드리", "백엔드 엔지니어", "API 개발", "Java 경험", "AWS 경험",
                "채용 공고 원문", 0.9, List.of(" Java ", "Spring", "Java"), "2026-10-31T18:00:00"
        );
    }

    private JobPostingGenerateResponse generated() {
        return new JobPostingGenerateResponse(
                "백엔드 채용", "잡드리", "백엔드 엔지니어", "API 개발", "Java 경험", "AWS 경험", "요약"
        );
    }
}
