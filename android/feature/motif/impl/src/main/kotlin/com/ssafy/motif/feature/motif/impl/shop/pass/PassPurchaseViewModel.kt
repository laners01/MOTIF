package com.ssafy.motif.feature.motif.impl.shop.pass

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.motif.core.common.result.Result
import com.ssafy.motif.core.common.result.asResult
import com.ssafy.motif.core.data.repository.ArtistRepository
import com.ssafy.motif.core.data.repository.MotifRepository
import com.ssafy.motif.core.data.repository.PaymentRepository
import com.ssafy.motif.core.model.payment.PaymentConfirmation
import com.ssafy.motif.core.model.shop.ShopArtistDetail
import com.ssafy.motif.feature.motif.impl.payment.MotifPaymentEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

@HiltViewModel
internal class PassPurchaseViewModel @Inject constructor(
    private val motifRepository:
    MotifRepository,
    private val artistRepository:
    ArtistRepository,
    private val paymentRepository:
    PaymentRepository,
) : ViewModel() {

    private val _uiState =
        MutableStateFlow<PassPurchaseUiState>(
            PassPurchaseUiState.Loading,
        )

    internal val uiState:
            StateFlow<PassPurchaseUiState> =
        _uiState.asStateFlow()

    private val _paymentEvents =
        MutableSharedFlow<MotifPaymentEvent>()

    internal val paymentEvents =
        _paymentEvents.asSharedFlow()

    private var loadedArtistId:
            Long? = null

    private var loadJob:
            Job? = null

    private var paymentJob:
            Job? = null

    private var confirmJob:
            Job? = null

    private var pendingOrderId:
            String? = null

    fun load(
        artistId: Long,
    ) {
        loadedArtistId =
            artistId

        loadJob?.cancel()

        loadJob =
            combine(
                artistRepository
                    .getArtistDetail(
                        id = artistId,
                    ),
                motifRepository
                    .getShop(),
                motifRepository
                    .getArtistMotifPass(
                        artistId =
                            artistId,
                    ),
            ) {
                    artistDetail,
                    shopSections,
                    pass,
                ->

                val section =
                    requireNotNull(
                        shopSections
                            .firstOrNull {
                                it.artist.id ==
                                        artistId
                            },
                    ) {
                        "artist $artistId does not exist in MOTIF shop"
                    }

                PassPurchaseUiState
                    .Success(
                        detail =
                            ShopArtistDetail(
                                artist =
                                    artistDetail.artist,
                                introduction =
                                    artistDetail
                                        .introduction,
                                assets =
                                    section.assets,
                                pass = pass,
                                hasPass =
                                    pass.isActive,
                            ),
                    )
            }
                .asResult()
                .onEach { result ->
                    _uiState.value =
                        when (result) {
                            Result.Loading ->
                                PassPurchaseUiState
                                    .Loading

                            is Result.Success ->
                                result.data

                            is Result.Error ->
                                PassPurchaseUiState
                                    .Error(
                                        exception =
                                            result.exception,
                                    )
                        }
                }
                .launchIn(
                    viewModelScope,
                )
    }

    fun purchasePlan(
        planId: Long,
    ) {
        val current =
            _uiState.value as?
                    PassPurchaseUiState.Success
                ?: return

        if (
            current.detail.pass.isActive
        ) {
            return
        }

        val plan =
            current.detail
                .pass
                .plans
                .firstOrNull {
                    it.id ==
                            planId
                }
                ?: return

        if (
            paymentJob?.isActive ==
            true
        ) {
            return
        }

        paymentJob =
            paymentRepository
                .createPassOrder(
                    artistId =
                        current.detail
                            .artist
                            .id,
                    durationMonths =
                        plan.months,
                )
                .asResult()
                .onEach { result ->
                    when (result) {
                        Result.Loading ->
                            Unit

                        is Result.Success -> {
                            pendingOrderId =
                                result.data.id

                            _paymentEvents.emit(
                                MotifPaymentEvent
                                    .Launch(
                                        order =
                                            result.data,
                                    ),
                            )
                        }

                        is Result.Error -> {
                            _paymentEvents.emit(
                                MotifPaymentEvent
                                    .Error(
                                        throwable =
                                            result.exception,
                                    ),
                            )
                        }
                    }
                }
                .launchIn(
                    viewModelScope,
                )
    }

    fun confirmPayment(
        paymentKey: String,
        orderId: String,
        amount: String,
    ) {
        if (
            pendingOrderId !=
            orderId
        ) {
            emitPaymentError(
                IllegalStateException(
                    "Payment order does not match pending order.",
                ),
            )

            return
        }

        if (
            confirmJob?.isActive ==
            true
        ) {
            return
        }

        confirmJob =
            paymentRepository
                .confirmOrder(
                    orderId = orderId,
                    paymentKey = paymentKey,
                    amount = amount,
                )
                .asResult()
                .onEach { result ->
                    when (result) {
                        Result.Loading ->
                            Unit

                        is Result.Success -> {
                            if (
                                result.data !is
                                        PaymentConfirmation.Pass
                            ) {
                                emitPaymentError(
                                    IllegalStateException(
                                        "Expected PASS payment confirmation.",
                                    ),
                                )

                                return@onEach
                            }

                            pendingOrderId =
                                null

                            loadedArtistId
                                ?.let { artistId ->
                                    refresh(
                                        artistId =
                                            artistId,
                                    )
                                }
                        }

                        is Result.Error -> {
                            // confirm 실패 시 자동 cancel하지 않는다.
                            _paymentEvents.emit(
                                MotifPaymentEvent
                                    .Error(
                                        throwable =
                                            result.exception,
                                    ),
                            )
                        }
                    }
                }
                .launchIn(
                    viewModelScope,
                )
    }

    fun cancelPendingOrder() {
        val orderId =
            pendingOrderId
                ?: return

        pendingOrderId =
            null

        paymentRepository
            .cancelOrder(
                orderId = orderId,
            )
            .catch {
                // 방치해도 30분 후 EXPIRED.
            }
            .launchIn(
                viewModelScope,
            )
    }

    private fun refresh(
        artistId: Long,
    ) {
        val current =
            _uiState.value as?
                    PassPurchaseUiState.Success
                ?: return

        combine(
            motifRepository
                .getShop(),
            motifRepository
                .getArtistMotifPass(
                    artistId =
                        artistId,
                ),
        ) {
                sections,
                pass,
            ->

            val section =
                requireNotNull(
                    sections
                        .firstOrNull {
                            it.artist.id ==
                                    artistId
                        },
                )

            current.copy(
                detail =
                    current.detail
                        .copy(
                            assets =
                                section.assets,
                            pass = pass,
                            hasPass =
                                pass.isActive,
                        ),
            )
        }
            .asResult()
            .onEach { result ->
                when (result) {
                    Result.Loading ->
                        Unit

                    is Result.Success ->
                        _uiState.value =
                            result.data

                    is Result.Error ->
                        _paymentEvents.emit(
                            MotifPaymentEvent
                                .Error(
                                    throwable =
                                        result.exception,
                                ),
                        )
                }
            }
            .launchIn(
                viewModelScope,
            )
    }

    private fun emitPaymentError(
        throwable: Throwable,
    ) {
        viewModelScope.launch {
            _paymentEvents.emit(
                MotifPaymentEvent
                    .Error(
                        throwable =
                            throwable,
                    ),
            )
        }
    }
}