plugins {
    id("gymtracker.jvm.library")
    alias(libs.plugins.kotlin.serialization)
    application
}

application {
    mainClass = "com.gymtracker.tools.catalog.MainKt"
}

dependencies {
    implementation(project(":core:domain"))
    implementation(libs.kotlinx.serialization.json)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlin.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}
