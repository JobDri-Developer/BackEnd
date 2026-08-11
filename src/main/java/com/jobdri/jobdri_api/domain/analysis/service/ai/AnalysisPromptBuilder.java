package com.jobdri.jobdri_api.domain.analysis.service.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobdri.jobdri_api.domain.analysis.dto.criteria.JobCategoryEvaluationCriteria;
import com.jobdri.jobdri_api.domain.analysis.dto.llm.AnalysisCandidateResponse;
import com.jobdri.jobdri_api.domain.analysis.policy.AnalysisPromptPolicy;
import com.jobdri.jobdri_api.domain.analysis.service.ai.fewshot.FewShotProperties;
import com.jobdri.jobdri_api.domain.analysis.service.ai.fewshot.FewShotSearchQuery;
import com.jobdri.jobdri_api.domain.analysis.service.ai.fewshot.FewShotSearchService;
import com.jobdri.jobdri_api.domain.analysis.service.ai.fewshot.SelectedFewShotCase;
import com.jobdri.jobdri_api.domain.corpus.service.CorpusRetrievalService.RetrievalContext;
import com.jobdri.jobdri_api.domain.corpus.service.CorpusRetrievalService.RetrievedJobPostingReference;
import com.jobdri.jobdri_api.domain.corpus.service.CorpusRetrievalService.RetrievedQuestionReference;
import com.jobdri.jobdri_api.domain.jobposting.entity.JobPosting;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
@Slf4j
@RequiredArgsConstructor
public class AnalysisPromptBuilder {
    private static final int MAX_REFERENCE_SECTION_LENGTH = 3000;
    private static final int MAX_REFERENCE_FIELD_LENGTH = 300;
    private static final int MAX_CRITERIA_ITEMS = 5;

    private final FewShotPromptProvider fewShotPromptProvider;
    private final FewShotSearchService fewShotSearchService;
    private final FewShotProperties fewShotProperties;
    private final ObjectMapper objectMapper;

    String buildPrompt(
            JobPosting jobPosting,
            List<com.jobdri.jobdri_api.domain.analysis.entity.Question> questions,
            RetrievalContext referenceContext,
            JobCategoryEvaluationCriteria jobCategoryEvaluationCriteria
    ) {
        return buildPrompt(
                AnalysisPromptInput.from(jobPosting, questions),
                referenceContext,
                jobCategoryEvaluationCriteria
        );
    }

    String buildPrompt(
            AnalysisPromptInput promptInput,
            RetrievalContext referenceContext,
            JobCategoryEvaluationCriteria jobCategoryEvaluationCriteria
    ) {
        return buildSinglePassPrompt(promptInput, referenceContext, jobCategoryEvaluationCriteria);
    }

