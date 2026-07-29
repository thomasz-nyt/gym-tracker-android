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

                /*
                 * A shared debug key, checked in, so every build signs identically.
                 *
                 * AGP otherwise generates a keystore per machine, which means a CI APK and a
                 * local one cannot be installed over each other — Android rejects the update
                 * and the only way through is uninstalling, which wipes the log. That makes
                 * "install the new build and carry on" impossible, and M1's exit gate is the
                 * maintainer logging three real workouts across builds.
                 *
                 * This is not a secret. The password is the Android-wide convention, the key
                 * cannot sign a release, and constitution §4 is about keys that grant access
                 * to data or paid APIs. Release signing, when it exists, uses a key that is
                 * never in the repository.
                 */
                signingConfigs.getByName("debug") {
                    storeFile = rootProject.file("debug.keystore")
                    storePassword = "android"
                    keyAlias = "androiddebugkey"
                    keyPassword = "android"
                }
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
                lint {
                    // Android Lint is the only check that reads the merged manifest against
                    // the compiled classes, so it is the one that catches a manifest naming a
                    // class that does not exist — which ktlint, detekt and a green
                    // assembleDebug all happily ignore.
                    abortOnError = true
                    checkDependencies = true
                    warningsAsErrors = false
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
