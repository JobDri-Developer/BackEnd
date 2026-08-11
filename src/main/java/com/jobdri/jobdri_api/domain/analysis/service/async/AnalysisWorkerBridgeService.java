package com.jobdri.jobdri_api.domain.analysis.service.async;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobdri.jobdri_api.domain.analysis.infrastructure.async.AnalysisAsyncWorkerBridge;
import com.jobdri.jobdri_api.domain.analysis.repository.AnalysisAsyncTaskRepository;
import com.jobdri.jobdri_api.domain.analysis.service.core.AnalysisCreditService;
import com.jobdri.jobdri_api.domain.analysis.service.core.AnalysisInputFingerprintProvider;
import com.jobdri.jobdri_api.domain.analysis.service.core.AnalysisService;
import com.jobdri.jobdri_api.domain.user.service.UserService;
import com.jobdri.jobdri_api.domain.workerresult.service.WorkerTaskResultService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
// 외부 분석 워커와 내부 분석 도메인 상태를 연결해 주는 브리지 서비스다.
public class AnalysisWorkerBridgeService extends AnalysisAsyncWorkerBridge {

    public AnalysisWorkerBridgeService(
            AnalysisAsyncTaskService analysisAsyncTaskService,
            AnalysisAsyncTaskRepository analysisAsyncTaskRepository,
            AnalysisService analysisService,
            AnalysisCreditService analysisCreditService,
            UserService userService,
            WorkerTaskResultService workerTaskResultService,
            AnalysisInputFingerprintProvider analysisInputFingerprintProvider,
            ObjectMapper objectMapper
    ) {
        super(
                analysisAsyncTaskService,
                analysisAsyncTaskRepository,
                analysisService,
                analysisCreditService,
                userService,
                workerTaskResultService,
                analysisInputFingerprintProvider,
                objectMapper
        );
    }
}
