package com.ssafy.modif

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.ssafy.modif.core.data.util.NetworkMonitor
import com.ssafy.modif.core.designsystem.theme.ModifTheme
import com.ssafy.modif.ui.ModifApp
import com.ssafy.modif.ui.rememberModifAppState
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
            val appState = rememberModifAppState(
                networkMonitor = networkMonitor,
            )

            ModifTheme {
                ModifApp(
                    appState = appState,
                )
            }
        }
    }
}