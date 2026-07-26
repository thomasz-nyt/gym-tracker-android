package com.gymtracker.buildlogic

import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.provider.Provider
import org.gradle.jvm.toolchain.JavaLanguageVersion

/** Min SDK per `specs/tech-stack.md` — Health Connect needs 26+. */
const val MIN_SDK = 26

/** Target SDK per `specs/tech-stack.md` — the runtime behaviour the app opts in to. */
const val TARGET_SDK = 36

/**
 * Compile SDK. Deliberately one ahead of [TARGET_SDK]: the Compose BOM and
 * `androidx.lifecycle` versions in the catalog require compiling against API 37.
 * `tech-stack.md` pins min and target SDK, which is what users are exposed to;
 * compiling against a newer SDK changes no runtime behaviour.
 */
const val COMPILE_SDK = 37

/** JVM bytecode level for every module, and the toolchain CI provisions. */
const val JAVA_TARGET = 17

/** Marketing version, also used to name the APK. */
const val VERSION_NAME = "0.1.0"

val JAVA_VERSION: JavaVersion = JavaVersion.VERSION_17

val JAVA_LANGUAGE_VERSION: JavaLanguageVersion = JavaLanguageVersion.of(JAVA_TARGET)

/** The `libs` version catalog, so convention plugins do not duplicate version numbers. */
val Project.libs: VersionCatalog
    get() = extensions.getByType(VersionCatalogsExtension::class.java).named("libs")

fun VersionCatalog.library(alias: String): Provider<MinimalExternalModuleDependency> =
    findLibrary(alias).orElseThrow { IllegalArgumentException("No library '$alias' in the version catalog") }
