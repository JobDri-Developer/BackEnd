package com.jobdri.jobdri_api.domain.payment.gateway;

import com.jobdri.jobdri_api.domain.payment.type.PaymentProviderType;
import com.jobdri.jobdri_api.domain.payment.gateway.command.GatewayConfirmCommand;
import com.jobdri.jobdri_api.domain.payment.gateway.query.GatewayPaymentQuery;
import com.jobdri.jobdri_api.domain.payment.gateway.result.GatewayPaymentSnapshot;
import com.jobdri.jobdri_api.domain.payment.gateway.command.GatewayPrepareCommand;
import com.jobdri.jobdri_api.domain.payment.gateway.result.GatewayPrepareResult;
import com.jobdri.jobdri_api.domain.payment.gateway.command.GatewayRefundCommand;
import com.jobdri.jobdri_api.domain.payment.gateway.result.GatewayRefundResult;

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
