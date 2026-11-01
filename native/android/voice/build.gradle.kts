// :android:voice (unit VOICE, 2026-11-01): the Android side of :engine:voice. Audio focus and capture
// (AndroidAudioSession, AndroidAudioCapture), playback (AndroidPcmPlayer), the system voice (AndroidSystemTts), the
// device and network probes, the HTTPS model fetcher, file access, and the sherpa-onnx recognizer and Kokoro
// synthesizer over the SherpaRuntime port. The sherpa AAR itself is not declared here: ExternalRules allow
// `com.k2fsa.sherpa.onnx` only in :android:media (see docs/native/units/VOICE.md "requests").
plugins { id("oc.android.library") }

android {
    testOptions { unitTests.isReturnDefaultValues = false }
    // `attempt {}` and the other :core:common inline functions are JVM 17 bytecode; oc.android.library leaves the
    // target at 11 (build-logic request in docs/native/units/VOICE.md).
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

dependencies {
    api(project(":engine:voice"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(kotlin("test-junit"))
    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
}
