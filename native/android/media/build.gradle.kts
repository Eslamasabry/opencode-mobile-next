// :android:media (plan 10 §3.3, plain Android library). S3-MEDIAATT owns image/ (AndroidImageCodec: decode with a
// memory cap, scale to ≤ 2048 px, re-encode) and clipboard/ (AndroidClipboardImages, the oc/clipboard port). Audio,
// speech, tts, pdf, qr and share packages arrive with the S5 media units; the media lead owns this build file after.
plugins { id("oc.android.library") }

android {
    testOptions { unitTests.isReturnDefaultValues = false }
    // P12-FIX-LINUX-VOICE: JNI reads sherpa's config fields by name; the AAR ships an empty proguard.txt (plan 10 §2.4).
    defaultConfig { consumerProguardFiles("sherpa.keep") }
}

dependencies {
    api(project(":engine:media"))
    implementation(libs.androidx.core.ktx)
    // P12-FIX-LINUX-VOICE: sherpa-onnx 2.0.1, the Flutter app's pin. Vendored and hash-pinned in third_party/maven
    // (tools/vendor/fetch.sh); ExternalRules allow the group only in this module.
    implementation("com.k2fsa.sherpa.onnx:sherpa-onnx-android:2.0.1@aar")
    testImplementation(kotlin("test-junit"))
    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
}
