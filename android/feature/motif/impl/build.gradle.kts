plugins {
    alias(libs.plugins.motif.android.feature.impl)
    alias(libs.plugins.motif.android.library.compose)
    alias(libs.plugins.motif.hilt)
}

android {
    namespace = "com.ssafy.motif.feature.motif.impl"
}

dependencies {
    implementation(projects.feature.motif.api)
    implementation(projects.core.data)

    implementation(libs.coil.kt.compose)
    implementation(libs.androidx.lifecycle.runtimeCompose)
}