    String buildCandidatePrompt(
            AnalysisPromptInput promptInput,
            RetrievalContext referenceContext,
            JobCategoryEvaluationCriteria jobCategoryEvaluationCriteria
    ) {
        String questionText = formatQuestions(promptInput);
        String jobCategoryCriteriaSection = formatJobCategoryEvaluationCriteriaSection(jobCategoryEvaluationCriteria);
        return """
                [시스템 지시]
                너는 자기소개서 분석을 위한 1차 후보 판정기다.
                반드시 Structured Output 스키마에 맞는 JSON object만 반환한다.
                내부 판단 과정이나 chain-of-thought를 출력하지 않는다.
                reasoning, analysis 같은 별도 필드를 추가하지 않는다.

                [1차 출력 필드]
                - strengthCandidates: 충분히 구체적이고 mainTask 또는 qualification과 직접 연결된 최종 PROVEN 문장 후보. 없으면 [].
                - analysisCandidates: 실제 첨삭이 필요한 명확한 문장 후보. status는 MENTIONED 또는 FABRICATED만 사용. 문항별 최대 3개.
                - missingKeywordCandidates: sentence가 없는 누락 역량 후보. 없으면 [].
                - 점수 필드, feedback, improvement, keyWeaknesses는 1차 출력에 존재하지 않는다.
                - analysisCandidates.candidateId는 각 후보마다 고유한 문자열로 반드시 채운다.
                - analysisCandidates.contextBefore/contextAfter는 sentence 앞뒤 문맥을 짧게 넣는다. 서버가 원문 기준으로 다시 검증한다.

                [허용값]
                - sentenceType: EXPERIENCE, PLAN, MOTIVATION, COMPETENCY
                - relatedSource: MAIN_TASK, QUALIFICATION, PREFERENCE, NONE
                - status: MENTIONED, FABRICATED
                - issueType: LACK_OF_ACTION, LACK_OF_METHOD, LACK_OF_RESULT, LACK_OF_ROLE, LACK_OF_JOB_CONNECTION, ABSTRACT_PLAN, GENERIC_MOTIVATION, UNSUPPORTED_CLAIM, DIRECT_CONTRADICTION

                [1차 판정 절차]
                1. 답변을 문장 단위로 확인한다.
                2. 각 문장의 유형을 분류한다.
                3. mainTask와 qualification의 직접 근거를 찾는다.
                4. preference만 근거인 후보는 제외한다.
                5. 문장이 이미 충분히 구체적인지 판단한다.
                6. 충분한 문장은 strengthCandidates로 분류한다.
                7. 보완이 필요한 문장만 analysisCandidates로 분류한다.
                8. 실제 충돌이 있는 경우에만 FABRICATED를 사용한다.
                9. 누락 요구사항은 sentence가 아니라 missingKeywordCandidates로 분리한다.
                10. 대표 1개만 기계적으로 고르지 말고, 독립적인 평가 근거가 있으면 문항별 1~3개를 반환한다.

                [1차 후보 규칙]
                - 후보 수보다 정확도를 우선한다.
                - 후보 개수 제한은 전체 답변 합계가 아니라 questionId별로 독립 적용한다.
                - 애매한 문장은 후보로 만들지 않는다.
                - 각 문장을 앞뒤 문맥과 함께 판단한다.
                - 문장 자체 또는 주변 문맥에 충분한 근거가 있으면 제외한다.
                - 대부분의 답변에서 후보가 0개 또는 1개일 수 있다.
                - 2개는 서로 다른 명확한 문제가 있을 때만 허용한다.
                - 3개는 독립적이고 중대한 문제가 정확히 3개 있을 때만 허용한다.
                - 개수를 채우기 위해 후보를 생성하지 않는다.
                - 대표 문장 하나만 기계적으로 고르지도 않는다.
                - questionId는 입력된 questionId 중 하나만 사용한다.
                - sentence와 quote는 해당 answer에 실제 포함된 정확한 부분 문자열만 사용한다.
                - preference-only 후보는 strengthCandidates, analysisCandidates, missingKeywordCandidates에서 제외한다.
                - 충분히 좋은 문장은 analysisCandidates에 넣지 않는다.
                - strengthCandidates로 검증된 문장은 서버가 status=PROVEN, improvement=null인 questionAnalyses로 변환한다.
                - 포부/계획 문장에는 과거 성과 수치나 Before-After를 요구하지 않는다.
                - 지원동기는 수치 부족으로 분류하지 않는다.
                - MISSING은 analysisCandidates에 넣지 않고 missingKeywordCandidates로만 분리한다.
                - FABRICATED는 JD 또는 답변 내부의 명시적 사실과 직접 충돌하거나, 지원자가 실제로 하지 않았다고 밝힌 경험을 한 것처럼 주장한 경우에만 사용한다.
                - status 다양성이나 개수를 채우기 위해 후보를 만들지 않는다.
                - improvement를 생성하지 않는다.

                [소제목 처리 규칙]
                - 답변에서 한 줄 전체가 대괄호로 감싸진 형식(예: [문제를 기회로 바꾼 경험])은 본문 문장이 아니라 소제목이다.
                - 소제목은 sentenceType을 분류하지 않고 strengthCandidates 또는 analysisCandidates에 넣지 않는다.
                - 소제목에 행동, 역할, 방법, 결과가 없다는 이유로 LACK_OF_ACTION, LACK_OF_METHOD, LACK_OF_RESULT, LACK_OF_ROLE 후보를 만들지 않는다.
                - 소제목 자체의 구체성을 평가하거나 점수 근거로 사용하지 않는다.
                - 소제목은 바로 뒤 문단의 주제와 흐름을 이해하는 보조 문맥으로만 사용하고, 후보 판정은 본문 문장을 기준으로 한다.
                - 소제목이 문단을 요약하는지는 문단 이해에만 참고하며, 소제목 문구 자체를 첨삭 후보로 만들지 않는다.

                [strengthCandidates 생성 규칙]
                - 반드시 수치 성과가 있어야만 strengthCandidates가 되는 것은 아니다.
                - JD와 직접 연결된다면 구체적인 도구·기술 사용 경험은 strengthCandidates가 될 수 있다.
                - 실제 업무 또는 프로젝트 수행 경험, 업무 전 과정을 수행한 경험은 strengthCandidates가 될 수 있다.
                - 문제를 발견하고 해결한 과정, 수치로 표현되지 않더라도 확인 가능한 결과는 strengthCandidates가 될 수 있다.
                - 자격 취득 자체가 아니라 직무와 연결되는 실습·적용 경험은 strengthCandidates가 될 수 있다.
                - JD 우대사항과 직접 연결되는 경험도 strengthCandidates가 될 수 있으나 preference-only 강점을 과대평가하지 않는다.
                - 추상적인 포부, 근거 없는 자기평가, 단순 성격 표현, JD와 무관한 경험, 답변에 없는 추론 강점, 동일 근거의 중복 강점은 생성하지 않는다.

                [missingKeywordCandidates 생성 규칙]
                - missingKeywordCandidates는 recall보다 precision을 우선한다. 확실한 누락 역량이 없으면 []를 반환한다.
                - 개수를 채우기 위해 missingKeywordCandidates를 생성하지 않는다. 애매하면 생성하지 않는다.
                - keyword는 main_tasks 또는 qualifications에 명시적으로 존재하거나 직접적으로 동일한 의미인 항목만 사용한다.
                - 유사 JD, preferences, 유사 자소서 문항, 직무 일반 지식, 모델의 추론에서 keyword를 만들지 않는다.
                - JD에 없는 개념으로 확장하거나 일반화하지 않는다.
                - "상세페이지 제작"을 "UX/UI 인터페이스 설계"로 바꾸지 않는다.
                - "브랜드 운영"을 "브랜드 BI/CI 수립 경험"으로 바꾸지 않는다.
                - "사업 및 행정지원"을 "복지 프로그램 기획"으로 바꾸지 않는다.
                - keyword는 가능하면 JD 원문 표현을 유지한다. 추상화하거나 더 넓은 상위 개념으로 바꾸지 않는다.
                - relatedRequirement는 main_tasks 또는 qualifications의 실제 문장을 그대로 복사하거나 조사/공백 정도만 최소 수정한다.
                - relatedRequirement에 JD 원문에 없는 새 표현을 만들지 않는다.
                - 답변 전체에 keyword와 직접 동일한 역량, 수행 경험, 도구 사용, 업무 수행이 이미 충분히 있으면 missingKeywordCandidates에 넣지 않는다.
                - "행정 업무", "행정 지원", "사업 및 행정지원"처럼 같은 역량이 답변에 입증되어 있으면 누락으로 만들지 않는다.

                [missingKeywordCandidates Negative Examples]
                - Example 1
                  JD main_tasks: 상세페이지 제작
                  JD qualifications: 포토샵
                  Answer: 포토샵으로 상세페이지를 제작했습니다.
                  missingKeywordCandidates: []
                  이유: 이미 답변에 있으며, 상세페이지 제작을 UX/UI 인터페이스 설계 같은 JD 밖 개념으로 확장하지 않는다.
                - Example 2
                  JD main_tasks: 브랜드 운영
                  Answer: 브랜드를 운영했습니다.
                  missingKeywordCandidates: []
                  이유: 브랜드 운영은 이미 답변에 있으며, BI/CI 구축 경험으로 일반화하지 않는다.
                - Example 3
                  JD main_tasks: 사업 및 행정지원
                  Answer: 총무부에서 사업 및 행정지원 업무와 예산 관리를 담당했습니다.
                  missingKeywordCandidates: []
                  이유: 사업 및 행정지원 역량이 이미 답변에 충분히 존재한다.

                [strengthCandidates Positive Examples]
                - Example 1
                  JD main_tasks: 상세페이지 제작
                  JD qualifications: 포토샵·일러스트 활용
                  JD preferences: 라이노·키샷 우대
                  Answer: 포토샵과 일러스트로 상세페이지를 제작했고, 1인 제품 브랜드를 직접 기획·운영했습니다. 라이노와 Fusion360으로 제품을 설계한 뒤 키샷으로 렌더링하고 실제 제품 제작까지 완료했습니다.
                  strengthCandidates: 포토샵과 일러스트 상세페이지 제작 경험, 1인 제품 브랜드 기획·운영 경험, 라이노·Fusion360·키샷 기반 제품 제작 경험 중 JD와 직접 연결되는 quote를 사용한다.
                  이유: 수치 성과가 없어도 도구, 수행 업무, 제작 완료 결과가 확인 가능하다.
                - Example 2
                  JD main_tasks: 엑셀 고급 활용, 4대보험 신고, 회계 업무
                  JD preferences: 더존 활용 우대
                  Answer: 급여 정산표를 만들어 함수로 자동 비교했고, 전표 입력과 계정 분류를 실습했습니다. 4대보험 자료 작성 방법을 학습하고 더존 프로그램 입력 기준을 비교했으며, 5만 원 시재 차이 원인을 거래 자료 대조로 추적해 수정 요청을 주 3~4건에서 1~2건으로 줄였습니다.
                  strengthCandidates: 엑셀 자동 비교, 회계 전표·계정 분류 실습, 4대보험 자료 작성 학습, 더존 입력 기준 비교, 시재 차이 원인 추적과 수정 요청 감소 중 JD와 직접 연결되는 quote를 사용한다.
                  이유: 직무 도구, 실무 처리 과정, 문제 해결, 개선 결과가 확인 가능하다.

                [문장 유형별 후보 기준]
                - EXPERIENCE: 역할, 행동, 방법, 결과, 직무 연결성 중 실제로 부족한 요소가 있어야 한다.
                - EXPERIENCE: 주변 문장에 역할, 방법, 성과가 이어지면 부족하다고 판단하지 않는다.
                - PLAN: 과거 성과나 수치를 요구하지 않고 실행 대상, 방법, 단계, 직무 연결성만 본다.
                - MOTIVATION: 수치를 요구하지 않고 회사·직무 선택 이유와 개인 경험의 연결성을 본다.
                - COMPETENCY: 자격증 또는 역량 언급 자체를 문제로 보지 않고 실제 활용 맥락이 필요한 경우에만 후보화한다.
                - FABRICATED는 명시적 사실 충돌이 있을 때만 사용한다. 단순 근거 부족은 MENTIONED다.

                [1차 few-shot 후보 개수 분포]
                - 예시 A: 충분히 구체적인 성과 문장은 strengthCandidates 1개, analysisCandidates 0개.
                - 예시 B: 주변 문맥에 방법과 결과가 이어지면 analysisCandidates 0개.
                - 예시 C: 추상적인 포부 문장 1개만 명확하면 analysisCandidates 1개.
                - 예시 D: 서로 다른 명확한 문제가 2개일 때만 analysisCandidates 2개.
                - 3개 후보 예시는 사용하지 않는다.

                [채용 공고]
                회사명: %s
                직무명: %s
                <main_tasks>
                %s
                </main_tasks>
                <qualifications>
                %s
                </qualifications>
                <preferences role="secondary_only">
                %s
                </preferences>

                [유사 JD 검색 결과]
                %s

                [유사 자소서 문항 검색 결과]
                %s

                %s

                [자소서 문항과 답변]
                %s
                """.formatted(
                defaultString(promptInput.companyName()),
                defaultString(promptInput.jobName()),
                defaultString(promptInput.mainTasks()),
                defaultString(promptInput.qualifications()),
                defaultString(promptInput.preferences()),
                formatJobPostingReferences(referenceContext.jobPostingReferences()),
                formatQuestionReferences(referenceContext.questionReferences()),
                jobCategoryCriteriaSection,
                questionText
        );
    }

