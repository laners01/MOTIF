plugins {
    alias(libs.plugins.motif.android.feature.impl)
    alias(libs.plugins.motif.android.library.compose)
}

android {
    namespace = "com.ssafy.motif.feature.challenges.impl"
}

dependencies {
    implementation(libs.coil.kt.compose)
}