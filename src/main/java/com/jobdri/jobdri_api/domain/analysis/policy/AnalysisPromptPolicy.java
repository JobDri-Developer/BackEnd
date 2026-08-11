package com.jobdri.jobdri_api.domain.analysis.policy;

import com.jobdri.jobdri_api.domain.analysis.service.ai.AnalysisImprovementRules;

public final class AnalysisPromptPolicy {
    private AnalysisPromptPolicy() {
    }

    public static final String OUTPUT_SCHEMA = """
            [출력 규칙]
            - Structured Output 스키마에 맞는 JSON object만 반환한다.
            - jobFit: 실제 JD와 실제 답변 전체를 기준으로 독립 산정한 0~100 정수
            - impact: 실제 JD와 실제 답변 전체를 기준으로 독립 산정한 0~100 정수
            - completeness: 실제 JD와 실제 답변 전체를 기준으로 독립 산정한 0~100 정수
            - feedback: 한 줄 피드백
            - keyStrengths: 없으면 []
            - keyWeaknesses: 없으면 []
            - missingKeywords: 없으면 []
            - questionAnalyses: 비어 있지 않은 문항마다 대표 평가 문장을 1~3개
            - improvement: 안전한 개선문을 만들 수 없으면 null
            - 프롬프트 안에 특정 점수 숫자 조합을 JSON 예시로 넣지 않는다.
            - 0, 50, 70, 100 같은 임의 점수도 출력 예시로 사용하지 않는다.
            - Structured Output은 JSON 형식과 타입만 보장한다.
            - sentence 원문 존재 여부, status/reason 정합성, improvement 사실 생성 여부, missingKeyword JD 근거 여부는 서버가 다시 검증한다.
            """;

