plugins {
    alias(libs.plugins.modif.android.application)
    alias(libs.plugins.modif.android.application.compose)
    alias(libs.plugins.modif.hilt)
}

android {
    namespace = "com.ssafy.modif"

    defaultConfig {
        applicationId = "com.ssafy.modif"
        versionCode = 1
        versionName = "1.0"
    }
}

dependencies {
    implementation(projects.feature.home.api)

    implementation(projects.core.data)
    implementation(projects.core.designsystem)
    implementation(projects.core.navigation)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
}