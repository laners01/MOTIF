plugins {
    alias(libs.plugins.motif.android.library)
    alias(libs.plugins.motif.hilt)
    alias(libs.plugins.kotlinx.serialization)
}

android {
    namespace = "com.ssafy.motif.core.network"
}

dependencies {
    implementation(projects.core.common)

    implementation(libs.sandwich)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlin.serialization)

    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.arch.core.testing)
}
