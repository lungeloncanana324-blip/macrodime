// The Android app: Room persistence, view models and the Compose UI, on top of
// the pure :core engines.

import com.android.build.api.artifact.SingleArtifact
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

// Release signing. Play App Signing holds the real app signing key; this is the
// *upload* key, which signs what is sent to Play Console. Read from
// android/keystore.properties (never committed) or, in CI, from environment
// variables, so the key and its passwords never enter the repository. Without
// either, bundleRelease still builds, unsigned, which Play Console rejects.
val signing = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun signingValue(key: String, env: String): String? =
    signing.getProperty(key) ?: System.getenv(env)?.takeIf { it.isNotBlank() }

val uploadStore = signingValue("storeFile", "MACRODIME_UPLOAD_STORE_FILE")

android {
    namespace = "com.lungelo.macrodime"
    // The SDK the code is compiled against, not the behaviour it opts into:
    // Compose 1.12 requires API 37 headers. targetSdk below is what decides
    // runtime behaviour, and what Play checks.
    compileSdk = 37

    defaultConfig {
        // The same identifier as the iOS bundle id. On Play it is permanent:
        // it can never be changed once the first release is uploaded.
        applicationId = "com.lungelo.macrodime"
        // Android 8.0. Covers effectively every phone still in use, and is the
        // first release with java.time and adaptive icons built in.
        minSdk = 26
        // Google Play has required API 36 for new apps since 31 August 2026.
        targetSdk = 36
        // Play rejects an upload whose versionCode it has already seen, so every
        // upload needs a higher one: `./gradlew bundleRelease -PversionCode=2`.
        versionCode = (findProperty("versionCode") as String?)?.toInt() ?: 1
        versionName = (findProperty("versionName") as String?) ?: "1.0.0"
    }

    signingConfigs {
        if (uploadStore != null) {
            create("upload") {
                storeFile = rootProject.file(uploadStore)
                storePassword = signingValue("storePassword", "MACRODIME_UPLOAD_STORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "MACRODIME_UPLOAD_KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "MACRODIME_UPLOAD_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("upload")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            // Robolectric needs the merged resources and manifest.
            isIncludeAndroidResources = true
            all {
                it.systemProperty("user.language", "en")
                it.systemProperty("user.country", "US")
                it.maxHeapSize = "2g"
                // Robolectric reaches into java.io.FileDescriptor to emulate
                // Android's shared memory; Java 17 and later lock that away
                // unless the package is opened explicitly.
                it.jvmArgs("--add-opens=java.base/java.io=ALL-UNNAMED")
                // A fresh JVM per test class: Robolectric's native graphics
                // holds rendered bitmaps in memory a 2 GB heap cannot keep
                // for a whole suite of screenshot tests.
                it.forkEvery = 1
            }
        }
    }

    lint {
        // A lint error is a build failure, not a report nobody reads.
        abortOnError = true
        checkReleaseBuilds = true
        // Two warnings stay, on purpose. OldTargetApi: targetSdk is 36, Play's
        // requirement; 37's behaviour changes are untested. ObsoleteSdkInt on
        // mipmap-anydpi-v26: moving the adaptive icon to mipmap-anydpi as lint
        // suggests makes resource linking fail with this AGP.
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// The Play Data safety answer is "no data collected", and the reason it is true
// is that the app holds no permission to send anything: no INTERNET, no
// storage, nothing. A library can merge a permission into the manifest without
// anyone noticing, so the release manifest is checked here and the build fails
// if one appears. Four entries are allowed: the signature-level permission
// androidx.core declares for the app's own unexported receivers, which grants
// no capability; com.android.vending.BILLING, which Play Billing declares so
// the app can talk to the Play Store app about MacroDime Pro; and
// POST_NOTIFICATIONS with RECEIVE_BOOT_COMPLETED, for the local trial
// reminder the paywall promises. None lets the app reach the network; there
// is still no INTERNET.
androidComponents {
    onVariants(selector().withBuildType("release")) { variant ->
        val manifest = variant.artifacts.get(SingleArtifact.MERGED_MANIFEST)
        val applicationId = variant.applicationId
        val task = tasks.register("verify${variant.name.replaceFirstChar { it.uppercase() }}Permissions") {
            group = "verification"
            description = "Fails if the merged ${variant.name} manifest requests any permission not on the allowed list."
            inputs.file(manifest)
            inputs.property("applicationId", applicationId)
            doLast {
                val text = manifest.get().asFile.readText()
                val requested = Regex("""<uses-permission[^>]*android:name="([^"]+)"""")
                    .findAll(text).map { it.groupValues[1] }.toSet()
                val allowed = setOf(
                    "${applicationId.get()}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION",
                    "com.android.vending.BILLING",
                    "android.permission.POST_NOTIFICATIONS",
                    "android.permission.RECEIVE_BOOT_COMPLETED",
                )
                val unexpected = requested - allowed
                if (unexpected.isNotEmpty()) {
                    throw GradleException(
                        "The release manifest requests ${unexpected.sorted()}, beyond the permissions MacroDime allows; " +
                            "find the dependency that merged it, or update the Play Data safety answers first.",
                    )
                }
                logger.lifecycle("Release manifest requests no permissions beyond ${allowed.sorted()}.")
            }
        }
        tasks.matching { it.name == "check" }.configureEach { dependsOn(task) }
    }
}

room {
    // Exported so a later schema change is written as a tested migration
    // rather than a destructive rebuild of the user's data.
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(project(":core"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)

    // MacroDime Pro. Talks to the Play Store app on the phone, never to a
    // server of ours, and adds only the BILLING permission (verified below).
    implementation(libs.play.billing.ktx)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    testImplementation(kotlin("test-junit"))
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
}
