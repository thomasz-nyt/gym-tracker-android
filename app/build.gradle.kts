plugins {
    id("gymtracker.android.application")
    id("gymtracker.android.compose")
    id("gymtracker.hilt")
    // Navigation's type-safe routes are @Serializable objects (ADR-0013, tech-stack.md).
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.gymtracker"

    defaultConfig {
        applicationId = "com.gymtracker"
        // Hilt needs its own Application in instrumented tests; see GymTrackerTestRunner.
        testInstrumentationRunner = "com.gymtracker.GymTrackerTestRunner"

        // The optional-feature suite (testing-strategy.md §1): `:app:connectedDebugAndroidTest
        // -Pgymtracker.optionalFeatures=off` runs the full UI suite a second time with
        // HealthMetricsSource bound to its no-op implementation, so a screen that only renders
        // correctly when a health source happens to be present is caught mechanically rather
        // than by inspection. Off is exactly this one Gradle property away; on (the default)
        // needs nothing, so a plain `./gradlew ...` still exercises the real binding.
        buildConfigField(
            "boolean",
            "OPTIONAL_FEATURES_ENABLED",
            (findProperty("gymtracker.optionalFeatures") != "off").toString(),
        )
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:domain"))
    implementation(project(":feature:catalog"))
    implementation(project(":feature:health"))
    implementation(project(":feature:logging"))
    implementation(project(":feature:progress"))
    implementation(project(":feature:routines"))
    implementation(project(":feature:settings"))

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    // navigation-compose arrives with hilt-navigation-compose, which already requires it.
    // Pinning it separately would mean inventing a version: Google's Maven is unreachable
    // from the environment this was written in, and a wrong pin overrides a working one.
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.kotlinx.serialization.json)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.datastore.preferences)
    androidTestImplementation(libs.androidx.room.runtime)
    // Espresso 3.6.x calls InputManager.getInstance, removed in API 36; 3.7.0 fixes it.
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.hilt.android.testing)
    androidTestImplementation(libs.kotlin.test)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    kspAndroidTest(libs.hilt.compiler)

    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
