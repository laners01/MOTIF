import com.ssafy.modif.configureKotlinJvm
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType

abstract class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            apply(plugin = "org.jetbrains.kotlin.jvm")

            val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

            configureKotlinJvm()
            dependencies {
                add("testImplementation", libs.findLibrary("kotlin.test").get())
            }
        }
    }
}
