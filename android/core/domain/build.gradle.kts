plugins {
    alias(libs.plugins.modif.android.library)
    alias(libs.plugins.modif.hilt)
}

android {
    namespace = "com.ssafy.modif.core.domain"
}

dependencies {
    api(projects.core.data)
    api(projects.core.model)
}
