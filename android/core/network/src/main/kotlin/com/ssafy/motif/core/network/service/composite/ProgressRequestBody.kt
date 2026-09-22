package com.ssafy.motif.core.network.service.composite

import okhttp3.MediaType
import okhttp3.RequestBody
import okio.Buffer
import okio.BufferedSink
import okio.ForwardingSink
import okio.buffer

internal class ProgressRequestBody(
    private val delegate: RequestBody,
    private val onProgress: (Int) -> Unit,
) : RequestBody() {

    override fun contentType(): MediaType? =
        delegate.contentType()

    override fun contentLength(): Long =
        delegate.contentLength()

    override fun writeTo(
        sink: BufferedSink,
    ) {
        val contentLength =
            contentLength()

        var writtenBytes = 0L
        var lastProgress = -1

        val progressSink =
            object : ForwardingSink(sink) {

                override fun write(
                    source: Buffer,
                    byteCount: Long,
                ) {
                    super.write(
                        source,
                        byteCount,
                    )

                    writtenBytes +=
                        byteCount

                    if (contentLength <= 0L) {
                        return
                    }

                    val progress =
                        (
                                writtenBytes *
                                        MAX_PROGRESS /
                                        contentLength
                                )
                            .toInt()
                            .coerceIn(
                                MIN_PROGRESS,
                                MAX_PROGRESS,
                            )

                    if (
                        progress ==
                        lastProgress
                    ) {
                        return
                    }

                    lastProgress =
                        progress

                    onProgress(
                        progress,
                    )
                }
            }

        val bufferedSink =
            progressSink.buffer()

        delegate.writeTo(
            bufferedSink,
        )

        bufferedSink.flush()

        if (
            lastProgress <
            MAX_PROGRESS
        ) {
            onProgress(
                MAX_PROGRESS,
            )
        }
    }

    private companion object {
        const val MIN_PROGRESS = 0
        const val MAX_PROGRESS = 100
    }
}