package com.jobdri.jobdri_api.domain.corpus.service;

public record CorpusImportResult(
        int createdCompanies,
        int createdJobPostings,
        int updatedJobPostings,
        int createdQuestions,
        int updatedQuestions,
        int matchedClassifications,
        int unmatchedClassifications
) {
}
