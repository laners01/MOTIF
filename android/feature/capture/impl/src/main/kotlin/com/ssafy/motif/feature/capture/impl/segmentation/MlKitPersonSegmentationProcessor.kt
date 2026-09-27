package com.ssafy.motif.feature.capture.impl.segmentation

import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.SegmentationMask
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

internal class MlKitPersonSegmentationProcessor :
    PersonSegmentationProcessor {

    private val segmenter =
        Segmentation.getClient(
            SelfieSegmenterOptions.Builder()
                .setDetectorMode(
                    SelfieSegmenterOptions.STREAM_MODE,
                )
                .build(),
        )

    private val postProcessingExecutor: ExecutorService =
        Executors.newSingleThreadExecutor()

    private val isProcessing =
        AtomicBoolean(false)

    private var lastProcessedTimeNs =
        0L

    private var sourcePixels =
        IntArray(0)

    private var maskValues =
        FloatArray(0)

    @OptIn(ExperimentalGetImage::class)
    override fun process(
        imageProxy: ImageProxy,
        onResult: (Bitmap) -> Unit,
        onError: (Throwable) -> Unit,
    ) {
        if (
            !isProcessing.compareAndSet(
                false,
                true,
            )
        ) {
            imageProxy.close()
            return
        }

        if (!shouldProcessFrame()) {
            finishProcessing(
                imageProxy,
            )
            return
        }

        val mediaImage =
            imageProxy.image

        if (mediaImage == null) {
            finishProcessing(
                imageProxy,
            )
            return
        }

        val rotationDegrees =
            imageProxy.imageInfo
                .rotationDegrees

        val inputImage =
            runCatching {
                InputImage.fromMediaImage(
                    mediaImage,
                    rotationDegrees,
                )
            }.getOrElse { throwable ->
                finishProcessing(
                    imageProxy,
                )

                onError(
                    throwable,
                )

                return
            }

        runCatching {
            segmenter
                .process(
                    inputImage,
                )
                .addOnSuccessListener(
                    postProcessingExecutor,
                ) { segmentationMask ->
                    runCatching {
                        val cameraBitmap =
                            imageProxy.toBitmap()

                        val rotatedBitmap =
                            cameraBitmap.rotate(
                                rotationDegrees,
                            )

                        createSegmentedBitmap(
                            source =
                                rotatedBitmap,
                            mask =
                                segmentationMask,
                        )
                    }.onSuccess { bitmap ->
                        onResult(
                            bitmap,
                        )
                    }.onFailure { throwable ->
                        onError(
                            throwable,
                        )
                    }
                }
                .addOnFailureListener(
                    postProcessingExecutor,
                ) { throwable ->
                    onError(
                        throwable,
                    )
                }
                .addOnCompleteListener(
                    postProcessingExecutor,
                ) {
                    finishProcessing(
                        imageProxy,
                    )
                }
        }.onFailure { throwable ->
            finishProcessing(
                imageProxy,
            )

            onError(
                throwable,
            )
        }
    }

    override fun close() {
        segmenter.close()

        postProcessingExecutor
            .shutdownNow()
    }

    private fun shouldProcessFrame():
            Boolean {
        val currentTimeNs =
            SystemClock
                .elapsedRealtimeNanos()

        val elapsedTimeNs =
            currentTimeNs -
                    lastProcessedTimeNs

        if (
            elapsedTimeNs <
            MIN_FRAME_INTERVAL_NS
        ) {
            return false
        }

        lastProcessedTimeNs =
            currentTimeNs

        return true
    }

    private fun createSegmentedBitmap(
        source: Bitmap,
        mask: SegmentationMask,
    ): Bitmap {
        val sourceWidth =
            source.width

        val sourceHeight =
            source.height

        val sourcePixelCount =
            sourceWidth *
                    sourceHeight

        ensureSourcePixelCapacity(
            sourcePixelCount,
        )

        source.getPixels(
            sourcePixels,
            0,
            sourceWidth,
            0,
            0,
            sourceWidth,
            sourceHeight,
        )

        val maskWidth =
            mask.width

        val maskHeight =
            mask.height

        val maskPixelCount =
            maskWidth *
                    maskHeight

        ensureMaskCapacity(
            maskPixelCount,
        )

        val maskBuffer =
            mask.buffer.apply {
                rewind()
            }

        for (
        index in
        0 until maskPixelCount
        ) {
            maskValues[index] =
                maskBuffer.float
        }

        for (
        y in
        0 until sourceHeight
        ) {
            val maskY =
                (
                        y.toFloat() /
                                sourceHeight *
                                maskHeight
                        )
                    .toInt()
                    .coerceIn(
                        0,
                        maskHeight - 1,
                    )

            val sourceRowOffset =
                y * sourceWidth

            val maskRowOffset =
                maskY * maskWidth

            for (
            x in
            0 until sourceWidth
            ) {
                val maskX =
                    (
                            x.toFloat() /
                                    sourceWidth *
                                    maskWidth
                            )
                        .toInt()
                        .coerceIn(
                            0,
                            maskWidth - 1,
                        )

                val confidence =
                    maskValues[
                        maskRowOffset +
                                maskX
                    ]

                val alpha =
                    confidenceToAlpha(
                        confidence,
                    )

                val pixelIndex =
                    sourceRowOffset +
                            x

                val sourcePixel =
                    sourcePixels[
                        pixelIndex
                    ]

                sourcePixels[
                    pixelIndex
                ] =
                    (alpha shl ALPHA_SHIFT) or
                            (
                                    sourcePixel and
                                            RGB_MASK
                                    )
            }
        }

        return Bitmap.createBitmap(
            sourcePixels,
            sourceWidth,
            sourceHeight,
            Bitmap.Config.ARGB_8888,
        )
    }

    private fun ensureSourcePixelCapacity(
        requiredSize: Int,
    ) {
        if (
            sourcePixels.size !=
            requiredSize
        ) {
            sourcePixels =
                IntArray(
                    requiredSize,
                )
        }
    }

    private fun ensureMaskCapacity(
        requiredSize: Int,
    ) {
        if (
            maskValues.size !=
            requiredSize
        ) {
            maskValues =
                FloatArray(
                    requiredSize,
                )
        }
    }

    private fun finishProcessing(
        imageProxy: ImageProxy,
    ) {
        runCatching {
            imageProxy.close()
        }

        isProcessing.set(
            false,
        )
    }

    private companion object {

        const val TARGET_SEGMENTATION_FPS =
            18L

        const val NANOS_PER_SECOND =
            1_000_000_000L

        const val MIN_FRAME_INTERVAL_NS =
            NANOS_PER_SECOND /
                    TARGET_SEGMENTATION_FPS

        const val ALPHA_SHIFT =
            24

        const val RGB_MASK =
            0x00FFFFFF
    }
}

private fun Bitmap.rotate(
    rotationDegrees: Int,
): Bitmap {
    if (
        rotationDegrees == 0
    ) {
        return this
    }

    val matrix =
        Matrix().apply {
            postRotate(
                rotationDegrees
                    .toFloat(),
            )
        }

    return Bitmap.createBitmap(
        this,
        0,
        0,
        width,
        height,
        matrix,
        true,
    )
}

private fun confidenceToAlpha(
    confidence: Float,
): Int {
    val normalized =
        (
                (confidence -
                        FOREGROUND_START) /
                        (
                                FOREGROUND_END -
                                        FOREGROUND_START
                                )
                )
            .coerceIn(
                0f,
                1f,
            )

    return (
            normalized *
                    MAX_ALPHA
            )
        .roundToInt()
}

private const val FOREGROUND_START =
    0.25f

private const val FOREGROUND_END =
    0.75f

private const val MAX_ALPHA =
    255