    String buildFinalPrompt(
            AnalysisPromptInput promptInput,
            RetrievalContext referenceContext,
            JobCategoryEvaluationCriteria jobCategoryEvaluationCriteria,
            AnalysisCandidateResponse candidates
    ) {
        String questionText = formatQuestions(promptInput);
        String candidateJson = writeJson(candidates);
        String jobCategoryCriteriaSection = formatJobCategoryEvaluationCriteriaSection(jobCategoryEvaluationCriteria);
        return """
                [시스템 지시]
                너는 한국 채용 담당자이자 자기소개서 평가 전문가다.
                반드시 Structured Output 스키마에 맞는 JSON object만 반환한다.
                내부 판단 과정이나 chain-of-thought를 출력하지 않는다.

                %s

                [2차 출력 필드]
                - decisions: 검증된 analysisCandidates 각각에 대해 승인 또는 거절 결정을 반환한다.
                - strengths: 검증된 strengthCandidates 범위 안에서 최종 keyStrengths 후보를 반환한다.
                - missingKeywords: 검증된 missingKeywordCandidates 범위 안에서 최종 missingKeywords 후보를 반환한다.
                - jobFit, impact, completeness, feedback을 함께 반환한다.
                - 기존 최종 AnalysisLlmResponse를 직접 만들지 않는다. 서버가 검증된 strengths는 PROVEN으로, accepted decision은 MENTIONED 또는 FABRICATED로 최종 questionAnalyses에 변환한다.
                - 최종 questionAnalyses 개수 제한은 전체 합계가 아니라 questionId별 1~3개로 적용한다.

                [rejectionCode 허용값]
                - ALREADY_SPECIFIC
                - CONTEXT_PROVIDES_EVIDENCE
                - WRONG_SENTENCE_TYPE_CRITERIA
                - PREFERENCE_ONLY
                - NOT_JOB_RELEVANT
                - DUPLICATE_ISSUE
                - UNSUPPORTED_JUDGMENT
                - NOT_ACTIONABLE
                - INVALID_SOURCE
                - NONE

                [2차 후보 검증 절차]
                1. sentence가 원문에 존재하는지 확인한다.
                2. 앞뒤 문맥을 확인한다.
                3. mainTask 또는 qualification과 직접 관련 있는지 확인한다.
                4. preference-only인지 확인한다.
                5. sentenceType이 맞는지 확인한다.
                6. 해당 문장 유형에 맞는 평가 기준이 적용됐는지 확인한다.
                7. 주변 문맥에 이미 역할·방법·성과가 제공되는지 확인한다.
                8. 실제로 사용자에게 첨삭 가치가 있는지 확인한다.
                9. 승인 또는 거절 결정한다.
                10. 승인된 후보에 대해서만 reason과 improvement를 작성한다.
                - 내부 판단 과정은 출력하지 않는다.

                [후보 유지 조건]
                - 2차 단계의 역할은 새로운 분석 생성이 아니라 1차 후보가 실제 수정 대상인지 검증하는 것이다.
                - 확실하지 않으면 무조건 제거하지 않는다.
                - mentioned는 문장 자체는 사실일 수 있지만 구체적인 행동, 역할, 성과/결과, 문제 해결 과정, JD 요구 역량 연결, 기여 범위 중 하나가 부족한 경우 유지한다.
                - mentioned는 추상적인 표현만 있거나 주장에 비해 근거가 부족하거나 지원자 기여와 팀 성과가 구분되지 않는 경우 유지한다.
                - 단순히 숫자가 없다는 이유만으로 제거하거나 유지하지 않는다.
                - fabricated는 존재하지 않는 경험을 사실처럼 기술, 실제 역할보다 과장된 역할 주장, 성과 수치나 책임 범위 임의 확대, 앞뒤 문장과 명백히 모순되는 경우에만 유지한다.
                - 근거가 부족하다는 이유만으로 fabricated를 사용하지 않는다.

                [후보 제거 조건]
                - 문장이 이미 충분히 구체적이고 행동, 역할, 결과, JD 연결성이 충분할 때만 제거한다.
                - 단순 문체 선호 차이이거나 후보 분석이 자기소개서 질문 의도와 무관할 때 제거한다.
                - 다짐이나 포부 문장을 성과 문장처럼 잘못 평가한 경우 제거한다.
                - 자격증, 학력, 기술명 등 단순 정량 키워드가 없다는 이유만으로 문제 삼은 경우 제거한다.
                - 한 줄 전체가 대괄호로 감싸진 소제목을 본문 문장처럼 평가한 후보는 NOT_ACTIONABLE로 거절한다.
                - 수정안이 원문보다 실질적으로 개선되지 않으면 accepted=true로 두더라도 improvement는 null로 둔다.

                [decision 정합성]
                - accepted=true이면 rejectionCode=NONE, status는 MENTIONED 또는 FABRICATED, reason은 사용자 노출 가능한 최종 사유다.
                - accepted=false이면 rejectionCode는 NONE이 아니어야 하고, improvement는 반드시 null이다.
                - accepted=false의 reason은 내부 거절 근거이며 최종 API에는 노출되지 않는다.
                - 후보가 이미 충분하면 ALREADY_SPECIFIC으로 거절한다.
                - 주변 문맥이 근거를 제공하면 CONTEXT_PROVIDES_EVIDENCE로 거절한다.
                - 문장 유형에 맞지 않는 기준이 적용됐으면 WRONG_SENTENCE_TYPE_CRITERIA로 거절한다.
                - 1차에 없는 새로운 questionAnalysis를 임의로 추가하지 않는다.

                [문장 유형별 첨삭]
                - 경험/성과: 행동, 역할, 방법, 결과 중 실제 부족한 요소만 보완한다.
                - 경험/성과: 이미 수치와 방법이 있으면 부족하다고 평가하지 않는다.
                - 포부/계획: 과거 성과 수치를 요구하지 않고 실행 대상, 방법, 단계, 직무 연결성을 중심으로 reason을 작성한다.
                - 지원동기: 수치를 요구하지 않고 회사 또는 직무 선택 이유와 개인 경험의 연결성을 평가한다.
                - 역량/자격: 자격증이나 전공 자체보다 실제 활용 맥락을 평가한다.
                - 역량/자격: 정형 자격요건은 missingKeywords에서 제외한다.

                [improvement 안전 규칙]
                - 사용자가 그대로 교체해 쓸 수 있는 자기소개서 문장이어야 한다.
                - 첨삭 행위를 설명하는 메타 문장을 금지한다.
                - 원문과 실질적으로 동일한 문장을 금지한다.
                - 답변의 다른 문장 복사를 금지한다.
                - 원문에 없는 수치, 도구, 경험, 직무 수행, 계획 추가를 금지한다.
                - JD 문구를 지원자의 실제 경험처럼 변환하지 않는다.
                - 과거 문장을 미래 포부로 변경하지 않는다.
                - 미래 포부를 과거 경험으로 변경하지 않는다.
                - 원문 사실만으로 안전한 개선이 불가능하면 null을 반환한다.
                - 금지 예시: 구체적으로 설명했습니다. 경험을 추가하면 좋겠습니다. 성과를 강조했습니다. 구체적인 계획을 추가하겠습니다. 명확하게 작성할 수 있습니다.
                - 금지 패턴: 추가해 보, 추가하면 좋, 설명해 보, 구체적으로 작성, 구체적으로 설명, 강조하겠, 추가하겠, 명확히 작성, 작성할 수 있, 설명할 수 있, 보완하겠, 드러내겠, 제시하겠.

                [keyStrengths 복구 규칙]
                - 검증된 strengthCandidates 안에서 strengths를 구성한다.
                - 충분히 구체적이고 JD와 직접 연결된 문장을 strengthCandidates에서 적극 활용한다.
                - 수치, 도구, 역할, 결과가 구체적인 문장은 문제 후보보다 strength 후보를 우선한다.
                - quote는 답변 원문 exact substring이어야 한다.
                - mainTask 또는 qualification 연결을 우선한다.
                - preference-only 강점은 제외한다.
                - 최소 개수를 강제하지 않고 없으면 []를 반환한다.

                [missingKeywords 복구 규칙]
                - 검증된 missingKeywordCandidates 범위 안에서만 missingKeywords를 정리한다.
                - questionAnalyses 후보 개수와 독립적으로 판단한다.
                - 누락 키워드가 없어도 문장 첨삭은 존재할 수 있다.
                - 누락 키워드가 있어도 문장 자체는 정상일 수 있다.
                - 문장 분석이 0건이라고 missingKeywords를 비우지 않는다.
                - mainTask와 qualification만 사용하고 preference는 제외한다.
                - 정형 자격요건은 제외하고 경험형 요구사항은 유지 가능하다.
                - 새 keyword를 임의 생성하지 않는다.

                [점수 산정]
                - 1차 후보 개수와 점수를 연결하지 않는다.
                - questionAnalyses가 0개여도 무조건 고득점으로 처리하지 않는다.
                - questionAnalyses가 많다고 기계적으로 감점하지 않는다.
                - 점수는 JD 전체와 답변 전체를 기준으로 독립적으로 산정한다.
                - Few-shot의 점수나 고정 숫자 예시는 사용하지 않는다.

                [채용 공고]
                회사명: %s
                직무명: %s
                <main_tasks>
                %s
                </main_tasks>
                <qualifications>
                %s
                </qualifications>
                <preferences role="secondary_only">
                %s
                </preferences>

                [검증된 1차 후보]
                %s

                [유사 JD 검색 결과]
                %s

                [유사 자소서 문항 검색 결과]
                %s

                %s

                [자소서 문항과 답변]
                %s
                """.formatted(
                AnalysisPromptPolicy.OUTPUT_SCHEMA,
                defaultString(promptInput.companyName()),
                defaultString(promptInput.jobName()),
                defaultString(promptInput.mainTasks()),
                defaultString(promptInput.qualifications()),
                defaultString(promptInput.preferences()),
                candidateJson,
                formatJobPostingReferences(referenceContext.jobPostingReferences()),
                formatQuestionReferences(referenceContext.questionReferences()),
                jobCategoryCriteriaSection,
                questionText
        );
    }

