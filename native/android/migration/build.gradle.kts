// :android:migration — the day-1 migration runner (plan 05 §7, unit S2-MIGRRUN in plan 15 §6.1).
// Allowed edges (ADR §6.1): :core:common, :core:security, :core:data, :core:model (+ :core:db when Room ships).
// While the core contract is being reconciled, this module depends only on :core:common and :core:security and
// reaches the prefs file, the secret store and the gate through its own narrow ports (docs/native/units/S2-MIGRRUN.md).
plugins { id("oc.android.library") }

android {
    // AGP 9 creates host tests for the tested build type only: run them on `qa`, the variant that holds the real
    // StepInterrupter, so `testQaUnitTest` covers main + qa sources (debug and release compile the no-op).
    testBuildType = "qa"
    buildTypes {
        // The `qa` build type is the only one that compiles the real StepInterrupter (`qa-kill-after`,
        // `qa-delay-ms`, `qa-free-bytes`; plan 05 §7.4 S0 step 3, §10.5 J2). debug and release get the no-op.
        create("qa") {
            initWith(getByName("debug"))
        }
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:security"))
    testImplementation(kotlin("test-junit"))
    testImplementation(libs.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
}

// One copy of the P5 synthetic fixture (drift-guarded by P5's CommittedFixturesTest), read in place.
val readersFixture: String =
    rootProject.layout.projectDirectory.dir("tools/flutter-migration-readers/src/test/resources/fixtures/synthetic-v1").asFile.path
tasks.withType<Test>().configureEach {
    systemProperty("oc.readersFixture", readersFixture)
}
