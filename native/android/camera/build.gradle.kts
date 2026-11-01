// :android:camera (unit QR-CAMERA; plan 10 §10.4, §14.2, §2.7): the camera seams for the pairing scanner and photo
// capture. src/main holds the parts that need no camera library (scan window, latch, session, permission stages,
// capture files, Android camera probe). src/camerax holds the CameraX + zxing-cpp adapters; they join the main source
// set only once the version catalog has the camerax-* and zxing-cpp aliases (plan 03 §5) — and build-logic's
// ExternalRules must allow this module those groups in the same change, or configuration fails on purpose.
// No ML Kit, no Google Play services (plan 02 G8, F-Droid-ready).
plugins { id("oc.android.library") }

val catalog = extensions.getByType<VersionCatalogsExtension>().named("libs")

/** Literal coordinates only until the catalog entry exists (the :android:linux pattern); the catalog wins. */
fun catalogOr(alias: String, fallback: String): Any = catalog.findLibrary(alias).map<Any> { it.get() }.orElse(fallback)

val adapterLibraries = listOf("camerax-core", "camerax-camera2", "camerax-lifecycle", "zxing-cpp")
val adaptersEnabled = adapterLibraries.all { catalog.findLibrary(it).isPresent }

android {
    // core is built for JVM 17 and its inline helpers (attempt {}) cannot inline into the convention's default 11.
    // Requested for oc.android.library (report, build-logic requests); kept here until then.
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions { unitTests.isReturnDefaultValues = false }
    if (adaptersEnabled) {
        sourceSets { getByName("main") { kotlin.directories += "src/camerax/kotlin" } }
    }
}

dependencies {
    api(project(":core:domain"))
    implementation(libs.androidx.core.ktx)
    if (adaptersEnabled) {
        adapterLibraries.forEach { implementation(catalog.findLibrary(it).get()) }
    }
    testImplementation(kotlin("test-junit"))
    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
    // JVM-side QR decoding for the image fixtures (plan 10 §2.1: ZXing core is test-only; zxing-cpp has no host build).
    testImplementation(catalogOr("zxing-core", "com.google.zxing:core:3.5.4"))
}