    public static final String EVALUATION_CRITERIA = """
            [평가 절차]
            최종 JSON을 작성하기 전에 내부적으로 다음 순서를 따른다.
            내부 판단 과정이나 chain-of-thought를 응답에 출력하지 않는다.
            별도의 reasoning 필드나 analysis 필드를 API 응답에 추가하지 않는다.
            모델의 상세 사고과정을 로그나 DB에 저장하지 않는다.
            1. 문장 유형을 경험/성과, 포부/계획, 지원동기, 역량/자격으로 판단한다.
            2. mainTask와 qualification 중 직접 관련된 요구사항을 찾는다.
            3. preference만 근거인 경우 첨삭 대상에서 제외한다.
            4. 문장 유형에 맞는 평가 기준만 적용한다.
            5. 문항을 대표해 평가할 문장인지 판단한다.
            6. proven, mentioned 또는 fabricated 상태를 결정한다.
            7. reason이 status 및 문장 유형과 일치하는지 확인한다.
            8. 원문 사실만으로 안전한 improvement를 작성할 수 있는지 확인한다.
            9. 새 경험, 수치, 기술, 계획, 시제 변경, 메타 조언이 없는지 재검사한다.
            10. 최종 JSON만 반환한다.

            [문장 유형 구분]
            - 경험/성과: 과거에 수행한 행동, 역할, 문제 해결, 결과, 수치, 산출물이 드러나는 문장
            - 포부/계획: 입사 후 하겠다, 기여하겠다, 성장하겠다처럼 미래 실행 의지를 말하는 문장
            - 지원동기: 회사/직무를 선택한 이유, 관심, 가치관, 동기를 설명하는 문장
            - 역량/자격: 보유 기술, 자격, 면허, 전공, 경력, 교육 이수 여부를 설명하는 문장

            [포부/계획 문장 평가 규칙]
            - "~하겠습니다", "~되겠습니다", "~기여하겠습니다", "~노력하겠습니다", "~성장하고 싶습니다" 등 미래 시점 문장은 포부/계획으로 우선 판단한다.
            - 포부/계획 문장에는 과거 성과 수치, 과거 결과, Before-After를 요구하지 않는다.
            - 포부/계획은 실행 대상, 실행 방법, 단계, 직무 연결성이 구체적인지 중심으로 판단한다.
            - 포부/계획 문장을 "성과 수치가 부족하다"는 이유로 questionAnalyses에 포함하지 않는다.
            - 포부/계획 문장의 reason에는 "성과 수치가 부족", "정량적 결과가 부족", "Before-After가 부족", "과거 성과가 드러나지 않음"을 사용하지 않는다.
            - 포부가 너무 추상적일 때만 실행 대상, 방법, 단계, 직무 연결성을 보완하도록 reason을 작성한다.

            [문장 유형별 평가 기준]
            - 경험/성과: 과거 수행 행동, 역할, 문제, 결과를 평가한다. 모든 경험에 반드시 수치를 요구하지 않는다.
            - 경험/성과: 과정, 역할, 산출물, 검증 결과 중 하나 이상이 충분히 구체적이면 인정한다.
            - 경험/성과: 이미 수치나 명확한 결과가 있으면 "성과 수치 부족" reason을 생성하지 않는다.
            - 포부/계획: 미래 시점 문장에는 과거 성과 수치나 Before-After를 요구하지 않고 실행 대상, 방법, 단계, 직무 연결성을 평가한다.
            - 지원동기: 회사 또는 직무를 선택한 이유가 개인 경험·관심과 연결되는지 평가하며, 지원동기를 성과 문장처럼 평가하지 않는다.
            - 지원동기: 해당 회사만의 이유가 없는 일반론인지 확인한다.
            - 역량/자격: 보유 기술이나 역량의 실제 사용 맥락을 평가한다.
            - 역량/자격: 자격증이나 전공 자체를 문장 첨삭의 핵심 문제로 삼지 않는다.
            - 역량/자격: 해당 기술을 실제로 어떻게 사용했는지가 있으면 그 근거를 평가한다.

            [JD 반영 우선순위]
            - 판단 우선순위는 mainTask > qualification >>> preference다.
            - mainTask와 qualification은 직무 적합성 판단의 핵심 기준이다.
            - preference는 reason과 점수에 보조적으로만 반영한다.
            - preference만 누락된 경우 questionAnalyses의 첨삭 대상으로 선택하지 않는다.
            - preference만 근거로 jobFit을 크게 감점하지 않는다.

            [점수 산정 규칙]
            - Few-shot 예시는 문장 상태 판정과 출력 형식 참고용이며 점수 예시가 아니다.
            - 점수는 실제 JD와 실제 답변 전체를 기준으로 독립적으로 산정한다.
            - 예시의 수치나 특정 고정 점수를 추론해서 사용하지 않는다.
            - 서로 다른 입력에 동일한 점수를 기계적으로 반복하지 않는다.

            [jobFit 평가 기준]
            JD가 요구하는 역량, 경험, 기술을 자기소개서가 얼마나 증명하는지 평가한다.
            체크 항목:
            - 필수 자격요건 매칭
            - 우대사항 반영
            - 주요 업무 연관성
            - 직무 키워드 활용
            - 암묵적 직무 역량 충족
            점수 구간:
            - 85~100: 필수 자격요건 대부분 proven, 직무 키워드가 풍부하고 업무 경험과 JD가 직접 연결됨
            - 70~84: 주요 자격요건이 증명되고 일부 우대사항도 반영됨
            - 55~69: 자격요건 일부만 증명되고 JD와 간접적으로 연결됨
            - 40~54: 자격요건 증명이 거의 없고 직무 관련성이 낮음
            - 40 미만: JD와 자기소개서가 거의 무관함

            [impact 평가 기준]
            주장을 뒷받침하는 근거가 얼마나 구체적이고 설득력 있는지 평가한다.
            체크 항목:
            - 정량적 성과
            - STAR 구조 활용
            - 주장과 근거의 연결
            - Before-After 비교
            - 차별적 경험
            점수 구간:
            - 85~100: 주요 주장에 정량 성과 또는 구체적 에피소드가 있고 STAR 구조가 명확함
            - 70~84: 핵심 주장 대부분에 근거가 있고 일부 수치와 STAR 구조가 존재함
            - 55~69: 경험은 있으나 근거가 모호하고 수치가 거의 없음
            - 40~54: 대부분 근거 없는 주장과 추상 표현으로 구성됨
            - 40 미만: 구체적 근거가 전혀 없음
            감점 금지:
            - 문제 상황 설명에 수치가 없다는 이유만으로 감점하지 않는다.
            - 과정이나 학습 경험에 정량 지표가 없다는 이유만으로 감점하지 않는다.
            - STAR 구조를 형식적으로 완벽하게 따르지 않는다는 이유만으로 감점하지 않는다.
            단, 전체 맥락과 행동, 결과는 이해 가능해야 한다.

            [completeness 평가 기준]
            질문 적합성, 논리 흐름, 문장 표현 품질을 종합 평가한다.
            체크 항목:
            - 질문 적합성
            - 문단 구조와 흐름
            - 논리적 일관성
            - 문장 가독성
            - 설득력 있는 마무리
            점수 구간:
            - 85~100: 모든 문항에 정확히 답하고 논리 흐름과 마무리가 매우 자연스러움
            - 70~84: 대부분 적절하게 답하고 전반적으로 읽기 좋음
            - 55~69: 일부 동문서답, 논리 비약, 반복 표현 또는 마무리 부족이 있음
            - 40~54: 질문과 답변 불일치가 많고 구조와 논리 문제가 큼
            - 40 미만: 대부분 질문 의도와 무관하거나 미완성임
            """;

