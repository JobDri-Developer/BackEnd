package com.jobdri.jobdri_api.domain.jobposting.service;

import com.jobdri.jobdri_api.domain.classification.entity.DetailClassification;
import com.jobdri.jobdri_api.domain.classification.repository.DetailClassificationRepository;
import com.jobdri.jobdri_api.domain.jobposting.dto.request.JobPostingExtractMultipartRequest;
import com.jobdri.jobdri_api.domain.jobposting.dto.request.JobPostingGenerateRequest;
import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingClassificationCandidateResponse;
import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingClassificationResultResponse;
import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingExtractResponse;
import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingGenerateResponse;
import com.jobdri.jobdri_api.global.apiPayload.code.GeneralErrorCode;
import com.jobdri.jobdri_api.global.apiPayload.exception.GeneralException;
import com.openai.client.OpenAIClient;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseInputContent;
import com.openai.models.responses.ResponseInputImage;
import com.openai.models.responses.ResponseInputItem;
import com.openai.models.responses.StructuredResponse;
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

    private final OpenAIClient openAIClient;
    private final DetailClassificationRepository detailClassificationRepository;

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
            normalizeGeneratedResponse(generated, request);
            return generated;
        } catch (Exception e) {
            log.error("채용 공고 생성 OpenAI API 호출 오류: {}", e.getMessage(), e);
            return createFallbackGeneratedResponse(request);
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
            normalizeClassificationResponse(classification, candidates);
            return classification;
        } catch (Exception e) {
            log.error("채용 공고 소분류 분류 OpenAI API 호출 오류: {}", e.getMessage(), e);
            return fallbackClassification(candidates);
        }
    }

    public JobPostingExtractResponse extractJobPosting(JobPostingExtractMultipartRequest request) {
        return extractJobPosting(request.getRawText(), request.getImage(), request.getSourceUrl());
    }

    public JobPostingExtractResponse extractJobPosting(String rawText, MultipartFile imageFile, String sourceUrl) {
        validateInput(rawText, imageFile);

        List<ResponseInputContent> contents = new ArrayList<>();
        contents.add(ResponseInputContent.ofInputText(
                com.openai.models.responses.ResponseInputText.builder()
                        .text(buildPrompt(rawText, sourceUrl, imageFile != null))
                        .build()
        ));

        if (imageFile != null && !imageFile.isEmpty()) {
            contents.add(ResponseInputContent.ofInputImage(buildImageContent(imageFile)));
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

            normalizeResponse(extracted, rawText);
            return extracted;

        } catch (Exception e) {
            log.error("채용 공고 추출 OpenAI API 호출 오류: {}", e.getMessage(), e);
            return createFallbackResponse(rawText);
        }
    }

    private String buildPrompt(String rawText, String sourceUrl, boolean hasImage) {
        String normalizedRawText = rawText == null ? "" : rawText;
        String normalizedSourceUrl = sourceUrl == null ? "" : sourceUrl;

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

                [원본 URL]
                %s

                [채용 공고 텍스트]
                %s
                """.formatted(hasImage ? "이미지 또는 텍스트" : "텍스트", normalizedSourceUrl, normalizedRawText);
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
                defaultString(extracted.getCompanyName()),
                defaultString(extracted.getJobTitle()),
                defaultString(extracted.getTask()),
                defaultString(extracted.getRequirements()),
                defaultString(extracted.getPreferredQualifications()),
                defaultString(extracted.getRawText()),
                candidateText
        );
    }

    private ResponseInputImage buildImageContent(MultipartFile imageFile) {
        validateImage(imageFile);

        try {
            String contentType = imageFile.getContentType();
            String base64 = Base64.getEncoder().encodeToString(imageFile.getBytes());
            String dataUrl = "data:%s;base64,%s".formatted(contentType, base64);

            return ResponseInputImage.builder()
                    .imageUrl(dataUrl)
                    .detail(ResponseInputImage.Detail.HIGH)
                    .build();
        } catch (IOException e) {
            throw new GeneralException(GeneralErrorCode.INVALID_PARAMETER, "이미지 파일을 읽을 수 없습니다.");
        }
    }

    private <T> T extractStructuredContent(StructuredResponse<T> response, Class<T> responseType) {
        return response.output().stream()
                .filter(item -> item.message().isPresent())
                .flatMap(item -> item.asMessage().content().stream())
                .filter(content -> content.outputText().isPresent())
                .map(content -> content.asOutputText())
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

    private void validateImage(MultipartFile imageFile) {
        String contentType = imageFile.getContentType();
        if (contentType == null || !SUPPORTED_IMAGE_TYPES.contains(contentType.toLowerCase())) {
            throw new GeneralException(
                    GeneralErrorCode.INVALID_PARAMETER,
                    "지원하는 이미지 형식은 png, jpg, jpeg, webp, gif 입니다."
            );
        }
    }

    private void normalizeResponse(JobPostingExtractResponse response, String rawText) {
        if (response == null) {
            throw new GeneralException(
                    GeneralErrorCode.INTERNAL_SERVER_ERROR,
                    "AI 응답이 비어 있습니다."
            );
        }

        if (response.getCompanyName() == null) {
            response.setCompanyName("");
        }
        if (response.getJobTitle() == null) {
            response.setJobTitle("");
        }
        if (response.getTask() == null) {
            response.setTask("");
        }
        if (response.getRequirements() == null) {
            response.setRequirements("");
        }
        if (response.getPreferredQualifications() == null) {
            response.setPreferredQualifications("");
        }
        if (response.getRawText() == null || response.getRawText().isBlank()) {
            response.setRawText(rawText == null ? "" : rawText);
        }

        double confidence = response.getConfidence();
        if (Double.isNaN(confidence) || Double.isInfinite(confidence)) {
            response.setConfidence(0.0);
        } else if (confidence < 0.0) {
            response.setConfidence(0.0);
        } else if (confidence > 1.0) {
            response.setConfidence(1.0);
        }
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
                request.companyName(),
                request.companySize().name(),
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

    private void normalizeGeneratedResponse(JobPostingGenerateResponse response, JobPostingGenerateRequest request) {
        if (response == null) {
            throw new GeneralException(
                GeneralErrorCode.INTERNAL_SERVER_ERROR,
                "AI 생성 응답이 비어 있습니다."
            );
        }

        if (response.getCompanyName() == null || response.getCompanyName().isBlank()) {
            response.setCompanyName(request.companyName());
        }
        if (response.getTask() == null) {
            response.setTask("");
        }
        if (response.getRequirements() == null) {
            response.setRequirements("");
        }
        if (response.getPreferredQualifications() == null) {
            response.setPreferredQualifications("");
        }
        if (response.getSummary() == null) {
            response.setSummary("");
        }
    }

    private void normalizeClassificationResponse(
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
                .filter(candidate -> candidate.getDetailClassificationId().equals(response.getDetailClassificationId()))
                .findFirst()
                .orElseGet(() -> candidates.getFirst());

        response.setDetailClassificationId(matched.getDetailClassificationId());
        response.setDetailClassificationName(matched.getDetailClassificationName());
        response.setMiddleClassificationName(matched.getMiddleClassificationName());
        response.setBigClassificationName(matched.getBigClassificationName());

        if (response.getReason() == null) {
            response.setReason("");
        }

        double confidence = response.getConfidence();
        if (Double.isNaN(confidence) || Double.isInfinite(confidence) || confidence < 0.0) {
            response.setConfidence(0.0);
        } else if (confidence > 1.0) {
            response.setConfidence(1.0);
        }
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
}
