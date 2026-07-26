import com.android.build.api.dsl.ApplicationExtension
import com.gymtracker.buildlogic.COMPILE_SDK
import com.gymtracker.buildlogic.JAVA_VERSION
import com.gymtracker.buildlogic.MIN_SDK
import com.gymtracker.buildlogic.TARGET_SDK
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

/** The installable app module per `specs/tech-stack.md` § Module layout. */
class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.application")

            extensions.configure<ApplicationExtension> {
                compileSdk = COMPILE_SDK
                defaultConfig {
                    minSdk = MIN_SDK
                    targetSdk = TARGET_SDK
                    versionCode = 1
                    versionName = "0.1.0"
                }
                compileOptions {
                    sourceCompatibility = JAVA_VERSION
                    targetCompatibility = JAVA_VERSION
                }
            }

            tasks.withType<KotlinCompile>().configureEach {
                compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
            }
        }
    }
}
