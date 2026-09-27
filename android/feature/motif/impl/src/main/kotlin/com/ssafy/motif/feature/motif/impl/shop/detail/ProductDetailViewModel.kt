package com.ssafy.motif.feature.motif.impl.shop.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.motif.core.common.result.Result
import com.ssafy.motif.core.common.result.asResult
import com.ssafy.motif.core.data.repository.ArtistRepository
import com.ssafy.motif.core.data.repository.MotifRepository
import com.ssafy.motif.core.data.repository.PaymentRepository
import com.ssafy.motif.core.model.motif.Asset
import com.ssafy.motif.core.model.motif.AssetAvailability
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
internal class ProductDetailViewModel @Inject constructor(
    private val motifRepository:
    MotifRepository,
    private val artistRepository:
    ArtistRepository,
    private val paymentRepository:
    PaymentRepository,
) : ViewModel() {

    private val _uiState =
        MutableStateFlow<ProductDetailUiState>(
            ProductDetailUiState.Loading,
        )

    internal val uiState:
            StateFlow<ProductDetailUiState> =
        _uiState.asStateFlow()

    private val _paymentEvents =
        MutableSharedFlow<MotifPaymentEvent>()

    internal val paymentEvents =
        _paymentEvents.asSharedFlow()

    private var loadJob: Job? = null

    private var assetJob: Job? = null

    private var paymentJob: Job? = null

    private var confirmJob: Job? = null

    private var pendingOrderId:
            String? = null

    fun load(
        artistId: Long,
        assetId: Long,
    ) {
        loadJob?.cancel()
        assetJob?.cancel()

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
                        artistId = artistId,
                    ),
                motifRepository
                    .getAssetDetail(
                        assetId = assetId,
                    ),
            ) {
                    artistDetail,
                    shopSections,
                    pass,
                    selectedAsset,
                ->

                require(
                    selectedAsset.artist.id ==
                            artistId,
                ) {
                    "asset $assetId does not belong to artist $artistId"
                }

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

                val assets =
                    section.assets
                        .map { asset ->
                            if (
                                asset.id ==
                                selectedAsset.id
                            ) {
                                selectedAsset
                            } else {
                                asset
                            }
                        }

                ProductDetailUiState.Success(
                    detail =
                        ShopArtistDetail(
                            artist =
                                artistDetail.artist,
                            introduction =
                                artistDetail.introduction,
                            assets = assets,
                            pass = pass,
                            hasPass =
                                pass.isActive,
                        ),
                    selectedAsset =
                        selectedAsset,
                )
            }
                .asResult()
                .onEach { result ->
                    _uiState.value =
                        when (result) {
                            Result.Loading ->
                                ProductDetailUiState
                                    .Loading

                            is Result.Success ->
                                result.data

                            is Result.Error ->
                                ProductDetailUiState
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

    fun selectAsset(
        assetId: Long,
    ) {
        val current =
            _uiState.value as?
                    ProductDetailUiState.Success
                ?: return

        if (
            current.selectedAsset.id ==
            assetId
        ) {
            return
        }

        loadAsset(
            assetId = assetId,
        )
    }

    fun purchaseSelectedAsset() {
        val current =
            _uiState.value as?
                    ProductDetailUiState.Success
                ?: return

        val asset =
            current.selectedAsset

        if (
            asset.availability !=
            AssetAvailability.LOCKED
        ) {
            return
        }

        if (!asset.purchasable) {
            return
        }

        if (
            paymentJob?.isActive ==
            true
        ) {
            return
        }

        paymentJob =
            paymentRepository
                .createAssetOrder(
                    assetId = asset.id,
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
                            val confirmation =
                                result.data

                            if (
                                confirmation !is
                                        PaymentConfirmation.Asset
                            ) {
                                emitPaymentError(
                                    IllegalStateException(
                                        "Expected ASSET payment confirmation.",
                                    ),
                                )

                                return@onEach
                            }

                            pendingOrderId =
                                null

                            loadAsset(
                                assetId =
                                    confirmation
                                        .assetId,
                            )
                        }

                        is Result.Error -> {
                            // 승인 결과가 불명확할 수 있으므로
                            // 여기서는 주문을 취소하지 않는다.
                            // 같은 paymentKey로 confirm 재시도가 가능한 상태를 유지한다.
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
                // 취소 API 호출이 실패해도
                // 서버에서 30분 후 EXPIRED 처리한다.
            }
            .launchIn(
                viewModelScope,
            )
    }

    private fun loadAsset(
        assetId: Long,
    ) {
        assetJob?.cancel()

        assetJob =
            motifRepository
                .getAssetDetail(
                    assetId = assetId,
                )
                .asResult()
                .onEach { result ->
                    if (
                        result !is
                                Result.Success
                    ) {
                        return@onEach
                    }

                    updateSelectedAsset(
                        result.data,
                    )
                }
                .launchIn(
                    viewModelScope,
                )
    }

    private fun updateSelectedAsset(
        asset: Asset,
    ) {
        val latest =
            _uiState.value as?
                    ProductDetailUiState.Success
                ?: return

        if (
            asset.artist.id !=
            latest.detail.artist.id
        ) {
            return
        }

        _uiState.update {
            latest.copy(
                detail =
                    latest.detail.copy(
                        assets =
                            latest.detail.assets
                                .map { item ->
                                    if (
                                        item.id ==
                                        asset.id
                                    ) {
                                        asset
                                    } else {
                                        item
                                    }
                                },
                    ),
                selectedAsset =
                    asset,
            )
        }
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