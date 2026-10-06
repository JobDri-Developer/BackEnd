package com.jobdri.jobdri_api.domain.masterresume.controller;

import com.jobdri.jobdri_api.domain.user.entity.User;
import com.jobdri.jobdri_api.domain.user.repository.UserRepository;
import com.jobdri.jobdri_api.global.security.UserDetailsImpl;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class MasterResumeControllerTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;

    @Test
    void getsEmptyResumeAndSavesBothTabsTogether() throws Exception {
        User owner = users.saveAndFlush(User.signup("API 사용자",
                UUID.randomUUID() + "@example.com", "encoded-password"));
        mvc.perform(get("/api/master-resume").with(user(new UserDetailsImpl(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.metrics").isEmpty())
                .andExpect(jsonPath("$.result.contentRevision").value(0));

        mvc.perform(put("/api/master-resume").with(user(new UserDetailsImpl(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"gpa":3.8,"maxGpa":4.5,
                                 "metrics":[{"type":"CERTIFICATE","name":"정보처리기사"}],
                                 "experiences":[{"name":"프로젝트","period":"2025.01 - 2025.06",
                                                 "description":"서비스 개발"}]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.metrics[0].name").value("정보처리기사"))
                .andExpect(jsonPath("$.result.experiences[0].name").value("프로젝트"))
                .andExpect(jsonPath("$.result.contentRevision").value(1));
    }
}
