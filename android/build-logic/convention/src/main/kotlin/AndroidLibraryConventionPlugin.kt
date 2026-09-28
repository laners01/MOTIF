import com.android.build.api.dsl.LibraryExtension
import com.ssafy.motif.configureKotlinAndroid
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("com.android.library")
            }

            val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

            extensions.configure<LibraryExtension> {
                configureKotlinAndroid(this)
            }

            extensions.getByType<KotlinAndroidProjectExtension>().apply {
                configureKotlinAndroid(this)
            }

            dependencies {
                add("implementation", libs.findLibrary("androidx.tracing.ktx").get())
                add("androidTestImplementation", libs.findLibrary("kotlin.test").get())
                add("testImplementation", libs.findLibrary("kotlin.test").get())
            }
        }
    }
}