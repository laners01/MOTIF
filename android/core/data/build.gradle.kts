plugins {
    alias(libs.plugins.motif.android.library)
    alias(libs.plugins.motif.hilt)
}

android {
    namespace = "com.ssafy.motif.core.data"
}

dependencies {
    api(projects.core.common)
    api(projects.core.database)
    api(projects.core.datastore)
    api(projects.core.network)

    implementation(libs.androidx.dataStore)
    implementation(libs.retrofit)

    testImplementation(libs.junit)
    testImplementation(libs.turbine)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.kotlinx.serialization.json)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.kotlin)
}
