import com.gymtracker.buildlogic.JAVA_LANGUAGE_VERSION
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType

/**
 * A plain JVM Kotlin module: Java 17 toolchain and JUnit 5 as the test platform
 * (`specs/tech-stack.md` § Testing).
 */
class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.jvm")

            extensions.configure<JavaPluginExtension> {
                toolchain.languageVersion.set(JAVA_LANGUAGE_VERSION)
            }

            tasks.withType<Test>().configureEach {
                useJUnitPlatform()
            }
        }
    }
}
