package com.jobdri.jobdri_api.domain.analysis.service;

import com.jobdri.jobdri_api.domain.analysis.dto.request.QuestionCandidateCreateRequest;
import com.jobdri.jobdri_api.domain.analysis.dto.request.QuestionAnswerSaveRequest;
import com.jobdri.jobdri_api.domain.analysis.dto.request.QuestionSelectionSaveRequest;
import com.jobdri.jobdri_api.domain.analysis.dto.response.QuestionAnswerResponse;
import com.jobdri.jobdri_api.domain.analysis.dto.response.QuestionCandidateResponse;
import com.jobdri.jobdri_api.domain.analysis.dto.response.QuestionResponse;
import com.jobdri.jobdri_api.domain.analysis.dto.response.QuestionSelectionResponse;
import com.jobdri.jobdri_api.domain.analysis.entity.CustomQuestionCandidate;
import com.jobdri.jobdri_api.domain.analysis.entity.Question;
import com.jobdri.jobdri_api.domain.analysis.repository.CustomQuestionCandidateRepository;
import com.jobdri.jobdri_api.domain.analysis.repository.QuestionRepository;
import com.jobdri.jobdri_api.domain.mockapply.entity.MockApply;
import com.jobdri.jobdri_api.domain.mockapply.entity.MockApplyStatus;
import com.jobdri.jobdri_api.domain.mockapply.repository.MockApplyRepository;
import com.jobdri.jobdri_api.domain.user.entity.User;
import com.jobdri.jobdri_api.global.apiPayload.code.GeneralErrorCode;
import com.jobdri.jobdri_api.global.apiPayload.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class QuestionService {

    private static final int MIN_SELECTION_COUNT = 1;
    private static final int MAX_SELECTION_COUNT = 5;
    private static final int DEFAULT_CHAR_LIMIT = 1000;

    private static final List<QuestionCandidate> DEFAULT_CANDIDATES = List.of(
            new QuestionCandidate(1L, "지원 동기와 입사 후 목표를 작성해주세요.", 700),
            new QuestionCandidate(2L, "지원 직무와 관련된 경험 또는 역량을 구체적으로 작성해주세요.", 1000),
            new QuestionCandidate(3L, "문제를 해결했던 경험과 그 과정에서의 역할을 작성해주세요.", 1000),
            new QuestionCandidate(4L, "협업 과정에서 갈등을 해결했던 경험을 작성해주세요.", 800),
            new QuestionCandidate(5L, "가장 성취감을 느꼈던 프로젝트와 성과를 작성해주세요.", 1000)
    );

    private final MockApplyRepository mockApplyRepository;
    private final QuestionRepository questionRepository;
    private final CustomQuestionCandidateRepository customQuestionCandidateRepository;

    public List<QuestionCandidateResponse> getQuestionCandidates(User user, Long mockApplyId) {
        MockApply mockApply = getOwnedMockApply(user, mockApplyId);
        List<Question> selectedQuestions = questionRepository.findAllByMockApplyIdOrderByIdAsc(mockApply.getId());
        Set<String> selectedContents = selectedQuestions.stream()
                .map(Question::getContent)
                .collect(Collectors.toSet());

        List<QuestionCandidateResponse> candidates = new ArrayList<>(DEFAULT_CANDIDATES.stream()
                .map(candidate -> new QuestionCandidateResponse(
                        candidate.id(),
                        candidate.content(),
                        candidate.charLimit(),
                        selectedContents.contains(candidate.content()),
                        false
                ))
                .toList());

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

    @Transactional
    public QuestionCandidateResponse addCustomQuestionCandidate(
            User user,
            Long mockApplyId,
            QuestionCandidateCreateRequest request
    ) {
        MockApply mockApply = getOwnedMockApply(user, mockApplyId);
        String content = request.content().trim();
        validateCustomCandidate(content);

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
                    resolveCharLimit(charLimit)
            ));
        } catch (DataIntegrityViolationException e) {
            return customQuestionCandidateRepository
                    .findByMockApplyIdAndContent(mockApply.getId(), content)
                    .orElseThrow(() -> e);
        }
    }

    public QuestionSelectionResponse getSelectedQuestions(User user, Long mockApplyId) {
        MockApply mockApply = getOwnedMockApply(user, mockApplyId);
        List<QuestionResponse> questions = questionRepository.findAllByMockApplyIdOrderByIdAsc(mockApply.getId()).stream()
                .map(QuestionResponse::from)
                .toList();

        return new QuestionSelectionResponse(mockApply.getId(), mockApply.getStatus(), questions);
    }

    @Transactional
    public QuestionSelectionResponse saveSelectedQuestions(
            User user,
            Long mockApplyId,
            QuestionSelectionSaveRequest request
    ) {
        MockApply mockApply = getOwnedMockApply(user, mockApplyId);
        validateSelectionCount(request.questions().size());

        List<Question> existingQuestions = questionRepository.findAllByMockApplyId(mockApply.getId());
        questionRepository.deleteAll(existingQuestions);

        List<Question> questions = request.questions().stream()
                .map(item -> Question.create(
                        mockApply,
                        item.content().trim(),
                        resolveCharLimit(item.charLimit()),
                        ""
                ))
                .toList();
        List<Question> savedQuestions = questionRepository.saveAll(questions);
        mockApply.updateStatus(MockApplyStatus.ANSWER_WRITE);

        return new QuestionSelectionResponse(
                mockApply.getId(),
                mockApply.getStatus(),
                savedQuestions.stream().map(QuestionResponse::from).toList()
        );
    }

    @Transactional
    public QuestionAnswerResponse saveAnswers(
            User user,
            Long mockApplyId,
            QuestionAnswerSaveRequest request
    ) {
        MockApply mockApply = getOwnedMockApply(user, mockApplyId);
        List<Question> questions = questionRepository.findAllByMockApplyIdOrderByIdAsc(mockApply.getId());
        Map<Long, Question> questionMap = questions.stream()
                .collect(Collectors.toMap(Question::getId, Function.identity()));

        for (QuestionAnswerSaveRequest.AnswerItem item : request.answers()) {
            Question question = questionMap.get(item.questionId());
            if (question == null) {
                throw new GeneralException(
                        GeneralErrorCode.QUESTION_NOT_FOUND,
                        "해당 지원서의 문항을 찾을 수 없습니다. questionId=" + item.questionId()
                );
            }
            question.updateAnswer(normalizeAnswer(item.answer()));
        }

        return new QuestionAnswerResponse(
                mockApply.getId(),
                mockApply.getStatus(),
                questions.stream().map(QuestionResponse::from).toList()
        );
    }

    private MockApply getOwnedMockApply(User user, Long mockApplyId) {
        MockApply mockApply = mockApplyRepository.findById(mockApplyId)
                .orElseThrow(() -> new GeneralException(
                        GeneralErrorCode.MOCK_APPLY_NOT_FOUND,
                        "해당 모의 서류 지원을 찾을 수 없습니다. mockApplyId=" + mockApplyId
                ));

        if (!mockApply.getUser().getId().equals(user.getId())) {
            throw new GeneralException(GeneralErrorCode.FORBIDDEN, "해당 모의 서류 지원에 접근할 수 없습니다.");
        }

        return mockApply;
    }

    private void validateSelectionCount(int count) {
        if (count < MIN_SELECTION_COUNT) {
            throw new GeneralException(GeneralErrorCode.INVALID_PARAMETER, "선택 문항은 1개 이상이어야 합니다.");
        }
        if (count > MAX_SELECTION_COUNT) {
            throw new GeneralException(GeneralErrorCode.INVALID_PARAMETER, "문항은 최대 5개까지 선택할 수 있습니다.");
        }
    }

    private int resolveCharLimit(Integer charLimit) {
        if (charLimit == null) {
            return DEFAULT_CHAR_LIMIT;
        }
        return charLimit;
    }

    private void validateCustomCandidate(String content) {
        boolean existsInDefault = DEFAULT_CANDIDATES.stream()
                .anyMatch(candidate -> candidate.content().equals(content));
        if (existsInDefault) {
            throw new GeneralException(GeneralErrorCode.INVALID_PARAMETER, "이미 기본 후보에 존재하는 문항입니다.");
        }
    }

    private String normalizeAnswer(String answer) {
        if (StringUtils.hasText(answer)) {
            return answer;
        }
        return "";
    }

    private record QuestionCandidate(Long id, String content, int charLimit) {
    }
}
