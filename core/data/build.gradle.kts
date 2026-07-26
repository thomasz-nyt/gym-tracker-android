plugins {
    id("gymtracker.android.library")
}

android {
    namespace = "com.gymtracker.core.data"
}

dependencies {
    implementation(project(":core:domain"))
}
