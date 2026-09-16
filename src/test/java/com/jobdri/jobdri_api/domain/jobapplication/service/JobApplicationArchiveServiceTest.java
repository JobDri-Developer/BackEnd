package com.jobdri.jobdri_api.domain.jobapplication.service;

import com.jobdri.jobdri_api.domain.classification.entity.Classification;
import com.jobdri.jobdri_api.domain.classification.entity.DetailClassification;
import com.jobdri.jobdri_api.domain.classification.entity.MiddleClassification;
import com.jobdri.jobdri_api.domain.classification.repository.ClassificationRepository;
import com.jobdri.jobdri_api.domain.classification.repository.DetailClassificationRepository;
import com.jobdri.jobdri_api.domain.company.entity.Company;
import com.jobdri.jobdri_api.domain.company.entity.CompanySize;
import com.jobdri.jobdri_api.domain.company.repository.CompanyRepository;
import com.jobdri.jobdri_api.domain.jobapplication.dto.request.JobApplicationChecklistItemRequest;
import com.jobdri.jobdri_api.domain.jobapplication.dto.request.JobApplicationCreateRequest;
import com.jobdri.jobdri_api.domain.jobapplication.dto.request.JobApplicationDetailUpdateRequest;
import com.jobdri.jobdri_api.domain.jobapplication.dto.request.JobApplicationEssayRequest;
import com.jobdri.jobdri_api.domain.jobapplication.dto.request.JobApplicationFromJobPostingRequest;
import com.jobdri.jobdri_api.domain.jobapplication.dto.request.JobApplicationMetricRequest;
import com.jobdri.jobdri_api.domain.jobapplication.dto.request.JobApplicationSort;
import com.jobdri.jobdri_api.domain.jobapplication.dto.response.JobApplicationBoardResponse;
import com.jobdri.jobdri_api.domain.jobapplication.dto.response.JobApplicationCardResponse;
import com.jobdri.jobdri_api.domain.jobapplication.dto.response.JobApplicationResponse;
import com.jobdri.jobdri_api.domain.jobapplication.entity.JobApplication;
import com.jobdri.jobdri_api.domain.jobapplication.entity.JobApplicationMetricType;
import com.jobdri.jobdri_api.domain.jobapplication.entity.JobApplicationStage;
import com.jobdri.jobdri_api.domain.jobapplication.repository.JobApplicationRepository;
import com.jobdri.jobdri_api.domain.jobposting.entity.JobPosting;
import com.jobdri.jobdri_api.domain.jobposting.entity.JobPostingProfileColor;
import com.jobdri.jobdri_api.domain.jobposting.repository.JobPostingRepository;
import com.jobdri.jobdri_api.domain.mockapply.entity.ApplyType;
import com.jobdri.jobdri_api.domain.mockapply.entity.MockApply;
import com.jobdri.jobdri_api.domain.mockapply.repository.MockApplyRepository;
import com.jobdri.jobdri_api.domain.mockapply.service.MockApplyService;
import com.jobdri.jobdri_api.domain.user.entity.User;
import com.jobdri.jobdri_api.domain.user.repository.UserRepository;
import com.jobdri.jobdri_api.global.apiPayload.code.GeneralErrorCode;
import com.jobdri.jobdri_api.global.apiPayload.exception.GeneralException;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
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
class JobApplicationArchiveServiceTest {

    @Autowired JobApplicationArchiveService archiveService;
    @Autowired JobApplicationBoardService boardService;
    @Autowired JobApplicationService applicationService;
    @Autowired JobApplicationDetailService detailService;
    @Autowired MockApplyService mockApplyService;
    @Autowired JobApplicationRepository applicationRepository;
    @Autowired JobPostingRepository jobPostingRepository;
    @Autowired MockApplyRepository mockApplyRepository;
    @Autowired UserRepository userRepository;
    @Autowired CompanyRepository companyRepository;
    @Autowired ClassificationRepository classificationRepository;
    @Autowired DetailClassificationRepository detailClassificationRepository;
    @Autowired EntityManager entityManager;

