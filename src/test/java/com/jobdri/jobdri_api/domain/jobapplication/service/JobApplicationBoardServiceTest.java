package com.jobdri.jobdri_api.domain.jobapplication.service;

import com.jobdri.jobdri_api.domain.jobapplication.dto.request.*;
import com.jobdri.jobdri_api.domain.jobapplication.dto.response.*;
import com.jobdri.jobdri_api.domain.jobapplication.entity.*;
import com.jobdri.jobdri_api.domain.jobapplication.repository.*;
import com.jobdri.jobdri_api.domain.user.entity.User;
import com.jobdri.jobdri_api.domain.user.repository.UserRepository;
import com.jobdri.jobdri_api.global.apiPayload.code.GeneralErrorCode;
import com.jobdri.jobdri_api.global.apiPayload.exception.GeneralException;
import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class JobApplicationBoardServiceTest {
    @Autowired JobApplicationBoardService board;
    @Autowired JobApplicationService registration;
    @Autowired JobApplicationRepository applications;
    @Autowired JobApplicationEssayRepository essays;
    @Autowired UserRepository users;
    @Autowired EntityManager em;

    @Test
    void boardIncludesAllColumnsAndSearchesEachFieldLiterally() {
        User owner = user();
        create(owner, "Acme", "공고", "개발자", JobApplicationStage.PLANNED);
        create(owner, "기업", "ACME 모집", "기획", JobApplicationStage.DOCUMENT);
        create(owner, "기업", "공고", "Acme engineer", JobApplicationStage.INTERVIEW);
        create(owner, "100%_기업", "공고", "직무", JobApplicationStage.COMPLETED);
        create(user(), "Acme", "타인", "직무", JobApplicationStage.PLANNED);
        var archived = create(owner, "Acme", "보관", "직무", JobApplicationStage.PLANNED);
        ReflectionTestUtils.setField(applications.findById(archived.getJobApplicationId()).orElseThrow(),
                "archivedAt", LocalDateTime.now());
        em.flush();
        em.clear();

        var result = board.getBoard(owner, " aCmE ", JobApplicationSort.MANUAL);
        assertThat(result.columns()).extracting(JobApplicationBoardResponse.Column::stage)
                .containsExactly(JobApplicationStage.values());
        assertThat(result.columns()).extracting(JobApplicationBoardResponse.Column::count).containsExactly(1, 1, 1, 0);
        assertThat(board.getBoard(owner, "%_", null).columns()).extracting(JobApplicationBoardResponse.Column::count)
                .containsExactly(0, 0, 0, 1);
        assertThat(board.getBoard(owner, "없는검색", null).columns())
                .allSatisfy(column -> assertThat(column.cards()).isEmpty());
    }

    @Test
    void manualAndCreatedSortAreIndependentAndResponseHasFreshTimestamps() {
        User user = user();
        var first = create(user, "기업", "첫", "직무", JobApplicationStage.PLANNED);
        var second = create(user, "기업", "둘", "직무", JobApplicationStage.PLANNED);
        var moved = board.move(user, second.getJobApplicationId(), position(JobApplicationStage.PLANNED, 0));
        assertThat(ids(moved, JobApplicationStage.PLANNED)).containsExactly(second.getJobApplicationId(), first.getJobApplicationId());
        board.move(user, second.getJobApplicationId(), position(JobApplicationStage.PLANNED, 1));
        var manual = board.getBoard(user, null, JobApplicationSort.MANUAL);
        assertThat(ids(manual, JobApplicationStage.PLANNED)).containsExactly(first.getJobApplicationId(), second.getJobApplicationId());
        assertThat(ids(board.getBoard(user, "", JobApplicationSort.CREATED_DESC), JobApplicationStage.PLANNED))
                .containsExactly(second.getJobApplicationId(), first.getJobApplicationId());
        var returned = manual.columns().getFirst().cards().getLast();
        em.clear();
        assertThat(applications.findById(second.getJobApplicationId()).orElseThrow().getUpdatedAt())
                .isEqualTo(returned.updatedAt());
    }

    @Test
    void movesAcrossColumnsToFirstLastAndEmptyPositionsWithoutGaps() {
        User user = user();
        var a = create(user, "기업", "a", "직무", JobApplicationStage.PLANNED);
        var b = create(user, "기업", "b", "직무", JobApplicationStage.PLANNED);
        var c = create(user, "기업", "c", "직무", JobApplicationStage.PLANNED);
        board.move(user, b.getJobApplicationId(), position(JobApplicationStage.INTERVIEW, 0));
        board.move(user, a.getJobApplicationId(), position(JobApplicationStage.INTERVIEW, 0));
        var result = board.move(user, c.getJobApplicationId(), position(JobApplicationStage.INTERVIEW, 2));
        assertThat(ids(result, JobApplicationStage.INTERVIEW)).containsExactly(
                a.getJobApplicationId(), b.getJobApplicationId(), c.getJobApplicationId());
        assertContiguous(result);
        assertThat(ids(result, JobApplicationStage.PLANNED)).isEmpty();
    }

    @Test
    void rejectsInvalidPositionArchivedMissingAndForeignCardsWithoutChangingOrder() {
        User user = user();
        var a = create(user, "기업", "a", "직무", JobApplicationStage.PLANNED);
        assertError(() -> board.move(user, a.getJobApplicationId(), position(JobApplicationStage.DOCUMENT, 1)),
                GeneralErrorCode.INVALID_PARAMETER);
        assertError(() -> board.move(user, a.getJobApplicationId(), position(JobApplicationStage.PLANNED, -1)),
                GeneralErrorCode.INVALID_PARAMETER);
        assertError(() -> board.move(user(), a.getJobApplicationId(), position(JobApplicationStage.PLANNED, 0)),
                GeneralErrorCode.FORBIDDEN);
        assertError(() -> board.move(user, Long.MAX_VALUE, position(JobApplicationStage.PLANNED, 0)),
                GeneralErrorCode.JOB_APPLICATION_NOT_FOUND);
        assertThat(ids(board.getBoard(user, "", null), JobApplicationStage.PLANNED)).containsExactly(a.getJobApplicationId());
        ReflectionTestUtils.setField(applications.findById(a.getJobApplicationId()).orElseThrow(), "archivedAt", LocalDateTime.now());
        assertError(() -> board.move(user, a.getJobApplicationId(), position(JobApplicationStage.PLANNED, 0)),
                GeneralErrorCode.JOB_APPLICATION_UPDATE_CONFLICT);
    }

    @Test
    void skillsAndEssayCountsDoNotIntroducePerCardQueries() {
        User user = user();
        var first = create(user, "기업", "a", "직무", JobApplicationStage.PLANNED);
        var application = applications.findById(first.getJobApplicationId()).orElseThrow();
        essays.save(JobApplicationEssay.create(application, "질문 1", "답변", 0));
        essays.save(JobApplicationEssay.create(application, "질문 2", "답변", 1));
        em.flush();
        em.clear();
        var statistics = em.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        boolean enabled = statistics.isStatisticsEnabled();
        statistics.setStatisticsEnabled(true);
        try {
            statistics.clear();
            var initial = board.getBoard(user, "", null);
            long initialQueries = statistics.getPrepareStatementCount();
            assertThat(initial.columns().getFirst().cards().getFirst().essayQuestionCount()).isEqualTo(2);
            assertThat(initial.columns().getFirst().cards().getFirst().requiredSkills()).containsExactly("Java", "SQL");
            for (int i = 0; i < 12; i++) create(user, "기업", "추가 " + i, "직무", JobApplicationStage.PLANNED);
            em.flush();
            em.clear();
            statistics.clear();
            var expanded = board.getBoard(user, "", null);
            assertThat(expanded.columns().getFirst().count()).isEqualTo(13);
            assertThat(statistics.getPrepareStatementCount()).isEqualTo(initialQueries);
            assertThat(initialQueries).isLessThanOrEqualTo(3);
        } finally {
            statistics.setStatisticsEnabled(enabled);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentMovesAndRegistrationsPreserveCardsAndContiguousOrder(boolean emptyDestination) throws Exception {
        User user = user();
        var a = create(user, "기업", "a", "직무", JobApplicationStage.PLANNED);
        var b = create(user, "기업", "b", "직무", JobApplicationStage.DOCUMENT);
        JobApplicationStage firstTarget = emptyDestination ? JobApplicationStage.INTERVIEW : JobApplicationStage.DOCUMENT;
        JobApplicationStage secondTarget = emptyDestination ? JobApplicationStage.INTERVIEW : JobApplicationStage.PLANNED;
        CountDownLatch ready = new CountDownLatch(3);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(3);
        try {
            List<Callable<Void>> actions = List.of(
                    () -> { board.move(user, a.getJobApplicationId(), position(firstTarget, 0)); return null; },
                    () -> { board.move(user, b.getJobApplicationId(), position(secondTarget, 0)); return null; },
                    () -> { create(user, "기업", "동시 등록", "직무", firstTarget); return null; });
            List<Future<Void>> futures = new ArrayList<>();
            for (Callable<Void> action : actions) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("시작 대기 시간 초과");
                    return action.call();
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (Future<Void> future : futures) future.get(20, TimeUnit.SECONDS);
            var result = board.getBoard(user, "", null);
            assertContiguous(result);
            assertThat(result.columns().stream().flatMap(column -> column.cards().stream()).toList())
                    .hasSize(3).extracting(JobApplicationCardResponse::jobApplicationId).doesNotHaveDuplicates();
            assertThat(ids(result, secondTarget)).contains(b.getJobApplicationId());
            assertThat(ids(result, firstTarget)).contains(a.getJobApplicationId());
        } finally {
            start.countDown();
            executor.shutdownNow();
            executor.awaitTermination(10, TimeUnit.SECONDS);
        }
    }

    private User user() {
        return users.save(User.signup("칸반", UUID.randomUUID() + "@example.com", "password"));
    }

    private JobApplicationResponse create(User user, String company, String posting, String title, JobApplicationStage stage) {
        return registration.create(user, new JobApplicationCreateRequest(company, posting, title,
                null, null, null, null, null, List.of("Java", "SQL"),
                LocalDateTime.of(2026, 10, 1, 18, 0), stage, "진행", LocalDateTime.of(2026, 9, 20, 12, 0)));
    }

    private JobApplicationPositionRequest position(JobApplicationStage stage, int index) {
        return new JobApplicationPositionRequest(stage, index);
    }

    private List<Long> ids(JobApplicationBoardResponse response, JobApplicationStage stage) {
        return response.columns().stream().filter(column -> column.stage() == stage).findFirst().orElseThrow()
                .cards().stream().map(JobApplicationCardResponse::jobApplicationId).toList();
    }

    private void assertContiguous(JobApplicationBoardResponse response) {
        response.columns().forEach(column -> assertThat(column.cards()).extracting(JobApplicationCardResponse::stageOrder)
                .containsExactlyElementsOf(IntStream.range(0, column.count()).boxed().toList()));
    }

    private void assertError(Runnable action, GeneralErrorCode code) {
        assertThatThrownBy(action::run).isInstanceOf(GeneralException.class).extracting("code").isEqualTo(code);
    }
}
