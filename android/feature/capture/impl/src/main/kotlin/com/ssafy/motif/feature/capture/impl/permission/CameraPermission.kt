package com.ssafy.motif.feature.capture.impl.permission

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ssafy.motif.core.designsystem.component.LoadingWheel
import com.ssafy.motif.core.designsystem.component.Text
import com.ssafy.motif.core.designsystem.theme.MotifTheme
import com.ssafy.motif.feature.capture.impl.R
import com.ssafy.motif.core.designsystem.R as DesignSystemR

internal data class CameraPermissionState(
    val isGranted: Boolean,
    val isRequesting: Boolean,
    val requestPermission: () -> Unit,
)

@Composable
internal fun rememberCameraPermissionState(
    requestOnLaunch: Boolean = true,
): CameraPermissionState {
    val context = androidx.compose.ui.platform.LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var isGranted by remember(context) {
        mutableStateOf(
            context.hasCameraPermission(),
        )
    }

    var isRequesting by remember {
        mutableStateOf(false)
    }

    val permissionLauncher =
        rememberLauncherForActivityResult(
            contract =
                ActivityResultContracts.RequestPermission(),
        ) { granted ->
            isGranted = granted
            isRequesting = false
        }

    val requestPermission: () -> Unit = {
        if (context.hasCameraPermission()) {
            isGranted = true
            isRequesting = false
        } else if (!isRequesting) {
            isRequesting = true

            permissionLauncher.launch(
                Manifest.permission.CAMERA,
            )
        }
    }

    LaunchedEffect(
        requestOnLaunch,
    ) {
        if (
            requestOnLaunch &&
            !isGranted
        ) {
            requestPermission()
        }
    }

    /*
     * 앱 설정으로 이동해서 카메라 권한을 직접 변경한 뒤
     * 다시 앱으로 돌아오는 경우도 반영한다.
     */
    DisposableEffect(
        lifecycleOwner,
        context,
    ) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (
                    event ==
                    Lifecycle.Event.ON_RESUME
                ) {
                    val granted =
                        context.hasCameraPermission()

                    isGranted = granted

                    if (granted) {
                        isRequesting = false
                    }
                }
            }

        lifecycleOwner.lifecycle
            .addObserver(
                observer,
            )

        onDispose {
            lifecycleOwner.lifecycle
                .removeObserver(
                    observer,
                )
        }
    }

    return CameraPermissionState(
        isGranted = isGranted,
        isRequesting = isRequesting,
        requestPermission = requestPermission,
    )
}

@Composable
internal fun CameraPermissionGate(
    permissionState: CameraPermissionState,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    when {
        permissionState.isGranted -> {
            Box(
                modifier =
                    modifier.fillMaxSize(),
            ) {
                content()
            }
        }

        permissionState.isRequesting -> {
            CameraPermissionRequestingContent(
                onBackClick = onBackClick,
                modifier = modifier,
            )
        }

        else -> {
            CameraPermissionDeniedContent(
                onBackClick = onBackClick,
                onRequestPermission =
                    permissionState.requestPermission,
                modifier = modifier,
            )
        }
    }
}

@Composable
private fun CameraPermissionRequestingContent(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(Color.Black),
        contentAlignment =
            Alignment.Center,
    ) {
        LoadingWheel()

        CameraPermissionBackButton(
            onClick = onBackClick,
            modifier =
                Modifier
                    .align(
                        Alignment.TopStart,
                    )
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing,
                    )
                    .padding(
                        top = 8.dp,
                        start = 12.dp,
                    ),
        )
    }
}

@Composable
private fun CameraPermissionDeniedContent(
    onBackClick: () -> Unit,
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(Color.Black),
    ) {
        Column(
            modifier =
                Modifier.align(
                    Alignment.Center,
                ),
            horizontalAlignment =
                Alignment.CenterHorizontally,
        ) {
            BasicText(
                text =
                    stringResource(
                        R.string.camera_permission_title,
                    ),
                style =
                    TextStyle(
                        color = Color.White,
                        fontSize = 22.sp,
                        lineHeight = 28.sp,
                        fontWeight =
                            FontWeight.Bold,
                        textAlign =
                            TextAlign.Center,
                    ),
            )

            Spacer(
                modifier =
                    Modifier.height(
                        12.dp,
                    ),
            )

            BasicText(
                text =
                    stringResource(
                        R.string.camera_permission_description,
                    ),
                style =
                    TextStyle(
                        color =
                            Color.White.copy(
                                alpha = 0.72f,
                            ),
                        fontSize = 15.sp,
                        lineHeight = 22.sp,
                        textAlign =
                            TextAlign.Center,
                    ),
            )

            Spacer(
                modifier =
                    Modifier.height(
                        28.dp,
                    ),
            )

            CameraPermissionButton(
                onClick =
                    onRequestPermission,
            )
        }

        CameraPermissionBackButton(
            onClick = onBackClick,
            modifier =
                Modifier
                    .align(
                        Alignment.TopStart,
                    )
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing,
                    )
                    .padding(
                        top = 8.dp,
                        start = 12.dp,
                    ),
        )
    }
}

@Composable
private fun CameraPermissionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource =
        remember {
            MutableInteractionSource()
        }

    Box(
        modifier =
            modifier
                .width(
                    220.dp,
                )
                .height(
                    52.dp,
                )
                .clip(
                    RoundedCornerShape(
                        12.dp,
                    ),
                )
                .background(
                    Color.White,
                )
                .clickable(
                    interactionSource =
                        interactionSource,
                    indication = null,
                    onClick = onClick,
                ),
        contentAlignment =
            Alignment.Center,
    ) {
        Text(
            text =
                stringResource(
                    R.string.camera_permission_allow,
                ),
            style = MotifTheme.typography.bodySB16,
            color = MotifTheme.colors.black
        )
    }
}

@Composable
private fun CameraPermissionBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource =
        remember {
            MutableInteractionSource()
        }

    Box(
        modifier =
            modifier
                .size(
                    48.dp,
                )
                .clickable(
                    interactionSource =
                        interactionSource,
                    indication = null,
                    onClick = onClick,
                ),
        contentAlignment =
            Alignment.Center,
    ) {
        Image(
            painter =
                painterResource(
                    DesignSystemR.drawable
                        .ic_arrow_left_24,
                ),
            contentDescription =
                stringResource(
                    R.string.capture_back,
                ),
            colorFilter =
                ColorFilter.tint(
                    Color.White,
                ),
            modifier =
                Modifier.size(
                    32.dp,
                ),
        )
    }
}

private fun Context.hasCameraPermission(): Boolean =
    ContextCompat.checkSelfPermission(
        this,
        Manifest.permission.CAMERA,
    ) == PackageManager.PERMISSION_GRANTED