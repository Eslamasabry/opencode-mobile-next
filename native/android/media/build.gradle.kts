// :android:media (plan 10 §3.3, plain Android library). S3-MEDIAATT owns image/ (AndroidImageCodec: decode with a
// memory cap, scale to ≤ 2048 px, re-encode) and clipboard/ (AndroidClipboardImages, the oc/clipboard port). Audio,
// speech, tts, pdf, qr and share packages arrive with the S5 media units; the media lead owns this build file after.
plugins { id("oc.android.library") }

android {
    testOptions { unitTests.isReturnDefaultValues = false }
}

dependencies {
    api(project(":engine:media"))
    implementation(libs.androidx.core.ktx)
    testImplementation(kotlin("test-junit"))
    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
}
