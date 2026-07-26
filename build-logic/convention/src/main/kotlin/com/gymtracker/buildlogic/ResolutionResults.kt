package com.gymtracker.buildlogic

import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult

/**
 * Flattens a resolved dependency graph into `group:name:version` strings.
 *
 * Components without a group (the root project component, and project dependencies)
 * are skipped — only external modules are of interest to [AndroidDependencyDetector].
 */
internal fun collectModuleIds(root: ResolvedComponentResult): Set<String> {
    val ids = sortedSetOf<String>()
    val seen = mutableSetOf<ResolvedComponentResult>()

    fun visit(component: ResolvedComponentResult) {
        if (!seen.add(component)) return
        component.moduleVersion?.let { module ->
            if (module.group.isNotEmpty()) {
                ids += "${module.group}:${module.name}:${module.version}"
            }
        }
        component.dependencies
            .filterIsInstance<ResolvedDependencyResult>()
            .forEach { visit(it.selected) }
    }

    visit(root)
    return ids
}
