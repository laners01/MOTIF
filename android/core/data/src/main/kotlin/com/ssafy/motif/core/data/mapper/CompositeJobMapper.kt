package com.ssafy.motif.core.data.mapper

import com.ssafy.motif.core.model.composite.ChallengeCompositeJob
import com.ssafy.motif.core.model.composite.CompositeJobRequestResult
import com.ssafy.motif.core.model.composite.CompositeJobRetryResult
import com.ssafy.motif.core.model.composite.CompositeJobStatus
import com.ssafy.motif.core.model.composite.CompositeJobUpload
import com.ssafy.motif.core.network.model.composite.CompositeJobResponse
import com.ssafy.motif.core.network.model.composite.CreateCompositeJobResponse
import com.ssafy.motif.core.network.model.composite.RequestCompositeJobResponse
import com.ssafy.motif.core.network.model.composite.RetryCompositeJobResponse
import com.ssafy.motif.core.network.model.composite.CompositeJobStatus as NetworkCompositeJobStatus

internal fun CreateCompositeJobResponse.toModel():
        CompositeJobUpload =
    CompositeJobUpload(
        jobUuid = jobUuid,
        uploadUrl = uploadUrl,
        expiresIn = expiresIn,
    )

internal fun RequestCompositeJobResponse.toModel():
        CompositeJobRequestResult =
    CompositeJobRequestResult(
        jobUuid = jobUuid,
        status = status.toModel(),
    )

internal fun CompositeJobResponse.toChallengeModel():
        ChallengeCompositeJob =
    ChallengeCompositeJob(
        jobUuid = jobUuid,
        status = status.toModel(),
        attempt = attempt,
        failCode = failCode,
        resultVideoUrl = resultVideoUrl,
        resultThumbnailUrl =
            resultThumbnailUrl,
    )

internal fun RetryCompositeJobResponse.toModel():
        CompositeJobRetryResult =
    CompositeJobRetryResult(
        jobUuid = jobUuid,
        status = status.toModel(),
        attempt = attempt,
    )

private fun NetworkCompositeJobStatus.toModel():
        CompositeJobStatus =
    when (this) {
        NetworkCompositeJobStatus.UPLOADED ->
            CompositeJobStatus.UPLOADED

        NetworkCompositeJobStatus.QUEUED ->
            CompositeJobStatus.QUEUED

        NetworkCompositeJobStatus.PROCESSING ->
            CompositeJobStatus.PROCESSING

        NetworkCompositeJobStatus.DONE ->
            CompositeJobStatus.DONE

        NetworkCompositeJobStatus.FAILED ->
            CompositeJobStatus.FAILED
    }