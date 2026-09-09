plugins {
    alias(libs.plugins.motif.jvm.library)
    alias(libs.plugins.motif.hilt)
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}