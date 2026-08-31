plugins {
    alias(libs.plugins.modif.android.library)
    alias(libs.plugins.modif.hilt)
}

android {
    namespace = "com.ssafy.modif.core.datastore"

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
