import com.android.build.api.dsl.ApplicationExtension
import com.gymtracker.buildlogic.COMPILE_SDK
import com.gymtracker.buildlogic.JAVA_VERSION
import com.gymtracker.buildlogic.MIN_SDK
import com.gymtracker.buildlogic.TARGET_SDK
import com.gymtracker.buildlogic.VERSION_NAME
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.BasePluginExtension
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
                    versionName = VERSION_NAME
                }
                compileOptions {
                    sourceCompatibility = JAVA_VERSION
                    targetCompatibility = JAVA_VERSION
                }
            }

            // APK file name. CI passes -PbuildNumber and -PbuildSha so that every artifact
            // downloaded for device testing is distinguishable; a local build says so.
            // Read eagerly rather than as a lazy provider: handing AGP a mapped provider for
            // archivesName makes it the value of VariantOutputImpl.outputFileName, which drags
            // AGP and KGP internals into the configuration-cache graph and fails to serialise.
            // Gradle still tracks these property reads, so -PbuildNumber invalidates the cache.
            val buildNumber = providers.gradleProperty("buildNumber").getOrElse("local")
            val buildSha = providers.gradleProperty("buildSha").getOrElse("dev")
            extensions.configure<BasePluginExtension> {
                archivesName.set("gym-tracker-$VERSION_NAME-$buildNumber-$buildSha")
            }

            tasks.withType<KotlinCompile>().configureEach {
                compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
            }
        }
    }
}
