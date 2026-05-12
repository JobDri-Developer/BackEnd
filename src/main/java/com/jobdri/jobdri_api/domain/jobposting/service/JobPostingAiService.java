package com.jobdri.jobdri_api.domain.jobposting.service;

import com.jobdri.jobdri_api.domain.jobposting.dto.request.JobPostingExtractMultipartRequest;
import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingExtractResponse;
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

@Service
@Slf4j
@RequiredArgsConstructor
public class JobPostingAiService {

    private final OpenAIClient openAIClient;

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
            JobPostingExtractResponse extracted = extractStructuredContent(response);

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

    private JobPostingExtractResponse extractStructuredContent(StructuredResponse<JobPostingExtractResponse> response) {
        return response.output().stream()
                .filter(item -> item.message().isPresent())
                .flatMap(item -> item.asMessage().content().stream())
                .filter(content -> content.outputText().isPresent())
                .map(content -> content.asOutputText())
                .findFirst()
                .orElseThrow(() -> new GeneralException(
                        GeneralErrorCode.INTERNAL_SERVER_ERROR,
                        "AI 응답에서 채용 공고 추출 결과를 찾을 수 없습니다."
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
}
