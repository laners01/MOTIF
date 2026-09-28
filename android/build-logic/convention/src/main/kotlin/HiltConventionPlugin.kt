import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType

class HiltConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.google.devtools.ksp")

            val libs = extensions
                .getByType<VersionCatalogsExtension>()
                .named("libs")

            dependencies {
                add("ksp", libs.findLibrary("hilt.compiler").get())
                add("ksp", libs.findLibrary("kotlin.metadata.jvm").get())
            }

            pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
                dependencies {
                    add("implementation", libs.findLibrary("hilt.core").get())
                }
            }

            pluginManager.withPlugin("com.android.base") {
                pluginManager.apply("com.google.dagger.hilt.android")

                dependencies {
                    add("implementation", libs.findLibrary("hilt.android").get())
                }
            }
        }
    }
}
