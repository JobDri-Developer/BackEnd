package com.jobdri.jobdri_api.domain.analysis.service.async;

import com.jobdri.jobdri_api.domain.analysis.application.usecase.async.AnalysisAsyncUseCase;
import com.jobdri.jobdri_api.domain.analysis.dto.response.AnalysisAsyncCancelResponse;
import com.jobdri.jobdri_api.domain.analysis.dto.response.AnalysisAsyncStatusResponse;
import com.jobdri.jobdri_api.domain.analysis.dto.response.AnalysisAsyncSubmitResponse;
import com.jobdri.jobdri_api.domain.analysis.service.core.AnalysisService;
import com.jobdri.jobdri_api.domain.user.entity.User;
import com.jobdri.jobdri_api.domain.user.service.UserService;
import org.springframework.stereotype.Service;

@Service
// 분석 비동기 작업의 접수와 상태 조회를 외부 API 관점에서 조율하는 서비스다.
public class AnalysisAsyncFacadeService {
    private final AnalysisAsyncUseCase analysisAsyncUseCase;

    public AnalysisAsyncFacadeService(
            AnalysisAsyncTaskService analysisAsyncTaskService,
            AnalysisAsyncProcessor analysisAsyncProcessor,
            AnalysisService analysisService,
            UserService userService
    ) {
        this.analysisAsyncUseCase = new AnalysisAsyncUseCase(
                analysisAsyncTaskService,
                analysisAsyncProcessor,
                analysisService,
                userService
        );
    }

    public AnalysisAsyncSubmitResponse submit(User user, Long mockApplyId) {
        return analysisAsyncUseCase.submit(user, mockApplyId);
    }

    public AnalysisAsyncStatusResponse getTask(User user, Long mockApplyId, String taskId) {
        return analysisAsyncUseCase.getTask(user, mockApplyId, taskId);
    }

    public AnalysisAsyncCancelResponse cancel(User user, Long mockApplyId, String taskId) {
        return analysisAsyncUseCase.cancel(user, mockApplyId, taskId);
    }
}
