plugins {
    alias(libs.plugins.motif.android.feature.impl)
    alias(libs.plugins.motif.android.library.compose)
}

android {
    namespace = "com.ssafy.motif.feature.capture.impl"
}

dependencies {
    implementation(projects.feature.capture.api)

    implementation(libs.coil.kt.compose)

    // CameraX
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.video)

    // Media3
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.ui.compose)

    // ML Kit
    implementation(libs.segmentation.selfie)
}