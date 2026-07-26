package com.gymtracker.buildlogic

/**
 * Classifies plugins, dependency coordinates and source imports as Android or not.
 *
 * This is the logic behind the `verifyDomainHasNoAndroidDeps` CI gate that keeps
 * `:core:domain` pure Kotlin (constitution §7, `specs/adr/0003-enforcing-a-pure-domain-layer.md`).
 * It is a plain object with no Gradle types so that it can be unit-tested directly.
 */
object AndroidDependencyDetector {
    private val FORBIDDEN_PLUGIN_PREFIXES = listOf("com.android.")
    private val FORBIDDEN_PLUGIN_IDS = setOf("org.jetbrains.kotlin.android")

    private val FORBIDDEN_GROUP_PREFIXES = listOf("com.android.", "androidx.", "com.google.android.")

    private val FORBIDDEN_IMPORT_PREFIXES = listOf("android.", "androidx.", "com.android.", "dalvik.")

    /** Matches a Kotlin/Java `import` statement, capturing the imported name without any alias. */
    private val IMPORT_REGEX =
        Regex(
            """^[ \t]*import[ \t]+(?:static[ \t]+)?([A-Za-z_]\w*(?:\.\w+)*(?:\.\*)?)""",
            RegexOption.MULTILINE,
        )

    /** True if applying [pluginId] would pull the Android toolchain into a module. */
    fun isForbiddenPlugin(pluginId: String): Boolean =
        pluginId in FORBIDDEN_PLUGIN_IDS || FORBIDDEN_PLUGIN_PREFIXES.any { pluginId.startsWith(it) }

    /**
     * True if [coordinates] (`group:name:version`, or any prefix of that) names an
     * Android-namespaced artifact. Prefix matches are anchored on a trailing dot so that
     * unrelated groups such as `com.androidessentials` are not caught.
     */
    fun isForbiddenModule(coordinates: String): Boolean {
        val group = coordinates.substringBefore(':')
        return FORBIDDEN_GROUP_PREFIXES.any { prefix ->
            group == prefix.removeSuffix(".") || group.startsWith(prefix)
        }
    }

    /** Every Android import in [source], in the order it appears. Commented-out imports are ignored. */
    fun forbiddenImportsIn(source: String): List<String> =
        IMPORT_REGEX
            .findAll(source)
            .map { it.groupValues[1] }
            .filter { imported -> FORBIDDEN_IMPORT_PREFIXES.any { imported.startsWith(it) } }
            .toList()
}
