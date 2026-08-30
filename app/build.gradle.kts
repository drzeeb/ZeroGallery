plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    jacoco
}

// Release signing: intentionally never committed to the repo, not even encrypted - a leaked
// keystore/passwords would let anyone impersonate ZeroGallery's Play Store listing with a
// malicious update, and Git history is effectively forever even after a later "fix". Instead,
// release.yml (the manual release workflow) decodes a base64 keystore from a GitHub Actions
// secret to a temp file on the runner and passes these same four values in as env vars for that
// one job only; they're simply unset for every regular/local build (including PR checks), which
// is exactly why buildTypes.release below only wires up a signingConfig conditionally - assembling
// a debuggable, unsigned release build type locally (e.g. to sanity-check `optimization.enable =
// false`'s output) still needs to work for every contributor who doesn't have these secrets.
val releaseKeystorePath: String? = System.getenv("RELEASE_KEYSTORE_PATH")
val releaseSigningConfigured = !releaseKeystorePath.isNullOrBlank()

android {
    namespace = "de.zerogallery"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "de.zerogallery"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (releaseSigningConfigured) {
            create("release") {
                storeFile = file(releaseKeystorePath!!)
                storePassword = System.getenv("RELEASE_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("RELEASE_KEY_ALIAS")
                keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
            if (releaseSigningConfigured) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.material)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.coil.compose)
    implementation(libs.coil.video)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.documentfile)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockito.core)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.compose.ui.tooling.preview)
}

// Coverage: JaCoco, reporting on the "debug" variant's JVM unit tests (app/src/test) - the only
// tests that run headlessly in CI without an emulator/device. Reports feed Codecov (see
// .github/workflows/ci.yml).
//
// Kotlinx Kover (the more idiomatic choice for a pure-Kotlin/Compose project) was tried first,
// but its Gradle plugin (0.9.1, latest as of writing) doesn't yet recognize AGP 9's new built-in
// Kotlin compilation (it looks for the old, now-obsolete `org.jetbrains.kotlin.android` plugin,
// which AGP 9 explicitly forbids applying alongside it - see https://issuetracker.google.com/438678642)
// - it silently instruments nothing and reports 0/0 lines, no error. JaCoco doesn't have that
// problem: it just needs the compiled .class files, wherever AGP's build-in Kotlin compiler put
// them, and works via Gradle's own `Test` task integration regardless of *how* those classes got
// compiled.
jacoco {
    toolVersion = "0.8.15"
}

tasks.register<JacocoReport>("jacocoTestReport") {
    dependsOn("testDebugUnitTest")
    group = "verification"
    description = "Generates an XML/HTML JVM unit test coverage report for the debug variant."

    reports {
        xml.required.set(true)
        html.required.set(true)
    }

    val debugTree = fileTree("${layout.buildDirectory.get()}/intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes") {
        // Generated, not hand-written, and/or files containing *only* @Composable UI with no
        // pure/testable logic at all - there's nothing a JVM unit test (no Robolectric/
        // instrumented UI tests here) could ever exercise in them, so counting their inherently-
        // 0%-covered declarative UI code would make the coverage floor meaningless for the logic
        // this project *does* unit-test. Deliberately narrow: several other files under
        // de.zerogallery.ui mix pure logic with Composables in the same file (e.g. VideoPlayer.kt's
        // currentBrightnessFraction/currentVolumeFraction, ZoomableAsyncImage.kt's
        // computeDoubleTapOffset, MediaGrid.kt's formatDuration) and are deliberately *not*
        // excluded here, since a whole-file/class exclusion would also throw away real,
        // already-tested logic instead of just the untestable UI parts alongside it.
        exclude(
            "**/BuildConfig.class",
            "**/*ComposableSingletons*",
            "**/*\$Preview*",
            "de/zerogallery/ui/gallery/GalleryScreenKt.class",
            "de/zerogallery/ui/gallery/GalleryScreenKt\$*.class",
            "de/zerogallery/ui/detail/MediaDetailScreenKt.class",
            "de/zerogallery/ui/detail/MediaDetailScreenKt\$*.class",
            "de/zerogallery/ui/theme/WordmarkKt.class",
            "de/zerogallery/ui/theme/WordmarkKt\$*.class",
            "de/zerogallery/ui/theme/ThemeKt.class",
            "de/zerogallery/ui/theme/ThemeKt\$*.class",
        )
    }

    classDirectories.setFrom(debugTree)
    sourceDirectories.setFrom(files("src/main/java"))
    executionData.setFrom(
        fileTree(layout.buildDirectory) {
            include("jacoco/testDebugUnitTest.exec")
        },
    )
}

