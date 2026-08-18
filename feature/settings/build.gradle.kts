plugins {
    id("gymtracker.android.library")
    id("gymtracker.android.compose")
    id("gymtracker.hilt")
}

android {
    namespace = "com.gymtracker.feature.settings"
}

dependencies {
    implementation(project(":core:designsystem"))
    implementation(project(":core:domain"))

    implementation(libs.androidx.activity.compose)
    // Only for PermissionController.createRequestPermissionResultContract() — the launcher
    // this screen needs to request Health Connect permissions one at a time (US-21). Reading
    // and aggregating health data itself stays in :feature:health; this module never touches
    // HealthConnectClient.
    implementation(libs.androidx.health.connect.client)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(testFixtures(project(":core:domain")))
    testImplementation(libs.junit4)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}
