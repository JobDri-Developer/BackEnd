package com.jobdri.jobdri_api.domain.jobposting.service;

import com.jobdri.jobdri_api.domain.jobposting.entity.JobPosting;
import com.jobdri.jobdri_api.global.cohere.CohereEmbeddingClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class JobPostingEmbeddingService {

    private final JobPostingEmbeddingTextBuilder textBuilder;
    private final CohereEmbeddingClient cohereEmbeddingClient;

    public float[] embed(JobPosting jobPosting) {
        List<float[]> embeddings = embedAll(List.of(jobPosting));
        return embeddings.getFirst();
    }

    public List<float[]> embedAll(List<JobPosting> jobPostings) {
        List<String> texts = jobPostings.stream()
                .map(textBuilder::build)
                .toList();
        return cohereEmbeddingClient.embedDocuments(texts);
    }
}
