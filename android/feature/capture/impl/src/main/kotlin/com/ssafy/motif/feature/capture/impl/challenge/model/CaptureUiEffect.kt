package com.ssafy.motif.feature.capture.impl.challenge.model

internal sealed interface CaptureUiEffect {

    data class Error(
        val throwable: Throwable,
    ) : CaptureUiEffect
}