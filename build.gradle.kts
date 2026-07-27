plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint)
}

allprojects {
    apply(plugin = "io.gitlab.arturbosch.detekt")
    apply(plugin = "org.jlleitschuh.gradle.ktlint")

    detekt {
        buildUponDefaultConfig = true
        config.setFrom(rootProject.files("config/detekt/detekt.yml"))
        basePath = rootProject.projectDir.absolutePath
    }

    ktlint {
        filter {
            exclude { it.file.invariantSeparatorsPath.contains("/build/") }
        }
    }
}

/**
 * The CI gate named in `specs/testing-strategy.md`. Aggregates the per-module
 * `verifyNoAndroidDeps` check so the command in the spec keeps working even if
 * more pure-Kotlin modules appear. See `specs/adr/0003-enforcing-a-pure-domain-layer.md`.
 */
tasks.register("verifyDomainHasNoAndroidDeps") {
    group = "verification"
    description = "Asserts that :core:domain has no Android plugin, dependency or import."
    dependsOn(":core:domain:verifyNoAndroidDeps")
}
