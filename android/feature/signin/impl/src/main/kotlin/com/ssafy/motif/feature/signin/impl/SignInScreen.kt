package com.ssafy.motif.feature.signin.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.motif.core.designsystem.component.GlassSurface
import com.ssafy.motif.core.designsystem.component.Icon
import com.ssafy.motif.core.designsystem.component.Text
import com.ssafy.motif.core.designsystem.glass.GlassBackdropState
import com.ssafy.motif.core.designsystem.glass.glassBackdrop
import com.ssafy.motif.core.designsystem.glass.rememberGlassBackdropState
import com.ssafy.motif.core.designsystem.theme.MotifTheme

@Composable
fun SignInScreen(
    onGoogleSignInClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val backdropState = rememberGlassBackdropState()

    Box(
        modifier = modifier.fillMaxSize(),
    ) {
        SignInBackground(
            backdropState = backdropState,
        )

        SignInGlassArea(
            backdropState = backdropState,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxWidth(0.5f)
                .fillMaxHeight(),
        )

        SignInLogo(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 48.dp, end = 10.dp),
        )

        GoogleSignInButton(
            onClick = onGoogleSignInClick,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(
                    horizontal = 60.dp,
                    vertical = 92.dp,
                )
                .fillMaxWidth()
                .height(56.dp),
        )
    }
}

@Composable
private fun SignInBackground(
    backdropState: GlassBackdropState,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .glassBackdrop(backdropState),
    ) {
        SignInBackgroundVideo(
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun SignInGlassArea(
    backdropState: GlassBackdropState,
    modifier: Modifier = Modifier,
) {
    GlassSurface(
        backdropState = backdropState,
        modifier = modifier,
        shape = RectangleShape,
        blurRadius = 8.dp,
        blurAlpha = 1f,
        tintColor = Color.White.copy(alpha = 0.08f),
        borderWidth = 0.dp,
    ) {}
}

@Composable
private fun SignInLogo(
    modifier: Modifier = Modifier,
) {
    val strokeWidth = with(LocalDensity.current) {
        1.5.dp.toPx()
    }

    Text(
        text = "MOTIF",
        modifier = modifier,
        color = MotifTheme.colors.white.copy(alpha = 0.6f),
        fontSize = 60.sp,
        style = MotifTheme.typography.titleSB18.copy(
            drawStyle = Stroke(
                width = strokeWidth,
            ),
        ),
    )
}

@Composable
private fun GoogleSignInButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .clickable(
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(
                R.drawable.ic_google,
            ),
            contentDescription = null,
            tint = Color.Unspecified,
            modifier = Modifier.size(24.dp),
        )

        Spacer(
            modifier = Modifier.width(12.dp),
        )

        Text(
            text = "Sign in with Google",
            color = Color.Black,
            style = MotifTheme.typography.bodySB16,
        )
    }
}

@Preview(name = "Sign In", showBackground = true)
@Composable
private fun SignInScreenPreview() {
    MotifTheme {
        SignInScreen(
            onGoogleSignInClick = {},
        )
    }
}