// :android:agenthost — slice 3 (S3-AGENTHOST; plan 11 §2.1 row `:android:agenthost`, §4.1, §5.3 `AgentProcesses`;
// plan 17 §1.2). The phone agents' host: private agent processes and homes, the Paseo helper's lifecycle, the Claude
// sign-in pipes and the private auth probe, copied from the Flutter app's Kotlin (package moved, every copy a twin in
// the README) and served as Outcome calls by AndroidAgentHost.
//
// Depends only on modules the ADR §6.1 rules allow today (`:android:*` → `:core:*`, `:engine:*`). CR11-0's
// `:android:agenthost → :android:linux` edge is not in ModuleRules yet, so the runtime arrives through the ports in
// AgentGuestPorts.kt (bound by S3-RTSPLIT); `:engine:agents` does not compile on the flat contract yet, so the
// `AgentHost` adapter is a follow-up (docs/native/units/S3-AGENTHOST.md).
plugins { id("oc.android.library") }

android {
    // :core:common's inline attempt {} is JVM 17 bytecode; the convention leaves Android libraries on AGP's default
    // (11), which cannot inline it (INLINE_FROM_HIGHER_PLATFORM). Build-logic request in the S3-AGENTHOST report.
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions { unitTests.isReturnDefaultValues = false }
}

// TODO(catalog): org.json is the Flutter app's exact test pin (S1-LINUX request R2); the catalog alias wins once it
// exists. Android ships org.json in the platform, so only the plain-JVM tests need the jar.
fun catalogOr(alias: String, fallback: String): Any =
    extensions.getByType<VersionCatalogsExtension>().named("libs").findLibrary(alias).map<Any> { it.get() }.orElse(fallback)

dependencies {
    implementation(project(":core:common"))
    implementation(project(":engine:runtime"))
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit4)
    testImplementation(kotlin("test-junit"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(catalogOr("json", "org.json:json:20180813"))
}
