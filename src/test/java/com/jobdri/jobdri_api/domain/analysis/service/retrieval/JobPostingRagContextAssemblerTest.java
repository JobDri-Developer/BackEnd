package com.jobdri.jobdri_api.domain.analysis.service.retrieval;

import com.jobdri.jobdri_api.domain.analysis.dto.worker.SimilarJobPostingContext;
import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingSimilarityResult;
import com.jobdri.jobdri_api.domain.jobposting.entity.JobPosting;
import com.jobdri.jobdri_api.domain.jobposting.repository.JobPostingRepository;
import com.jobdri.jobdri_api.domain.jobposting.service.JobPostingRetrievalService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JobPostingRagContextAssemblerTest {

    private final JobPostingRetrievalService retrievalService = mock(JobPostingRetrievalService.class);
    private final JobPostingRepository jobPostingRepository = mock(JobPostingRepository.class);
    private final JobPostingRagContextAssembler assembler = new JobPostingRagContextAssembler(
            retrievalService,
            jobPostingRepository
    );

    @Test
    @DisplayName("검색 순서를 유지하며 Top3 유사 공고의 분석 필드를 조립한다")
    void assembleTop3InSimilarityOrder() {
        JobPosting postingA = posting(21L, "업무 A", "자격 A", "우대 A");
        JobPosting postingB = posting(22L, "업무 B", "자격 B", "우대 B");
        JobPosting postingC = posting(23L, "업무 C", "자격 C", "우대 C");
        when(retrievalService.findSimilarJobPostings(10L, 3)).thenReturn(List.of(
                result(21L, "회사 A", "공고 A", "백엔드 A", 0.91),
                result(22L, "회사 B", "공고 B", "백엔드 B", 0.82),
                result(23L, "회사 C", "공고 C", "백엔드 C", 0.73),
                result(24L, "회사 D", "공고 D", "백엔드 D", 0.64)
        ));
        when(jobPostingRepository.findAllById(anyList())).thenReturn(List.of(
                postingC,
                postingA,
                postingB
        ));

        List<SimilarJobPostingContext> contexts = assembler.assemble(10L);

        assertThat(contexts).extracting(SimilarJobPostingContext::jobPostingId)
                .containsExactly(21L, 22L, 23L);
        assertThat(contexts).extracting(SimilarJobPostingContext::similarityRank)
                .containsExactly(1, 2, 3);
        assertThat(contexts.getFirst().task()).isEqualTo("업무 A");
        assertThat(contexts.getFirst().requirements()).isEqualTo("자격 A");
        assertThat(contexts.getFirst().preferredQualifications()).isEqualTo("우대 A");

        verify(jobPostingRepository).findAllById(List.of(21L, 22L, 23L));
    }

    @Test
    @DisplayName("빈 필드를 정규화하고 긴 필드를 제한한다")
    void normalizeBlankAndTruncateLongFields() {
        String longTask = "업무 ".repeat(500);
        JobPosting posting = posting(21L, longTask, "  ", null);
        when(retrievalService.findSimilarJobPostings(10L, 3))
                .thenReturn(List.of(result(21L, " 회사 ", " 공고 ", " 직무 ", 0.9)));
        when(jobPostingRepository.findAllById(anyList()))
                .thenReturn(List.of(posting));

        SimilarJobPostingContext context = assembler.assemble(10L).getFirst();

        assertThat(context.companyName()).isEqualTo("회사");
        assertThat(context.task().length())
                .isLessThanOrEqualTo(JobPostingRagContextAssembler.MAX_CONTEXT_FIELD_LENGTH);
        assertThat(context.requirements()).isEmpty();
        assertThat(context.preferredQualifications()).isEmpty();
    }

    @Test
    @DisplayName("Retrieval 실패 시 빈 context로 분석을 계속한다")
    void failOpenWhenRetrievalFails() {
        when(retrievalService.findSimilarJobPostings(10L, 3))
                .thenThrow(new IllegalStateException("pgvector unavailable"));

        assertThat(assembler.assemble(10L)).isEmpty();
    }

    @Test
    @DisplayName("상세 조회에서 누락된 공고만 제외하고 원래 검색 순위를 유지한다")
    void excludesMissingPostingDetailsWithoutCompactingRanks() {
        JobPosting postingA = posting(21L, "업무 A", "자격 A", "우대 A");
        JobPosting postingC = posting(23L, "업무 C", "자격 C", "우대 C");
        when(retrievalService.findSimilarJobPostings(10L, 3)).thenReturn(List.of(
                result(21L, "회사 A", "공고 A", "백엔드 A", 0.91),
                result(22L, "회사 B", "공고 B", "백엔드 B", 0.82),
                result(23L, "회사 C", "공고 C", "백엔드 C", 0.73)
        ));
        when(jobPostingRepository.findAllById(anyList())).thenReturn(List.of(postingC, postingA));

        List<SimilarJobPostingContext> contexts = assembler.assemble(10L);

        assertThat(contexts).extracting(SimilarJobPostingContext::jobPostingId)
                .containsExactly(21L, 23L);
        assertThat(contexts).extracting(SimilarJobPostingContext::similarityRank)
                .containsExactly(1, 3);
    }

    @Test
    @DisplayName("유사 공고 검색 결과가 비어 있으면 상세 조회를 호출하지 않는다")
    void skipsDetailLookupWhenRetrievalIsEmpty() {
        when(retrievalService.findSimilarJobPostings(10L, 3)).thenReturn(List.of());

        assertThat(assembler.assemble(10L)).isEmpty();

        verify(jobPostingRepository, never()).findAllById(anyList());
    }

    private JobPostingSimilarityResult result(
            Long id,
            String companyName,
            String postingName,
            String jobTitle,
            double score
    ) {
        return new JobPostingSimilarityResult(id, postingName, companyName, jobTitle, score);
    }

    private JobPosting posting(Long id, String task, String requirement, String preferred) {
        JobPosting posting = mock(JobPosting.class);
        when(posting.getId()).thenReturn(id);
        when(posting.getTask()).thenReturn(task);
        when(posting.getRequirement()).thenReturn(requirement);
        when(posting.getPreferred()).thenReturn(preferred);
        return posting;
    }
}
