package com.jobdri.jobdri_api.domain.analysis.service.async;

import com.jobdri.jobdri_api.domain.analysis.application.usecase.async.AnalysisAsyncUseCase;
import com.jobdri.jobdri_api.domain.analysis.service.core.AnalysisService;
import com.jobdri.jobdri_api.domain.user.service.UserService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
// 분석 비동기 작업의 접수와 상태 조회를 외부 API 관점에서 조율하는 서비스다.
public class AnalysisAsyncFacadeService extends AnalysisAsyncUseCase {

    public AnalysisAsyncFacadeService(
            AnalysisAsyncTaskService analysisAsyncTaskService,
            AnalysisAsyncProcessor analysisAsyncProcessor,
            AnalysisService analysisService,
            UserService userService
    ) {
        super(analysisAsyncTaskService, analysisAsyncProcessor, analysisService, userService);
    }
}
