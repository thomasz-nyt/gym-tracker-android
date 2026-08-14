plugins {
    id("gymtracker.android.library")
    id("gymtracker.android.compose")
}

android {
    namespace = "com.gymtracker.core.designsystem"

    testOptions {
        unitTests {
            // Constructing a TextStyle should not reach the platform, but if some corner of
            // Compose ever does, a default beats an exception in a test about font sizes.
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    // GymPhoto wraps AsyncImage so the Modernist grayscale treatment (redesign audit, PR A
    // finding 2) cannot be forgotten at a call site. Already an approved dependency
    // (`tech-stack.md`) and already used by :feature:catalog and :feature:logging — this only
    // wires it into the one place it belongs, the design-system component itself.
    implementation(libs.coil.compose)

    // The type scale is a product decision (ADR-0011), so it has a test. Compose's TextStyle
    // and TextUnit are plain JVM types, so this needs no Robolectric and no device.
    testImplementation(libs.junit4)
    testImplementation(libs.kotlin.test)
}
