plugins {
    `kotlin-dsl`
}

group = "com.ssafy.motif.buildlogic"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.compose.compiler.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.ksp.gradlePlugin)
    compileOnly(libs.room.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("androidApplicationCompose") {
            id = libs.plugins.motif.android.application.compose.get().pluginId
            implementationClass = "AndroidApplicationComposeConventionPlugin"
        }

        register("androidApplication") {
            id = libs.plugins.motif.android.application.asProvider().get().pluginId
            implementationClass = "AndroidApplicationConventionPlugin"
        }

        register("androidLibraryCompose") {
            id = libs.plugins.motif.android.library.compose.get().pluginId
            implementationClass = "AndroidLibraryComposeConventionPlugin"
        }

        register("androidLibrary") {
            id = libs.plugins.motif.android.library.asProvider().get().pluginId
            implementationClass = "AndroidLibraryConventionPlugin"
        }

        register("androidFeatureApi") {
            id = libs.plugins.motif.android.feature.api.get().pluginId
            implementationClass = "AndroidFeatureApiConventionPlugin"
        }

        register("androidFeatureImpl") {
            id = libs.plugins.motif.android.feature.impl.get().pluginId
            implementationClass = "AndroidFeatureImplConventionPlugin"
        }

        register("androidRoom") {
            id = libs.plugins.motif.android.room.get().pluginId
            implementationClass = "AndroidRoomConventionPlugin"
        }

        register("androidHilt") {
            id = libs.plugins.motif.hilt.get().pluginId
            implementationClass = "HiltConventionPlugin"
        }

        register("jvmLibrary") {
            id = "motif.jvm.library"
            implementationClass = "JvmLibraryConventionPlugin"
        }
    }
}