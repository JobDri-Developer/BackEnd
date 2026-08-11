package com.jobdri.jobdri_api.domain.analysis.service.question;

import com.jobdri.jobdri_api.domain.analysis.dto.response.QuestionCandidateResponse;
import com.jobdri.jobdri_api.global.apiPayload.code.GeneralErrorCode;
import com.jobdri.jobdri_api.global.apiPayload.exception.GeneralException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
// 기본 문항 후보와 후보 판별 규칙을 제공한다.
public class QuestionCandidateCatalogService {
    private static final List<QuestionCandidate> DEFAULT_CANDIDATES = List.of(
            new QuestionCandidate(1L, "지원 동기와 입사 후 목표를 작성해주세요.", 700),
            new QuestionCandidate(2L, "지원 직무와 관련된 경험 또는 역량을 구체적으로 작성해주세요.", 1000),
            new QuestionCandidate(3L, "문제를 해결했던 경험과 그 과정에서의 역할을 작성해주세요.", 1000),
            new QuestionCandidate(4L, "협업 과정에서 갈등을 해결했던 경험을 작성해주세요.", 800),
            new QuestionCandidate(5L, "가장 성취감을 느꼈던 프로젝트와 성과를 작성해주세요.", 1000)
    );
    private static final Set<String> DEFAULT_CANDIDATE_CONTENTS = DEFAULT_CANDIDATES.stream()
            .map(QuestionCandidate::content)
            .collect(Collectors.toUnmodifiableSet());

    public List<QuestionCandidateResponse> getDefaultCandidateResponses(Set<String> selectedContents) {
        return DEFAULT_CANDIDATES.stream()
                .map(candidate -> new QuestionCandidateResponse(
                        candidate.id(),
                        candidate.content(),
                        candidate.charLimit(),
                        selectedContents.contains(candidate.content()),
                        false
                ))
                .toList();
    }

    public boolean isCustomQuestion(String content) {
        return !DEFAULT_CANDIDATE_CONTENTS.contains(content);
    }

    public void validateCustomCandidate(String content) {
        if (!isCustomQuestion(content)) {
            throw new GeneralException(GeneralErrorCode.INVALID_PARAMETER, "이미 기본 후보에 존재하는 문항입니다.");
        }
    }

    private record QuestionCandidate(Long id, String content, int charLimit) {
    }
}
