package com.jobdri.jobdri_api.domain.jobposting.controller;

import com.jobdri.jobdri_api.domain.jobposting.dto.request.JobPostingImageUploadPresignRequest;
import com.jobdri.jobdri_api.domain.jobposting.dto.response.JobPostingImageUploadPresignResponse;
import com.jobdri.jobdri_api.domain.jobposting.service.JobPostingImageStorageService;
import com.jobdri.jobdri_api.domain.user.service.UserService;
import com.jobdri.jobdri_api.global.apiPayload.ApiResponse;
import com.jobdri.jobdri_api.global.security.UserDetailsImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/job-postings/images")
@Tag(name = "JobPosting Upload", description = "채용 공고 이미지 업로드 API")
public class JobPostingUploadController {

    private final JobPostingImageStorageService jobPostingImageStorageService;
    private final UserService userService;

    @Operation(
            summary = "채용 공고 이미지 업로드용 presigned PUT URL 발급",
            description = "로그인 사용자가 S3에 직접 이미지를 업로드할 수 있도록 presigned PUT URL과 object key를 발급합니다."
    )
    @PostMapping("/presign-upload")
    public ApiResponse<JobPostingImageUploadPresignResponse> createPresignedUploadUrl(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @Valid @RequestBody JobPostingImageUploadPresignRequest request
    ) {
        var user = userService.validateUser(userDetails == null ? null : userDetails.getUser());
        return ApiResponse.onSuccess(
                "채용 공고 이미지 업로드 URL 발급에 성공했습니다.",
                jobPostingImageStorageService.createUploadPresignUrl(user.getId(), request)
        );
    }
}
