package com.jobdri.jobdri_api.domain.analysis.service;

import com.jobdri.jobdri_api.domain.analysis.dto.llm.AnalysisLlmResponse;
import com.jobdri.jobdri_api.domain.analysis.entity.Question;
import com.jobdri.jobdri_api.domain.corpus.service.CorpusRetrievalService;
import com.jobdri.jobdri_api.domain.corpus.service.CorpusRetrievalService.RetrievalContext;
import com.jobdri.jobdri_api.domain.corpus.service.CorpusRetrievalService.RetrievedJobPostingReference;
import com.jobdri.jobdri_api.domain.corpus.service.CorpusRetrievalService.RetrievedQuestionReference;
import com.jobdri.jobdri_api.domain.jobposting.entity.JobPosting;
import com.jobdri.jobdri_api.global.apiPayload.code.GeneralErrorCode;
import com.jobdri.jobdri_api.global.apiPayload.exception.GeneralException;
import com.openai.client.OpenAIClient;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.StructuredResponse;
import com.openai.models.responses.StructuredResponseOutputMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class AnalysisAiClient {

    private final OpenAIClient openAIClient;
    private final CorpusRetrievalService corpusRetrievalService;

    @Value("${openai.model.cover-letter-analysis:gpt-4o-mini}")
    private String analysisModel;

    public AnalysisLlmResponse analyze(JobPosting jobPosting, List<Question> questions) {
        RetrievalContext referenceContext = corpusRetrievalService.retrieveForAnalysis(jobPosting, questions);
        var params = ResponseCreateParams.builder()
                .model(analysisModel)
                .input(buildPrompt(jobPosting, questions, referenceContext))
                .temperature(0.2)
                .text(AnalysisLlmResponse.class)
                .build();

        try {
            StructuredResponse<AnalysisLlmResponse> response = openAIClient.responses().create(params);
            return extractStructuredContent(response);
        } catch (GeneralException e) {
            throw e;
        } catch (Exception e) {
            log.error("자소서 분석 OpenAI API 호출 오류: {}", e.getMessage(), e);
            throw new GeneralException(
                    GeneralErrorCode.SERVICE_UNAVAILABLE,
                    "자소서 분석 AI 호출에 실패했습니다."
            );
        }
    }

    private String buildPrompt(
            JobPosting jobPosting,
            List<Question> questions,
            RetrievalContext referenceContext
    ) {
        String questionText = questions.stream()
                .map(question -> """
                        - questionId: %d
                          question: %s
                          answer: %s
                        """.formatted(
                        question.getId(),
                        defaultString(question.getContent()),
                        defaultString(question.getAnswer())
                ))
                .reduce("", (left, right) -> left + "\n" + right);

        String similarJobPostingText = formatJobPostingReferences(referenceContext.jobPostingReferences());
        String similarQuestionText = formatQuestionReferences(referenceContext.questionReferences());

        return """
                [시스템 지시]
                너는 한국 채용 담당자이자 자기소개서 평가 전문가다.
                반드시 JSON만 출력한다.
                자소서 원문에 없는 sentence를 만들지 않는다.
                sentence는 반드시 해당 question의 answer에 포함된 정확한 부분 문자열이어야 한다.

                [출력 형식]
                {
                  "score": 64,
                  "jobFit": 70,
                  "impact": 55,
                  "completeness": 67,
                  "feedback": "한 줄 피드백",
                  "questionAnalyses": [
                    {
                      "questionId": 1,
                      "sentence": "자소서 답변 안에 실제 존재하는 정확한 부분 문자열",
                      "status": "mentioned",
                      "reason": "문제 이유",
                      "improvement": "사용자가 그대로 붙여 넣을 수 있는 완성된 개선 예시 문장"
                    }
                  ]
                }

                [평가 절차]
                1. JD의 주요 업무, 자격 요건, 우대 사항을 읽고 핵심 역량을 정리한다.
                2. 각 문항 답변이 JD와 얼마나 연결되는지 평가한다.
                3. 주장, 경험, 성과가 구체적 근거로 입증되는지 평가한다.
                4. 질문에 맞게 답했는지, 문장 흐름과 완성도가 충분한지 평가한다.
                5. 보완이 필요한 원문 문장을 최대 2~3개만 추출한다.

                [점수 기준]
                - 85~100: 매우 우수
                - 70~84: 양호
                - 55~69: 개선 필요
                - 40~54: 대폭 수정 필요
                - 40 미만: 직무/JD와 거의 무관

                [세부 기준]
                - jobFit: JD와 직무 역량 매칭
                - impact: 성과 구체성, 수치, 결과
                - completeness: 문장 완성도, 논리 흐름, 질문 적합성

                [상태 라벨 참고]
                - proven: 구체적 경험/수치로 충분히 입증됨
                - mentioned: 관련 내용을 언급은 했지만 구체 근거가 부족함
                - missing: 자소서에서 아예 다루지 않음
                - fabricated: 주장은 하지만 신뢰할 수 있는 근거가 부족함

                [약점 유형 참고]
                unsupported_claim, vague_evidence, exaggeration, missing_outcome

                [채용 공고]
                회사명: %s
                직무명: %s
                주요 업무:
                %s

                자격 요건:
                %s

                우대 사항:
                %s

                [유사 JD 검색 결과]
                %s

                [유사 자소서 문항 검색 결과]
                %s

                [자소서 문항과 답변]
                %s

                [중요 규칙]
                - JSON 외 텍스트, 마크다운, 코드블럭을 출력하지 않는다.
                - questionAnalyses의 questionId는 입력된 questionId 중 하나만 사용한다.
                - questionAnalyses의 status는 proven, mentioned, missing, fabricated 중 하나만 사용한다.
                - sentence는 answer에 포함된 정확한 substring만 사용한다.
                - improvement는 첨삭 조언이 아니라 sentence를 대체할 수 있는 완성된 예시 문장이어야 한다.
                - improvement에는 "하세요.", "해주세요.", "해야 합니다.", "필요합니다."로 끝나는 지시문을 쓰지 않는다.
                - improvement에는 "추가하세요.", "보완하세요.", "수정해주세요.", "명확히 해야 합니다." 같은 첨삭 조언 표현을 쓰지 않는다.
                - improvement는 반드시 한국어 평서문으로 작성하고, 가능하면 수치/성과/행동을 포함한다.
                - 좋은 improvement 예: "저는 쿼리 실행 계획을 분석해 누락된 인덱스를 추가했고, 평균 응답 시간을 1.8초에서 0.6초로 단축했습니다."
                - 나쁜 improvement 예: "성과 수치를 추가하여 문제 해결의 효과를 명확히 하세요."
                - start/end index는 출력하지 않는다. 서버가 Java에서 계산한다.
                - 원문 매칭이 불확실하면 questionAnalyses에 포함하지 않는다.
                """.formatted(
                defaultString(jobPosting.getCompany().getName()),
                defaultString(jobPosting.getDetailClassification().getDetailName()),
                defaultString(jobPosting.getTask()),
                defaultString(jobPosting.getRequirement()),
                defaultString(jobPosting.getPreferred()),
                similarJobPostingText,
                similarQuestionText,
                questionText
        );
    }

    private String formatJobPostingReferences(List<RetrievedJobPostingReference> references) {
        if (references == null || references.isEmpty()) {
            return "없음";
        }
        return references.stream()
                .map(reference -> """
                        - 회사명: %s
                          직무명: %s
                          주요 업무: %s
                          자격 요건: %s
                          우대 사항: %s
                          거리: %.4f
                        """.formatted(
                        defaultString(reference.companyName()),
                        defaultString(reference.roleName()),
                        defaultString(reference.responsibilities()),
                        defaultString(reference.requirements()),
                        defaultString(reference.preferred()),
                        reference.distance()
                ))
                .reduce("", (left, right) -> left + "\n" + right)
                .trim();
    }

    private String formatQuestionReferences(List<RetrievedQuestionReference> references) {
        if (references == null || references.isEmpty()) {
            return "없음";
        }
        return references.stream()
                .map(reference -> """
                        - 회사명: %s
                          직무명: %s
                          문항 유형: %s
                          글자 수 제한: %s
                          문항: %s
                          거리: %.4f
                        """.formatted(
                        defaultString(reference.companyName()),
                        defaultString(reference.roleName()),
                        defaultString(reference.questionType()),
                        reference.charLimit() == null ? "" : reference.charLimit(),
                        defaultString(reference.questionText()),
                        reference.distance()
                ))
                .reduce("", (left, right) -> left + "\n" + right)
                .trim();
    }

    private AnalysisLlmResponse extractStructuredContent(StructuredResponse<AnalysisLlmResponse> response) {
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

    private String defaultString(String value) {
        return value == null ? "" : value;
    }
}
