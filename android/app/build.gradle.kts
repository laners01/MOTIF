plugins {
    alias(libs.plugins.motif.android.application)
    alias(libs.plugins.motif.android.application.compose)
    alias(libs.plugins.motif.hilt)
}

android {
    namespace = "com.ssafy.motif"

    defaultConfig {
        applicationId = "com.ssafy.motif"
        versionCode = 1
        versionName = "1.0"
    }
}

dependencies {
    implementation(projects.feature.home.api)
    implementation(projects.feature.home.impl)
    implementation(projects.feature.onboarding.api)
    implementation(projects.feature.onboarding.impl)

    implementation(projects.core.data)
    implementation(projects.core.designsystem)
    implementation(projects.core.navigation)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
}