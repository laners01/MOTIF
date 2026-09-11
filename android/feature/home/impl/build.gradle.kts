plugins {
    alias(libs.plugins.motif.android.feature.impl)
    alias(libs.plugins.motif.android.library.compose)
}

android {
    namespace = "com.ssafy.motif.feature.home.impl"
}

dependencies {
    implementation(projects.feature.home.api)
    implementation(libs.coil.kt.compose)
}