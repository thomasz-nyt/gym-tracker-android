package com.gymtracker.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * Fails the build if a module that is meant to be pure Kotlin has acquired anything Android.
 *
 * Backs the `verifyDomainHasNoAndroidDeps` CI gate from `specs/testing-strategy.md`.
 * See `specs/adr/0003-enforcing-a-pure-domain-layer.md` for why all three checks exist.
 */
@CacheableTask
abstract class VerifyNoAndroidDepsTask : DefaultTask() {
    /** Gradle path of the module under inspection, used in failure messages. */
    @get:Input
    abstract val modulePath: Property<String>

    /** Android plugin ids actually applied to the module. */
    @get:Input
    abstract val appliedPluginIds: SetProperty<String>

    /** Every external module on the module's compile, runtime and test classpaths. */
    @get:Input
    abstract val resolvedModuleIds: SetProperty<String>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceFiles: ConfigurableFileCollection

    @get:Internal
    abstract val moduleDirectory: DirectoryProperty

    @get:OutputFile
    abstract val reportFile: RegularFileProperty

    @TaskAction
    fun verify() {
        val violations = findViolations()
        val report = reportFile.get().asFile
        report.parentFile.mkdirs()

        if (violations.isEmpty()) {
            report.writeText("${modulePath.get()} is free of Android plugins, dependencies and imports.\n")
            return
        }

        report.writeText(violations.joinToString(separator = "\n", postfix = "\n"))
        throw GradleException(
            buildString {
                append(modulePath.get())
                appendLine(" must stay pure Kotlin, but ${violations.size} Android reference(s) were found:")
                violations.forEach { appendLine("  - $it") }
                appendLine()
                appendLine("The domain layer has no Android dependencies (constitution §7, tech-stack.md).")
                appendLine("It is what keeps the M8 iOS port a UI rewrite rather than a logic rewrite.")
                append("If this dependency is genuinely needed, it belongs in :core:data or a :feature: module.")
            },
        )
    }

    private fun findViolations(): List<String> {
        val moduleDir = moduleDirectory.get().asFile
        return buildList {
            appliedPluginIds
                .get()
                .sorted()
                .filter(AndroidDependencyDetector::isForbiddenPlugin)
                .forEach { add("Android Gradle plugin applied: $it") }

            resolvedModuleIds
                .get()
                .sorted()
                .filter(AndroidDependencyDetector::isForbiddenModule)
                .forEach { add("Android dependency on the classpath: $it") }

            sourceFiles.files.sortedBy { it.invariantSeparatorsPath }.forEach { file ->
                val where = file.relativeToOrSelf(moduleDir).invariantSeparatorsPath
                AndroidDependencyDetector
                    .forbiddenImportsIn(file.readText())
                    .forEach { imported -> add("Android import in $where: $imported") }
            }
        }
    }
}
