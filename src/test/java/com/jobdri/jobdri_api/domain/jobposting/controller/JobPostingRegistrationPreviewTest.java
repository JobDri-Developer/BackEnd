package com.jobdri.jobdri_api.domain.jobposting.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jobdri.jobdri_api.domain.jobapplication.repository.JobApplicationRepository;
import com.jobdri.jobdri_api.domain.jobposting.dto.request.JobPostingExtractRequest;
import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingExtractResponse;
import com.jobdri.jobdri_api.domain.jobposting.repository.JobPostingAsyncTaskRepository;
import com.jobdri.jobdri_api.domain.jobposting.repository.JobPostingRepository;
import com.jobdri.jobdri_api.domain.jobposting.service.JobPostingAiService;
import com.jobdri.jobdri_api.domain.user.entity.User;
import com.jobdri.jobdri_api.domain.user.repository.UserRepository;
import com.jobdri.jobdri_api.global.apiPayload.code.GeneralErrorCode;
import com.jobdri.jobdri_api.global.apiPayload.exception.GeneralException;
import com.jobdri.jobdri_api.global.security.UserDetailsImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class JobPostingRegistrationPreviewTest {
    private static final String PATH = "/api/job-postings/extract/preview";
    private static final String INPUT = "{\"rawText\":\"채용 공고 본문: Java, SQL 백엔드 개발자 모집\"}";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired UserRepository users;
    @Autowired JobApplicationRepository applications;
    @Autowired JobPostingRepository postings;
    @Autowired JobPostingAsyncTaskRepository tasks;
    @MockitoBean JobPostingAiService ai;

    @Test
    void previewDoesNotSaveAndEditedDraftCanBeRegistered() throws Exception {
        User owner = owner();
        long cardCount = applications.count(), postingCount = postings.count(), taskCount = tasks.count();
        when(ai.extractForPreview(eq(owner.getId()), any(JobPostingExtractRequest.class)))
                .thenReturn(new JobPostingExtractResponse("백엔드 채용", "예시 기업", "백엔드 개발자",
                        "API 개발", "Java", "SQL", "원문", 0.9,
                        Arrays.asList(" Java ", "SQL", "Java", "", null, "x".repeat(51)), "2026-11-30T18:00:00"));

        var response = mvc.perform(post(PATH).with(user(new UserDetailsImpl(owner)))
                        .contentType(MediaType.APPLICATION_JSON).content(INPUT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.draft.requirement").value("Java"))
                .andExpect(jsonPath("$.result.draft.preferred").value("SQL"))
                .andExpect(jsonPath("$.result.draft.requiredSkills.length()").value(2))
                .andExpect(jsonPath("$.result.draft.requiredSkills[0]").value("Java"))
                .andExpect(jsonPath("$.result.draft.requiredSkills[1]").value("SQL"))
                .andExpect(jsonPath("$.result.draft.deadlineAt").value("2026-11-30T18:00:00"))
                .andExpect(jsonPath("$.result.missingRequiredFields").isEmpty())
                .andReturn();
        assertThat(applications.count()).isEqualTo(cardCount);
        assertThat(postings.count()).isEqualTo(postingCount);
        assertThat(tasks.count()).isEqualTo(taskCount);

        ObjectNode draft = (ObjectNode) mapper.readTree(response.getResponse().getContentAsString())
                .path("result").path("draft");
        draft.put("companyName", "확인한 회사");
        mvc.perform(post("/api/job-applications").with(user(new UserDetailsImpl(owner)))
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(draft)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.companyName").value("확인한 회사"))
                .andExpect(jsonPath("$.result.stage").value("PLANNED"));
        assertThat(applications.count()).isEqualTo(cardCount + 1);
    }

    @Test
    void reportsMissingFieldsAndDoesNotGuessAnInvalidDeadline() throws Exception {
        User owner = owner();
        when(ai.extractForPreview(eq(owner.getId()), eq(new JobPostingExtractRequest(null, "image.png"))))
                .thenReturn(new JobPostingExtractResponse("", null, " ", "", "", "", "", 0.0,
                        List.of(), "2026-11-30T18:00:00Z"));
        mvc.perform(post(PATH).with(user(new UserDetailsImpl(owner)))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"imageObjectKey\":\"image.png\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.draft.companyName").value(""))
                .andExpect(jsonPath("$.result.draft.deadlineAt").doesNotExist())
                .andExpect(jsonPath("$.result.missingRequiredFields[0]").value("companyName"))
                .andExpect(jsonPath("$.result.missingRequiredFields[1]").value("jobTitle"));
    }

    @Test
    void rejectsUnauthenticatedAndEmptyRequestsBeforeExtraction() throws Exception {
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(INPUT))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(PATH).with(user(new UserDetailsImpl(owner())))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(ai);
    }

    @Test
    void providerFailureIsAnErrorRatherThanAnEmptyDraft() throws Exception {
        User owner = owner();
        when(ai.extractForPreview(eq(owner.getId()), any(JobPostingExtractRequest.class)))
                .thenThrow(new GeneralException(GeneralErrorCode.SERVICE_UNAVAILABLE));
        mvc.perform(post(PATH).with(user(new UserDetailsImpl(owner)))
                        .contentType(MediaType.APPLICATION_JSON).content(INPUT))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("SERVER_5031"));
    }

    private User owner() {
        return users.saveAndFlush(User.signup("등록 테스트", UUID.randomUUID() + "@example.com", "encoded"));
    }
}
