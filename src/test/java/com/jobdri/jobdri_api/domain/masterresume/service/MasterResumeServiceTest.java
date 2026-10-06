package com.jobdri.jobdri_api.domain.masterresume.service;

import com.jobdri.jobdri_api.domain.jobapplication.entity.JobApplicationMetricType;
import com.jobdri.jobdri_api.domain.jobapplication.dto.request.JobApplicationCreateRequest;
import com.jobdri.jobdri_api.domain.jobapplication.dto.request.JobApplicationMetricRequest;
import com.jobdri.jobdri_api.domain.jobapplication.service.JobApplicationService;
import com.jobdri.jobdri_api.domain.jobapplication.service.JobApplicationDetailService;
import com.jobdri.jobdri_api.domain.masterresume.dto.MasterResumeRequest;
import com.jobdri.jobdri_api.domain.masterresume.repository.MasterResumeRepository;
import com.jobdri.jobdri_api.domain.user.entity.User;
import com.jobdri.jobdri_api.domain.user.repository.UserRepository;
import com.jobdri.jobdri_api.global.apiPayload.exception.GeneralException;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MasterResumeServiceTest {
    @Autowired MasterResumeService service;
    @Autowired MasterResumeRepository resumes;
    @Autowired UserRepository users;
    @Autowired EntityManager entityManager;
    @Autowired JobApplicationService applications;
    @Autowired JobApplicationDetailService details;

    @Test
    void savesAndReplacesResumeWithoutAccumulatingRows() {
        User user = users.saveAndFlush(User.signup("이력서 사용자",
                UUID.randomUUID() + "@example.com", "encoded-password"));
        assertThat(service.get(user).metrics()).isEmpty();

        var first = service.save(user, new MasterResumeRequest(new BigDecimal("3.8"), new BigDecimal("4.5"),
                List.of(new MasterResumeRequest.Metric(JobApplicationMetricType.CERTIFICATE, "정보처리기사", "취득"),
                        new MasterResumeRequest.Metric(JobApplicationMetricType.LANGUAGE, "", "")),
                List.of(new MasterResumeRequest.Experience("프로젝트", "2025.01 - 2025.06", "서비스 개발"))));
        entityManager.flush();
        entityManager.clear();

        var stored = service.get(user);
        assertThat(stored.gpa()).isEqualByComparingTo("3.8");
        assertThat(stored.metrics()).hasSize(1);
        assertThat(stored.experiences()).hasSize(1);

        service.save(user, new MasterResumeRequest(null, null, List.of(), List.of(), stored.updatedAt()));
        entityManager.flush();
        entityManager.clear();
        assertThat(service.get(user).metrics()).isEmpty();
        assertThat(service.get(user).experiences()).isEmpty();
        assertThat(resumes.count()).isEqualTo(1);
    }

    @Test
    void copiedSpecsRemainOnApplicationAfterMasterResumeChanges() {
        User user = users.saveAndFlush(User.signup("복사 사용자",
                UUID.randomUUID() + "@example.com", "encoded-password"));
        var original = service.save(user, new MasterResumeRequest(
                new BigDecimal("3.8"), new BigDecimal("4.5"),
                List.of(new MasterResumeRequest.Metric(JobApplicationMetricType.CERTIFICATE, "정보처리기사", "취득")),
                List.of()));
        var card = applications.create(user, new JobApplicationCreateRequest(
                "회사", "공고", "직무", null, null, null, null, null, List.of(),
                null, null, null, null, original.gpa(), original.maxGpa(),
                original.metrics().stream().map(metric -> new JobApplicationMetricRequest(
                        metric.type(), metric.name(), metric.value())).toList()));
        service.save(user, new MasterResumeRequest(null, null, List.of(), List.of(), original.updatedAt()));
        entityManager.flush();
        entityManager.clear();

        var detail = details.get(user, card.getJobApplicationId());
        assertThat(detail.getGpa()).isEqualByComparingTo("3.8");
        assertThat(detail.getMetrics()).hasSize(1);
        assertThat(detail.getMetrics().get(0).name()).isEqualTo("정보처리기사");
    }

    @Test
    void rejectsStaleSaveAndKeepsAnotherUsersResumePrivate() {
        User owner = users.saveAndFlush(User.signup("소유자",
                UUID.randomUUID() + "@example.com", "encoded-password"));
        User other = users.saveAndFlush(User.signup("다른 사용자",
                UUID.randomUUID() + "@example.com", "encoded-password"));
        var saved = service.save(owner, new MasterResumeRequest(null, null,
                List.of(new MasterResumeRequest.Metric(JobApplicationMetricType.CERTIFICATE, "자격증", "")),
                List.of()));
        assertThat(service.get(other).metrics()).isEmpty();
        assertThat(saved.metrics()).hasSize(1);
        assertThat(saved.metrics().get(0).value()).isEqualTo("보유");

        assertThatThrownBy(() -> service.save(owner,
                new MasterResumeRequest(null, null, List.of(), List.of())))
                .isInstanceOf(GeneralException.class)
                .hasMessageContaining("이미 수정");
    }
}
