package com.jobdri.jobdri_api.domain.analysis.service.question;

import com.jobdri.jobdri_api.domain.analysis.dto.request.QuestionAnswerSaveRequest;
import com.jobdri.jobdri_api.domain.analysis.dto.request.QuestionCandidateCreateRequest;
import com.jobdri.jobdri_api.domain.analysis.dto.request.QuestionSelectionSaveRequest;
import com.jobdri.jobdri_api.domain.analysis.dto.response.QuestionAnswerResponse;
import com.jobdri.jobdri_api.domain.analysis.dto.response.QuestionCandidateResponse;
import com.jobdri.jobdri_api.domain.analysis.dto.response.QuestionSelectionResponse;
import com.jobdri.jobdri_api.domain.analysis.entity.CustomQuestionCandidate;
import com.jobdri.jobdri_api.domain.analysis.entity.Question;
import com.jobdri.jobdri_api.domain.analysis.repository.CustomQuestionCandidateRepository;
import com.jobdri.jobdri_api.domain.analysis.repository.QuestionAnalysisRepository;
import com.jobdri.jobdri_api.domain.analysis.repository.QuestionRepository;
import com.jobdri.jobdri_api.domain.audit.annotation.AuditLogEvent;
import com.jobdri.jobdri_api.domain.mockapply.entity.MockApply;
import com.jobdri.jobdri_api.domain.mockapply.entity.MockApplyStatus;
import com.jobdri.jobdri_api.domain.mockapply.repository.MockApplyRepository;
import com.jobdri.jobdri_api.domain.user.entity.User;
import com.jobdri.jobdri_api.global.apiPayload.code.GeneralErrorCode;
import com.jobdri.jobdri_api.global.apiPayload.exception.GeneralException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
// 문항 후보 추가, 선택 저장, 답변 저장, 삭제를 담당한다.
public class QuestionCommandService {
    private final QuestionDomainSupport questionDomainSupport;
    private final QuestionCandidateCatalogService questionCandidateCatalogService;
    private final MockApplyRepository mockApplyRepository;
    private final QuestionRepository questionRepository;
    private final QuestionAnalysisRepository questionAnalysisRepository;
    private final CustomQuestionCandidateRepository customQuestionCandidateRepository;

    public QuestionCommandService(
            QuestionDomainSupport questionDomainSupport,
            QuestionCandidateCatalogService questionCandidateCatalogService,
            MockApplyRepository mockApplyRepository,
            QuestionRepository questionRepository,
            QuestionAnalysisRepository questionAnalysisRepository,
            CustomQuestionCandidateRepository customQuestionCandidateRepository
    ) {
        this.questionDomainSupport = questionDomainSupport;
        this.questionCandidateCatalogService = questionCandidateCatalogService;
        this.mockApplyRepository = mockApplyRepository;
        this.questionRepository = questionRepository;
        this.questionAnalysisRepository = questionAnalysisRepository;
        this.customQuestionCandidateRepository = customQuestionCandidateRepository;
    }

    @Transactional
    @AuditLogEvent(action = "CUSTOM_QUESTION_CANDIDATE_ADD", targetType = "MOCK_APPLY", targetId = "#arg1")
    public QuestionCandidateResponse addCustomQuestionCandidate(
            User user,
            Long mockApplyId,
            QuestionCandidateCreateRequest request
    ) {
        MockApply mockApply = questionDomainSupport.getOwnedMockApply(user, mockApplyId);
        String content = request.content().trim();
        questionCandidateCatalogService.validateCustomCandidate(content);

        CustomQuestionCandidate candidate = findOrCreateCustomCandidate(mockApply, content, request.charLimit());
        boolean selected = questionRepository.existsByMockApplyIdAndContent(mockApply.getId(), candidate.getContent());

        return new QuestionCandidateResponse(
                candidate.getId(),
                candidate.getContent(),
                candidate.getLimit(),
                selected,
                true
        );
    }

    @Transactional
    @AuditLogEvent(action = "QUESTION_SELECTION_SAVE", targetType = "MOCK_APPLY", targetId = "#arg1")
    public QuestionSelectionResponse saveSelectedQuestions(
            User user,
            Long mockApplyId,
            QuestionSelectionSaveRequest request
    ) {
        MockApply mockApply = questionDomainSupport.getOwnedMockApply(user, mockApplyId);
        questionDomainSupport.validateSelectionCount(request.questions().size());

        List<Question> existingQuestions = questionRepository.findAllByMockApplyId(mockApply.getId());
        questionRepository.deleteAll(existingQuestions);

        List<Question> questions = request.questions().stream()
                .map(item -> Question.create(
                        mockApply,
                        item.content().trim(),
                        questionDomainSupport.resolveCharLimit(item.charLimit()),
                        ""
                ))
                .toList();
        List<Question> savedQuestions = questionRepository.saveAll(questions);
        mockApply.updateStatus(MockApplyStatus.ANSWER_WRITE);

        return new QuestionSelectionResponse(
                mockApply.getId(),
                mockApply.getStatus(),
                savedQuestions.stream().map(questionDomainSupport::toQuestionResponse).toList()
        );
    }

