plugins {
    alias(libs.plugins.motif.android.library)
    alias(libs.plugins.motif.hilt)
}

android {
    namespace = "com.ssafy.motif.core.notifications"
}

dependencies {
    api(projects.core.model)

    implementation(projects.core.common)

    compileOnly(platform(libs.androidx.compose.bom))
}
