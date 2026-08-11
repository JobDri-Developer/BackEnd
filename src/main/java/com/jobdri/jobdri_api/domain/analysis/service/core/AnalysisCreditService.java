package com.jobdri.jobdri_api.domain.analysis.service.core;

import com.jobdri.jobdri_api.domain.payment.service.CreditService;
import com.jobdri.jobdri_api.domain.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AnalysisCreditService {

    private final CreditService creditService;

    @Transactional(readOnly = true)
    public String createSyncReferenceId(Long mockApplyId, String inputFingerprint) {
        return "mockApplyId=" + mockApplyId + ":fingerprint=" + inputFingerprint;
    }

    @Transactional(readOnly = true)
    public String createAsyncReferenceId(String taskId) {
        return "analysisTaskId=" + taskId;
    }

    @Transactional
    public void deduct(User user, String referenceId) {
        creditService.use(user, 1, "자소서 분석 크레딧 차감", referenceId);
    }

    @Transactional
    public void refund(User user, String referenceId) {
        creditService.refund(user, 1, "자소서 분석 크레딧 환불", referenceId);
    }
}
