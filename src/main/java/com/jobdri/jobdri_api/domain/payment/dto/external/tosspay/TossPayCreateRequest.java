package com.jobdri.jobdri_api.domain.payment.dto.external.tosspay;

public record TossPayCreateRequest(
        String orderNo,
        int amount,
        int amountTaxFree,
        String productDesc,
        String apiKey,
        String retUrl,
        String retCancelUrl,
        boolean autoExecute,
        String resultCallback,
        String callbackVersion
) {
    private static final int DEFAULT_TAX_FREE_AMOUNT = 0;
    private static final boolean AUTO_EXECUTE = true;
    private static final String CALLBACK_VERSION = "V2";

    public static TossPayCreateRequest of(
            String orderNo,
            int amount,
            String productDesc,
            String apiKey,
            String returnUrl,
            String cancelUrl,
            String resultCallbackUrl
    ) {
        return new TossPayCreateRequest(
                orderNo,
                amount,
                DEFAULT_TAX_FREE_AMOUNT,
                productDesc,
                apiKey,
                returnUrl,
                cancelUrl,
                AUTO_EXECUTE,
                resultCallbackUrl,
                CALLBACK_VERSION
        );
    }
}
