plugins {
    alias(libs.plugins.motif.android.library)
    alias(libs.plugins.motif.android.library.compose)
}

android {
    namespace = "com.ssafy.motif.core.designsystem"
}

dependencies {
    api(platform(libs.androidx.compose.bom))

    // splash screen
    api(libs.androidx.core.splashscreen)

    api(libs.androidx.compose.runtime)
    api(libs.androidx.compose.ui)
    api(libs.androidx.compose.ui.tooling)
    api(libs.androidx.compose.ui.tooling.preview)
    api(libs.androidx.compose.animation)
    api(libs.androidx.compose.foundation)
    api(libs.androidx.compose.foundation.layout)
}
