package com.jobdri.jobdri_api.domain.jobapplication.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobdri.jobdri_api.domain.jobapplication.dto.request.JobApplicationCreateRequest;
import com.jobdri.jobdri_api.domain.jobapplication.entity.JobApplicationChecklistItem;
import com.jobdri.jobdri_api.domain.jobapplication.repository.JobApplicationRepository;
import com.jobdri.jobdri_api.domain.jobapplication.repository.JobApplicationTaskRepository;
import com.jobdri.jobdri_api.domain.jobapplication.service.JobApplicationService;
import com.jobdri.jobdri_api.domain.user.entity.User;
import com.jobdri.jobdri_api.domain.user.repository.UserRepository;
import com.jobdri.jobdri_api.global.security.UserDetailsImpl;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class JobApplicationTaskControllerTest {
    private static final String APPLICATION = "/api/job-applications/{applicationId}";
    private static final String TASKS = APPLICATION + "/tasks";
    private static final String TASK = TASKS + "/{taskId}";
    private static final String COMPLETION = TASK + "/completion";
    private static final String TITLE_ONLY = """
            {"title":"Prepare application"}
            """;

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository users;
    @Autowired JobApplicationService applicationService;
    @Autowired JobApplicationRepository applications;
    @Autowired JobApplicationTaskRepository tasks;
    @Autowired EntityManager em;

    @Test
    void createsReadsUpdatesCompletesAndDeletesTaskWithoutChangingLegacyChecklist() throws Exception {
        User owner = owner();
        long applicationId = application(owner);
        var card = applications.findById(applicationId).orElseThrow();
        card.replaceChecklistItems(List.of(JobApplicationChecklistItem.create(card, "Legacy item", true, 0)));
        long taskId = createTask(owner, applicationId, """
                {
                  "title":" Prepare application ",
                  "dueDate":"2026-11-15",
                  "memo":"Check the posting",
                  "checklistItems":[{"content":"Draft","completed":true},{"content":"Review"}]
                }
                """);
        flushAndClear();

        mvc.perform(get(TASKS, applicationId).with(user(new UserDetailsImpl(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.length()").value(1))
                .andExpect(jsonPath("$.result[0].taskId").value(taskId))
                .andExpect(jsonPath("$.result[0].title").value("Prepare application"))
                .andExpect(jsonPath("$.result[0].dueDate").value("2026-11-15"))
                .andExpect(jsonPath("$.result[0].memo").value("Check the posting"))
                .andExpect(jsonPath("$.result[0].completed").value(false))
                .andExpect(jsonPath("$.result[0].checklistItems[0].content").value("Draft"))
                .andExpect(jsonPath("$.result[0].checklistItems[1].content").value("Review"))
                .andExpect(jsonPath("$.result[0].completedChecklistCount").value(1))
                .andExpect(jsonPath("$.result[0].totalChecklistCount").value(2));

        for (int attempt = 0; attempt < 2; attempt++) {
            mvc.perform(json(patch(COMPLETION, applicationId, taskId), owner, "{\"completed\":true}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result.completed").value(true))
                    .andExpect(jsonPath("$.result.completedChecklistCount").value(1));
        }
        flushAndClear();

        mvc.perform(json(put(TASK, applicationId, taskId), owner, """
                        {"title":"Submit application","checklistItems":[{"content":"Submit","completed":true}]}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.completed").value(true))
                .andExpect(jsonPath("$.result.dueDate").isEmpty())
                .andExpect(jsonPath("$.result.memo").isEmpty());
        flushAndClear();

        mvc.perform(get(TASK, applicationId, taskId).with(user(new UserDetailsImpl(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.title").value("Submit application"))
                .andExpect(jsonPath("$.result.checklistItems[0].content").value("Submit"))
                .andExpect(jsonPath("$.result.totalChecklistCount").value(1));
        mvc.perform(json(patch(COMPLETION, applicationId, taskId), owner, "{\"completed\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.completed").value(false));
        mvc.perform(delete(TASK, applicationId, taskId).with(user(new UserDetailsImpl(owner))))
                .andExpect(status().isOk());
        flushAndClear();

        mvc.perform(get(TASK, applicationId, taskId).with(user(new UserDetailsImpl(owner))))
                .andExpect(status().isNotFound());
        mvc.perform(get(TASKS, applicationId).with(user(new UserDetailsImpl(owner))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result").isEmpty());
        mvc.perform(get(APPLICATION, applicationId).with(user(new UserDetailsImpl(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.checklistItems[0].content").value("Legacy item"))
                .andExpect(jsonPath("$.result.checklistItems[0].completed").value(true));
    }

    @Test
    void rejectsUnauthenticatedForeignAndMismatchedTaskRequests() throws Exception {
        User owner = owner();
        User outsider = owner();
        long applicationId = application(owner);
        long anotherApplicationId = application(owner);
        long outsiderApplicationId = application(outsider);
        long taskId = createTask(owner, applicationId, TITLE_ONLY);

        mvc.perform(get(TASKS, applicationId)).andExpect(status().isUnauthorized());
        mvc.perform(get(TASKS, applicationId).with(user(new UserDetailsImpl(outsider))))
                .andExpect(status().isForbidden());
        mvc.perform(json(post(TASKS, applicationId), outsider, TITLE_ONLY)).andExpect(status().isForbidden());
        for (var request : List.of(get(TASK, applicationId, taskId), delete(TASK, applicationId, taskId),
                json(put(TASK, applicationId, taskId), outsider, TITLE_ONLY),
                json(patch(COMPLETION, applicationId, taskId), outsider, "{\"completed\":true}"))) {
            mvc.perform(request.with(user(new UserDetailsImpl(outsider)))).andExpect(status().isForbidden());
        }
        mvc.perform(get(TASK, anotherApplicationId, taskId).with(user(new UserDetailsImpl(owner))))
                .andExpect(status().isNotFound());
        mvc.perform(delete(TASK, outsiderApplicationId, taskId).with(user(new UserDetailsImpl(outsider))))
                .andExpect(status().isNotFound());
        mvc.perform(get(TASK, applicationId, taskId).with(user(new UserDetailsImpl(owner))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.completed").value(false));
    }

    @Test
    void validatesRequestsAndCascadesPermanentApplicationDeletion() throws Exception {
        User owner = owner();
        long applicationId = application(owner);
        for (String body : List.of(
                "{\"title\":\" \"}",
                "{\"title\":\"Task\",\"dueDate\":\"tomorrow\"}",
                "{\"title\":\"Task\",\"checklistItems\":[null]}",
                "{\"title\":\"Task\",\"checklistItems\":[{\"content\":\" \"}]}")) {
            mvc.perform(json(post(TASKS, applicationId), owner, body)).andExpect(status().isBadRequest());
        }
        long taskId = createTask(owner, applicationId,
                "{\"title\":\"Task\",\"checklistItems\":[{\"content\":\"Draft\"}]}");
        mvc.perform(json(patch(COMPLETION, applicationId, taskId), owner, "{}"))
                .andExpect(status().isBadRequest());
        flushAndClear();

        mvc.perform(delete(APPLICATION, applicationId).with(user(new UserDetailsImpl(owner))))
                .andExpect(status().isOk());
        flushAndClear();
        assertThat(tasks.findById(taskId)).isEmpty();
        assertThat(((Number) em.createNativeQuery(
                        "select count(*) from job_application_task_checklist_items where task_id = :id")
                .setParameter("id", taskId).getSingleResult()).longValue()).isZero();
    }

    private long createTask(User owner, long applicationId, String body) throws Exception {
        var response = mvc.perform(json(post(TASKS, applicationId), owner, body))
                .andExpect(status().isOk()).andReturn().getResponse();
        return objectMapper.readTree(response.getContentAsByteArray()).path("result").path("taskId").asLong();
    }

    private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, User owner, String body) {
        return request.with(user(new UserDetailsImpl(owner))).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private User owner() {
        return users.saveAndFlush(User.signup("Task user", UUID.randomUUID() + "@example.com", "encoded"));
    }

    private long application(User owner) {
        return applicationService.create(owner, new JobApplicationCreateRequest(
                "Company", "Posting", "Backend", null, null, null, null, null,
                null, null, null, null, null)).getJobApplicationId();
    }

    private void flushAndClear() {
        em.flush();
        em.clear();
    }
}
