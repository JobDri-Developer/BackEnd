package com.jobdri.jobdri_api.domain.analysis.service.question;

import com.jobdri.jobdri_api.domain.analysis.dto.request.QuestionAnswerSaveRequest;
import com.jobdri.jobdri_api.domain.analysis.dto.request.QuestionCandidateCreateRequest;
import com.jobdri.jobdri_api.domain.analysis.dto.request.QuestionSelectionSaveRequest;
import com.jobdri.jobdri_api.domain.analysis.dto.response.QuestionAnswerResponse;
import com.jobdri.jobdri_api.domain.analysis.dto.response.QuestionCandidateResponse;
import com.jobdri.jobdri_api.domain.analysis.dto.response.QuestionSelectionResponse;
import com.jobdri.jobdri_api.domain.user.entity.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@Transactional(readOnly = true)
// 기존 QuestionService 진입점을 유지하는 호환용 파사드다.
public class QuestionService {
    private final QuestionQueryService questionQueryService;
    private final QuestionCommandService questionCommandService;

    public QuestionService(
            QuestionQueryService questionQueryService,
            QuestionCommandService questionCommandService
    ) {
        this.questionQueryService = questionQueryService;
        this.questionCommandService = questionCommandService;
    }

    public List<QuestionCandidateResponse> getQuestionCandidates(User user, Long mockApplyId) {
        return questionQueryService.getQuestionCandidates(user, mockApplyId);
    }

    public QuestionCandidateResponse addCustomQuestionCandidate(
            User user,
            Long mockApplyId,
            QuestionCandidateCreateRequest request
    ) {
        return questionCommandService.addCustomQuestionCandidate(user, mockApplyId, request);
    }

    public QuestionSelectionResponse getSelectedQuestions(User user, Long mockApplyId) {
        return questionQueryService.getSelectedQuestions(user, mockApplyId);
    }

    public QuestionSelectionResponse saveSelectedQuestions(
            User user,
            Long mockApplyId,
            QuestionSelectionSaveRequest request
    ) {
        return questionCommandService.saveSelectedQuestions(user, mockApplyId, request);
    }

    public QuestionAnswerResponse saveAnswers(
            User user,
            Long mockApplyId,
            QuestionAnswerSaveRequest request
    ) {
        return questionCommandService.saveAnswers(user, mockApplyId, request);
    }

    public QuestionSelectionResponse deleteQuestion(User user, Long mockApplyId, Long questionId) {
        return questionCommandService.deleteQuestion(user, mockApplyId, questionId);
    }
}
