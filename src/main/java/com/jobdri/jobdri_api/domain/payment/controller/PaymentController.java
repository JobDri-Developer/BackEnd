package com.jobdri.jobdri_api.domain.payment.controller;

import com.jobdri.jobdri_api.domain.payment.dto.request.PaymentConfirmRequest;
import com.jobdri.jobdri_api.domain.payment.dto.request.PaymentPrepareRequest;
import com.jobdri.jobdri_api.domain.payment.dto.response.*;
import com.jobdri.jobdri_api.domain.payment.entity.CreditTransactionType;
import com.jobdri.jobdri_api.domain.payment.service.PaymentService;
import com.jobdri.jobdri_api.global.apiPayload.ApiResponse;
import com.jobdri.jobdri_api.global.security.UserDetailsImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/payments")
@Tag(name = "Payment", description = "크레딧 결제 및 거래 내역 API")
public class PaymentController {

    private final PaymentService paymentService;

    @Operation(summary = "크레딧 가격 플랜 조회", description = "구매 가능한 크레딧 플랜 목록을 조회합니다.")
    @GetMapping("/plans")
    public ApiResponse<List<CreditPlanResponse>> getPlans() {
        return ApiResponse.onSuccess("크레딧 가격 플랜 조회에 성공했습니다.", paymentService.getPlans());
    }

    @Operation(summary = "토스 결제 준비", description = "선택한 크레딧 플랜 기준으로 결제 주문을 생성합니다.")
    @PostMapping("/prepare")
    public ApiResponse<PaymentPrepareResponse> prepare(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @Valid @RequestBody PaymentPrepareRequest request
    ) {
        return ApiResponse.onSuccess(
                "결제 준비가 완료되었습니다.",
                paymentService.prepare(userDetails.getUser(), request)
        );
    }

    @Operation(summary = "토스 결제 승인", description = "토스페이먼츠 결제 성공 후 paymentKey/orderId/amount를 검증하고 크레딧을 충전합니다.")
    @PostMapping("/confirm")
    public ApiResponse<PaymentConfirmResponse> confirm(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @Valid @RequestBody PaymentConfirmRequest request
    ) {
        return ApiResponse.onSuccess(
                "결제가 완료되었습니다.",
                paymentService.confirm(userDetails.getUser(), request)
        );
    }

    @Operation(summary = "내 크레딧 잔액 조회", description = "로그인 사용자의 현재 크레딧 잔액을 조회합니다.")
    @GetMapping("/credits/me")
    public ApiResponse<CreditBalanceResponse> getBalance(
            @AuthenticationPrincipal UserDetailsImpl userDetails
    ) {
        return ApiResponse.onSuccess(
                "크레딧 잔액 조회에 성공했습니다.",
                paymentService.getBalance(userDetails.getUser())
        );
    }

    @Operation(summary = "내 크레딧 거래 내역 조회", description = "충전/사용/환불/쿠폰 거래 내역을 조회합니다. type query parameter로 필터링할 수 있습니다.")
    @GetMapping("/credits/me/transactions")
    public ApiResponse<List<CreditTransactionResponse>> getTransactions(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @RequestParam(required = false) CreditTransactionType type
    ) {
        return ApiResponse.onSuccess(
                "크레딧 거래 내역 조회에 성공했습니다.",
                paymentService.getTransactions(userDetails.getUser(), type)
        );
    }
}
