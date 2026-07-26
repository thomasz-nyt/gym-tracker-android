import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import com.gymtracker.buildlogic.library
import com.gymtracker.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/**
 * Compose + Material 3 for whichever Android module type this is applied on top of
 * (`specs/tech-stack.md`: Jetpack Compose + Material 3, Compose-compiler plugin from Kotlin 2.x).
 */
class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

            pluginManager.withPlugin("com.android.application") {
                extensions.configure<ApplicationExtension> { buildFeatures.compose = true }
            }
            pluginManager.withPlugin("com.android.library") {
                extensions.configure<LibraryExtension> { buildFeatures.compose = true }
            }

            dependencies {
                val bom = platform(libs.library("androidx-compose-bom"))
                add("implementation", bom)
                add("implementation", libs.library("androidx-compose-ui"))
                add("implementation", libs.library("androidx-compose-ui-tooling-preview"))
                add("implementation", libs.library("androidx-compose-material3"))
                add("debugImplementation", bom)
                add("debugImplementation", libs.library("androidx-compose-ui-tooling"))
            }
        }
    }
}
