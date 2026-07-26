import com.gymtracker.buildlogic.library
import com.gymtracker.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/** Hilt DI per `specs/tech-stack.md`, with KSP as the annotation processor. */
class HiltConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.google.devtools.ksp")
            pluginManager.apply("com.google.dagger.hilt.android")

            dependencies {
                add("implementation", libs.library("hilt-android"))
                add("ksp", libs.library("hilt-compiler"))
            }
        }
    }
}
