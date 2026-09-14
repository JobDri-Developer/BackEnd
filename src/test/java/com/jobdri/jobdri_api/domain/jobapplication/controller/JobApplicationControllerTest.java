package com.jobdri.jobdri_api.domain.jobapplication.controller;

import com.jobdri.jobdri_api.domain.user.entity.User;
import com.jobdri.jobdri_api.domain.user.repository.UserRepository;
import com.jobdri.jobdri_api.global.security.UserDetailsImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class JobApplicationControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired com.jobdri.jobdri_api.domain.jobapplication.service.JobApplicationService applications;

    @Test
    void boardRouteAndPositionContract() throws Exception {
        User owner = saveUser();
        var card = applications.create(owner,
                new com.jobdri.jobdri_api.domain.jobapplication.dto.request.JobApplicationCreateRequest(
                        "기업", "공고", "직무", null, null, null, null, null, null, null, null, null, null));
        mockMvc.perform(get("/api/job-applications/board").with(user(new UserDetailsImpl(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.sort").value("MANUAL"))
                .andExpect(jsonPath("$.result.columns.length()").value(4))
                .andExpect(jsonPath("$.result.columns[0].count").value(1));
        mockMvc.perform(patch("/api/job-applications/{id}/position", card.getJobApplicationId())
                        .with(user(new UserDetailsImpl(owner))).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetStage\":\"INTERVIEW\",\"targetIndex\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.columns[0].count").value(0))
                .andExpect(jsonPath("$.result.columns[2].cards[0].jobApplicationId").value(card.getJobApplicationId()));
        mockMvc.perform(patch("/api/job-applications/{id}/position", card.getJobApplicationId())
                        .with(user(new UserDetailsImpl(owner))).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetStage\":\"INTERVIEW\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/job-applications/board").param("sort", "INVALID")
                        .with(user(new UserDetailsImpl(owner))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/job-applications/board"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("수동 등록 API는 최소 입력으로 독립 지원 카드를 반환한다")
    void createMinimalCard() throws Exception {
        User savedUser = saveUser();

        mockMvc.perform(post("/api/job-applications")
                        .with(user(new UserDetailsImpl(savedUser)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "companyName": "API 테스트 기업",
                                  "postingName": "백엔드 채용",
                                  "jobTitle": "백엔드 엔지니어"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.result.jobApplicationId").isNumber())
                .andExpect(jsonPath("$.result.stage").value("PLANNED"))
                .andExpect(jsonPath("$.result.stageOrder").value(0))
                .andExpect(jsonPath("$.result.sourceJobPostingId").doesNotExist())
                .andExpect(jsonPath("$.result.mockApplyId").doesNotExist());
    }

    @Test
    @DisplayName("수동 등록 API는 필수 스냅샷 필드 누락을 거절한다")
    void rejectMissingRequiredFields() throws Exception {
        User savedUser = saveUser();

        mockMvc.perform(post("/api/job-applications")
                        .with(user(new UserDetailsImpl(savedUser)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "companyName": " ",
                                  "postingName": "",
                                  "jobTitle": null
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("REQ_4002"))
                .andExpect(jsonPath("$.error.length()").value(3));
    }

    private User saveUser() {
        return userRepository.save(User.signup(
                "API 테스트 사용자",
                "application-controller-" + UUID.randomUUID() + "@example.com",
                "encoded-password"
        ));
    }
}
