package com.ssafy.motif.feature.motif.impl.payment

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import com.ssafy.motif.core.model.payment.PaymentOrder
import com.ssafy.motif.feature.motif.impl.BuildConfig
import com.ssafy.motif.feature.motif.impl.R
import com.tosspayments.paymentsdk.PaymentWidget
import com.tosspayments.paymentsdk.model.PaymentCallback
import com.tosspayments.paymentsdk.model.PaymentWidgetStatusListener
import com.tosspayments.paymentsdk.model.TossPaymentResult
import com.tosspayments.paymentsdk.view.Agreement
import com.tosspayments.paymentsdk.view.PaymentMethod
import java.math.BigDecimal
import java.util.UUID

internal class TossPaymentActivity : AppCompatActivity() {

    private lateinit var paymentButton: Button

    private var isPaymentMethodLoaded = false
    private var isAgreementLoaded = false

    override fun onCreate(
        savedInstanceState: Bundle?,
    ) {
        super.onCreate(savedInstanceState)

        Log.d(
            TAG,
            "onCreate",
        )

        setContentView(
            R.layout.activity_motif_payment,
        )

        val orderId =
            intent.getStringExtra(
                EXTRA_ORDER_ID,
            )

        val orderName =
            intent.getStringExtra(
                EXTRA_ORDER_NAME,
            )

        val amount =
            intent.getStringExtra(
                EXTRA_AMOUNT,
            )

        Log.d(
            TAG,
            """
            received payment order
            orderId=$orderId
            orderName=$orderName
            amount=$amount
            """.trimIndent(),
        )

        if (
            orderId.isNullOrBlank() ||
            orderName.isNullOrBlank() ||
            amount.isNullOrBlank()
        ) {
            Log.e(
                TAG,
                "invalid payment order",
            )

            finishWithFailure(
                message =
                    getString(
                        R.string.feature_motif_impl_payment_invalid_order,
                    ),
            )

            return
        }

        if (
            BuildConfig.TOSS_CLIENT_KEY
                .isBlank()
        ) {
            Log.e(
                TAG,
                "TOSS_CLIENT_KEY is blank",
            )

            finishWithFailure(
                message =
                    getString(
                        R.string.feature_motif_impl_payment_key_missing,
                    ),
            )

            return
        }

        val amountValue =
            runCatching {
                BigDecimal(
                    amount,
                ).longValueExact()
            }.getOrElse { throwable ->
                Log.e(
                    TAG,
                    "invalid amount=$amount",
                    throwable,
                )

                finishWithFailure(
                    message =
                        getString(
                            R.string.feature_motif_impl_payment_invalid_order,
                        ),
                )

                return
            }

        val paymentMethod =
            findViewById<PaymentMethod>(
                R.id.feature_motif_impl_payment_method,
            )

        val agreement =
            findViewById<Agreement>(
                R.id.feature_motif_impl_payment_agreement,
            )

        paymentButton =
            findViewById(
                R.id.feature_motif_impl_payment_button,
            )

        updatePaymentButtonState()

        val customerKey =
            UUID.randomUUID()
                .toString()

        Log.d(
            TAG,
            """
            create PaymentWidget
            clientKeyExists=${BuildConfig.TOSS_CLIENT_KEY.isNotBlank()}
            customerKey=$customerKey
            amountValue=$amountValue
            """.trimIndent(),
        )

        val paymentWidget =
            PaymentWidget(
                activity = this,
                clientKey = BuildConfig.TOSS_CLIENT_KEY,
                customerKey = customerKey,
            )

        Log.d(
            TAG,
            "PaymentWidget created",
        )

        renderPaymentMethods(
            paymentWidget = paymentWidget,
            paymentMethod = paymentMethod,
            amount = amountValue,
        )

        renderAgreement(
            paymentWidget = paymentWidget,
            agreement = agreement,
        )

        paymentButton.setOnClickListener {
            Log.d(
                TAG,
                """
                payment button clicked
                enabled=${paymentButton.isEnabled}
                orderId=$orderId
                amount=$amount
                """.trimIndent(),
            )

            if (!paymentButton.isEnabled) {
                Log.d(
                    TAG,
                    "payment button click ignored: disabled",
                )

                return@setOnClickListener
            }

            setPaymentButtonEnabled(
                enabled = false,
            )

            Log.d(
                TAG,
                """
                requestPayment start
                orderId=$orderId
                orderName=$orderName
                amount=$amount
                """.trimIndent(),
            )

            paymentWidget.requestPayment(
                paymentInfo =
                    PaymentMethod.PaymentInfo(
                        orderId = orderId,
                        orderName = orderName,
                    ),
                paymentCallback =
                    object : PaymentCallback {

                        override fun onPaymentSuccess(
                            success:
                            TossPaymentResult.Success,
                        ) {
                            Log.d(
                                TAG,
                                """
                                onPaymentSuccess
                                paymentKey=${success.paymentKey}
                                orderId=${success.orderId}
                                requestedOrderId=$orderId
                                amount=$amount
                                """.trimIndent(),
                            )

                            if (
                                success.orderId !=
                                orderId
                            ) {
                                Log.e(
                                    TAG,
                                    """
                                    orderId mismatch
                                    expected=$orderId
                                    actual=${success.orderId}
                                    """.trimIndent(),
                                )

                                finishWithFailure(
                                    message =
                                        getString(
                                            R.string
                                                .feature_motif_impl_payment_invalid_order,
                                        ),
                                )

                                return
                            }

                            finishWithSuccess(
                                paymentKey =
                                    success.paymentKey,
                                orderId =
                                    success.orderId,
                                amount =
                                    amount,
                            )
                        }

                        override fun onPaymentFailed(
                            fail:
                            TossPaymentResult.Fail,
                        ) {
                            Log.e(
                                TAG,
                                """
                                onPaymentFailed
                                message=${fail.errorMessage}
                                """.trimIndent(),
                            )

                            setPaymentButtonEnabled(
                                enabled = true,
                            )

                            Toast
                                .makeText(
                                    this@TossPaymentActivity,
                                    fail.errorMessage,
                                    Toast.LENGTH_SHORT,
                                )
                                .show()
                        }
                    },
            )

            Log.d(
                TAG,
                "requestPayment invoked",
            )
        }

        onBackPressedDispatcher.addCallback(
            this,
        ) {
            Log.d(
                TAG,
                "payment activity canceled by back",
            )

            setResult(
                Activity.RESULT_CANCELED,
            )

            finish()
        }
    }