    String buildRecheckPrompt(
            AnalysisPromptInput promptInput,
            RetrievalContext referenceContext,
            JobCategoryEvaluationCriteria jobCategoryEvaluationCriteria,
            AnalysisCandidateResponse candidates,
            com.jobdri.jobdri_api.domain.analysis.dto.llm.CandidateReviewResponse reviewResponse
    ) {
        String questionText = formatQuestions(promptInput);
        String candidateJson = writeJson(candidates);
        String reviewJson = writeJson(reviewResponse);
        String jobCategoryCriteriaSection = formatJobCategoryEvaluationCriteriaSection(jobCategoryEvaluationCriteria);
        return """
                [시스템 지시]
                너는 자기소개서 two-pass 분석의 재검증기다.
                반드시 Structured Output 스키마에 맞는 JSON object만 반환한다.
                내부 판단 과정이나 chain-of-thought를 출력하지 않는다.

                [재검증 목적]
                1차 후보가 하나 이상 있었지만 2차 검증 후 accepted 후보가 0개다.
                바로 빈 배열로 확정하지 말고 다음 질문만 판단한다.
                - 1차 후보 중 사용자가 실제로 수정하면 도움이 되는 문장이 정말 하나도 없는가?

                [decision]
                - NO_CORRECTION_NEEDED: 후보들이 모두 실제 첨삭 대상이 아니다.
                - KEEP_BEST_CANDIDATE: 가장 명확한 수정 필요성이 있는 후보 1건만 유지한다.

                [KEEP_BEST_CANDIDATE 선택 기준]
                - 단순히 첫 번째 후보를 선택하지 않는다.
                - problemClarity, jobRelevance, evidenceGap, improvementUsefulness, fabricationConfidence를 1~5로 내부 평가한다.
                - KEEP_BEST_CANDIDATE는 problemClarity >= 4, jobRelevance >= 4, improvementUsefulness >= 4일 때만 선택한다.
                - 문제 명확성, JD 관련성, 근거 부족 정도, 안전한 개선 가능성이 높은 후보를 선택한다.
                - questionTypeMatched, contextConsistent, reasonSpecific, improvementActionable은 모두 true여야 한다.
                - status는 MENTIONED 또는 FABRICATED만 사용한다.
                - MENTIONED: 관련 경험이나 의도는 있으나 행동, 역할, 결과, 문제 해결 과정, JD 연결, 기여 범위 중 하나가 부족하다.
                - FABRICATED: 원문 또는 제공 정보와 명백히 충돌할 때만 사용한다.
                - FABRICATED는 fabricationConfidence >= 4이고 directContradiction=true일 때만 선택한다.
                - 근거 부족만으로 FABRICATED를 사용하지 않는다.
                - 원문에 없는 사실, 수치, 경험, 계획을 추가하지 않는다.

                [재검증 입력 확인]
                - 문항 원문, 전체 답변, JD 주요 업무, 자격 요건, 우대 사항을 함께 확인한다.
                - 후보 문장, 1차 status, 1차 reasonBasis, 2차 제거 decision, 2차 제거 reason을 함께 확인한다.
                - 후보 문장 하나만 보고 복원하지 않는다.
                - 후보가 문항 의도에 맞는지 확인한다.
                - 전체 답변 문맥에서 실제 수정이 필요한지 확인한다.
                - JD 요구사항과 직접 관련되는지 확인한다.
                - reason이 원문에서 확인 가능한지 확인한다.
                - improvement가 실제로 더 유용한지 확인한다.

                [NO_CORRECTION_NEEDED 판단 기준]
                - 모든 후보 문장이 이미 충분히 구체적이다.
                - 후보가 단순 문체 선호 차이이거나 질문 의도와 무관하다.
                - 후보가 한 줄 전체가 대괄호로 감싸진 소제목이다.
                - 다짐/포부 문장을 성과 부족으로 잘못 평가했다.
                - 자격증, 학력, 기술명 등 단순 정량 키워드 부재만 문제 삼았다.
                - 안전하고 실질적인 개선문을 만들 수 없고 reason도 사용자에게 도움이 되지 않는다.

                [improvement]
                - 안전한 교체 문장을 만들 수 없으면 null이다.
                - 첨삭 행위를 설명하는 메타 문장을 쓰지 않는다.
                - 원문과 같은 문장, 다른 원문 문장 복사, JD 경험 생성, 시제 변경을 금지한다.
                - KEEP_BEST_CANDIDATE를 선택했다면 improvement는 reason에서 지적한 문제를 실제로 개선해야 한다.
                - 더 구체적으로 작성하겠습니다, 직무 역량을 강화하겠습니다, 성과를 명확히 보여주었습니다 같은 문장은 improvementActionable=false다.

                [문항 유형별 복원 기준]
                - 지원 동기: 직무 선택 이유, 본인 경험과 직무 연결, 지원자 근거가 부족할 때만 복원한다. 과거 성과 수치나 STAR 구조가 없다는 이유로 복원하지 않는다.
                - 포부/다짐: 실행 방향, 직무 연결, 성장 또는 기여 계획이 지나치게 추상적일 때만 복원한다. 과거 성과, 정량 수치, 과거 행동 근거가 없다는 이유로 복원하지 않는다.
                - 경험/성과: 역할, 행동 과정, 결과, 개인 기여가 부족할 때 복원할 수 있다.
                - 협업/갈등: 갈등 원인, 조율 행동, 결과, 상호작용이 부족할 때 복원할 수 있다.

                [채용 공고]
                회사명: %s
                직무명: %s
                <main_tasks>
                %s
                </main_tasks>
                <qualifications>
                %s
                </qualifications>
                <preferences role="secondary_only">
                %s
                </preferences>

                [검증된 1차 후보]
                %s

                [2차 검증 결과]
                %s

                [유사 JD 검색 결과]
                %s

                [유사 자소서 문항 검색 결과]
                %s

                %s

                [자소서 문항과 답변]
                %s
                """.formatted(
                defaultString(promptInput.companyName()),
                defaultString(promptInput.jobName()),
                defaultString(promptInput.mainTasks()),
                defaultString(promptInput.qualifications()),
                defaultString(promptInput.preferences()),
                candidateJson,
                reviewJson,
                formatJobPostingReferences(referenceContext.jobPostingReferences()),
                formatQuestionReferences(referenceContext.questionReferences()),
                jobCategoryCriteriaSection,
                questionText
        );
    }

