plugins {
    alias(libs.plugins.motif.android.library)
    alias(libs.plugins.motif.hilt)
}

android {
    namespace = "com.ssafy.motif.core.datastore"

    defaultConfig {
        consumerProguardFiles("consumer-rules.pro")
    }
}

dependencies {
    api(projects.core.common)
    api(projects.core.model)
    api(projects.core.datastoreProto)

    implementation(libs.androidx.dataStore)
    implementation(libs.androidx.dataStore.preferences)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