    @Test
    @DisplayName("카드를 보관하면 활성 보드에서 제외되고 남은 단계 순서가 압축된다")
    void archiveExcludesCardAndCompactsStageOrder() {
        User user = saveUser();
        JobApplicationResponse first = createCard(user, "첫 카드", JobApplicationStage.DOCUMENT);
        JobApplicationResponse archived = createCard(user, "보관 카드", JobApplicationStage.DOCUMENT);
        JobApplicationResponse last = createCard(user, "마지막 카드", JobApplicationStage.DOCUMENT);

        JobApplicationResponse result = archiveService.archive(user, archived.getJobApplicationId());
        JobApplicationBoardResponse board = boardService.getBoard(user, "", JobApplicationSort.MANUAL);

        assertThat(result.getArchivedAt()).isNotNull();
        assertThat(ids(board, JobApplicationStage.DOCUMENT))
                .containsExactly(first.getJobApplicationId(), last.getJobApplicationId());
        assertThat(column(board, JobApplicationStage.DOCUMENT).cards())
                .extracting(JobApplicationCardResponse::stageOrder)
                .containsExactly(0, 1);
    }

    @Test
    @DisplayName("보관함은 사용자별로 격리되어 보관 시각 내림차순 페이지를 반환한다")
    void archivePageIsIsolatedSortedAndBounded() {
        User owner = saveUser();
        User other = saveUser();
        JobApplicationResponse first = createCard(owner, "먼저 보관", JobApplicationStage.PLANNED);
        JobApplicationResponse second = createCard(owner, "나중 보관", JobApplicationStage.INTERVIEW);
        JobApplicationResponse foreign = createCard(other, "타인 카드", JobApplicationStage.PLANNED);
        archiveService.archive(owner, first.getJobApplicationId());
        archiveService.archive(owner, second.getJobApplicationId());
        archiveService.archive(other, foreign.getJobApplicationId());

        Page<com.jobdri.jobdri_api.domain.jobapplication.dto.response.JobApplicationArchiveItemResponse> firstPage =
                archiveService.getArchive(owner, -1, 1);
        Page<com.jobdri.jobdri_api.domain.jobapplication.dto.response.JobApplicationArchiveItemResponse> secondPage =
                archiveService.getArchive(owner, 1, 1);
        Page<com.jobdri.jobdri_api.domain.jobapplication.dto.response.JobApplicationArchiveItemResponse> bounded =
                archiveService.getArchive(owner, 0, JobApplicationArchiveService.MAX_PAGE_SIZE + 10);

        assertThat(firstPage.getContent()).extracting(item -> item.jobApplicationId())
                .containsExactly(second.getJobApplicationId());
        assertThat(secondPage.getContent()).extracting(item -> item.jobApplicationId())
                .containsExactly(first.getJobApplicationId());
        assertThat(firstPage.getTotalElements()).isEqualTo(2);
        assertThat(bounded.getSize()).isEqualTo(JobApplicationArchiveService.MAX_PAGE_SIZE);
    }

    @Test
    @DisplayName("복원 카드는 보관 전 단계의 마지막 순서에 배치된다")
    void restoreToEndOfOriginalStage() {
        User user = saveUser();
        JobApplicationResponse restored = createCard(user, "복원 카드", JobApplicationStage.INTERVIEW);
        createCard(user, "기존 카드", JobApplicationStage.INTERVIEW);
        archiveService.archive(user, restored.getJobApplicationId());
        JobApplicationResponse newest = createCard(user, "추가 카드", JobApplicationStage.INTERVIEW);

        JobApplicationResponse result = archiveService.restore(user, restored.getJobApplicationId());
        JobApplicationBoardResponse board = boardService.getBoard(user, "", JobApplicationSort.MANUAL);

        assertThat(result.getArchivedAt()).isNull();
        assertThat(result.getStage()).isEqualTo(JobApplicationStage.INTERVIEW);
        assertThat(ids(board, JobApplicationStage.INTERVIEW).getLast()).isEqualTo(restored.getJobApplicationId());
        assertThat(ids(board, JobApplicationStage.INTERVIEW)).contains(newest.getJobApplicationId());
        assertThat(column(board, JobApplicationStage.INTERVIEW).cards())
                .extracting(JobApplicationCardResponse::stageOrder)
                .containsExactlyElementsOf(IntStream.range(0, 3).boxed().toList());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("같은 단계의 보관 카드 두 개를 동시에 복원해도 순서가 중복되지 않는다")
    void concurrentRestoresKeepContiguousUniqueOrder() throws Exception {
        User user = saveUser();
        JobApplicationResponse first = createCard(user, "동시 복원 1", JobApplicationStage.DOCUMENT);
        JobApplicationResponse second = createCard(user, "동시 복원 2", JobApplicationStage.DOCUMENT);
        archiveService.archive(user, first.getJobApplicationId());
        archiveService.archive(user, second.getJobApplicationId());
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<JobApplicationResponse> firstRestore = executor.submit(
                    () -> restoreAfterSignal(user, first.getJobApplicationId(), ready, start));
            Future<JobApplicationResponse> secondRestore = executor.submit(
                    () -> restoreAfterSignal(user, second.getJobApplicationId(), ready, start));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(List.of(
                    firstRestore.get(20, TimeUnit.SECONDS),
                    secondRestore.get(20, TimeUnit.SECONDS)
            )).extracting(JobApplicationResponse::getStageOrder)
                    .containsExactlyInAnyOrder(0, 1)
                    .doesNotHaveDuplicates();

            JobApplicationBoardResponse board = boardService.getBoard(user, "", JobApplicationSort.MANUAL);
            assertThat(ids(board, JobApplicationStage.DOCUMENT))
                    .containsExactlyInAnyOrder(first.getJobApplicationId(), second.getJobApplicationId());
            assertThat(column(board, JobApplicationStage.DOCUMENT).cards())
                    .extracting(JobApplicationCardResponse::stageOrder)
                    .containsExactly(0, 1)
                    .doesNotHaveDuplicates();
        } finally {
            start.countDown();
            executor.shutdownNow();
            executor.awaitTermination(10, TimeUnit.SECONDS);
        }
    }

