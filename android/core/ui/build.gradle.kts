plugins {
    alias(libs.plugins.motif.android.library)
    alias(libs.plugins.motif.android.library.compose)
}

android {
    namespace = "com.ssafy.motif.core.ui"
}

dependencies {
    implementation(projects.core.designsystem)
    implementation(projects.core.model)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.coil.kt.compose)

    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.ui.compose)

    implementation(libs.androidx.media3.datasource)
    implementation(libs.androidx.media3.database)
}
