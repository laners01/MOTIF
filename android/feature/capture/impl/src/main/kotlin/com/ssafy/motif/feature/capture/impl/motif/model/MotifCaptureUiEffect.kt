package com.ssafy.motif.feature.capture.impl.motif.model

internal sealed interface MotifCaptureUiEffect {

    data class Error(
        val throwable: Throwable,
    ) : MotifCaptureUiEffect
}
