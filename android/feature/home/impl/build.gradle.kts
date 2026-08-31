plugins {
    alias(libs.plugins.modif.android.feature.impl)
    alias(libs.plugins.modif.android.library.compose)
}

android {
    namespace = "com.ssafy.modif.feature.home.impl"
}

dependencies {
    implementation(projects.feature.home.api)
}