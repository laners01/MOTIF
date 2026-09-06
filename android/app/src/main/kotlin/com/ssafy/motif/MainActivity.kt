package com.ssafy.motif

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.ssafy.motif.core.data.util.NetworkMonitor
import com.ssafy.motif.core.designsystem.theme.MotifTheme
import com.ssafy.motif.ui.MotifApp
import com.ssafy.motif.ui.rememberMotifAppState
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var networkMonitor: NetworkMonitor

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(
                scrim = Color.TRANSPARENT,
            ),
            navigationBarStyle = SystemBarStyle.dark(
                scrim = Color.TRANSPARENT,
            ),
        )

        setContent {
            val appState = rememberMotifAppState(
                networkMonitor = networkMonitor,
            )

            MotifTheme {
                MotifApp(
                    appState = appState,
                )
            }
        }
    }
}