    @Test
    @DisplayName("보관과 복원은 상태 및 소유권을 검증한다")
    void rejectInvalidStateForeignAndMissingCards() {
        User owner = saveUser();
        User other = saveUser();
        JobApplicationResponse card = createCard(owner, "소유 카드", JobApplicationStage.PLANNED);

        assertError(() -> archiveService.restore(owner, card.getJobApplicationId()),
                GeneralErrorCode.JOB_APPLICATION_UPDATE_CONFLICT);
        assertError(() -> archiveService.archive(other, card.getJobApplicationId()), GeneralErrorCode.FORBIDDEN);
        assertError(() -> archiveService.delete(other, card.getJobApplicationId()), GeneralErrorCode.FORBIDDEN);
        assertError(() -> archiveService.archive(owner, Long.MAX_VALUE), GeneralErrorCode.JOB_APPLICATION_NOT_FOUND);

        archiveService.archive(owner, card.getJobApplicationId());
        assertError(() -> archiveService.archive(owner, card.getJobApplicationId()),
                GeneralErrorCode.JOB_APPLICATION_UPDATE_CONFLICT);
    }

    @Test
    @DisplayName("카드 영구삭제는 하위 데이터만 삭제하고 출처 공고와 연결 모의지원을 보존한다")
    void deleteCardCascadesOwnedChildrenButPreservesExternalData() {
        User user = saveUser();
        JobPosting posting = savePosting(user);
        JobApplicationResponse first = createCard(user, "앞 카드", JobApplicationStage.DOCUMENT);
        JobApplicationResponse target = applicationService.createFromJobPosting(
                user, new JobApplicationFromJobPostingRequest(posting.getId(), JobApplicationStage.DOCUMENT));
        JobApplicationResponse last = createCard(user, "뒤 카드", JobApplicationStage.DOCUMENT);
        detailService.update(user, target.getJobApplicationId(), detailRequest(target.getUpdatedAt()));
        MockApply mockApply = mockApplyRepository.save(MockApply.create(user, posting, ApplyType.ACTUAL));
        JobApplication application = applicationRepository.findById(target.getJobApplicationId()).orElseThrow();
        ReflectionTestUtils.setField(application, "mockApply", mockApply);
        entityManager.flush();
        entityManager.clear();

        archiveService.delete(user, target.getJobApplicationId());

        assertThat(applicationRepository.findById(target.getJobApplicationId())).isEmpty();
        assertThat(jobPostingRepository.findById(posting.getId())).isPresent();
        assertThat(mockApplyRepository.findById(mockApply.getId())).isPresent();
        assertThat(countChildren("job_application_checklist_items", target.getJobApplicationId())).isZero();
        assertThat(countChildren("job_application_metrics", target.getJobApplicationId())).isZero();
        assertThat(countChildren("job_application_essays", target.getJobApplicationId())).isZero();
        assertThat(ids(boardService.getBoard(user, "", JobApplicationSort.MANUAL), JobApplicationStage.DOCUMENT))
                .containsExactly(first.getJobApplicationId(), last.getJobApplicationId());
    }

