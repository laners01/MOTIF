package com.ssafy.motif.feature.capture.impl.motif.model

enum class MotifCaptureProcessingState {
    Idle,
    PreparingUpload,
    Uploading,
    RequestingComposite,
    Queued,
    Failed,
}
