package com.jobdri.jobdri_api.domain.analysis.controller;

import com.jobdri.jobdri_api.domain.analysis.dto.request.QuestionCandidateCreateRequest;
import com.jobdri.jobdri_api.domain.analysis.dto.request.QuestionAnswerSaveRequest;
import com.jobdri.jobdri_api.domain.analysis.dto.request.QuestionSelectionSaveRequest;
import com.jobdri.jobdri_api.domain.analysis.dto.response.QuestionAnswerResponse;
import com.jobdri.jobdri_api.domain.analysis.dto.response.QuestionCandidateResponse;
import com.jobdri.jobdri_api.domain.analysis.dto.response.QuestionSelectionResponse;
import com.jobdri.jobdri_api.domain.analysis.service.question.QuestionService;
import com.jobdri.jobdri_api.global.apiPayload.ApiResponse;
import com.jobdri.jobdri_api.global.security.UserDetailsImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/mock-applies/{mockApplyId}/questions")
@Tag(name = "Question", description = "자소서 문항 선택 및 답변 저장 API")
public class QuestionController {

    private final QuestionService questionService;

    @Operation(summary = "문항 후보 목록 조회", description = "문항 선택 화면에서 사용할 기본 문항 후보와 선택 여부를 조회합니다.")
    @GetMapping("/candidates")
    public ApiResponse<List<QuestionCandidateResponse>> getQuestionCandidates(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @PathVariable Long mockApplyId
    ) {
        return ApiResponse.onSuccess(
                "문항 후보 목록 조회에 성공했습니다.",
                questionService.getQuestionCandidates(userDetails.getUser(), mockApplyId)
        );
    }

    @Operation(summary = "직접 추가 문항 후보 생성", description = "직접 입력한 문항을 선택 후보 목록에 추가합니다. 선택 문항으로 확정 저장되지는 않습니다.")
    @PostMapping("/candidates")
    public ApiResponse<QuestionCandidateResponse> addCustomQuestionCandidate(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @PathVariable Long mockApplyId,
            @Valid @RequestBody QuestionCandidateCreateRequest request
    ) {
        return ApiResponse.onSuccess(
                "직접 추가 문항 후보가 생성되었습니다.",
                questionService.addCustomQuestionCandidate(userDetails.getUser(), mockApplyId, request)
        );
    }

    @Operation(summary = "선택 문항 조회", description = "현재 모의 서류 지원에 저장된 선택 문항 목록을 조회합니다.")
    @GetMapping
    public ApiResponse<QuestionSelectionResponse> getSelectedQuestions(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @PathVariable Long mockApplyId
    ) {
        return ApiResponse.onSuccess(
                "선택 문항 조회에 성공했습니다.",
                questionService.getSelectedQuestions(userDetails.getUser(), mockApplyId)
        );
    }

    @Operation(summary = "선택 문항 저장", description = "사용자가 선택하거나 직접 추가한 문항을 저장하고 답변 작성 단계로 진입합니다.")
    @PutMapping
    public ApiResponse<QuestionSelectionResponse> saveSelectedQuestions(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @PathVariable Long mockApplyId,
            @Valid @RequestBody QuestionSelectionSaveRequest request
    ) {
        return ApiResponse.onSuccess(
                "선택 문항이 저장되었습니다.",
                questionService.saveSelectedQuestions(userDetails.getUser(), mockApplyId, request)
        );
    }

    @Operation(summary = "자소서 문항 답변 저장/수정", description = "자소서 작성 화면의 문항 목록, 글자수 제한, 답변을 함께 저장합니다.")
    @PatchMapping("/answers")
    public ApiResponse<QuestionAnswerResponse> saveAnswers(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @PathVariable Long mockApplyId,
            @Valid @RequestBody QuestionAnswerSaveRequest request
    ) {
        return ApiResponse.onSuccess(
                "자소서 답변이 저장되었습니다.",
                questionService.saveAnswers(userDetails.getUser(), mockApplyId, request)
        );
    }

    @Operation(summary = "선택 문항 삭제", description = "자소서 작성 화면에서 문항 1개를 삭제하고 남은 선택 문항 목록을 반환합니다.")
    @DeleteMapping("/{questionId}")
    public ApiResponse<QuestionSelectionResponse> deleteQuestion(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @PathVariable Long mockApplyId,
            @PathVariable Long questionId
    ) {
        return ApiResponse.onSuccess(
                "선택 문항이 삭제되었습니다.",
                questionService.deleteQuestion(userDetails.getUser(), mockApplyId, questionId)
        );
    }
}