    public static final String STATUS_AND_WRITING_RULES = """
            [status 판정 기준]
            - questionAnalyses의 허용 status는 proven, mentioned, fabricated다.
            - proven: JD와 관련된 구체적인 행동, 근거 또는 결과가 충분히 드러난 문장
            - mentioned: 관련 경험이나 의도는 있으나 구체성이 부족한 문장
            - missing: 해당 역량이나 요건을 자기소개서에서 전혀 다루지 않음
            - fabricated: JD 또는 답변 내부의 명시적 사실과 직접 충돌하거나, 지원자가 실제로 하지 않았다고 밝힌 경험을 한 것처럼 주장한 경우

            [status 중요 규칙]
            - 충분히 좋은 대표 문장은 proven으로 questionAnalyses에 반환할 수 있다.
            - proven 문장의 improvement는 null로 반환한다.
            - MISSING은 sentence가 없으므로 questionAnalyses에 넣지 않고 missingKeywords로만 반환한다.
            - 직접적인 증거가 부족해도 관련 경험이 있으면 mentioned로 분류한다.
            - missing은 관련 언급이 전혀 없을 때만 사용한다.
            - fabricated는 단순히 근거가 부족하다는 이유로 사용하지 않는다.
            - fabricated는 명시적 사실과 직접 충돌하거나 지원자가 하지 않았다고 밝힌 경험을 한 것처럼 주장한 경우에만 사용한다.
            - status 다양성을 만들기 위해 억지로 fabricated를 생성하지 않는다.
            - 구체적인 경험이나 수치가 부족하다는 이유만으로 fabricated를 사용하지 않는다.

            [sentence 규칙]
            - questionAnalyses의 sentence는 반드시 해당 questionId의 answer에 실제 포함된 정확한 substring이어야 한다.
            - 원문에 없는 문장을 생성하지 않는다.
            - sentence를 요약하거나 수정하지 않는다.
            - 원문 매칭이 불확실하면 questionAnalyses에 포함하지 않는다.
            - answer가 비어 있지 않은 모든 문항은 가장 평가 가치가 큰 실제 문장을 최소 1개 반환한다.
            - questionAnalyses는 비어 있지 않은 문항마다 1~3개 반환한다.
            - 구체적인 강점 문장은 proven, 보완이 필요한 문장은 mentioned 또는 fabricated로 반환한다.
            - 실제로 독립적인 평가 문장이 여러 개라면 대표 1개만 선택하지 말고 최대 3개까지 반환한다.
            - 동일한 문제를 반복하는 문장은 하나만 선택한다.
            - answer가 비어 있는 문항만 분석을 반환하지 않는다.
            - 동일하거나 거의 동일한 문장을 중복 반환하지 않는다.
            - start/end index는 출력하지 않는다. 서버가 Java String character index 기준으로 계산한다.
            - missing은 원문에 해당 문장이 없을 수 있으므로 sentence를 임의로 만들지 않는다.
            - missing은 questionAnalyses에 억지로 넣지 않는다.

            [소제목 처리 규칙]
            - 답변에서 한 줄 전체가 대괄호로 감싸진 형식(예: [문제를 기회로 바꾼 경험])은 본문 문장이 아니라 소제목이다.
            - 소제목 자체를 EXPERIENCE, PLAN, MOTIVATION, COMPETENCY 문장으로 분류하거나 questionAnalyses의 sentence로 반환하지 않는다.
            - 소제목 자체를 keyStrengths의 quote 또는 keyWeaknesses의 근거로 반환하지 않는다.
            - 소제목에 행동, 역할, 방법, 성과가 없다는 이유로 구체성이 부족하다고 판단하거나 점수를 감점하지 않는다.
            - 소제목은 바로 뒤 문단의 주제와 흐름을 이해하는 보조 맥락으로만 사용하고, 실제 평가는 본문 문장을 기준으로 한다.
            - 소제목이 문단 내용을 요약하는 표현인지 여부는 문단 이해에만 참고하며, 소제목 문구 자체를 별도 첨삭 대상으로 만들지 않는다.

            [missingKeywords 규칙]
            - 실제 입력 JD의 주요 업무, 자격 요건 원문에 존재하지만 자소서에 충분히 드러나지 않은 경험형 역량만 추출한다.
            - 유사 JD 검색 결과, 직무별 보조 평가 기준, few-shot 예시, 모델의 일반 지식에서 키워드를 생성하지 않는다.
            - JD 원문에 없는 SQL, Python, AWS, 대용량 트래픽 같은 키워드를 생성하지 않는다.
            - 자격증, 면허, 어학성적, 학위, 전공, 경력 연차, 근무 가능 여부, 나이, 국적, 졸업 여부 같은 정형 자격요건은 missingKeywords에 넣지 않는다.
            - preference에만 존재하는 항목은 원칙적으로 missingKeywords에서 제외한다.
            - mainTask와 qualification에서 근거를 찾지 못한 경우 preference만으로 문장을 문제 삼지 않는다.
            - reason에서 preference를 핵심 결격 사유처럼 표현하지 않는다.
            - preference가 없다는 이유만으로 mentioned를 생성하지 않는다.
            - questionAnalyses와 분리해서 missingKeywords에만 넣는다.
            - 최대 3개만 반환한다.
            - 누락 키워드가 없으면 null이나 필드 생략이 아니라 빈 배열 []을 반환한다.
            - 우선순위는 주요 업무(mainTask) > 자격요건(qualification) >>> 우대사항(preference)다.
            - keyword는 단순 단어보다 짧은 역량 문구 형태로 작성한다.
            - 반드시 실제 입력 JD에 들어간 표현을 유지한다.
            - 중복되거나 유사한 keyword는 하나로 묶고, 대표 문구는 자격요건 표현을 우선한다.
            - source는 qualification, preference, mainTask 중 하나만 사용한다.

            [핵심 강점/약점 작성 규칙]
            - keyStrengths와 keyWeaknesses는 각각 최대 3개만 반환한다.
            - title은 화면 카드 제목으로 바로 노출할 짧은 한국어 문장으로 작성한다.
            - quote는 반드시 실제 텍스트에서 가져온 짧은 직접 인용이어야 하며 새로 만들거나 요약하지 않는다.
            - keyStrengths의 quote는 자소서 answer에 실제 포함된 정확한 부분 문자열만 사용한다.
            - 충분히 구체적인 좋은 문장은 keyStrengths 후보로 사용한다.
            - keyStrengths는 mainTask 또는 qualification과 직접 연결된 근거를 우선한다.
            - preference만 충족하는 문장은 핵심 강점으로 과대평가하지 않는다.
            - proven 문장은 keyStrengths의 quote와 questionAnalyses의 sentence에 함께 사용할 수 있다.
            - mentioned 또는 fabricated 문장은 keyStrengths와 중복하지 않는다.
            - keyWeaknesses의 첫 항목들은 missingKeywords와 같은 누락 요건을 다룬다.
            - missingKeywords 기반 keyWeaknesses의 quote는 JD의 주요 업무, 자격 요건, 우대 사항에 실제 포함된 표현을 사용한다.
            - missingKeywords가 없으면 keyWeaknesses는 questionAnalyses의 보완 대상 문장 quote를 우선 사용한다.
            - quote는 너무 길게 붙이지 말고 사용자가 근거를 확인할 수 있는 핵심 구절만 사용한다.
            - 적절한 강점이나 약점이 없으면 null이나 필드 생략이 아니라 빈 배열 []을 반환한다.

            [reason 작성 규칙]
            - 사용자가 왜 해당 문장이 보완 대상인지 이해할 수 있게 작성한다.
            - 가능하면 JD의 어떤 업무, 자격요건, 우대사항과 관련된 문제인지 설명한다.
            - 1~2문장으로 간결하게 작성한다.
            - 사용자의 경험을 부정하거나 비난하지 않는다.
            - "잘못되었다"보다 "근거가 부족하다", "결과가 드러나지 않는다"처럼 진단형 표현을 사용한다.
            - 단순 문법 교정보다 직무 적합성, 구체성, 논리 완성도 관점의 이유를 우선한다.

            [improvement 작성 규칙]
            - improvement는 첨삭 조언이 아니라 사용자가 sentence를 대체해 사용할 수 있는 완성된 한국어 문장이어야 한다.
            - improvement는 사용자가 그대로 교체해 쓸 수 있는 자기소개서 문장이어야 하며 첨삭 행위를 설명하는 메타 문장이 아니어야 한다.
            - 개선이 필요하지 않으면 improvement는 null로 반환한다.
            - 원문 정보만으로 개선문을 만들 수 없으면 improvement는 null로 반환한다.
            - 반드시 한국어 평서문으로 작성한다.
            - "추가하세요", "보완하세요", "수정해주세요", "필요합니다" 같은 지시문을 사용하지 않는다.
            - "구체적으로 작성했습니다", "명확히 설명했습니다" 같은 메타 조언을 improvement로 반환하지 않는다.
            - 원문과 실질적으로 동일한 문장을 improvement로 반환하지 않는다.
            - 답변의 다른 문장을 그대로 복사해 improvement로 반환하지 않는다.
            - JD 요구사항을 지원자가 실제 수행한 경험처럼 생성하지 않는다.
            - 사용자가 언급하지 않은 경험, 기술, 도구명, 인원수, 금액, 성과 수치를 임의로 만들지 않는다.
            - 원문이 과거 경험이면 개선문도 과거 경험을 유지한다.
            - 원문이 포부이면 개선문도 포부를 유지한다.
            - 새로운 경험이나 계획을 추가하지 않는다.
            - 수치가 필요하지만 원문에 없다면 N건, X%%, 약 N시간 같은 빈칸 표현을 사용한다.
            - 원래 경험과 맥락을 최대한 유지한다.
            - 가능하면 행동, 역할, 결과가 드러나도록 개선한다.
            - 너무 길거나 과도하게 화려한 문장으로 만들지 않는다.
            - 금지 표현: %s
            """.formatted(AnalysisImprovementRules.bannedPhrasesText());
}
