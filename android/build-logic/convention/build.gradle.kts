plugins {
    `kotlin-dsl`
}

group = "com.ssafy.modif.buildlogic"

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
            id = libs.plugins.modif.android.application.compose.get().pluginId
            implementationClass = "AndroidApplicationComposeConventionPlugin"
        }

        register("androidApplication") {
            id = libs.plugins.modif.android.application.asProvider().get().pluginId
            implementationClass = "AndroidApplicationConventionPlugin"
        }

        register("androidLibraryCompose") {
            id = libs.plugins.modif.android.library.compose.get().pluginId
            implementationClass = "AndroidLibraryComposeConventionPlugin"
        }

        register("androidLibrary") {
            id = libs.plugins.modif.android.library.asProvider().get().pluginId
            implementationClass = "AndroidLibraryConventionPlugin"
        }

        register("androidFeatureApi") {
            id = libs.plugins.modif.android.feature.api.get().pluginId
            implementationClass = "AndroidFeatureApiConventionPlugin"
        }

        register("androidFeatureImpl") {
            id = libs.plugins.modif.android.feature.impl.get().pluginId
            implementationClass = "AndroidFeatureImplConventionPlugin"
        }

        register("androidRoom") {
            id = libs.plugins.modif.android.room.get().pluginId
            implementationClass = "AndroidRoomConventionPlugin"
        }

        register("androidHilt") {
            id = libs.plugins.modif.hilt.get().pluginId
            implementationClass = "HiltConventionPlugin"
        }

        register("jvmLibrary") {
            id = "modif.jvm.library"
            implementationClass = "JvmLibraryConventionPlugin"
        }
    }
}