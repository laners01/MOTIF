plugins {
    alias(libs.plugins.modif.android.library)
    alias(libs.plugins.modif.android.room)
    alias(libs.plugins.modif.hilt)
}

android {
    namespace = "com.ssafy.modif.core.database"
}

dependencies {
    api(projects.core.model)
    implementation(libs.kotlinx.serialization.json)

    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}