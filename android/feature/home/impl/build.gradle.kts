plugins {
    alias(libs.plugins.motif.android.feature.impl)
    alias(libs.plugins.motif.android.library.compose)
    alias(libs.plugins.motif.hilt)
}

android {
    namespace = "com.ssafy.motif.feature.home.impl"
}

dependencies {
    implementation(projects.feature.home.api)
    implementation(projects.core.data)

    implementation(libs.coil.kt.compose)
}