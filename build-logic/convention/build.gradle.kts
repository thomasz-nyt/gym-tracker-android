plugins {
    `kotlin-dsl`
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint)
}

group = "com.gymtracker.buildlogic"

kotlin {
    jvmToolchain(17)
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(rootProject.file("../config/detekt/detekt.yml"))
    basePath = rootProject.projectDir.absolutePath
}

ktlint {
    filter {
        exclude { it.file.invariantSeparatorsPath.contains("/build/") }
    }
}

dependencies {
    // AGP and KGP types are needed to compile the convention plugins, but the plugins
    // themselves come from the consuming build's classpath (root build.gradle.kts).
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "gymtracker.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = "gymtracker.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidCompose") {
            id = "gymtracker.android.compose"
            implementationClass = "AndroidComposeConventionPlugin"
        }
        register("hilt") {
            id = "gymtracker.hilt"
            implementationClass = "HiltConventionPlugin"
        }
        register("jvmLibrary") {
            id = "gymtracker.jvm.library"
            implementationClass = "JvmLibraryConventionPlugin"
        }
    }
}
