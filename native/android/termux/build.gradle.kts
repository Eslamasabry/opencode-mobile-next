// :android:termux — the move-from-Termux journey (ADR D7 "migrate-out only"; plan 03 §12, plan 11 §2.1 rows 53/63).
// Platform-neutral Kotlin over ports: the space budget, the provider-name reader, the private archive importer with
// its in-directory receipt, the Termux transport (scripts + authenticated loopback receiver), the controller with its
// journal, the profile switch, the owner and the page presenter. The Termux RUN_COMMAND bridge, the profile store and
// the preferences file are ports the app binds. Catalog rows B-LINUX-233..238 and B-SEC-177. See README.md.
plugins { id("oc.android.library") }

android {
    // :core:common's inline attempt {} is JVM 17 bytecode (same seam as :android:tiles).
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.isReturnDefaultValues = false
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(kotlin("test-junit"))
    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
}
