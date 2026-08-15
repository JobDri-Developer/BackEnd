package com.jobdri.jobdri_api.global.security;

import com.jobdri.jobdri_api.domain.analysis.dto.response.AnalysisAsyncStatusResponse;
import com.jobdri.jobdri_api.domain.analysis.service.async.AnalysisAsyncTaskService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.worker.internal-api-key=test-internal-api-key")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InternalWorkerApiKeyFilterIntegrationTest {

    private static final String INTERNAL_API_KEY_HEADER = "X-Internal-Api-Key";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AnalysisAsyncTaskService analysisAsyncTaskService;

    @Test
    @DisplayName("내부 worker API는 헤더가 없으면 403을 반환한다")
    void rejectsRequestWithoutInternalApiKey() throws Exception {
        mockMvc.perform(get("/api/internal/worker/analysis/tasks/test-task"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("AUTH_4031"))
                .andExpect(jsonPath("$.message").value("접근 권한이 없습니다."))
                .andExpect(jsonPath("$.error").value("내부 worker 인증에 실패했습니다."));

        verifyNoInteractions(analysisAsyncTaskService);
    }

    @Test
    @DisplayName("내부 worker API는 잘못된 키면 403을 반환한다")
    void rejectsRequestWithInvalidInternalApiKey() throws Exception {
        mockMvc.perform(get("/api/internal/worker/analysis/tasks/test-task")
                        .header(INTERNAL_API_KEY_HEADER, "wrong-key"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("AUTH_4031"))
                .andExpect(jsonPath("$.message").value("접근 권한이 없습니다."))
                .andExpect(jsonPath("$.error").value("내부 worker 인증에 실패했습니다."));

        verifyNoInteractions(analysisAsyncTaskService);
    }

    @Test
    @DisplayName("내부 worker API는 올바른 키면 컨트롤러까지 요청을 전달한다")
    void allowsRequestWithValidInternalApiKey() throws Exception {
        when(analysisAsyncTaskService.getTaskStatusByTaskId(anyString()))
                .thenReturn(AnalysisAsyncStatusResponse.builder()
                        .taskId("test-task")
                        .status("RUNNING")
                        .message("processing")
                        .build());

        mockMvc.perform(get("/api/internal/worker/analysis/tasks/test-task")
                        .header(INTERNAL_API_KEY_HEADER, "test-internal-api-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.result.taskId").value("test-task"))
                .andExpect(jsonPath("$.result.status").value("RUNNING"));
    }
}
