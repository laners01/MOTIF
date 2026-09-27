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
    implementation(projects.core.data)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)

    implementation(libs.androidx.dataStore.preferences)
}