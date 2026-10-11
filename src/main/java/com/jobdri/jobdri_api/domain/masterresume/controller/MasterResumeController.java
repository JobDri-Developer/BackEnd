package com.jobdri.jobdri_api.domain.masterresume.controller;

import com.jobdri.jobdri_api.domain.masterresume.dto.MasterResumeRequest;
import com.jobdri.jobdri_api.domain.masterresume.dto.MasterResumeResponse;
import com.jobdri.jobdri_api.domain.masterresume.service.MasterResumeService;
import com.jobdri.jobdri_api.global.apiPayload.ApiResponse;
import com.jobdri.jobdri_api.global.security.UserDetailsImpl;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/master-resume")
public class MasterResumeController {
    private final MasterResumeService service;

    @GetMapping
    public ApiResponse<MasterResumeResponse> get(@AuthenticationPrincipal UserDetailsImpl principal) {
        return ApiResponse.onSuccess("마스터 이력서 조회에 성공했습니다.",
                service.get(principal == null ? null : principal.getUser()));
    }

    @PutMapping
    public ApiResponse<MasterResumeResponse> save(@AuthenticationPrincipal UserDetailsImpl principal,
                                                   @Valid @RequestBody MasterResumeRequest request) {
        return ApiResponse.onSuccess("마스터 이력서 저장에 성공했습니다.",
                service.save(principal == null ? null : principal.getUser(), request));
    }
}
