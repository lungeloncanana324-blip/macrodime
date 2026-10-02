// Plugins are declared here with `apply false` so every module resolves the
// same versions. The Kotlin JVM plugin also puts the Kotlin Gradle plugin on the
// build classpath, which is the version AGP's built-in Kotlin support compiles
// the app module with.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.room) apply false
}
