package com.ssafy.motif.feature.capture.impl.challenge.model

enum class CaptureProcessingState {
    Idle,
    PreparingUpload,
    Uploading,
    RequestingComposite,
    Queued,
    Failed,
}