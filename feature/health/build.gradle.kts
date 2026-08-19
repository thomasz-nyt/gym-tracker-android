plugins {
    id("gymtracker.android.library")
    id("gymtracker.hilt")
}

android {
    namespace = "com.gymtracker.feature.health"
}

dependencies {
    implementation(project(":core:domain"))

    implementation(libs.androidx.health.connect.client)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit4)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    // Only HealthConnectMetricsSourceTest's fault-injection case needs this: it exercises the
    // real android.util.Log.w() call in the failure path, which plain JUnit's android.jar stub
    // throws on rather than no-ops.
    testImplementation(libs.robolectric)
}
