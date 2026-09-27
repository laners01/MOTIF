import java.util.Properties

plugins {
    alias(libs.plugins.motif.android.feature.impl)
    alias(libs.plugins.motif.android.library.compose)
    alias(libs.plugins.motif.hilt)
}

val localProperties =
    Properties().apply {
        val file = rootProject.file("local.properties")

        if (file.exists()) {
            file.inputStream().use(::load)
        }
    }

val tossClientKey =
    localProperties
        .getProperty("TOSS_CLIENT_KEY")
        .orEmpty()

android {
    namespace = "com.ssafy.motif.feature.motif.impl"

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        buildConfigField(
            "String",
            "TOSS_CLIENT_KEY",
            "\"$tossClientKey\"",
        )
    }
}

dependencies {
    implementation(projects.feature.motif.api)
    implementation(projects.core.data)

    implementation(libs.coil.kt.compose)
    implementation(libs.androidx.lifecycle.runtimeCompose)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)

    implementation(libs.toss.payments)
}