    String buildSinglePassPrompt(
            AnalysisPromptInput promptInput,
            RetrievalContext referenceContext,
            JobCategoryEvaluationCriteria jobCategoryEvaluationCriteria
    ) {
        String questionText = formatQuestions(promptInput);
        String similarJobPostingText = formatJobPostingReferences(referenceContext.jobPostingReferences());
        String similarQuestionText = formatQuestionReferences(referenceContext.questionReferences());
        String jobCategoryCriteriaSection = formatJobCategoryEvaluationCriteriaSection(jobCategoryEvaluationCriteria);
        String fewShotPromptBlock = resolveFewShotPromptBlock(promptInput);

        return """
                [시스템 지시]
                너는 한국 채용 담당자이자 자기소개서 평가 전문가다.
                반드시 JSON만 출력한다.
                자소서 원문에 없는 sentence를 만들지 않는다.
                sentence는 반드시 해당 question의 answer에 포함된 정확한 부분 문자열이어야 한다.
                한국어 사용자 노출 라벨을 만들거나 추정하지 않는다.

                %s

                %s

                %s

                [Few-shot 예시]
                %s

                [채용 공고]
                회사명: %s
                직무명: %s
                아래 JD 영역의 역할:
                - <main_tasks>는 최우선 업무 기준이다.
                - <qualifications>는 핵심 자격요건 기준이다.
                - <preferences role="secondary_only">는 보조 기준이며 단독 결격 사유로 사용하지 않는다.

                <main_tasks>
                %s
                </main_tasks>

                <qualifications>
                %s
                </qualifications>

                <preferences role="secondary_only">
                %s
                </preferences>

                [유사 JD 검색 결과]
                %s

                [유사 자소서 문항 검색 결과]
                %s

                %s

                [자소서 문항과 답변]
                %s

                [출력 전 자체 검증]
                - JSON 외 텍스트, 마크다운, 코드블럭을 출력하지 않는다.
                - questionAnalyses의 questionId는 입력된 questionId 중 하나만 사용한다.
                - questionAnalyses의 status는 proven, mentioned, fabricated 중 하나만 사용한다.
                - sentence는 answer에 포함된 정확한 substring만 사용한다.
                - missing은 questionAnalyses에 넣지 않고 missingKeywords로만 반환한다.
                - keyStrengths와 keyWeaknesses는 각각 최대 3개이며, 없으면 []로 출력한다.
                - keyStrengths의 quote는 answer에 실제 포함된 substring만 사용한다.
                - keyWeaknesses에서 missingKeywords를 다루는 항목의 quote는 실제 JD 문구만 사용한다.
                - missingKeywords는 최대 3개이며, 없으면 []로 출력한다.
                - missingKeywords의 source는 qualification, preference, mainTask 중 하나만 사용한다.
                - improvement가 지시문이 아닌 완성된 한국어 평서문인지 확인한다.
                - 원문에 없는 경험, 기술, 도구명, 인원수, 금액, 성과 수치를 만들지 않았는지 확인한다.
                - fabricated를 단순 근거 부족에 사용하지 않았는지 확인한다.
                - 한 줄 전체가 대괄호로 감싸진 소제목을 questionAnalyses 또는 keyStrengths에 포함하지 않았는지 확인한다.
                - jobFit, impact, completeness는 0~100 정수로 출력한다.
                - 총점 score는 서버가 jobFit 50%%, impact 30%%, completeness 20%%로 계산하므로 출력하지 않는다.
                """.formatted(
                AnalysisPromptPolicy.OUTPUT_SCHEMA,
                AnalysisPromptPolicy.EVALUATION_CRITERIA,
                AnalysisPromptPolicy.STATUS_AND_WRITING_RULES,
                fewShotPromptBlock,
                defaultString(promptInput.companyName()),
                defaultString(promptInput.jobName()),
                defaultString(promptInput.mainTasks()),
                defaultString(promptInput.qualifications()),
                defaultString(promptInput.preferences()),
                similarJobPostingText,
                similarQuestionText,
                jobCategoryCriteriaSection,
                questionText
        );
    }

