plugins {
    id("gymtracker.android.library")
    id("gymtracker.android.compose")
}

android {
    namespace = "com.gymtracker.core.designsystem"
}

dependencies {
    // The type scale is a product decision (ADR-0011), so it has a test. Compose's TextStyle
    // and TextUnit are plain JVM types, so this needs no Robolectric and no device.
    testImplementation(libs.junit4)
    testImplementation(libs.kotlin.test)
}