    override fun onStart() {
        super.onStart()

        Log.d(
            TAG,
            "onStart",
        )
    }

    override fun onResume() {
        super.onResume()

        Log.d(
            TAG,
            "onResume",
        )
    }

    override fun onPause() {
        Log.d(
            TAG,
            "onPause",
        )

        super.onPause()
    }

    override fun onStop() {
        Log.d(
            TAG,
            "onStop",
        )

        super.onStop()
    }

    override fun onDestroy() {
        Log.d(
            TAG,
            "onDestroy",
        )

        super.onDestroy()
    }

    private fun renderPaymentMethods(
        paymentWidget: PaymentWidget,
        paymentMethod: PaymentMethod,
        amount: Long,
    ) {
        Log.d(
            TAG,
            "renderPaymentMethods start amount=$amount",
        )

        paymentWidget.renderPaymentMethods(
            method = paymentMethod,
            amount =
                PaymentMethod.Rendering.Amount(
                    amount,
                ),
            paymentWidgetStatusListener =
                object :
                    PaymentWidgetStatusListener {

                    override fun onLoad() {
                        Log.d(
                            TAG,
                            "PaymentMethod onLoad",
                        )

                        isPaymentMethodLoaded =
                            true

                        updatePaymentButtonState()
                    }

                    override fun onFail(
                        fail:
                        TossPaymentResult.Fail,
                    ) {
                        Log.e(
                            TAG,
                            """
                            PaymentMethod onFail
                            message=${fail.errorMessage}
                            """.trimIndent(),
                        )

                        isPaymentMethodLoaded =
                            false

                        updatePaymentButtonState()

                        Toast
                            .makeText(
                                this@TossPaymentActivity,
                                fail.errorMessage,
                                Toast.LENGTH_SHORT,
                            )
                            .show()
                    }
                },
        )
    }

    private fun renderAgreement(
        paymentWidget: PaymentWidget,
        agreement: Agreement,
    ) {
        Log.d(
            TAG,
            "renderAgreement start",
        )

        paymentWidget.renderAgreement(
            agreement = agreement,
            paymentWidgetStatusListener =
                object :
                    PaymentWidgetStatusListener {

                    override fun onLoad() {
                        Log.d(
                            TAG,
                            "Agreement onLoad",
                        )

                        isAgreementLoaded =
                            true

                        updatePaymentButtonState()
                    }

                    override fun onFail(
                        fail:
                        TossPaymentResult.Fail,
                    ) {
                        Log.e(
                            TAG,
                            """
                            Agreement onFail
                            message=${fail.errorMessage}
                            """.trimIndent(),
                        )

                        isAgreementLoaded =
                            false

                        updatePaymentButtonState()

                        Toast
                            .makeText(
                                this@TossPaymentActivity,
                                fail.errorMessage,
                                Toast.LENGTH_SHORT,
                            )
                            .show()
                    }
                },
        )
    }

