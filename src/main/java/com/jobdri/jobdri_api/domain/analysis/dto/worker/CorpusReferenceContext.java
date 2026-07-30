package com.jobdri.jobdri_api.domain.analysis.dto.worker;

import com.jobdri.jobdri_api.domain.corpus.service.CorpusRetrievalService.RetrievalContext;
import com.jobdri.jobdri_api.domain.corpus.service.CorpusRetrievalService.RetrievedJobPostingReference;
import com.jobdri.jobdri_api.domain.corpus.service.CorpusRetrievalService.RetrievedQuestionReference;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

public record CorpusReferenceContext(
        Long corpusId,
        String category,
        String title,
        String content,
        int rank
) {
    public static List<CorpusReferenceContext> from(RetrievalContext retrievalContext) {
        if (retrievalContext == null) {
            return List.of();
        }

        List<CorpusReferenceContext> references = new ArrayList<>();
        List<RetrievedJobPostingReference> jobPostings = retrievalContext.jobPostingReferences() == null
                ? List.of()
                : retrievalContext.jobPostingReferences();
        for (int index = 0; index < jobPostings.size(); index++) {
            references.add(fromJobPosting(jobPostings.get(index), index + 1));
        }

        List<RetrievedQuestionReference> questions = retrievalContext.questionReferences() == null
                ? List.of()
                : retrievalContext.questionReferences();
        for (int index = 0; index < questions.size(); index++) {
            references.add(fromQuestion(questions.get(index), index + 1));
        }
        return List.copyOf(references);
    }

    private static CorpusReferenceContext fromJobPosting(RetrievedJobPostingReference reference, int rank) {
        return new CorpusReferenceContext(
                reference.corpusId(),
                "JOB_POSTING",
                joinTitle(reference.companyName(), reference.roleName()),
                joinContent(
                        line("주요 업무", reference.responsibilities()),
                        line("자격 요건", reference.requirements()),
                        line("우대 사항", reference.preferred())
                ),
                rank
        );
    }

    private static CorpusReferenceContext fromQuestion(RetrievedQuestionReference reference, int rank) {
        return new CorpusReferenceContext(
                reference.corpusId(),
                "QUESTION",
                joinTitle(reference.companyName(), reference.roleName(), reference.questionType()),
                joinContent(
                        line("문항", reference.questionText()),
                        reference.charLimit() == null ? "" : "글자 수 제한: " + reference.charLimit()
                ),
                rank
        );
    }

    private static String joinTitle(String... values) {
        return java.util.Arrays.stream(values)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .reduce((left, right) -> left + " - " + right)
                .orElse("");
    }

    private static String joinContent(String... values) {
        return java.util.Arrays.stream(values)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
    }

    private static String line(String label, String value) {
        return StringUtils.hasText(value) ? label + ": " + value.trim() : "";
    }
}
