package com.jobdri.jobdri_api.domain.analysis.service;

import com.jobdri.jobdri_api.domain.analysis.dto.response.AnalysisRetrievalPreviewResponse;
import com.jobdri.jobdri_api.domain.analysis.entity.Question;
import com.jobdri.jobdri_api.domain.analysis.repository.QuestionRepository;
import com.jobdri.jobdri_api.domain.analysis.service.AnalysisReferenceRetrievalService.AnalysisReferenceContext;
import com.jobdri.jobdri_api.domain.jobposting.entity.JobPosting;
import com.jobdri.jobdri_api.domain.mockapply.entity.MockApply;
import com.jobdri.jobdri_api.domain.mockapply.repository.MockApplyRepository;
import com.jobdri.jobdri_api.global.apiPayload.code.GeneralErrorCode;
import com.jobdri.jobdri_api.global.apiPayload.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AnalysisAdminDebugService {

    private final MockApplyRepository mockApplyRepository;
    private final QuestionRepository questionRepository;
    private final AnalysisReferenceRetrievalService analysisReferenceRetrievalService;

    public AnalysisRetrievalPreviewResponse previewRetrieval(Long mockApplyId) {
        MockApply mockApply = mockApplyRepository.findByIdWithJobPosting(mockApplyId)
                .orElseThrow(() -> new GeneralException(
                        GeneralErrorCode.MOCK_APPLY_NOT_FOUND,
                        "해당 모의 서류 지원을 찾을 수 없습니다. mockApplyId=" + mockApplyId
                ));
        List<Question> questions = questionRepository.findAllByMockApplyIdOrderByIdAsc(mockApplyId);
        JobPosting jobPosting = mockApply.getJobPosting();

        AnalysisReferenceContext referenceContext =
                analysisReferenceRetrievalService.retrieve(jobPosting, questions);

        return new AnalysisRetrievalPreviewResponse(
                mockApply.getId(),
                new AnalysisRetrievalPreviewResponse.JobPostingSnapshot(
                        jobPosting.getId(),
                        jobPosting.getCompany().getName(),
                        jobPosting.getDetailClassification().getDetailName(),
                        jobPosting.getTask(),
                        jobPosting.getRequirement(),
                        jobPosting.getPreferred()
                ),
                questions.stream()
                        .map(question -> new AnalysisRetrievalPreviewResponse.QuestionSnapshot(
                                question.getId(),
                                question.getContent(),
                                question.getAnswer()
                        ))
                        .toList(),
                referenceContext.jobPostingReferences().stream()
                        .map(reference -> new AnalysisRetrievalPreviewResponse.JobPostingReference(
                                reference.corpusId(),
                                reference.companyName(),
                                reference.roleName(),
                                reference.responsibilities(),
                                reference.requirements(),
                                reference.preferred(),
                                reference.distance()
                        ))
                        .toList(),
                referenceContext.questionReferences().stream()
                        .map(reference -> new AnalysisRetrievalPreviewResponse.QuestionReference(
                                reference.corpusId(),
                                reference.companyName(),
                                reference.roleName(),
                                reference.questionType(),
                                reference.charLimit(),
                                reference.questionText(),
                                reference.distance()
                        ))
                        .toList()
        );
    }
}
