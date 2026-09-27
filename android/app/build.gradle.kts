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
    implementation(projects.feature.signin.api)
    implementation(projects.feature.signin.impl)
    implementation(projects.feature.home.api)
    implementation(projects.feature.home.impl)
    implementation(projects.feature.challenges.api)
    implementation(projects.feature.challenges.impl)
    implementation(projects.feature.onboarding.api)
    implementation(projects.feature.onboarding.impl)
    implementation(projects.feature.ranking.api)
    implementation(projects.feature.ranking.impl)
    implementation(projects.feature.artistdetail.api)
    implementation(projects.feature.artistdetail.impl)
    implementation(projects.feature.mypage.api)
    implementation(projects.feature.mypage.impl)
    implementation(projects.feature.motif.api)
    implementation(projects.feature.motif.impl)
    implementation(projects.feature.capture.api)
    implementation(projects.feature.capture.impl)
    implementation(projects.core.notifications)

    implementation(projects.core.data)
    implementation(projects.core.designsystem)
    implementation(projects.core.navigation)

    implementation(libs.coil.kt.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
}