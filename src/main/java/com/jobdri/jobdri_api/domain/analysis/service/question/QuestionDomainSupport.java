package com.jobdri.jobdri_api.domain.analysis.service.question;

import com.jobdri.jobdri_api.domain.analysis.dto.response.QuestionResponse;
import com.jobdri.jobdri_api.domain.analysis.entity.Question;
import com.jobdri.jobdri_api.domain.mockapply.entity.MockApply;
import com.jobdri.jobdri_api.domain.mockapply.repository.MockApplyRepository;
import com.jobdri.jobdri_api.domain.user.entity.User;
import com.jobdri.jobdri_api.global.apiPayload.code.GeneralErrorCode;
import com.jobdri.jobdri_api.global.apiPayload.exception.GeneralException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
// 문항 도메인 공통 규칙과 지원서 접근 검증을 제공한다.
public class QuestionDomainSupport {
    private static final int MIN_SELECTION_COUNT = 1;
    private static final int MAX_SELECTION_COUNT = 5;
    private static final int DEFAULT_CHAR_LIMIT = 1000;
    public static final int MAX_CHAR_LIMIT = 5000;

    private final MockApplyRepository mockApplyRepository;
    private final QuestionCandidateCatalogService questionCandidateCatalogService;

    public QuestionDomainSupport(
            MockApplyRepository mockApplyRepository,
            QuestionCandidateCatalogService questionCandidateCatalogService
    ) {
        this.mockApplyRepository = mockApplyRepository;
        this.questionCandidateCatalogService = questionCandidateCatalogService;
    }

    public MockApply getOwnedMockApply(User user, Long mockApplyId) {
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

    public void validateSelectionCount(int count) {
        if (count < MIN_SELECTION_COUNT) {
            throw new GeneralException(GeneralErrorCode.INVALID_PARAMETER, "선택 문항은 1개 이상이어야 합니다.");
        }
        if (count > MAX_SELECTION_COUNT) {
            throw new GeneralException(GeneralErrorCode.INVALID_PARAMETER, "문항은 최대 5개까지 선택할 수 있습니다.");
        }
    }

    public int resolveCharLimit(Integer charLimit) {
        if (charLimit == null) {
            return DEFAULT_CHAR_LIMIT;
        }
        if (charLimit > MAX_CHAR_LIMIT) {
            throw new GeneralException(
                    GeneralErrorCode.INVALID_PARAMETER,
                    "글자수 제한은 최대 " + MAX_CHAR_LIMIT + "자까지 설정할 수 있습니다."
            );
        }
        return charLimit;
    }

    public String normalizeAnswer(String answer) {
        if (StringUtils.hasText(answer)) {
            return answer.trim();
        }
        return "";
    }

    public QuestionResponse toQuestionResponse(Question question) {
        return QuestionResponse.from(question, questionCandidateCatalogService.isCustomQuestion(question.getContent()));
    }
}
