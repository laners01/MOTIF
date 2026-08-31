plugins {
    alias(libs.plugins.modif.android.library)
    alias(libs.plugins.modif.android.library.compose)
}

android {
    namespace = "com.ssafy.modif.core.ui"
}

dependencies {
    api(projects.core.common)
    api(projects.core.designsystem)
    api(projects.core.model)

    implementation(libs.androidx.core.ktx)
    implementation(libs.coil.kt)
    implementation(libs.coil.kt.compose)
}
