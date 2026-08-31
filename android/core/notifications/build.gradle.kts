plugins {
    alias(libs.plugins.modif.android.library)
    alias(libs.plugins.modif.hilt)
}

android {
    namespace = "com.ssafy.modif.core.notifications"
}

dependencies {
    api(projects.core.model)

    implementation(projects.core.common)

    compileOnly(platform(libs.androidx.compose.bom))
}
