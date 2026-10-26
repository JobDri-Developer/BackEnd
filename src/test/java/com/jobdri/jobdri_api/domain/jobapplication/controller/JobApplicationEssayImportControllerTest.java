package com.jobdri.jobdri_api.domain.jobapplication.controller;

import com.jobdri.jobdri_api.domain.jobapplication.dto.request.JobApplicationCreateRequest;
import com.jobdri.jobdri_api.domain.jobapplication.entity.JobApplicationEssay;
import com.jobdri.jobdri_api.domain.jobapplication.repository.JobApplicationRepository;
import com.jobdri.jobdri_api.domain.jobapplication.service.JobApplicationService;
import com.jobdri.jobdri_api.domain.user.entity.User;
import com.jobdri.jobdri_api.domain.user.repository.UserRepository;
import com.jobdri.jobdri_api.global.security.UserDetailsImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class JobApplicationEssayImportControllerTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired JobApplicationService service;
    @Autowired JobApplicationRepository applications;

    @Test
    void searchesOnlyOwnedSourcesWithQuestionsAndPaginatesDistinctCards() throws Exception {
        User owner = owner();
        long target = card(owner, "Target", true);
        card(owner, "No questions", false);
        card(owner(), "Other owner", true);
        long first = card(owner, "Spring old", true);
        long last = card(owner, "Spring new", true);
        mvc.perform(get(path(target)).with(user(new UserDetailsImpl(owner)))
                        .param("query", "  SPRING  ").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.totalElements").value(2))
                .andExpect(jsonPath("$.result.content.length()").value(1))
                .andExpect(jsonPath("$.result.content[0].jobApplicationId").value(last))
                .andExpect(jsonPath("$.result.content[0].essayQuestionCount").value(2));
        mvc.perform(get(path(target)).with(user(new UserDetailsImpl(owner)))
                        .param("query", "spring").param("size", "1").param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.content[0].jobApplicationId").value(first));
        mvc.perform(get(path(target)).with(user(new UserDetailsImpl(owner))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.totalElements").value(2));
    }

    @Test
    void searchesCompanyAndJobTitleAndTreatsWildcardsLiterally() throws Exception {
        User owner = owner();
        long target = card(owner, "Target", false);
        long source = card(owner, "100%_! Spring", true);
        card(owner, "100xxxx Spring", true);
        for (String query : List.of("100%_!", "Example company", "Backend engineer")) {
            mvc.perform(get(path(target)).with(user(new UserDetailsImpl(owner))).param("query", query))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result.totalElements").value(query.equals("100%_!") ? 1 : 2));
        }
        mvc.perform(get(path(target)).with(user(new UserDetailsImpl(owner))).param("query", "100%_!"))
                .andExpect(jsonPath("$.result.content[0].jobApplicationId").value(source));
    }

    @Test
    void archiveIsExplicitAndEmptyResultsAreAllowed() throws Exception {
        User owner = owner();
        long target = card(owner, "Target", false);
        long source = card(owner, "Archived", true);
        applications.findById(source).orElseThrow().archive(LocalDateTime.now());
        applications.flush();
        mvc.perform(get(path(target)).with(user(new UserDetailsImpl(owner))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.content").isEmpty());
        mvc.perform(get(path(target)).with(user(new UserDetailsImpl(owner))).param("includeArchived", "true"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.content[0].jobApplicationId").value(source));
        mvc.perform(get(path(target) + "/" + target).with(user(new UserDetailsImpl(owner))))
                .andExpect(status().isBadRequest());
        long empty = card(owner, "Empty", false);
        mvc.perform(get(path(target) + "/" + empty).with(user(new UserDetailsImpl(owner))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result").isEmpty());
    }

    @Test
    void previewPreservesOrderAndAnswersWithoutChangingEitherCard() throws Exception {
        User owner = owner();
        long target = card(owner, "Target", true);
        long source = card(owner, "Source", true);
        var original = applications.findById(source).orElseThrow();
        var destination = applications.findById(target).orElseThrow();
        long revision = destination.getContentRevision();
        var sourceIds = original.getEssays().stream().map(JobApplicationEssay::getId).toList();
        var targetIds = destination.getEssays().stream().map(JobApplicationEssay::getId).toList();
        mvc.perform(get(path(target) + "/" + source).with(user(new UserDetailsImpl(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.length()").value(2))
                .andExpect(jsonPath("$.result[0].essayId").value(sourceIds.get(1)))
                .andExpect(jsonPath("$.result[0].question").value("First question"))
                .andExpect(jsonPath("$.result[0].answer").value("First answer\nSecond line"))
                .andExpect(jsonPath("$.result[1].question").value("Second question"));
        assertThat(destination.getContentRevision()).isEqualTo(revision);
        assertThat(destination.getEssays()).extracting(JobApplicationEssay::getId).containsExactlyElementsOf(targetIds);
        assertThat(original.getEssays()).extracting(JobApplicationEssay::getId).containsExactlyElementsOf(sourceIds);
    }

    @Test
    void rejectsUnauthenticatedAndCrossUserTargetsAndSources() throws Exception {
        User owner = owner();
        long target = card(owner, "Target", false);
        long foreign = card(owner(), "Private", true);
        mvc.perform(get(path(target))).andExpect(status().isUnauthorized());
        mvc.perform(get(path(target) + "/" + foreign)).andExpect(status().isUnauthorized());
        mvc.perform(get(path(foreign)).with(user(new UserDetailsImpl(owner))))
                .andExpect(status().isForbidden());
        mvc.perform(get(path(foreign) + "/" + target).with(user(new UserDetailsImpl(owner))))
                .andExpect(status().isForbidden());
        mvc.perform(get(path(target) + "/" + foreign).with(user(new UserDetailsImpl(owner))))
                .andExpect(status().isForbidden());
        mvc.perform(get(path(Long.MAX_VALUE)).with(user(new UserDetailsImpl(owner))))
                .andExpect(status().isNotFound());
        mvc.perform(get(path(target) + "/" + Long.MAX_VALUE).with(user(new UserDetailsImpl(owner))))
                .andExpect(status().isNotFound());
    }

    private String path(long target) {
        return "/api/job-applications/" + target + "/essay-sources";
    }

    private User owner() {
        return users.saveAndFlush(User.signup("Essay user", UUID.randomUUID() + "@example.com", "encoded"));
    }

    private long card(User owner, String name, boolean withQuestions) {
        long id = service.create(owner, new JobApplicationCreateRequest(
                "Example company", name, "Backend engineer", null, null,
                null, null, null, null, null, null, null, null)).getJobApplicationId();
        var card = applications.findById(id).orElseThrow();
        if (withQuestions) {
            card.replaceEssays(List.of(
                    JobApplicationEssay.create(card, "Second question", null, 1),
                    JobApplicationEssay.create(card, "First question", "First answer\nSecond line", 0)));
        }
        applications.flush();
        return id;
    }
}
