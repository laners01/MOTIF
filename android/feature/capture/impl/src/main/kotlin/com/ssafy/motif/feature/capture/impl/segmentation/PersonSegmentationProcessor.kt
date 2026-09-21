package com.ssafy.motif.feature.capture.impl.segmentation

import android.graphics.Bitmap
import androidx.camera.core.ImageProxy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember

internal interface PersonSegmentationProcessor {

    fun process(
        imageProxy: ImageProxy,
        onResult: (Bitmap) -> Unit,
        onError: (Throwable) -> Unit,
    )

    fun close()
}

@Composable
internal fun rememberPersonSegmentationProcessor():
        PersonSegmentationProcessor {
    val processor =
        remember {
            MlKitPersonSegmentationProcessor()
        }

    DisposableEffect(
        processor,
    ) {
        onDispose {
            processor.close()
        }
    }

    return processor
}