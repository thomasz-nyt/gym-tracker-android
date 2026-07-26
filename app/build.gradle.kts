plugins {
    id("gymtracker.android.application")
    id("gymtracker.android.compose")
    id("gymtracker.hilt")
}

android {
    namespace = "com.gymtracker"

    defaultConfig {
        applicationId = "com.gymtracker"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
}

dependencies {
    implementation(project(":core:designsystem"))
    implementation(project(":core:domain"))

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
}