    @Transactional
    @AuditLogEvent(action = "QUESTION_ANSWER_SAVE", targetType = "MOCK_APPLY", targetId = "#arg1")
    public QuestionAnswerResponse saveAnswers(
            User user,
            Long mockApplyId,
            QuestionAnswerSaveRequest request
    ) {
        MockApply mockApply = questionDomainSupport.getOwnedMockApply(user, mockApplyId);
        questionDomainSupport.validateSelectionCount(request.questions().size());

        List<Question> existingQuestions = questionRepository.findAllByMockApplyIdOrderByIdAsc(mockApply.getId());
        Map<Long, Question> questionMap = existingQuestions.stream()
                .collect(Collectors.toMap(Question::getId, Function.identity()));
        Set<Long> requestedQuestionIds = new HashSet<>();
        List<Question> syncedQuestions = new ArrayList<>();

        for (QuestionAnswerSaveRequest.QuestionAnswerItem item : request.questions()) {
            if (item.questionId() == null) {
                syncedQuestions.add(Question.create(
                        mockApply,
                        item.content().trim(),
                        questionDomainSupport.resolveCharLimit(item.charLimit()),
                        questionDomainSupport.normalizeAnswer(item.answer())
                ));
                continue;
            }

            if (!requestedQuestionIds.add(item.questionId())) {
                throw new GeneralException(
                        GeneralErrorCode.INVALID_PARAMETER,
                        "중복된 문항 ID가 포함되어 있습니다. questionId=" + item.questionId()
                );
            }

            Question existingQuestion = questionMap.get(item.questionId());
            if (existingQuestion == null) {
                throw new GeneralException(
                        GeneralErrorCode.QUESTION_NOT_FOUND,
                        "해당 지원서의 문항을 찾을 수 없습니다. questionId=" + item.questionId()
                );
            }

            existingQuestion.updateContentLimitAndAnswer(
                    item.content().trim(),
                    questionDomainSupport.resolveCharLimit(item.charLimit()),
                    questionDomainSupport.normalizeAnswer(item.answer())
            );
            syncedQuestions.add(existingQuestion);
        }

        List<Question> deletedQuestions = existingQuestions.stream()
                .filter(question -> !requestedQuestionIds.contains(question.getId()))
                .toList();
        questionRepository.deleteAll(deletedQuestions);
        List<Question> savedQuestions = questionRepository.saveAll(syncedQuestions);
        mockApply.updateStatus(MockApplyStatus.ANSWER_WRITE);

        return new QuestionAnswerResponse(
                mockApply.getId(),
                mockApply.getStatus(),
                mockApplyRepository.calculateSequence(mockApply),
                savedQuestions.stream().map(questionDomainSupport::toQuestionResponse).toList()
        );
    }

    @Transactional
    @AuditLogEvent(action = "QUESTION_DELETE", targetType = "MOCK_APPLY", targetId = "#arg1")
    public QuestionSelectionResponse deleteQuestion(User user, Long mockApplyId, Long questionId) {
        MockApply mockApply = questionDomainSupport.getOwnedMockApply(user, mockApplyId);
        List<Question> existingQuestions = questionRepository.findAllByMockApplyIdOrderByIdAsc(mockApply.getId());
        if (existingQuestions.size() <= 1) {
            throw new GeneralException(GeneralErrorCode.INVALID_PARAMETER, "선택 문항은 1개 이상이어야 합니다.");
        }

        Question question = existingQuestions.stream()
                .filter(existingQuestion -> existingQuestion.getId().equals(questionId))
                .findFirst()
                .orElseThrow(() -> new GeneralException(
                        GeneralErrorCode.QUESTION_NOT_FOUND,
                        "해당 지원서의 문항을 찾을 수 없습니다. questionId=" + questionId
                ));

        questionAnalysisRepository.deleteAllByQuestionId(question.getId());
        questionRepository.delete(question);
        List<com.jobdri.jobdri_api.domain.analysis.dto.response.QuestionResponse> remainingQuestions = existingQuestions.stream()
                .filter(existingQuestion -> !existingQuestion.getId().equals(question.getId()))
                .map(questionDomainSupport::toQuestionResponse)
                .toList();

        return new QuestionSelectionResponse(mockApply.getId(), mockApply.getStatus(), remainingQuestions);
    }

    private CustomQuestionCandidate findOrCreateCustomCandidate(
            MockApply mockApply,
            String content,
            Integer charLimit
    ) {
        return customQuestionCandidateRepository
                .findByMockApplyIdAndContent(mockApply.getId(), content)
                .orElseGet(() -> saveCustomCandidate(mockApply, content, charLimit));
    }

    private CustomQuestionCandidate saveCustomCandidate(
            MockApply mockApply,
            String content,
            Integer charLimit
    ) {
        try {
            return customQuestionCandidateRepository.saveAndFlush(CustomQuestionCandidate.create(
                    mockApply,
                    content,
                    questionDomainSupport.resolveCharLimit(charLimit)
            ));
        } catch (DataIntegrityViolationException e) {
            return customQuestionCandidateRepository
                    .findByMockApplyIdAndContent(mockApply.getId(), content)
                    .orElseThrow(() -> e);
        }
    }
}
