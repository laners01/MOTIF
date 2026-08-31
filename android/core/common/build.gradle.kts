plugins {
    alias(libs.plugins.modif.jvm.library)
    alias(libs.plugins.modif.hilt)
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}