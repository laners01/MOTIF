package com.ssafy.motif.feature.capture.impl.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.net.Uri
import android.util.Log
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.ssafy.motif.feature.capture.impl.segmentation.PersonSegmentationProcessor
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

internal class CaptureCameraController(
    private val context: Context,
    private val segmentationProcessor: PersonSegmentationProcessor,
) {

    private val mainExecutor =
        ContextCompat.getMainExecutor(
            context,
        )

    private val analysisExecutor: ExecutorService =
        Executors.newSingleThreadExecutor()

    private var cameraProvider:
            ProcessCameraProvider? =
        null

    private var imageAnalysis:
            ImageAnalysis? =
        null

    private var videoCapture:
            VideoCapture<Recorder>? =
        null

    private var recording:
            Recording? =
        null

    fun bind(
        lifecycleOwner: LifecycleOwner,
        targetRotation: Int,
        onReady: () -> Unit,
        onSegmentedFrame: (Bitmap) -> Unit,
        onError: (Throwable) -> Unit,
    ) {
        val cameraProviderFuture =
            ProcessCameraProvider
                .getInstance(
                    context,
                )

        cameraProviderFuture
            .addListener(
                {
                    runCatching {
                        val provider =
                            cameraProviderFuture
                                .get()

                        val analysis =
                            createImageAnalysis(
                                targetRotation =
                                    targetRotation,
                                onSegmentedFrame =
                                    onSegmentedFrame,
                                onError =
                                    onError,
                            )

                        val capture =
                            createVideoCapture(
                                targetRotation =
                                    targetRotation,
                            )

                        provider.unbindAll()

                        provider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector
                                .DEFAULT_FRONT_CAMERA,
                            analysis,
                            capture,
                        )

                        cameraProvider =
                            provider

                        imageAnalysis =
                            analysis

                        videoCapture =
                            capture

                        onReady()
                    }.onFailure(
                        onError,
                    )
                },
                mainExecutor,
            )
    }

    private fun createImageAnalysis(
        targetRotation: Int,
        onSegmentedFrame: (Bitmap) -> Unit,
        onError: (Throwable) -> Unit,
    ): ImageAnalysis {
        val resolutionSelector =
            ResolutionSelector.Builder()
                .setAspectRatioStrategy(
                    AspectRatioStrategy
                        .RATIO_16_9_FALLBACK_AUTO_STRATEGY,
                )
                .setResolutionStrategy(
                    ResolutionStrategy(
                        SEGMENTATION_RESOLUTION,
                        ResolutionStrategy
                            .FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER,
                    ),
                )
                .build()

        return ImageAnalysis.Builder()
            .setResolutionSelector(
                resolutionSelector,
            )
            .setTargetRotation(
                targetRotation,
            )
            .setBackpressureStrategy(
                ImageAnalysis
                    .STRATEGY_KEEP_ONLY_LATEST,
            )
            .setOutputImageFormat(
                ImageAnalysis
                    .OUTPUT_IMAGE_FORMAT_YUV_420_888,
            )
            .build()
            .also { analysis ->
                analysis.setAnalyzer(
                    analysisExecutor,
                ) { imageProxy ->
                    if (
                        imageProxy.format !=
                        ImageFormat.YUV_420_888
                    ) {
                        val throwable =
                            IllegalStateException(
                                "Unsupported ImageProxy format: " +
                                        "${imageProxy.format}",
                            )

                        imageProxy.close()

                        mainExecutor.execute {
                            onError(
                                throwable,
                            )
                        }

                        return@setAnalyzer
                    }

                    segmentationProcessor
                        .process(
                            imageProxy =
                                imageProxy,
                            onResult = { bitmap ->
                                mainExecutor.execute {
                                    onSegmentedFrame(
                                        bitmap,
                                    )
                                }
                            },
                            onError = { throwable ->
                                mainExecutor.execute {
                                    onError(
                                        throwable,
                                    )
                                }
                            },
                        )
                }
            }
    }

    private fun createVideoCapture(
        targetRotation: Int,
    ): VideoCapture<Recorder> {
        val qualitySelector =
            QualitySelector.from(
                Quality.FHD,
                FallbackStrategy
                    .lowerQualityOrHigherThan(
                        Quality.FHD,
                    ),
            )

        val recorder =
            Recorder.Builder()
                .setQualitySelector(
                    qualitySelector,
                )
                .build()

        return VideoCapture
            .withOutput(
                recorder,
            )
            .apply {
                this.targetRotation =
                    targetRotation
            }
    }

    fun startRecording(
        onStarted: () -> Unit,
        onProgress: (Long) -> Unit,
        onFinished: (Uri) -> Unit,
        onError: (Throwable) -> Unit,
    ) {
        if (
            recording != null
        ) {
            return
        }

        val capture =
            videoCapture
                ?: run {
                    onError(
                        IllegalStateException(
                            "VideoCapture is not ready",
                        ),
                    )

                    return
                }

        val outputFile =
            File(
                context.cacheDir,
                "capture_" +
                        "${System.currentTimeMillis()}.mp4",
            )

        val outputOptions =
            FileOutputOptions.Builder(
                outputFile,
            )
                .build()

        recording =
            capture.output
                .prepareRecording(
                    context,
                    outputOptions,
                )
                .start(
                    mainExecutor,
                ) { event ->
                    when (event) {
                        is VideoRecordEvent.Start -> {
                            onStarted()
                        }

                        is VideoRecordEvent.Status -> {
                            val elapsedTimeMs =
                                event
                                    .recordingStats
                                    .recordedDurationNanos /
                                        NANOS_PER_MILLISECOND

                            onProgress(
                                elapsedTimeMs,
                            )
                        }

                        is VideoRecordEvent.Finalize -> {
                            recording =
                                null

                            if (
                                event.hasError()
                            ) {
                                Log.e(
                                    TAG,
                                    "Video recording failed: ${event.error}",
                                )

                                onError(
                                    IllegalStateException(
                                        "Video recording failed: ${event.error}",
                                    ),
                                )
                            } else {
                                onFinished(
                                    Uri.fromFile(
                                        outputFile,
                                    ),
                                )
                            }
                        }
                    }
                }
    }

    fun stopRecording() {
        recording?.stop()
    }

    fun release() {
        recording?.stop()

        recording =
            null

        imageAnalysis
            ?.clearAnalyzer()

        cameraProvider
            ?.unbindAll()

        imageAnalysis =
            null

        videoCapture =
            null

        cameraProvider =
            null

        analysisExecutor
            .shutdownNow()
    }

    private companion object {

        const val TAG =
            "CaptureCamera"

        val SEGMENTATION_RESOLUTION =
            Size(
                480,
                854,
            )

        const val NANOS_PER_MILLISECOND =
            1_000_000L
    }
}