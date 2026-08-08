plugins {
    id("gymtracker.pure.kotlin")
    // `specs/testing-strategy.md` § Fixture data: one shared TestData, not ad-hoc rows per
    // test file, because divergent fixtures are how chart bugs hide. A test-fixtures source
    // set is how :feature:progress reaches the same data without depending on a test source.
    `java-test-fixtures`
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)

    testFixturesImplementation(libs.kotlinx.coroutines.core)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testRuntimeOnly(libs.junit.platform.launcher)
}
