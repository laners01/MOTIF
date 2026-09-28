package com.ssafy.motif.feature.motif.impl.payment

import android.app.Activity
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.ssafy.motif.core.model.payment.PaymentOrder

@Composable
internal fun rememberMotifPaymentLauncher(
    onSuccess: (
        paymentKey: String,
        orderId: String,
        amount: String,
    ) -> Unit,
    onCanceled: () -> Unit,
    onFailure: (String) -> Unit,
): (PaymentOrder) -> Unit {

    val context =
        LocalContext.current

    val currentOnSuccess =
        rememberUpdatedState(
            onSuccess,
        )

    val currentOnCanceled =
        rememberUpdatedState(
            onCanceled,
        )

    val currentOnFailure =
        rememberUpdatedState(
            onFailure,
        )

    val launcher =
        rememberLauncherForActivityResult(
            contract =
                ActivityResultContracts
                    .StartActivityForResult(),
        ) { result ->

            Log.d(
                TAG,
                """
                activity result
                resultCode=${result.resultCode}
                hasData=${result.data != null}
                """.trimIndent(),
            )

            when (result.resultCode) {
                TossPaymentActivity
                    .RESULT_PAYMENT_SUCCESS -> {

                    val data =
                        result.data

                    val paymentKey =
                        data?.getStringExtra(
                            TossPaymentActivity
                                .EXTRA_PAYMENT_KEY,
                        )

                    val orderId =
                        data?.getStringExtra(
                            TossPaymentActivity
                                .EXTRA_RESULT_ORDER_ID,
                        )

                    val amount =
                        data?.getStringExtra(
                            TossPaymentActivity
                                .EXTRA_RESULT_AMOUNT,
                        )

                    Log.d(
                        TAG,
                        """
                        payment success result
                        paymentKey=$paymentKey
                        orderId=$orderId
                        amount=$amount
                        """.trimIndent(),
                    )

                    if (
                        paymentKey != null &&
                        orderId != null &&
                        amount != null
                    ) {
                        Log.d(
                            TAG,
                            "invoke onSuccess",
                        )

                        currentOnSuccess.value(
                            paymentKey,
                            orderId,
                            amount,
                        )
                    } else {
                        Log.e(
                            TAG,
                            "invalid payment success result",
                        )

                        currentOnFailure.value(
                            "결제 결과가 올바르지 않습니다.",
                        )
                    }
                }

                TossPaymentActivity
                    .RESULT_PAYMENT_FAILURE -> {

                    val message =
                        result.data
                            ?.getStringExtra(
                                TossPaymentActivity
                                    .EXTRA_ERROR_MESSAGE,
                            )
                            .orEmpty()

                    Log.e(
                        TAG,
                        "payment failure result message=$message",
                    )

                    currentOnFailure.value(
                        message,
                    )
                }

                Activity.RESULT_CANCELED -> {
                    Log.d(
                        TAG,
                        "payment canceled",
                    )

                    currentOnCanceled.value()
                }

                else -> {
                    Log.w(
                        TAG,
                        "unknown payment resultCode=${result.resultCode}",
                    )

                    currentOnCanceled.value()
                }
            }
        }

    return remember(
        context,
        launcher,
    ) {
        { order ->

            Log.d(
                TAG,
                """
                launch TossPaymentActivity
                orderId=${order.id}
                orderName=${order.name}
                amount=${order.amount}
                """.trimIndent(),
            )

            launcher.launch(
                TossPaymentActivity
                    .createIntent(
                        context = context,
                        order = order,
                    ),
            )
        }
    }
}

private const val TAG =
    "TossPayment"