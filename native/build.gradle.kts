// Every plugin a module uses goes on the root classpath once, with `apply false` (plan 03 §3.2).
// Slice 0 lists only the plugins its modules apply; ksp, room3, roborazzi, baselineprofile, kover and
// dependency-analysis join this block in the slice that first uses them.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.kmp.library) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.metro) apply false
    // Dev loop (tool/dev/README.md): Compose Hot Reload, applied only with -Poc.hotReload=true (ADR §4 row 16 trial).
    alias(libs.plugins.compose.hotreload) apply false
    // Applied by :androidApp only when the private Firebase config is present (OC_GOOGLE_SERVICES_DIR).
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.firebase.crashlytics) apply false
}

// Q-TESTROT: one task that compiles every module's unit-test sources (desktop, Android host, JVM, Android unit
// tests and :quality's secTest). T1 runs it on every change and T2 as its own step, so an API change that breaks
// another module's tests fails the gate that made it, instead of rotting until someone runs that module.
val unitTestCompileTask =
    Regex("compileTestKotlinDesktop|compileAndroidHostTest|compileTestKotlin|compileSecTestKotlin|" +
        "compile(Debug|PreviewDebug|SwitchDebug|Qa)UnitTestKotlin")
tasks.register("compileAllTests") {
    group = "verification"
    description = "Compiles every module's unit-test source sets without running them."
    subprojects.forEach { p ->
        // Query names lazily: matching realizes unrelated AGP Compose Preview tasks, which cannot be created here.
        dependsOn(
            p.provider {
                p.tasks.names
                    .filter { unitTestCompileTask.matches(it) }
                    .map { p.tasks.named(it) }
            },
        )
    }
}

// CI quarantine (tool/ci/QUARANTINE.md): excludes the listed tests on CI only; -PrunQuarantined runs them.
apply(from = "tool/ci/quarantine.gradle.kts")

// CI lanes (tool/ci/LANES.md): -PskipGoldens=true drops the picture tests, -PonlyGoldens=true runs only them.
apply(from = "tool/ci/goldens.gradle.kts")

// B1-LINT: the guard tests that scan source, as rules checked on the compile check path (tools/rules). Every module's
// main Kotlin compile first waits for ocSourceRules (detekt with the OC rules over the whole tree) and ocDataChecks
// (the repository-file checks); both run once per build and are skipped when nothing they read changed. A violation
// fails the compile check with a plain message and a way forward. -Poc.skipRules=true skips both for one run.
subprojects {
    if (path != ":tools:rules") {
        tasks.configureEach {
            if (name.startsWith("compile") && name.contains("Kotlin") && !name.contains("Test")) {
                dependsOn(":tools:rules:ocSourceRules", ":tools:rules:ocDataChecks")
            }
        }
    }
}
