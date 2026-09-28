plugins {
    alias(libs.plugins.motif.android.library)
    alias(libs.plugins.motif.android.room)
    alias(libs.plugins.motif.hilt)
}

android {
    namespace = "com.ssafy.motif.core.database"
}

dependencies {
    api(projects.core.model)
    implementation(libs.kotlinx.serialization.json)

    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}