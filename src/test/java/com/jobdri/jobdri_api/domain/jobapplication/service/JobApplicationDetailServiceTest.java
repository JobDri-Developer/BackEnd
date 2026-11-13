package com.jobdri.jobdri_api.domain.jobapplication.service;

import com.jobdri.jobdri_api.domain.company.entity.CompanySize;
import com.jobdri.jobdri_api.domain.jobapplication.dto.request.JobApplicationChecklistItemRequest;
import com.jobdri.jobdri_api.domain.jobapplication.dto.request.JobApplicationCreateRequest;
import com.jobdri.jobdri_api.domain.jobapplication.dto.request.JobApplicationDetailUpdateRequest;
import com.jobdri.jobdri_api.domain.jobapplication.dto.request.JobApplicationEssayRequest;
import com.jobdri.jobdri_api.domain.jobapplication.dto.request.JobApplicationMetricRequest;
import com.jobdri.jobdri_api.domain.jobapplication.dto.request.JobApplicationPositionRequest;
import com.jobdri.jobdri_api.domain.jobapplication.dto.response.JobApplicationDetailResponse;
import com.jobdri.jobdri_api.domain.jobapplication.dto.response.JobApplicationResponse;
import com.jobdri.jobdri_api.domain.jobapplication.entity.JobApplicationMetricType;
import com.jobdri.jobdri_api.domain.jobapplication.entity.JobApplicationStage;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class JobApplicationDetailServiceTest {

    @Autowired JobApplicationDetailService detailService;
    @Autowired JobApplicationService applicationService;
    @Autowired JobApplicationBoardService boardService;
    @Autowired JobApplicationArchiveService archiveService;
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
        JobApplicationResponse sibling = createCard(user, "롤백 순서 유지");
        JobApplicationDetailUpdateRequest invalid = withStage(request(
                LocalDateTime.now().plusYears(1),
                List.of(new JobApplicationChecklistItemRequest("저장되면 안 됨", true)),
                List.of(new JobApplicationMetricRequest(JobApplicationMetricType.AWARD, "저장되면 안 되는 수상", "대상")),
                Collections.singletonList(null),
                null
        ), JobApplicationStage.INTERVIEW);

        assertThatThrownBy(() -> detailService.update(user, card.getJobApplicationId(), invalid))
                .isInstanceOf(NullPointerException.class);

        JobApplicationDetailResponse found = detailService.get(user, card.getJobApplicationId());
        assertThat(found.getStage()).isEqualTo(JobApplicationStage.PLANNED);
        assertThat(jobApplicationRepository.findById(card.getJobApplicationId()).orElseThrow().getStageOrder()).isZero();
        assertThat(jobApplicationRepository.findById(sibling.getJobApplicationId()).orElseThrow().getStageOrder()).isEqualTo(1);
        assertThat(found.getCompanyName()).isEqualTo("테스트 기업");
        assertThat(found.getChecklistItems()).isEmpty();
        assertThat(found.getMetrics()).isEmpty();
        assertThat(found.getEssays()).isEmpty();
    }

    @Test
    void stageChangeAppendsAndCompactsSourceAndPreservesUnchangedOrder() {
        User user = saveUser();
        JobApplicationResponse first = createCard(user, "첫 카드");
        JobApplicationResponse middle = createCard(user, "중간 카드");
        JobApplicationResponse last = createCard(user, "마지막 카드");
        detailService.update(user, first.getJobApplicationId(), withStage(request(
                first.getUpdatedAt(), List.of(), List.of(), List.of(), null), JobApplicationStage.INTERVIEW));
        JobApplicationDetailResponse moved = detailService.update(user, middle.getJobApplicationId(), withStage(request(
                detailService.get(user, middle.getJobApplicationId()).getUpdatedAt(),
                List.of(), List.of(), List.of(), null), JobApplicationStage.INTERVIEW));
        assertThat(moved.getStage()).isEqualTo(JobApplicationStage.INTERVIEW);
        assertThat(moved.getCompanyName()).isEqualTo("수정 기업");
        assertThat(jobApplicationRepository.findById(first.getJobApplicationId()).orElseThrow().getStageOrder()).isZero();
        assertThat(jobApplicationRepository.findById(middle.getJobApplicationId()).orElseThrow().getStageOrder()).isEqualTo(1);
        assertThat(jobApplicationRepository.findById(last.getJobApplicationId()).orElseThrow().getStageOrder()).isZero();
        JobApplicationDetailResponse same = detailService.update(user, first.getJobApplicationId(), withStage(request(
                detailService.get(user, first.getJobApplicationId()).getUpdatedAt(), List.of(), List.of(), List.of(), null),
                JobApplicationStage.INTERVIEW));
        detailService.update(user, first.getJobApplicationId(), request(
                same.getUpdatedAt(), List.of(), List.of(), List.of(), null));
        assertThat(jobApplicationRepository.findById(first.getJobApplicationId()).orElseThrow().getStageOrder()).isZero();
        assertThat(detailService.get(user, first.getJobApplicationId()).getStage()).isEqualTo(JobApplicationStage.INTERVIEW);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("독립 트랜잭션의 카드 이동과 상세 단계 저장이 동시에 실행돼도 양쪽 열의 순서가 연속적이다")
    void concurrentMoveAndDetailStageSaveKeepBothColumnsContiguous() throws Exception {
        // Fixtures must commit before the worker threads acquire their own service transactions.
        User user = userRepository.saveAndFlush(User.signup(
                "동시 이동 사용자", "detail-move-" + UUID.randomUUID() + "@example.com", "encoded-password"));
        JobApplicationResponse detailCard = createCard(user, "상세로 이동");
        JobApplicationResponse plannedSibling = createCard(user, "지원예정 유지");
        JobApplicationResponse draggedCard = createCard(user, "드래그로 이동");
        JobApplicationResponse documentSibling = createCard(user, "서류지원 유지");
        boardService.move(user, draggedCard.getJobApplicationId(),
                new JobApplicationPositionRequest(JobApplicationStage.DOCUMENT, 0));
        boardService.move(user, documentSibling.getJobApplicationId(),
                new JobApplicationPositionRequest(JobApplicationStage.DOCUMENT, 1));
        long revision = detailService.get(user, detailCard.getJobApplicationId()).getRevision();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> move = executor.submit(() -> {
                ready.countDown();
                assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                return boardService.move(user, draggedCard.getJobApplicationId(),
                        new JobApplicationPositionRequest(JobApplicationStage.PLANNED, 0));
            });
            Future<?> save = executor.submit(() -> {
                ready.countDown();
                assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                return detailService.update(user, detailCard.getJobApplicationId(),
                        revisionRequest(revision, JobApplicationStage.DOCUMENT));
            });
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            move.get(20, TimeUnit.SECONDS);
            save.get(20, TimeUnit.SECONDS);

            // Read committed state after both service transactions have completed.
            List<JobApplicationDetailResponse> cards = List.of(detailCard, plannedSibling, draggedCard, documentSibling)
                    .stream().map(card -> detailService.get(user, card.getJobApplicationId())).toList();
            List<JobApplicationDetailResponse> planned = cards.stream()
                    .filter(card -> card.getStage() == JobApplicationStage.PLANNED)
                    .sorted(java.util.Comparator.comparingInt(JobApplicationDetailResponse::getStageOrder)).toList();
            List<JobApplicationDetailResponse> document = cards.stream()
                    .filter(card -> card.getStage() == JobApplicationStage.DOCUMENT)
                    .sorted(java.util.Comparator.comparingInt(JobApplicationDetailResponse::getStageOrder)).toList();
            assertThat(planned).extracting(JobApplicationDetailResponse::getJobApplicationId)
                    .containsExactly(draggedCard.getJobApplicationId(), plannedSibling.getJobApplicationId());
            assertThat(document).extracting(JobApplicationDetailResponse::getJobApplicationId)
                    .containsExactly(documentSibling.getJobApplicationId(), detailCard.getJobApplicationId());
            assertThat(planned).extracting(JobApplicationDetailResponse::getStageOrder).containsExactly(0, 1);
            assertThat(document).extracting(JobApplicationDetailResponse::getStageOrder).containsExactly(0, 1);
            assertThat(detailService.get(user, detailCard.getJobApplicationId()).getCompanyName()).isEqualTo("수정 기업");
        } finally {
            start.countDown();
            executor.shutdownNow();
            executor.awaitTermination(10, TimeUnit.SECONDS);
        }
    }

    private JobApplicationDetailUpdateRequest withStage(JobApplicationDetailUpdateRequest r, JobApplicationStage stage) {
        return new JobApplicationDetailUpdateRequest(r.lastKnownUpdatedAt(), r.companyName(), r.postingName(),
                r.jobTitle(), r.companySize(), r.detailClassificationId(), r.task(), r.requirement(), r.preferred(),
                r.requiredSkills(), r.deadlineAt(), r.currentLabel(), r.currentAt(), r.memo(), r.gpa(), r.maxGpa(),
                r.checklistItems(), r.metrics(), r.essays(), r.postingUrl(), r.expectedRevision(), stage);
    }

    @Test
    @DisplayName("이름이 빈 행과 값이 빈 어학·기타 행은 제외하고 자격증·수상은 기본값으로 저장한다")
    void dropBlankMetricRowsAndFillOptionalValues() {
        User user = saveUser();
        JobApplicationResponse card = createCard(user, "빈 행");

        JobApplicationDetailResponse saved = detailService.update(user, card.getJobApplicationId(), request(
                card.getUpdatedAt(),
                List.of(),
                List.of(
                        new JobApplicationMetricRequest(JobApplicationMetricType.LANGUAGE, "TOEIC", " "),
                        new JobApplicationMetricRequest(JobApplicationMetricType.CUSTOM, "기타", null),
                        new JobApplicationMetricRequest(null, " ", null),
                        new JobApplicationMetricRequest(JobApplicationMetricType.CERTIFICATE, " 정보처리기사 ", ""),
                        new JobApplicationMetricRequest(JobApplicationMetricType.AWARD, "공모전", null),
                        new JobApplicationMetricRequest(JobApplicationMetricType.LANGUAGE, "OPIc", " IH ")
                ),
                List.of(),
                null
        ));

        assertThat(saved.getMetrics()).extracting(JobApplicationDetailResponse.Metric::name)
                .containsExactly("정보처리기사", "공모전", "OPIc");
        assertThat(saved.getMetrics()).extracting(JobApplicationDetailResponse.Metric::value)
                .containsExactly("보유", "취득일 미입력", "IH");
        assertThat(saved.getMetrics()).extracting(JobApplicationDetailResponse.Metric::displayOrder)
                .containsExactly(0, 1, 2);
    }

    @Test
    @DisplayName("이름이 있는데 유형이 없는 정량 정보 행은 400으로 거절한다")
    void rejectMetricRowWithoutType() {
        User user = saveUser();
        JobApplicationResponse card = createCard(user, "유형 없음");

        assertThatThrownBy(() -> detailService.update(user, card.getJobApplicationId(), request(
                card.getUpdatedAt(), List.of(),
                List.of(new JobApplicationMetricRequest(null, "TOEIC", "900")), List.of(), null)))
                .isInstanceOfSatisfying(GeneralException.class,
                        error -> assertThat(error.getCode()).isEqualTo(GeneralErrorCode.INVALID_PARAMETER));
    }

    @Test
    @DisplayName("expectedRevision이 서버 revision과 다르면 409를 반환하고 저장할 때마다 revision이 오른다")
    void rejectStaleRevision() {
        User user = saveUser();
        JobApplicationResponse card = createCard(user, "revision");
        long initial = detailService.get(user, card.getJobApplicationId()).getRevision();

        JobApplicationDetailResponse saved = detailService.update(user, card.getJobApplicationId(),
                revisionRequest(initial, null));

        assertThat(saved.getRevision()).isEqualTo(initial + 1);
        assertThatThrownBy(() -> detailService.update(user, card.getJobApplicationId(), revisionRequest(initial, null)))
                .isInstanceOfSatisfying(GeneralException.class, error -> assertThat(error.getCode())
                        .isEqualTo(GeneralErrorCode.JOB_APPLICATION_UPDATE_CONFLICT));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("같은 expectedRevision으로 동시에 저장하면 하나만 성공하고 나머지는 409를 받는다")
    void concurrentSavesWithSameRevisionAllowOnlyOneWinner() throws Exception {
        User user = userRepository.saveAndFlush(User.signup(
                "동시 저장 사용자", "detail-concurrent-" + UUID.randomUUID() + "@example.com", "encoded-password"));
        JobApplicationResponse card = createCard(user, "동시 저장");
        long revision = detailService.get(user, card.getJobApplicationId()).getRevision();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<GeneralErrorCode> attempt = () -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) {
                    throw new AssertionError("동시 저장 시작 대기 시간 초과");
                }
                try {
                    detailService.update(user, card.getJobApplicationId(), revisionRequest(revision, null));
                    return null;
                } catch (GeneralException error) {
                    return (GeneralErrorCode) error.getCode();
                }
            };
            Future<GeneralErrorCode> first = executor.submit(attempt);
            Future<GeneralErrorCode> second = executor.submit(attempt);
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(Arrays.asList(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(null, GeneralErrorCode.JOB_APPLICATION_UPDATE_CONFLICT);
            assertThat(detailService.get(user, card.getJobApplicationId()).getRevision()).isEqualTo(revision + 1);
        } finally {
            start.countDown();
            executor.shutdownNow();
            executor.awaitTermination(10, TimeUnit.SECONDS);
        }
    }

    @Test
    @DisplayName("같은 열의 다른 카드를 드래그해 순서가 밀려도 revision 기준 저장은 충돌하지 않는다")
    void siblingMoveDoesNotConflictWithRevision() {
        User user = saveUser();
        JobApplicationResponse first = createCard(user, "첫 카드");
        JobApplicationResponse second = createCard(user, "둘째 카드");
        long revision = detailService.get(user, first.getJobApplicationId()).getRevision();

        boardService.move(user, second.getJobApplicationId(),
                new JobApplicationPositionRequest(JobApplicationStage.PLANNED, 0));
        JobApplicationDetailResponse saved = detailService.update(user, first.getJobApplicationId(),
                revisionRequest(revision, null));

        assertThat(saved.getStageOrder()).isEqualTo(1);
        assertThat(saved.getCompanyName()).isEqualTo("수정 기업");
    }

    @Test
    @DisplayName("상세 저장에 stage를 넣으면 같은 트랜잭션에서 도착 열의 마지막으로 옮기고 출발 열을 당긴다")
    void changeStageWithinDetailSave() {
        User user = saveUser();
        JobApplicationResponse moving = createCard(user, "옮길 카드");
        JobApplicationResponse staying = createCard(user, "남을 카드");
        JobApplicationResponse existing = createCard(user, "서류 카드");
        boardService.move(user, existing.getJobApplicationId(),
                new JobApplicationPositionRequest(JobApplicationStage.DOCUMENT, 0));
        long revision = detailService.get(user, moving.getJobApplicationId()).getRevision();

        JobApplicationDetailResponse saved = detailService.update(user, moving.getJobApplicationId(),
                revisionRequest(revision, JobApplicationStage.DOCUMENT));

        assertThat(saved.getStage()).isEqualTo(JobApplicationStage.DOCUMENT);
        assertThat(saved.getStageOrder()).isEqualTo(1);
        assertThat(saved.getCompanyName()).isEqualTo("수정 기업");
        assertThat(detailService.get(user, staying.getJobApplicationId()).getStageOrder()).isZero();
    }

    @Test
    @DisplayName("보관된 카드는 상세 저장으로도 단계를 바꿀 수 없다")
    void rejectStageChangeOnArchivedCard() {
        User user = saveUser();
        JobApplicationResponse card = createCard(user, "보관 카드");
        archiveService.archive(user, card.getJobApplicationId());
        long revision = detailService.get(user, card.getJobApplicationId()).getRevision();

        assertThatThrownBy(() -> detailService.update(user, card.getJobApplicationId(),
                revisionRequest(revision, JobApplicationStage.INTERVIEW)))
                .isInstanceOfSatisfying(GeneralException.class, error -> assertThat(error.getCode())
                        .isEqualTo(GeneralErrorCode.JOB_APPLICATION_UPDATE_CONFLICT));
    }

    private JobApplicationDetailUpdateRequest revisionRequest(long expectedRevision, JobApplicationStage stage) {
        JobApplicationDetailUpdateRequest base = request(null, List.of(), List.of(), List.of(), null);
        return new JobApplicationDetailUpdateRequest(
                null, base.companyName(), base.postingName(), base.jobTitle(), base.companySize(),
                base.detailClassificationId(), base.task(), base.requirement(), base.preferred(),
                base.requiredSkills(), base.deadlineAt(), base.currentLabel(), base.currentAt(), base.memo(),
                base.gpa(), base.maxGpa(), base.checklistItems(), base.metrics(), base.essays(), null,
                expectedRevision, stage
        );
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
