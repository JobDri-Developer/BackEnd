package com.jobdri.jobdri_api.domain.analysis.service.ai;

import com.jobdri.jobdri_api.domain.analysis.dto.external.llm.AnalysisLlmResponse;
import com.jobdri.jobdri_api.domain.analysis.entity.QuestionAnalysisStatus;
import com.jobdri.jobdri_api.global.apiPayload.code.GeneralErrorCode;
import com.jobdri.jobdri_api.global.apiPayload.exception.GeneralException;
import com.openai.models.responses.StructuredResponse;
import com.openai.models.responses.StructuredResponseOutputMessage;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class AnalysisResponseParser {

    public <T> T extractStructuredContent(StructuredResponse<T> response) {
        return response.output().stream()
                .filter(item -> item.message().isPresent())
                .flatMap(item -> item.asMessage().content().stream())
                .filter(content -> content.outputText().isPresent())
                .map(StructuredResponseOutputMessage.Content::asOutputText)
                .findFirst()
                .orElseThrow(() -> new GeneralException(
                        GeneralErrorCode.INTERNAL_SERVER_ERROR,
                        "AI 응답에서 자소서 분석 결과를 찾을 수 없습니다."
                ));
    }

    public AnalysisLlmResponse sanitizeSinglePassSubheadings(
            AnalysisPromptInput promptInput,
            AnalysisLlmResponse response
    ) {
        if (response == null || promptInput == null || promptInput.questions() == null) {
            return response;
        }
        Map<Long, String> answerByQuestionId = promptInput.questions().stream()
                .collect(Collectors.toMap(
                        AnalysisPromptInput.QuestionAnswer::questionId,
                        AnalysisPromptInput.QuestionAnswer::answer
                ));
        List<String> answers = new ArrayList<>(answerByQuestionId.values());
        List<AnalysisLlmResponse.HighlightItem> keyStrengths = response.keyStrengths() == null
                ? null
                : response.keyStrengths().stream()
                .filter(item -> item != null && !isBracketedSubheadingInAnyAnswer(answers, item.quote()))
                .toList();
        List<AnalysisLlmResponse.HighlightItem> keyWeaknesses = response.keyWeaknesses() == null
                ? null
                : response.keyWeaknesses().stream()
                .filter(item -> item != null && !isBracketedSubheadingInAnyAnswer(answers, item.quote()))
                .toList();
        List<AnalysisLlmResponse.QuestionAnalysisItem> questionAnalyses = response.questionAnalyses() == null
                ? null
                : response.questionAnalyses().stream()
                .filter(item -> item != null
                        && !isBracketedSubheading(answerByQuestionId.get(item.questionId()), item.sentence()))
                .toList();
        return new AnalysisLlmResponse(
                response.jobFit(),
                response.impact(),
                response.completeness(),
                response.feedback(),
                keyStrengths,
                keyWeaknesses,
                response.missingKeywords(),
                questionAnalyses
        );
    }

    public QuestionAnalysisStatus parseQuestionAnalysisStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return null;
        }
        try {
            return QuestionAnalysisStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private boolean isBracketedSubheadingInAnyAnswer(List<String> answers, String candidateText) {
        return answers.stream().anyMatch(answer -> isBracketedSubheading(answer, candidateText));
    }

    private boolean isBracketedSubheading(String answer, String candidateText) {
        if (!StringUtils.hasText(answer) || !StringUtils.hasText(candidateText)) {
            return false;
        }
        String trimmedCandidate = candidateText.trim();
        if (trimmedCandidate.indexOf('\n') >= 0
                || trimmedCandidate.indexOf('\r') >= 0
                || !trimmedCandidate.startsWith("[")
                || !trimmedCandidate.endsWith("]")
                || trimmedCandidate.length() <= 2) {
            return false;
        }
        return answer.lines().map(String::trim).anyMatch(trimmedCandidate::equals);
    }
}
