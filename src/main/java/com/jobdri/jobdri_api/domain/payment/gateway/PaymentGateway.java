package com.jobdri.jobdri_api.domain.payment.gateway;

import com.jobdri.jobdri_api.domain.payment.entity.PaymentProviderType;
import com.jobdri.jobdri_api.domain.payment.gateway.model.GatewayConfirmCommand;
import com.jobdri.jobdri_api.domain.payment.gateway.model.GatewayPaymentQuery;
import com.jobdri.jobdri_api.domain.payment.gateway.model.GatewayPaymentSnapshot;
import com.jobdri.jobdri_api.domain.payment.gateway.model.GatewayPrepareCommand;
import com.jobdri.jobdri_api.domain.payment.gateway.model.GatewayPrepareResult;
import com.jobdri.jobdri_api.domain.payment.gateway.model.GatewayRefundCommand;
import com.jobdri.jobdri_api.domain.payment.gateway.model.GatewayRefundResult;

/**
 * Normalized payment gateway contract used to isolate provider-specific DTOs
 * and API flows from application services.
 */
public interface PaymentGateway {

    PaymentProviderType type();

    GatewayPrepareResult prepare(GatewayPrepareCommand command);

    GatewayPaymentSnapshot confirm(GatewayConfirmCommand command);

    GatewayPaymentSnapshot fetch(GatewayPaymentQuery query);

    GatewayRefundResult refund(GatewayRefundCommand command);
}
