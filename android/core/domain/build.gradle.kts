plugins {
    alias(libs.plugins.motif.android.library)
    alias(libs.plugins.motif.hilt)
}

android {
    namespace = "com.ssafy.motif.core.domain"
}

dependencies {
    api(projects.core.data)
    api(projects.core.model)
}