    private fun updatePaymentButtonState() {
        val enabled =
            isPaymentMethodLoaded &&
                    isAgreementLoaded

        Log.d(
            TAG,
            """
            updatePaymentButtonState
            paymentMethodLoaded=$isPaymentMethodLoaded
            agreementLoaded=$isAgreementLoaded
            enabled=$enabled
            """.trimIndent(),
        )

        setPaymentButtonEnabled(
            enabled = enabled,
        )
    }

    private fun setPaymentButtonEnabled(
        enabled: Boolean,
    ) {
        paymentButton.isEnabled =
            enabled

        val backgroundColor =
            if (enabled) {
                PAYMENT_BUTTON_ENABLED_COLOR
            } else {
                PAYMENT_BUTTON_DISABLED_COLOR
            }

        val textColor =
            if (enabled) {
                PAYMENT_BUTTON_ENABLED_TEXT_COLOR
            } else {
                PAYMENT_BUTTON_DISABLED_TEXT_COLOR
            }

        paymentButton.backgroundTintList =
            ColorStateList.valueOf(
                Color.parseColor(
                    backgroundColor,
                ),
            )

        paymentButton.setTextColor(
            Color.parseColor(
                textColor,
            ),
        )
    }

    private fun finishWithSuccess(
        paymentKey: String,
        orderId: String,
        amount: String,
    ) {
        Log.d(
            TAG,
            """
            finishWithSuccess
            orderId=$orderId
            amount=$amount
            paymentKey=$paymentKey
            """.trimIndent(),
        )

        setResult(
            RESULT_PAYMENT_SUCCESS,
            Intent()
                .putExtra(
                    EXTRA_PAYMENT_KEY,
                    paymentKey,
                )
                .putExtra(
                    EXTRA_RESULT_ORDER_ID,
                    orderId,
                )
                .putExtra(
                    EXTRA_RESULT_AMOUNT,
                    amount,
                ),
        )

        finish()
    }

    private fun finishWithFailure(
        message: String,
    ) {
        Log.e(
            TAG,
            "finishWithFailure message=$message",
        )

        setResult(
            RESULT_PAYMENT_FAILURE,
            Intent()
                .putExtra(
                    EXTRA_ERROR_MESSAGE,
                    message,
                ),
        )

        finish()
    }

    companion object {

        private const val TAG =
            "TossPayment"

        const val RESULT_PAYMENT_SUCCESS =
            Activity.RESULT_FIRST_USER

        const val RESULT_PAYMENT_FAILURE =
            Activity.RESULT_FIRST_USER + 1

        private const val EXTRA_ORDER_ID =
            "order_id"

        private const val EXTRA_ORDER_NAME =
            "order_name"

        private const val EXTRA_AMOUNT =
            "amount"

        internal const val EXTRA_PAYMENT_KEY =
            "payment_key"

        internal const val EXTRA_RESULT_ORDER_ID =
            "result_order_id"

        internal const val EXTRA_RESULT_AMOUNT =
            "result_amount"

        internal const val EXTRA_ERROR_MESSAGE =
            "error_message"

        private const val PAYMENT_BUTTON_ENABLED_COLOR =
            "#FF4F8B"

        private const val PAYMENT_BUTTON_DISABLED_COLOR =
            "#4D4D4D"

        private const val PAYMENT_BUTTON_ENABLED_TEXT_COLOR =
            "#FFFFFF"

        private const val PAYMENT_BUTTON_DISABLED_TEXT_COLOR =
            "#8A8A8A"

        fun createIntent(
            context: Context,
            order: PaymentOrder,
        ): Intent {
            Log.d(
                TAG,
                """
                createIntent
                orderId=${order.id}
                orderName=${order.name}
                amount=${order.amount}
                """.trimIndent(),
            )

            return Intent(
                context,
                TossPaymentActivity::class.java,
            )
                .putExtra(
                    EXTRA_ORDER_ID,
                    order.id,
                )
                .putExtra(
                    EXTRA_ORDER_NAME,
                    order.name,
                )
                .putExtra(
                    EXTRA_AMOUNT,
                    order.amount,
                )
        }
    }
}