    @Test
    @DisplayName("연결된 모의지원이 먼저 삭제돼도 카드 스냅샷은 유지된다")
    void deletingMockApplyClearsLinkWithoutDeletingCard() {
        User user = saveUser();
        JobPosting posting = savePosting(user);
        MockApply mockApply = mockApplyRepository.save(MockApply.create(user, posting, ApplyType.ACTUAL));
        JobApplicationResponse card = createCard(user, "독립 스냅샷", JobApplicationStage.PLANNED);
        JobApplication application = applicationRepository.findById(card.getJobApplicationId()).orElseThrow();
        ReflectionTestUtils.setField(application, "mockApply", mockApply);
        entityManager.flush();
        entityManager.clear();

        mockApplyService.deleteMockApply(user, mockApply.getId());

        JobApplication retained = applicationRepository.findById(card.getJobApplicationId()).orElseThrow();
        assertThat(retained.getMockApply()).isNull();
        assertThat(retained.getPostingName()).isEqualTo("독립 스냅샷");
    }

    private JobApplicationDetailUpdateRequest detailRequest(LocalDateTime lastKnownUpdatedAt) {
        return new JobApplicationDetailUpdateRequest(
                lastKnownUpdatedAt, "상세 기업", "상세 공고", "상세 직무", CompanySize.MEDIUM, null,
                null, null, null, List.of("Java"), null, null, null, "메모",
                new BigDecimal("4.0"), new BigDecimal("4.5"),
                List.of(new JobApplicationChecklistItemRequest("체크", true)),
                List.of(new JobApplicationMetricRequest(JobApplicationMetricType.CERTIFICATE, "자격증", "기사")),
                List.of(new JobApplicationEssayRequest("질문", "답변"))
        );
    }

    private JobApplicationResponse restoreAfterSignal(
            User user,
            Long jobApplicationId,
            CountDownLatch ready,
            CountDownLatch start
    ) throws Exception {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("동시 복원 시작 대기 시간이 초과되었습니다.");
        }
        return archiveService.restore(user, jobApplicationId);
    }

    private JobApplicationResponse createCard(User user, String postingName, JobApplicationStage stage) {
        return applicationService.create(user, new JobApplicationCreateRequest(
                "테스트 기업", postingName, "서버 개발자", null, null,
                null, null, null, List.of("Java"), null, stage, null, null
        ));
    }

    private JobPosting savePosting(User user) {
        Company company = companyRepository.save(Company.create("외부 기업 " + UUID.randomUUID(), CompanySize.MEDIUM));
        Classification classification = Classification.create("보관 대분류 " + UUID.randomUUID());
        MiddleClassification middle = classification.addMiddleClassification("보관 중분류");
        DetailClassification detail = middle.addDetailClassification("보관 소분류");
        classificationRepository.save(classification);
        detail = detailClassificationRepository.findById(detail.getId()).orElseThrow();
        return jobPostingRepository.save(JobPosting.create(
                user, company, detail, JobPostingProfileColor.DEFAULT,
                "외부 공고", "외부 직무", "업무", "요건", "우대"
        ));
    }

    private long countChildren(String table, Long jobApplicationId) {
        return ((Number) entityManager.createNativeQuery(
                        "select count(*) from " + table + " where job_application_id = :id")
                .setParameter("id", jobApplicationId)
                .getSingleResult()).longValue();
    }

    private User saveUser() {
        return userRepository.save(User.signup(
                "보관 테스트", "application-archive-" + UUID.randomUUID() + "@example.com", "password"));
    }

    private JobApplicationBoardResponse.Column column(
            JobApplicationBoardResponse response,
            JobApplicationStage stage
    ) {
        return response.columns().stream().filter(item -> item.stage() == stage).findFirst().orElseThrow();
    }

    private List<Long> ids(JobApplicationBoardResponse response, JobApplicationStage stage) {
        return column(response, stage).cards().stream().map(JobApplicationCardResponse::jobApplicationId).toList();
    }

    private void assertError(Runnable action, GeneralErrorCode code) {
        assertThatThrownBy(action::run)
                .isInstanceOf(GeneralException.class)
                .extracting("code")
                .isEqualTo(code);
    }
}
