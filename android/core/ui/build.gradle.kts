plugins {
    alias(libs.plugins.motif.android.library)
    alias(libs.plugins.motif.android.library.compose)
}

android {
    namespace = "com.ssafy.motif.core.ui"
}

dependencies {
    api(projects.core.common)
    api(projects.core.designsystem)
    api(projects.core.model)

    implementation(libs.androidx.core.ktx)
    implementation(libs.coil.kt)
    implementation(libs.coil.kt.compose)
}
