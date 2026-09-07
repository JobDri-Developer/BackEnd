package com.jobdri.jobdri_api.domain.analysis.service.core;

import com.jobdri.jobdri_api.domain.analysis.dto.response.AnalysisQuestionResponse;
import com.jobdri.jobdri_api.domain.analysis.dto.response.AnalysisResponse;
import com.jobdri.jobdri_api.domain.analysis.dto.response.QuestionAnalysisResponse;
import com.jobdri.jobdri_api.domain.analysis.entity.Analysis;
import com.jobdri.jobdri_api.domain.analysis.entity.Question;
import com.jobdri.jobdri_api.domain.analysis.entity.QuestionAnalysis;
import com.jobdri.jobdri_api.domain.analysis.service.sanitization.AnalysisResultSanitizationService;
import com.jobdri.jobdri_api.domain.analysis.type.QuestionAnalysisStatus;
import com.jobdri.jobdri_api.domain.mockapply.entity.MockApply;
import com.jobdri.jobdri_api.domain.mockapply.repository.MockApplyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AnalysisResponseAssembler {
    private final MockApplyRepository mockApplyRepository;

    AnalysisResponse toResponse(
            MockApply mockApply,
            Analysis analysis,
            List<Question> questions,
            List<QuestionAnalysis> questionAnalyses,
            AnalysisResultSanitizationService.AnalysisResultPayload resultPayload
    ) {
        Map<Long, Question> questionById = questions.stream()
                .collect(Collectors.toMap(Question::getId, Function.identity()));
        Map<Long, List<QuestionAnalysisResponse>> analysesByQuestionId = questionAnalyses.stream()
                .filter(questionAnalysis -> isValidQuestionAnalysisForResponse(
                        questionAnalysis,
                        questionById.get(questionAnalysis.getQuestion().getId())
                ))
                .collect(Collectors.groupingBy(
                        questionAnalysis -> questionAnalysis.getQuestion().getId(),
                        Collectors.mapping(QuestionAnalysisResponse::from, Collectors.toList())
                ));

        List<AnalysisQuestionResponse> questionResponses = questions.stream()
                .sorted(Comparator.comparing(Question::getId))
                .map(question -> AnalysisQuestionResponse.of(
                        question,
                        analysesByQuestionId.getOrDefault(question.getId(), List.of())
                ))
                .toList();

        return AnalysisResponse.of(
                analysis,
                mockApply.getStatus(),
                mockApplyRepository.calculateSequence(mockApply),
                resultPayload.keyStrengths(),
                resultPayload.keyWeaknesses(),
                resultPayload.missingKeywords(),
                questionResponses
        );
    }

    private boolean isValidQuestionAnalysisForResponse(QuestionAnalysis questionAnalysis, Question question) {
        if (questionAnalysis == null || question == null) {
            return false;
        }
        if (questionAnalysis.getStatus() == QuestionAnalysisStatus.MISSING) {
            return false;
        }
        String answer = question.getAnswer();
        String sentence = questionAnalysis.getSentence();
        int start = questionAnalysis.getStart();
        int end = questionAnalysis.getEnd();
        if (!StringUtils.hasText(answer) || !StringUtils.hasText(sentence)) {
            return false;
        }
        if (start < 0 || end <= start || end > answer.length()) {
            return false;
        }
        return answer.substring(start, end).equals(sentence);
    }
}
