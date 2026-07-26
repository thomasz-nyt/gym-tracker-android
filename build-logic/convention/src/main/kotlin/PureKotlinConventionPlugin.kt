import com.gymtracker.buildlogic.VerifyNoAndroidDepsTask
import com.gymtracker.buildlogic.collectModuleIds
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.kotlin.dsl.register
import org.gradle.language.base.plugins.LifecycleBasePlugin

/**
 * A JVM Kotlin module that is additionally guarded against ever becoming an Android module.
 *
 * Registers `verifyNoAndroidDeps` and hooks it into `check`. The root project aggregates it
 * as `verifyDomainHasNoAndroidDeps`, which is the command named in `specs/testing-strategy.md`.
 */
class PureKotlinConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("gymtracker.jvm.library")

            val androidPluginIds = objects.setProperty(String::class.java)
            CANDIDATE_ANDROID_PLUGIN_IDS.forEach { id ->
                pluginManager.withPlugin(id) { androidPluginIds.add(id) }
            }

            val moduleIds = objects.setProperty(String::class.java)
            GUARDED_CONFIGURATIONS.forEach { name ->
                moduleIds.addAll(
                    configurations.named(name).flatMap { configuration: Configuration ->
                        configuration.incoming.resolutionResult.rootComponent
                            .map { collectModuleIds(it) }
                    },
                )
            }

            val projectPath = path
            val verify =
                tasks.register<VerifyNoAndroidDepsTask>("verifyNoAndroidDeps") {
                    group = LifecycleBasePlugin.VERIFICATION_GROUP
                    description = "Fails if this pure-Kotlin module gains an Android plugin, dependency or import."
                    modulePath.set(projectPath)
                    moduleDirectory.set(layout.projectDirectory)
                    appliedPluginIds.set(androidPluginIds)
                    resolvedModuleIds.set(moduleIds)
                    sourceFiles.from(
                        layout.projectDirectory.dir("src").asFileTree.matching {
                            include("**/*.kt", "**/*.kts", "**/*.java")
                        },
                    )
                    reportFile.set(layout.buildDirectory.file("reports/verify-no-android-deps.txt"))
                }

            tasks.named(LifecycleBasePlugin.CHECK_TASK_NAME) { dependsOn(verify) }
        }
    }

    private companion object {
        /**
         * Every plugin id that would drag the Android toolchain in. Checked with
         * `pluginManager.withPlugin` so ordering does not matter.
         */
        val CANDIDATE_ANDROID_PLUGIN_IDS =
            listOf(
                "com.android.application",
                "com.android.library",
                "com.android.test",
                "com.android.dynamic-feature",
                "com.android.asset-pack",
                "com.android.asset-pack-bundle",
                "org.jetbrains.kotlin.android",
            )

        val GUARDED_CONFIGURATIONS =
            listOf(
                "compileClasspath",
                "runtimeClasspath",
                "testCompileClasspath",
                "testRuntimeClasspath",
            )
    }
}