    private String resolveFewShotPromptBlock(AnalysisPromptInput promptInput) {
        if (fewShotSearchService == null || fewShotProperties == null || !fewShotProperties.isDynamicSelectionEnabled()) {
            return fewShotPromptProvider.getPrompt();
        }
        try {
            List<SelectedFewShotCase> selectedFewShots = fewShotSearchService.searchRelevantFewShots(
                    FewShotSearchQuery.from(promptInput),
                    fewShotProperties.getSearch().getTopK()
            );
            if (selectedFewShots.isEmpty()) {
                log.warn(
                        "dynamic few-shot selection returned empty result. fallback=fixed, caseId={}, datasetVersion={}",
                        promptInput.caseId(),
                        fewShotProperties.getDatasetVersion()
                );
                return fewShotPromptProvider.getPrompt();
            }
            log.debug(
                    "dynamic few-shot prompt selected. caseId={}, selectedIds={}, sources={}, scores={}, datasetVersion={}",
                    promptInput.caseId(),
                    selectedFewShots.stream().map(item -> item.fewShotCase().id()).toList(),
                    selectedFewShots.stream().map(item -> item.fewShotCase().source()).toList(),
                    selectedFewShots.stream().map(item -> "%.4f".formatted(item.score())).toList(),
                    fewShotProperties.getDatasetVersion()
            );
            return fewShotPromptProvider.buildPromptBlock(selectedFewShots);
        } catch (Exception e) {
            log.warn(
                    "dynamic few-shot selection failed. fallback=fixed, caseId={}, datasetVersion={}, reason={}, message={}",
                    promptInput.caseId(),
                    fewShotProperties.getDatasetVersion(),
                    e.getClass().getSimpleName(),
                    e.getMessage()
            );
            log.debug("dynamic few-shot selection exception", e);
            return fewShotPromptProvider.getPrompt();
        }
    }

