package com.gymtracker.tools.catalog

import kotlinx.serialization.json.Json
import java.io.File
import kotlin.system.exitProcess

private val JSON =
    Json {
        // The source file carries fields we do not model (images, level, force, mechanic).
        ignoreUnknownKeys = true
        prettyPrint = true
        prettyPrintIndent = "  "
    }

/**
 * Converts a free-exercise-db `exercises.json` into the catalog the app bundles.
 *
 * ```
 * ./gradlew :tools:catalog:run --args="<source.json> <output.json>"
 * ```
 *
 * The source is public domain and lives at
 * https://github.com/yuhonas/free-exercise-db (`dist/exercises.json`). It is not vendored
 * here: only the converted output ships, because that is what the app reads.
 */
fun main(args: Array<String>) {
    if (args.size != 2) {
        System.err.println("usage: catalog <source-exercises.json> <output-exercises.json>")
        exitProcess(1)
    }

    val source = File(args[0])
    require(source.isFile) { "No such source catalog: ${source.absolutePath}" }

    val parsed = JSON.decodeFromString<List<SourceExercise>>(source.readText())
    val converted = CatalogConverter.convert(parsed)

    File(args[1]).apply {
        parentFile?.mkdirs()
        writeText(JSON.encodeToString(converted) + "\n")
    }

    println("Converted ${parsed.size} exercises -> ${args[1]}")
}
