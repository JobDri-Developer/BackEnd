package com.jobdri.jobdri_api.domain.analysis.service.question;

import com.jobdri.jobdri_api.domain.analysis.dto.response.QuestionCandidateResponse;
import com.jobdri.jobdri_api.domain.analysis.dto.response.QuestionResponse;
import com.jobdri.jobdri_api.domain.analysis.dto.response.QuestionSelectionResponse;
import com.jobdri.jobdri_api.domain.analysis.entity.Question;
import com.jobdri.jobdri_api.domain.analysis.repository.CustomQuestionCandidateRepository;
import com.jobdri.jobdri_api.domain.analysis.repository.QuestionRepository;
import com.jobdri.jobdri_api.domain.mockapply.entity.MockApply;
import com.jobdri.jobdri_api.domain.user.entity.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
// 문항 후보/선택 목록 조회를 담당한다.
public class QuestionQueryService {
    private final QuestionDomainSupport questionDomainSupport;
    private final QuestionCandidateCatalogService questionCandidateCatalogService;
    private final QuestionRepository questionRepository;
    private final CustomQuestionCandidateRepository customQuestionCandidateRepository;

    public QuestionQueryService(
            QuestionDomainSupport questionDomainSupport,
            QuestionCandidateCatalogService questionCandidateCatalogService,
            QuestionRepository questionRepository,
            CustomQuestionCandidateRepository customQuestionCandidateRepository
    ) {
        this.questionDomainSupport = questionDomainSupport;
        this.questionCandidateCatalogService = questionCandidateCatalogService;
        this.questionRepository = questionRepository;
        this.customQuestionCandidateRepository = customQuestionCandidateRepository;
    }

    public List<QuestionCandidateResponse> getQuestionCandidates(User user, Long mockApplyId) {
        MockApply mockApply = questionDomainSupport.getOwnedMockApply(user, mockApplyId);
        List<Question> selectedQuestions = questionRepository.findAllByMockApplyIdOrderByIdAsc(mockApply.getId());
        Set<String> selectedContents = selectedQuestions.stream()
                .map(Question::getContent)
                .collect(Collectors.toSet());

        List<QuestionCandidateResponse> candidates = new ArrayList<>(
                questionCandidateCatalogService.getDefaultCandidateResponses(selectedContents)
        );

        customQuestionCandidateRepository.findAllByMockApplyIdOrderByIdAsc(mockApply.getId()).stream()
                .map(candidate -> new QuestionCandidateResponse(
                        candidate.getId(),
                        candidate.getContent(),
                        candidate.getLimit(),
                        selectedContents.contains(candidate.getContent()),
                        true
                ))
                .forEach(candidates::add);

        return candidates;
    }

    public QuestionSelectionResponse getSelectedQuestions(User user, Long mockApplyId) {
        MockApply mockApply = questionDomainSupport.getOwnedMockApply(user, mockApplyId);
        List<QuestionResponse> questions = questionRepository.findAllByMockApplyIdOrderByIdAsc(mockApply.getId()).stream()
                .map(questionDomainSupport::toQuestionResponse)
                .toList();

        return new QuestionSelectionResponse(mockApply.getId(), mockApply.getStatus(), questions);
    }
}
