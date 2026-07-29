package com.jobdri.jobdri_api.domain.analysis.service.retrieval;

import com.jobdri.jobdri_api.domain.analysis.dto.worker.SimilarJobPostingContext;
import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingSimilarityResult;
import com.jobdri.jobdri_api.domain.jobposting.entity.JobPosting;
import com.jobdri.jobdri_api.domain.jobposting.repository.JobPostingRepository;
import com.jobdri.jobdri_api.domain.jobposting.service.JobPostingRetrievalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class JobPostingRagContextAssembler {

    static final int MAX_SIMILAR_JOB_POSTINGS = 3;
    static final int MAX_CONTEXT_FIELD_LENGTH = 1_200;

    private final JobPostingRetrievalService jobPostingRetrievalService;
    private final JobPostingRepository jobPostingRepository;

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public List<SimilarJobPostingContext> assemble(Long jobPostingId) {
        try {
            List<JobPostingSimilarityResult> results = jobPostingRetrievalService
                    .findSimilarJobPostings(jobPostingId, MAX_SIMILAR_JOB_POSTINGS)
                    .stream()
                    .limit(MAX_SIMILAR_JOB_POSTINGS)
                    .toList();
            if (results.isEmpty()) {
                return List.of();
            }

            Map<Long, JobPosting> postingsById = jobPostingRepository.findAllById(
                            results.stream().map(JobPostingSimilarityResult::jobPostingId).toList()
                    ).stream()
                    .collect(Collectors.toMap(JobPosting::getId, Function.identity()));

            return java.util.stream.IntStream.range(0, results.size())
                    .mapToObj(index -> toContext(results.get(index), postingsById, index + 1))
                    .filter(java.util.Objects::nonNull)
                    .toList();
        } catch (RuntimeException exception) {
            log.warn(
                    "유사 채용 공고 RAG context 조회에 실패해 빈 목록으로 분석을 계속합니다. jobPostingId={}, errorType={}",
                    jobPostingId,
                    exception.getClass().getSimpleName()
            );
            log.debug("similar job posting RAG context retrieval exception", exception);
            return List.of();
        }
    }

    private SimilarJobPostingContext toContext(
            JobPostingSimilarityResult result,
            Map<Long, JobPosting> postingsById,
            int rank
    ) {
        JobPosting posting = postingsById.get(result.jobPostingId());
        if (posting == null) {
            log.warn(
                    "유사 채용 공고 상세 정보를 찾지 못해 context에서 제외합니다. jobPostingId={}, similarityRank={}",
                    result.jobPostingId(),
                    rank
            );
            return null;
        }
        return new SimilarJobPostingContext(
                result.jobPostingId(),
                normalize(result.companyName()),
                normalize(result.postingName()),
                normalize(result.jobTitle()),
                truncate(posting.getTask()),
                truncate(posting.getRequirement()),
                truncate(posting.getPreferred()),
                rank,
                result.similarityScore()
        );
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }

    private String truncate(String value) {
        String normalized = normalize(value);
        if (normalized.length() <= MAX_CONTEXT_FIELD_LENGTH) {
            return normalized;
        }

        int boundary = Math.max(
                normalized.lastIndexOf('\n', MAX_CONTEXT_FIELD_LENGTH),
                normalized.lastIndexOf(' ', MAX_CONTEXT_FIELD_LENGTH)
        );
        int end = boundary >= MAX_CONTEXT_FIELD_LENGTH / 2 ? boundary : MAX_CONTEXT_FIELD_LENGTH;
        return normalized.substring(0, end).stripTrailing();
    }
}
