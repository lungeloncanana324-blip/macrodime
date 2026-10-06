// The pure layer: Domain and Engine, ported from MacroDime/Domain and
// MacroDime/Engine. No Android dependency, on purpose: the same reason the iOS
// engines import only Foundation. Everything here runs on Android API 26, so
// only java.text, java.util and the Kotlin standard library are used.

plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Bytecode level 17, matching the app module. Built with whatever JDK runs
// Gradle (17 or newer), so no toolchain download is needed.
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    testImplementation(kotlin("test-junit"))
    testImplementation(libs.junit)
}

tasks.test {
    // Display formatting is locale-aware, and the expected strings in the tests
    // are written for en-US, as the iOS tests are on the CI runner. Pinned here
    // so a South African or German machine runs the same assertions.
    systemProperty("user.language", "en")
    systemProperty("user.country", "US")
    // DataSourcesTest holds the Play description to the app's source links, so
    // an edit to the listing reruns it rather than leaving the result cached.
    val listing = rootProject.file("../docs/play-store-listing.md")
    inputs.file(listing)
    systemProperty("macrodime.listing", listing.absolutePath)
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
