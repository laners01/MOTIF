plugins {
    alias(libs.plugins.motif.android.feature.impl)
    alias(libs.plugins.motif.android.library.compose)
    alias(libs.plugins.motif.hilt)
}

android {
    namespace = "com.ssafy.motif.feature.artistdetail.impl"
}

dependencies {
    implementation(projects.feature.challenges.api)
    implementation(projects.core.data)
    implementation(libs.androidx.lifecycle.runtimeCompose)
    implementation(libs.coil.kt.compose)
    implementation(projects.feature.artistdetail.api)
}