    private String formatJobCategoryEvaluationCriteriaSection(JobCategoryEvaluationCriteria criteria) {
        if (criteria == null) {
            return "";
        }

        return """
                [직무별 보조 평가 기준]
                중분류: %s
                주의:
                - 이 직무별 기준은 실제 JD를 대체하지 않는다.
                - 실제 JD의 자격요건, 우대사항, 주요업무를 우선한다.
                - 직무별 기준은 JD가 모호하거나 암묵 역량 판단이 필요할 때만 보조적으로 참고한다.
                - missingKeywords는 실제 JD 표현을 우선 사용하고, 직무별 missingKeywordExamples는 문구 정리와 유사 키워드 묶기에만 참고한다.
                - 직무별 기준에 있는 키워드가 자소서에 없다는 이유만으로 무조건 missing 처리하지 않는다.
                - 원문에 없는 수치, 도구, 경험을 만들어내지 않는다.
                핵심 역량: %s
                관련 행동: %s
                관련 키워드: %s
                좋은 근거 예시: %s
                누락 키워드 문구 예시: %s
                """.formatted(
                defaultString(criteria.jobCategoryMiddle()),
                formatCriteriaList(criteria.coreCompetencies()),
                formatCriteriaList(criteria.relatedActions()),
                formatCriteriaList(criteria.relatedKeywords()),
                truncate(defaultString(criteria.goodEvidenceExample()), MAX_REFERENCE_FIELD_LENGTH),
                formatCriteriaList(criteria.missingKeywordExamples())
        ).trim();
    }

