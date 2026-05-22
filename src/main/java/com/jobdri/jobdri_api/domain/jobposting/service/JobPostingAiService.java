package com.jobdri.jobdri_api.domain.jobposting.service;

import com.jobdri.jobdri_api.domain.classification.entity.DetailClassification;
import com.jobdri.jobdri_api.domain.classification.repository.DetailClassificationRepository;
import com.jobdri.jobdri_api.domain.company.entity.Company;
import com.jobdri.jobdri_api.domain.jobposting.dto.request.JobPostingExtractMultipartRequest;
import com.jobdri.jobdri_api.domain.jobposting.dto.request.JobPostingGenerateRequest;
import com.jobdri.jobdri_api.domain.jobposting.dto.request.JobPostingMockGenerateRequest;
import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingClassificationCandidateResponse;
import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingClassificationResultResponse;
import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingExtractResponse;
import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingGenerateResponse;
import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingMockGenerateResponse;
import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingMockQuestionResponse;
import com.jobdri.jobdri_api.domain.jobposting.entity.JobPosting;
import com.jobdri.jobdri_api.domain.jobposting.repository.JobPostingRepository;
import com.jobdri.jobdri_api.global.apiPayload.code.GeneralErrorCode;
import com.jobdri.jobdri_api.global.apiPayload.exception.GeneralException;
import com.openai.client.OpenAIClient;
import com.openai.models.responses.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.Base64;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class JobPostingAiService {

    private static final int MAX_REFERENCE_FIELD_LENGTH = 400;

    private final OpenAIClient openAIClient;
    private final DetailClassificationRepository detailClassificationRepository;
    private final JobPostingRepository jobPostingRepository;

    @Value("${openai.model.job-posting-extractor:gpt-4o-mini}")
    private String extractionModel;

    private static final Set<String> SUPPORTED_IMAGE_TYPES = Set.of(
            "image/png",
            "image/jpeg",
            "image/jpg",
            "image/webp",
            "image/gif"
    );

    public JobPostingExtractResponse extractJobPosting(String rawText) {
        return extractJobPosting(rawText, null, null);
    }

    public JobPostingGenerateResponse generateJobPosting(JobPostingGenerateRequest request) {
        DetailClassification detailClassification = detailClassificationRepository.findById(request.detailClassificationId())
                .orElseThrow(() -> new GeneralException(
                        GeneralErrorCode.CLASSIFICATION_NOT_FOUND,
                        "해당 소분류를 찾을 수 없습니다. detailClassificationId=" + request.detailClassificationId()
                ));

        var params = ResponseCreateParams.builder()
                .model(extractionModel)
                .input(buildGenerationPrompt(request, detailClassification))
                .temperature(0.7)
                .text(JobPostingGenerateResponse.class)
                .build();

        try {
            StructuredResponse<JobPostingGenerateResponse> response = openAIClient.responses().create(params);
            JobPostingGenerateResponse generated = extractStructuredContent(response, JobPostingGenerateResponse.class);
            return normalizeGeneratedResponse(generated, request);
        } catch (Exception e) {
            log.error("채용 공고 생성 OpenAI API 호출 오류: {}", e.getMessage(), e);
            return createFallbackGeneratedResponse(request);
        }
    }

    public JobPostingMockGenerateResponse generateMockJobPosting(JobPostingMockGenerateRequest request, Company company) {
        DetailClassification detailClassification = findDetailClassification(request.detailClassificationId());
        validateMiddleClassification(request, detailClassification);

        List<JobPosting> referencePostings = findMockReferencePostings(request, company);

        var params = ResponseCreateParams.builder()
                .model(extractionModel)
                .input(buildMockGenerationPrompt(request, company, detailClassification, referencePostings))
                .temperature(0.7)
                .text(JobPostingMockGenerateResponse.class)
                .build();

        try {
            StructuredResponse<JobPostingMockGenerateResponse> response = openAIClient.responses().create(params);
            JobPostingMockGenerateResponse generated = extractStructuredContent(
                    response,
                    JobPostingMockGenerateResponse.class
            );
            return normalizeMockGeneratedResponse(generated, company, detailClassification);
        } catch (Exception e) {
            log.error("모의 공고 생성 OpenAI API 호출 오류: {}", e.getMessage(), e);
            return createFallbackMockGeneratedResponse(company, detailClassification, referencePostings);
        }
    }

    public JobPostingMockQuestionResponse generateMockRecommendedQuestions(JobPostingMockGenerateRequest request) {
        DetailClassification detailClassification = findDetailClassification(request.detailClassificationId());
        validateMiddleClassification(request, detailClassification);

        List<JobPosting> referencePostings = findMockReferencePostings(request, null);

        var params = ResponseCreateParams.builder()
                .model(extractionModel)
                .input(buildMockQuestionPrompt(request, detailClassification, referencePostings))
                .temperature(0.4)
                .text(JobPostingMockQuestionResponse.class)
                .build();

        try {
            StructuredResponse<JobPostingMockQuestionResponse> response = openAIClient.responses().create(params);
            JobPostingMockQuestionResponse generated = extractStructuredContent(
                    response,
                    JobPostingMockQuestionResponse.class
            );
            return normalizeMockQuestionResponse(generated, detailClassification);
        } catch (Exception e) {
            log.error("모의 공고 추천 질문 생성 OpenAI API 호출 오류: {}", e.getMessage(), e);
            return createFallbackMockQuestionResponse(detailClassification);
        }
    }

    public JobPostingClassificationResultResponse classifyDetailClassification(
            JobPostingExtractResponse extracted,
            List<JobPostingClassificationCandidateResponse> candidates
    ) {
        var params = ResponseCreateParams.builder()
                .model(extractionModel)
                .input(buildClassificationPrompt(extracted, candidates))
                .temperature(0.1)
                .text(JobPostingClassificationResultResponse.class)
                .build();

        try {
            StructuredResponse<JobPostingClassificationResultResponse> response =
                    openAIClient.responses().create(params);
            JobPostingClassificationResultResponse classification =
                    extractStructuredContent(response, JobPostingClassificationResultResponse.class);
            return normalizeClassificationResponse(classification, candidates);
        } catch (Exception e) {
            log.error("채용 공고 소분류 분류 OpenAI API 호출 오류: {}", e.getMessage(), e);
            return fallbackClassification(candidates);
        }
    }

    public JobPostingExtractResponse extractJobPosting(JobPostingExtractMultipartRequest request) {
        return extractJobPosting(request.rawText(), request.image());
    }

    public JobPostingExtractResponse extractJobPosting(String rawText, byte[] imageBytes, String imageContentType) {
        validateInput(rawText, imageBytes);

        List<ResponseInputContent> contents = new ArrayList<>();
        contents.add(ResponseInputContent.ofInputText(
                com.openai.models.responses.ResponseInputText.builder()
                        .text(buildPrompt(rawText, imageBytes != null && imageBytes.length > 0))
                        .build()
        ));

        if (imageBytes != null && imageBytes.length > 0) {
            contents.add(ResponseInputContent.ofInputImage(buildImageContent(imageBytes, imageContentType)));
        }

        var params = ResponseCreateParams.builder()
                .model(extractionModel)
                .inputOfResponse(List.of(
                        ResponseInputItem.ofMessage(
                                ResponseInputItem.Message.builder()
                                        .role(ResponseInputItem.Message.Role.USER)
                                        .content(contents)
                                        .build()
                        )
                ))
                .temperature(0.1)
                .text(JobPostingExtractResponse.class)
                .build();

        try {
            StructuredResponse<JobPostingExtractResponse> response = openAIClient.responses().create(params);
            JobPostingExtractResponse extracted = extractStructuredContent(response, JobPostingExtractResponse.class);
            return normalizeResponse(extracted, rawText);
        } catch (Exception e) {
            log.error("채용 공고 추출 OpenAI API 호출 오류: {}", e.getMessage(), e);
            return createFallbackResponse(rawText);
        }
    }

    public JobPostingExtractResponse extractJobPosting(String rawText, MultipartFile imageFile) {
        return extractJobPosting(
                rawText,
                imageFile == null || imageFile.isEmpty() ? null : readImageBytes(imageFile),
                imageFile == null || imageFile.isEmpty() ? null : imageFile.getContentType()
        );
    }

    private String buildPrompt(String rawText, boolean hasImage) {
        String normalizedRawText = rawText == null ? "" : rawText;

        return """
                이 %s는 채용 공고입니다.
                회사명, 직무명, 주요 업무, 자격 요건, 우대 사항을 추출해주세요.

                반드시 아래 JSON 형식으로만 응답해주세요.
                설명 문장, 마크다운, 코드블럭은 포함하지 마세요.

                {
                  "companyName": "string",
                  "jobTitle": "string",
                  "task": "string",
                  "requirements": "string",
                  "preferredQualifications": "string",
                  "rawText": "string",
                  "confidence": number
                }

                규칙:
                1. 이미지가 있으면 이미지 안의 채용 공고 문구를 읽어 rawText에 정리해주세요.
                2. 텍스트가 있으면 rawText에는 입력 원문을 최대한 그대로 넣어주세요.
                3. 이미지와 텍스트가 둘 다 있으면 둘을 함께 참고해서 가장 정확한 값으로 채워주세요.
                4. 정보가 없거나 확실하지 않으면 해당 필드는 빈 문자열로 두세요.
                5. confidence는 추출 결과 전체에 대한 신뢰도를 0~1 사이 실수로 반환하세요.
                6. JSON 외의 다른 텍스트는 절대 출력하지 마세요.

                [채용 공고 텍스트]
                %s
                """.formatted(hasImage ? "이미지 또는 텍스트" : "텍스트", normalizedRawText);
    }

    private String buildClassificationPrompt(
            JobPostingExtractResponse extracted,
            List<JobPostingClassificationCandidateResponse> candidates
    ) {
        String candidateText = candidates.stream()
                .map(candidate -> String.format(
                        "- id=%d | 대분류=%s | 중분류=%s | 소분류=%s | score=%.4f",
                        candidate.getDetailClassificationId(),
                        candidate.getBigClassificationName(),
                        candidate.getMiddleClassificationName(),
                        candidate.getDetailClassificationName(),
                        candidate.getScore()
                ))
                .collect(Collectors.joining("\n"));

        return """
                다음 채용 공고 정보에 가장 적합한 소분류를 아래 후보 중 하나만 골라주세요.
                반드시 후보에 있는 id만 선택해야 하며, 새 값을 만들면 안 됩니다.
                출력은 반드시 JSON 객체 하나만 반환하세요.

                {
                  "detailClassificationId": number,
                  "detailClassificationName": "string",
                  "middleClassificationName": "string",
                  "bigClassificationName": "string",
                  "reason": "string",
                  "confidence": number
                }

                [추출된 회사명]
                %s

                [추출된 직무명]
                %s

                [추출된 주요 업무]
                %s

                [추출된 자격 요건]
                %s

                [추출된 우대 사항]
                %s

                [추출 원문]
                %s

                [후보 목록]
                %s
                """.formatted(
                defaultString(extracted.companyName()),
                defaultString(extracted.jobTitle()),
                defaultString(extracted.task()),
                defaultString(extracted.requirements()),
                defaultString(extracted.preferredQualifications()),
                defaultString(extracted.rawText()),
                candidateText
        );
    }

    private ResponseInputImage buildImageContent(MultipartFile imageFile) {
        return buildImageContent(readImageBytes(imageFile), imageFile.getContentType());
    }

    private ResponseInputImage buildImageContent(byte[] imageBytes, String imageContentType) {
        validateImage(imageContentType);
        String base64 = Base64.getEncoder().encodeToString(imageBytes);
        String dataUrl = "data:%s;base64,%s".formatted(imageContentType, base64);

        return ResponseInputImage.builder()
                .imageUrl(dataUrl)
                .detail(ResponseInputImage.Detail.HIGH)
                .build();
    }

    private <T> T extractStructuredContent(StructuredResponse<T> response, Class<T> responseType) {
        return response.output().stream()
                .filter(item -> item.message().isPresent())
                .flatMap(item -> item.asMessage().content().stream())
                .filter(content -> content.outputText().isPresent())
                .map(StructuredResponseOutputMessage.Content::asOutputText)
                .findFirst()
                .orElseThrow(() -> new GeneralException(
                        GeneralErrorCode.INTERNAL_SERVER_ERROR,
                        "AI 응답에서 " + responseType.getSimpleName() + " 결과를 찾을 수 없습니다."
                ));
    }

    private void validateInput(String rawText, MultipartFile imageFile) {
        boolean hasRawText = rawText != null && !rawText.isBlank();
        boolean hasImage = imageFile != null && !imageFile.isEmpty();

        if (!hasRawText && !hasImage) {
            throw new GeneralException(
                    GeneralErrorCode.INVALID_PARAMETER,
                    "rawText 또는 image 중 하나는 반드시 포함되어야 합니다."
            );
        }
    }

    private void validateInput(String rawText, byte[] imageBytes) {
        boolean hasRawText = rawText != null && !rawText.isBlank();
        boolean hasImage = imageBytes != null && imageBytes.length > 0;

        if (!hasRawText && !hasImage) {
            throw new GeneralException(
                    GeneralErrorCode.INVALID_PARAMETER,
                    "rawText 또는 image 중 하나는 반드시 포함되어야 합니다."
            );
        }
    }

    private void validateImage(MultipartFile imageFile) {
        validateImage(imageFile.getContentType());
    }

    private void validateImage(String contentType) {
        if (contentType == null || !SUPPORTED_IMAGE_TYPES.contains(contentType.toLowerCase())) {
            throw new GeneralException(
                    GeneralErrorCode.INVALID_PARAMETER,
                    "지원하는 이미지 형식은 png, jpg, jpeg, webp, gif 입니다."
            );
        }
    }

    private byte[] readImageBytes(MultipartFile imageFile) {
        validateImage(imageFile);

        try {
            return imageFile.getBytes();
        } catch (IOException e) {
            throw new GeneralException(GeneralErrorCode.INVALID_PARAMETER, "이미지 파일을 읽을 수 없습니다.");
        }
    }

    private JobPostingExtractResponse normalizeResponse(JobPostingExtractResponse response, String rawText) {
        if (response == null) {
            throw new GeneralException(
                    GeneralErrorCode.INTERNAL_SERVER_ERROR,
                    "AI 응답이 비어 있습니다."
            );
        }

        double confidence = response.confidence();
        if (Double.isNaN(confidence) || Double.isInfinite(confidence) || confidence < 0.0) {
            confidence = 0.0;
        } else if (confidence > 1.0) {
            confidence = 1.0;
        }

        return new JobPostingExtractResponse(
                defaultString(response.companyName()),
                defaultString(response.jobTitle()),
                defaultString(response.task()),
                defaultString(response.requirements()),
                defaultString(response.preferredQualifications()),
                response.rawText() == null || response.rawText().isBlank() ? defaultString(rawText) : response.rawText(),
                confidence
        );
    }

    private JobPostingExtractResponse createFallbackResponse(String rawText) {
        return new JobPostingExtractResponse(
                "",
                "",
                "",
                "",
                "",
                rawText == null ? "" : rawText,
                0.0
        );
    }

    private String buildGenerationPrompt(JobPostingGenerateRequest request, DetailClassification detailClassification) {
        String companySize = request.companySize() == null ? "미지정" : request.companySize().name();

        return """
                아래 정보를 바탕으로 한국어 채용 공고 초안을 작성해주세요.
                출력은 반드시 JSON 객체 하나만 반환하세요.
                설명 문장, 마크다운, 코드블럭은 포함하지 마세요.

                {
                  "companyName": "string",
                  "jobTitle": "string",
                  "task": "string",
                  "requirements": "string",
                  "preferredQualifications": "string",
                  "summary": "string"
                }

                작성 규칙:
                1. task는 문장형 또는 불릿을 줄바꿈으로 구분한 자연스러운 본문으로 작성하세요.
                2. requirements는 필수 자격 요건만 정리하세요.
                3. preferredQualifications는 우대 사항만 정리하세요.
                4. summary는 2~3문장으로 포지션 소개를 작성하세요.
                5. 과장되거나 허위인 내용을 만들지 말고, 입력 정보 범위 안에서 실무적인 표현으로 작성하세요.

                [회사명]
                %s

                [회사 규모]
                %s

                [소분류 직무]
                %s

                [직무명 힌트]
                %s

                [채용 배경 또는 포지션 소개]
                %s

                [기술 스택]
                %s

                [주요 업무 초안]
                %s

                [자격 요건 초안]
                %s

                [우대 사항 초안]
                %s

                [원하는 톤]
                %s
                """.formatted(
                defaultString(request.companyName()),
                companySize,
                detailClassification.getDetailName(),
                defaultString(request.jobTitleHint()),
                defaultString(request.hiringSummary()),
                defaultString(request.techStack()),
                defaultString(request.mainResponsibilities()),
                defaultString(request.requirements()),
                defaultString(request.preferredQualifications()),
                defaultString(request.tone())
        );
    }

    private String buildMockGenerationPrompt(
            JobPostingMockGenerateRequest request,
            Company company,
            DetailClassification detailClassification,
            List<JobPosting> referencePostings
    ) {
        String middleName = detailClassification.getMiddleClassification().getMiddleName();
        String detailName = detailClassification.getDetailName();
        String referenceText = buildReferencePostingText(referencePostings);

        return """
                아래 직무 분류를 바탕으로 한국어 모의 채용 공고 초안을 작성해주세요.
                companyName은 반드시 아래 제공된 회사명으로 작성하세요.
                실제 DB 저장용이 아니라 프론트에서 확인할 초안이므로, 출력은 반드시 JSON 객체 하나만 반환하세요.
                설명 문장, 마크다운, 코드블럭은 포함하지 마세요.

                {
                  "companyName": "string",
                  "jobTitle": "string",
                  "task": "string",
                  "requirement": "string",
                  "preferred": "string",
                  "summary": "string"
                }

                작성 규칙:
                1. jobTitle은 소분류 직무명을 기반으로 자연스러운 직무명으로 작성하세요.
                2. task는 신입/주니어가 수행할 수 있는 주요 업무를 중심으로 작성하세요.
                3. requirement는 필수 자격 요건만 정리하세요.
                4. preferred는 우대 사항만 정리하세요.
                5. summary는 2~3문장으로 포지션 소개를 작성하세요.
                6. 참고 공고가 있으면 표현과 직무 맥락만 참고하고, 특정 회사 고유 정보는 만들지 마세요.
                7. 참고 공고가 없으면 중분류/소분류명만 기반으로 일반적인 신입/주니어용 공고를 작성하세요.

                [회사명]
                %s

                [중분류 ID]
                %d

                [중분류 직무]
                %s

                [소분류 ID]
                %d

                [소분류 직무]
                %s

                [같은 소분류의 기존 공고 참고 자료]
                %s
                """.formatted(
                company.getName(),
                request.middleClassificationId(),
                middleName,
                request.detailClassificationId(),
                detailName,
                referenceText
        );
    }

    private String buildMockQuestionPrompt(
            JobPostingMockGenerateRequest request,
            DetailClassification detailClassification,
            List<JobPosting> referencePostings
    ) {
        String middleName = detailClassification.getMiddleClassification().getMiddleName();
        String detailName = detailClassification.getDetailName();
        String referenceText = buildReferencePostingText(referencePostings);

        return """
                아래 직무 분류와 참고 공고를 바탕으로, 모의 지원자에게 제시할 추천 질문 5개를 작성해주세요.
                출력은 반드시 JSON 객체 하나만 반환하세요.
                설명 문장, 마크다운, 코드블럭은 포함하지 마세요.

                {
                  "recommendedQuestions": [
                    "string"
                  ]
                }

                작성 규칙:
                1. 질문은 자기소개서 또는 지원 동기 작성을 돕는 면접/지원서형 질문으로 작성하세요.
                2. 질문은 신입/주니어 지원자 기준으로 너무 과도하게 어렵지 않게 작성하세요.
                3. 질문은 서로 중복되지 않게 작성하세요.
                4. 참고 공고가 있으면 직무 맥락과 자주 요구되는 역량을 반영하세요.
                5. 참고 공고가 없으면 중분류/소분류명만 기반으로 일반적인 직무 질문을 작성하세요.

                [중분류 ID]
                %d

                [중분류 직무]
                %s

                [소분류 ID]
                %d

                [소분류 직무]
                %s

                [같은 소분류의 기존 공고 참고 자료]
                %s
                """.formatted(
                request.middleClassificationId(),
                middleName,
                request.detailClassificationId(),
                detailName,
                referenceText
        );
    }

    private String buildReferencePostingText(List<JobPosting> referencePostings) {
        if (referencePostings == null || referencePostings.isEmpty()) {
            return "참고 가능한 기존 공고가 없습니다.";
        }

        return referencePostings.stream()
                .map(jobPosting -> """
                        - 주요 업무:
                        %s
                        - 자격 요건:
                        %s
                        - 우대 사항:
                        %s
                        """.formatted(
                        truncateForPrompt(jobPosting.getTask()),
                        truncateForPrompt(jobPosting.getRequirement()),
                        truncateForPrompt(jobPosting.getPreferred())
                ))
                .collect(Collectors.joining("\n"));
    }

    private List<JobPosting> findMockReferencePostings(JobPostingMockGenerateRequest request, Company company) {
        return jobPostingRepository.findTop5ReferencePostings(
                company == null ? null : company.getId(),
                request.detailClassificationId()
        );
    }

    private JobPostingGenerateResponse normalizeGeneratedResponse(JobPostingGenerateResponse response, JobPostingGenerateRequest request) {
        if (response == null) {
            throw new GeneralException(
                GeneralErrorCode.INTERNAL_SERVER_ERROR,
                "AI 생성 응답이 비어 있습니다."
            );
        }

        String companyName = response.companyName();
        if (companyName == null || companyName.isBlank()) {
            companyName = request.companyName();
        }

        return new JobPostingGenerateResponse(
                companyName,
                defaultString(response.jobTitle()),
                defaultString(response.task()),
                defaultString(response.requirements()),
                defaultString(response.preferredQualifications()),
                defaultString(response.summary())
        );
    }

    private JobPostingMockGenerateResponse normalizeMockGeneratedResponse(
            JobPostingMockGenerateResponse response,
            Company company,
            DetailClassification detailClassification
    ) {
        if (response == null) {
            throw new GeneralException(
                    GeneralErrorCode.INTERNAL_SERVER_ERROR,
                    "AI 모의 공고 생성 응답이 비어 있습니다."
            );
        }

        String companyName = response.companyName();
        if (companyName == null || companyName.isBlank()) {
            companyName = company.getName();
        }

        String jobTitle = response.jobTitle();
        if (jobTitle == null || jobTitle.isBlank()) {
            jobTitle = detailClassification.getDetailName();
        }

        return new JobPostingMockGenerateResponse(
                companyName,
                jobTitle,
                defaultString(response.task()),
                defaultString(response.requirement()),
                defaultString(response.preferred()),
                defaultString(response.summary()),
                defaultStringList(response.recommendedQuestions())
        );
    }

    private JobPostingMockQuestionResponse normalizeMockQuestionResponse(
            JobPostingMockQuestionResponse response,
            DetailClassification detailClassification
    ) {
        if (response == null) {
            throw new GeneralException(
                    GeneralErrorCode.INTERNAL_SERVER_ERROR,
                    "AI 모의 공고 추천 질문 응답이 비어 있습니다."
            );
        }

        List<String> questions = defaultStringList(response.recommendedQuestions()).stream()
                .map(String::trim)
                .filter(question -> !question.isBlank())
                .distinct()
                .limit(5)
                .toList();

        if (!questions.isEmpty()) {
            return new JobPostingMockQuestionResponse(questions);
        }

        return createFallbackMockQuestionResponse(detailClassification);
    }

    private DetailClassification findDetailClassification(Long detailClassificationId) {
        return detailClassificationRepository.findById(detailClassificationId)
                .orElseThrow(() -> new GeneralException(
                        GeneralErrorCode.CLASSIFICATION_NOT_FOUND,
                        "해당 소분류를 찾을 수 없습니다. detailClassificationId=" + detailClassificationId
                ));
    }

    private void validateMiddleClassification(
            JobPostingMockGenerateRequest request,
            DetailClassification detailClassification
    ) {
        Long actualMiddleClassificationId = detailClassification.getMiddleClassification().getId();
        if (!actualMiddleClassificationId.equals(request.middleClassificationId())) {
            throw new GeneralException(
                    GeneralErrorCode.CLASSIFICATION_NOT_FOUND,
                    "해당 소분류가 중분류에 속하지 않습니다. middleClassificationId="
                            + request.middleClassificationId()
                            + ", detailClassificationId="
                            + request.detailClassificationId()
            );
        }
    }

    private JobPostingClassificationResultResponse normalizeClassificationResponse(
            JobPostingClassificationResultResponse response,
            List<JobPostingClassificationCandidateResponse> candidates
    ) {
        if (response == null) {
            throw new GeneralException(
                    GeneralErrorCode.INTERNAL_SERVER_ERROR,
                    "AI 분류 응답이 비어 있습니다."
            );
        }

        JobPostingClassificationCandidateResponse matched = candidates.stream()
                .filter(candidate -> candidate.getDetailClassificationId().equals(response.detailClassificationId()))
                .findFirst()
                .orElseGet(() -> candidates.getFirst());

        double confidence = response.confidence();
        if (Double.isNaN(confidence) || Double.isInfinite(confidence) || confidence < 0.0) {
            confidence = 0.0;
        } else if (confidence > 1.0) {
            confidence = 1.0;
        }

        return new JobPostingClassificationResultResponse(
                matched.getDetailClassificationId(),
                matched.getDetailClassificationName(),
                matched.getMiddleClassificationName(),
                matched.getBigClassificationName(),
                defaultString(response.reason()),
                confidence
        );
    }

    private JobPostingGenerateResponse createFallbackGeneratedResponse(JobPostingGenerateRequest request) {
        return new JobPostingGenerateResponse(
                request.companyName(),
                defaultString(request.jobTitleHint()),
                defaultString(request.mainResponsibilities()),
                defaultString(request.requirements()),
                defaultString(request.preferredQualifications()),
                defaultString(request.hiringSummary())
        );
    }

    private JobPostingMockGenerateResponse createFallbackMockGeneratedResponse(
            Company company,
            DetailClassification detailClassification,
            List<JobPosting> referencePostings
    ) {
        JobPosting referencePosting = referencePostings == null || referencePostings.isEmpty()
                ? null
                : referencePostings.getFirst();
        String middleName = detailClassification.getMiddleClassification().getMiddleName();
        String detailName = detailClassification.getDetailName();

        return new JobPostingMockGenerateResponse(
                company.getName(),
                detailName,
                referencePosting == null
                        ? "%s 직무의 기본 업무를 수행하며, 서비스 개발과 운영 과정에 참여합니다.".formatted(detailName)
                        : defaultString(referencePosting.getTask()),
                referencePosting == null
                        ? "%s 분야에 대한 기본 이해와 협업 역량을 갖춘 분을 찾습니다.".formatted(detailName)
                        : defaultString(referencePosting.getRequirement()),
                referencePosting == null
                        ? "관련 프로젝트 경험 또는 %s 분야 학습 경험이 있으면 좋습니다.".formatted(middleName)
                        : defaultString(referencePosting.getPreferred()),
                "%s/%s 직무 기반으로 생성된 신입 및 주니어 대상 모의 공고입니다.".formatted(middleName, detailName),
                List.of()
        );
    }

    private JobPostingMockQuestionResponse createFallbackMockQuestionResponse(DetailClassification detailClassification) {
        String middleName = detailClassification.getMiddleClassification().getMiddleName();
        String detailName = detailClassification.getDetailName();

        return new JobPostingMockQuestionResponse(List.of(
                "%s 직무에 지원한 이유와 가장 관심 있는 업무를 설명해주세요.".formatted(detailName),
                "%s 관련 프로젝트나 학습 경험이 있다면 구체적으로 소개해주세요.".formatted(detailName),
                "%s 업무를 수행할 때 본인의 강점이 무엇이라고 생각하는지 설명해주세요.".formatted(middleName),
                "%s 직무에서 협업이 중요한 이유와 본인의 협업 경험을 말씀해주세요.".formatted(detailName),
                "%s 분야 역량을 기르기 위해 최근에 노력한 점을 설명해주세요.".formatted(middleName)
        ));
    }

    private JobPostingClassificationResultResponse fallbackClassification(
            List<JobPostingClassificationCandidateResponse> candidates
    ) {
        JobPostingClassificationCandidateResponse first = candidates.getFirst();
        return new JobPostingClassificationResultResponse(
                first.getDetailClassificationId(),
                first.getDetailClassificationName(),
                first.getMiddleClassificationName(),
                first.getBigClassificationName(),
                "후보 점수가 가장 높은 소분류를 기본값으로 선택했습니다.",
                0.0
        );
    }

    private String defaultString(String value) {
        return value == null ? "" : value;
    }

    private List<String> defaultStringList(List<String> values) {
        return values == null ? List.of() : values;
    }

    private String truncateForPrompt(String value) {
        String normalized = defaultString(value).trim();
        if (normalized.length() <= MAX_REFERENCE_FIELD_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, MAX_REFERENCE_FIELD_LENGTH) + "...";
    }
}
