// :android:schedules (unit P12-SCHEDULES; plan 11 §12, plan 15 S4-SCHED). The Android half of scheduled prompts:
// WorkManager timed runs (one unique work per occurrence, never retried after a dispatch), the started → finished
// notification in one place, and the run's routing extras. The worker and the notification trampoline are thin
// classes in :androidApp (SchedulesWiring.kt) that reach the app graph; everything they do lives here and in
// :engine:schedules.
plugins { id("oc.android.library") }

android { testOptions { unitTests.isReturnDefaultValues = false } }

// TODO(catalog): work-runtime is the Flutter app's pin (android/app/build.gradle.kts), as :android:widgets uses it.
fun catalogOr(alias: String, fallback: String): Any =
    extensions
        .getByType<VersionCatalogsExtension>()
        .named("libs")
        .findLibrary(alias)
        .map<Any> { it.get() }
        .orElse(fallback)

dependencies {
    api(project(":engine:schedules"))
    implementation(project(":core:common"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.core)
    api(catalogOr("androidx-work-runtime", "androidx.work:work-runtime:2.12.0"))
    testImplementation(kotlin("test-junit"))
    testImplementation(libs.junit4)
    testImplementation(libs.robolectric) // ScheduleExactAlarmsTest (AlarmManager shadow)
}
