plugins {
    alias(libs.plugins.motif.android.feature.impl)
    alias(libs.plugins.motif.android.library.compose)
}

android {
    namespace = "com.ssafy.motif.feature.signin.impl"
}

dependencies {
    implementation(projects.feature.signin.api)
    implementation(libs.androidx.media3.exoplayer)
}