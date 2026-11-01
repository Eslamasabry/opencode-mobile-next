plugins { id("oc.android.application") }

android {
    // Slice-0 proofs P3 and start-up (native/proof-p3-2026-11-01): a release build that the shell may profile, signed
    // with the debug key so Macrobenchmark can install it. R8 on, like the release the plan ships (ADR §9.9).
    buildTypes {
        create("benchmark") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
            isDebuggable = false
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "benchmark-rules.pro")
        }
    }
}

dependencies {
    // One line per shipped module (plan 03 §20.1: the coordinator adds each at merge). Metro merges every
    // contribution on this classpath into AndroidAppGraph.
    listOf(
        ":core:common", ":core:security", ":core:model", ":core:domain",
        ":engine:connection", ":engine:transcript",
        ":ui:design", ":ui:words", ":ui:routes", ":ui:kit", ":ui:shell",
        ":feature:chats",
    ).forEach { implementation(project(it)) }
    implementation(libs.androidx.activity.compose)
    implementation(libs.compose.runtime)
    implementation(libs.compose.ui)
    implementation(libs.metrox.viewmodel.compose)
    implementation(libs.androidx.profileinstaller) // installs a baseline profile on sideloaded builds (start-up proof)
    // P3 stream proof screen (benchmark build type only)
    listOf(":feature:chat", ":ui:kit-chat").forEach { "benchmarkImplementation"(project(it)) }
    "benchmarkImplementation"(libs.compose.foundation)
    "benchmarkImplementation"(libs.kotlinx.serialization.json)
}
