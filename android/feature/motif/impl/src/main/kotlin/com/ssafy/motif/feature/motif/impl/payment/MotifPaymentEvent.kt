package com.ssafy.motif.feature.motif.impl.payment

import com.ssafy.motif.core.model.payment.PaymentOrder

internal sealed interface MotifPaymentEvent {

    data class Launch(
        val order: PaymentOrder,
    ) : MotifPaymentEvent

    data class Error(
        val throwable: Throwable,
    ) : MotifPaymentEvent
}