    private String formatCriteriaList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "없음";
        }
        String formatted = values.stream()
                .filter(value -> value != null && !value.isBlank())
                .limit(MAX_CRITERIA_ITEMS)
                .map(value -> "- " + truncate(value.trim(), MAX_REFERENCE_FIELD_LENGTH))
                .collect(Collectors.joining("\n"));
        return formatted.isBlank() ? "없음" : "\n" + formatted;
    }

    private String formatJobPostingReferences(List<RetrievedJobPostingReference> references) {
        if (references == null || references.isEmpty()) {
            return "없음";
        }
        String formatted = references.stream()
                .map(reference -> """
                        - 회사명: %s
                          직무명: %s
                          주요 업무: %s
                          자격 요건: %s
                          우대 사항: %s
                          거리: %.4f
                        """.formatted(
                        truncate(defaultString(reference.companyName()), MAX_REFERENCE_FIELD_LENGTH),
                        truncate(defaultString(reference.roleName()), MAX_REFERENCE_FIELD_LENGTH),
                        truncate(defaultString(reference.responsibilities()), MAX_REFERENCE_FIELD_LENGTH),
                        truncate(defaultString(reference.requirements()), MAX_REFERENCE_FIELD_LENGTH),
                        truncate(defaultString(reference.preferred()), MAX_REFERENCE_FIELD_LENGTH),
                        reference.distance()
                ))
                .reduce("", (left, right) -> left + "\n" + right)
                .trim();
        return truncate(formatted, MAX_REFERENCE_SECTION_LENGTH);
    }

    private String formatQuestionReferences(List<RetrievedQuestionReference> references) {
        if (references == null || references.isEmpty()) {
            return "없음";
        }
        String formatted = references.stream()
                .map(reference -> """
                        - 회사명: %s
                          직무명: %s
                          문항 유형: %s
                          글자 수 제한: %s
                          문항: %s
                          거리: %.4f
                        """.formatted(
                        truncate(defaultString(reference.companyName()), MAX_REFERENCE_FIELD_LENGTH),
                        truncate(defaultString(reference.roleName()), MAX_REFERENCE_FIELD_LENGTH),
                        truncate(defaultString(reference.questionType()), MAX_REFERENCE_FIELD_LENGTH),
                        reference.charLimit() == null ? "" : reference.charLimit(),
                        truncate(defaultString(reference.questionText()), MAX_REFERENCE_FIELD_LENGTH),
                        reference.distance()
                ))
                .reduce("", (left, right) -> left + "\n" + right)
                .trim();
        return truncate(formatted, MAX_REFERENCE_SECTION_LENGTH);
    }

    private String formatQuestions(AnalysisPromptInput promptInput) {
        return promptInput.questions().stream()
                .map(question -> """
                        - questionId: %d
                          question: %s
                          answer: %s
                        """.formatted(
                        question.questionId(),
                        defaultString(question.question()),
                        defaultString(question.answer())
                ))
                .collect(Collectors.joining("\n"));
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? List.of() : value);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    private String defaultString(String value) {
        return value == null ? "" : value;
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "...";
    }
}
