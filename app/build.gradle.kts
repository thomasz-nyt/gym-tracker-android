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
    implementation(project(":feature:logging"))
    implementation(project(":feature:progress"))
    implementation(project(":feature:routines"))

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
