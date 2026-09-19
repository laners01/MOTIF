pluginManagement {
    includeBuild("build-logic")

    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)

    repositories {
        google()
        mavenCentral()
    }
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "motif"

include(":app")
include(":core:common")
include(":core:data")
include(":core:database")
include(":core:datastore")
include(":core:datastore-proto")
include(":core:designsystem")
include(":core:domain")
include(":core:model")
include(":core:navigation")
include(":core:network")
include(":core:notifications")
include(":core:ui")
include("feature:home:api")
include("feature:home:impl")
include("feature:signin:api")
include("feature:signin:impl")
include("feature:onboarding:api")
include("feature:onboarding:impl")
include("feature:ranking:api")
include("feature:ranking:impl")
include("feature:artistdetail:api")
include("feature:artistdetail:impl")
include("feature:challenges:api")
include("feature:challenges:impl")
include("feature:mypage:api")
include("feature:mypage:impl")
include("feature:motif:api")
include("feature:motif:impl")
