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
    // Slice-0 P5 + P12-MIGRATION: the same-package build. `switch` has no applicationIdSuffix, so it is
    // io.github.eslamasabry.opencode_mobile and installs OVER the Flutter app (adb install -r) when signed with the
    // same key; on first launch MigrationWiring runs the day-1 migration behind the update gate. See
    // docs/native/units/P12-MIGRATION.md "Building the same-package APK" for the signing step.
    productFlavors {
        create("switch") {
            dimension = "channel"
        }
    }
}

dependencies {
    // One line per shipped module (plan 03 §20.1: the coordinator adds each at merge). Metro merges
    // every
    // contribution on this classpath into AndroidAppGraph.
    listOf(
            ":core:common",
            ":core:security",
            ":core:model",
            ":core:domain",
            ":engine:connection",
            ":engine:transcript",
            ":ui:design",
            ":ui:words",
            ":ui:routes",
            ":ui:kit",
            ":ui:shell",
            ":feature:chats",
            ":feature:chat",
            ":feature:servers",
            ":core:data",
            ":core:net",
            ":protocol:oc2",
            ":engine:profiles",
            ":engine:composer",
            ":engine:media",
            ":android:media",
            ":engine:agents",
            ":engine:delivery",
            ":engine:requests",
            ":ui:kit-chat",
            ":ui:kit-content",
            ":feature:settings",
            ":feature:onboarding",
            ":engine:settings",
            ":engine:analytics",
            ":engine:diagnostics",
            ":android:notifications",
            ":android:migration",
            ":engine:migration",
            ":feature:gate",
            ":protocol:paseo",
            ":feature:agents",
            ":engine:runtime",
            ":feature:phone",
            ":feature:linux",
            ":engine:git",
            ":feature:git",
            ":engine:updates", // P12-PLATFORM: in-app update notice
            ":feature:updates", // P12-PLATFORM: Updates page
            ":android:tiles", // P12-PLATFORM: QS tiles, shortcuts, share, update installer
            ":android:widgets", // P12-PLATFORM: home-screen widgets
            ":feature:preview", // PREVIEW-QR
            ":android:webview", // PREVIEW-QR
            ":android:camera", // PREVIEW-QR (pairing scanner)
            ":feature:prompts",
            ":engine:schedules",
            ":android:schedules",
            ":feature:schedules",
            ":engine:cards", // P12-CHAT-CARDS
            ":engine:files",
            ":feature:files",
        )
        .forEach { implementation(project(it)) }
    implementation(libs.androidx.activity.compose)
    implementation(libs.compose.runtime)
    implementation(libs.compose.ui)
    implementation(libs.compose.resources) // getString outside composition (LinuxWiring's job notice words); P6's proof screen too.
    debugImplementation(libs.compose.foundation) // P6 proof screen (debug only)
    implementation(libs.metrox.viewmodel.compose)
    implementation(libs.kotlinx.io.core) // Real app-private draft payload vault.
    implementation(libs.camerax.core) // PREVIEW-QR: the scanner wiring hands CameraX's SurfaceRequest to a TextureView.
    testImplementation(kotlin("test-junit"))
    testImplementation(libs.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
    implementation(libs.androidx.profileinstaller) // installs a baseline profile on sideloaded builds (start-up proof)
    // P3 stream proof screen (benchmark build type only)
    listOf(":feature:chat", ":ui:kit-chat").forEach { "benchmarkImplementation"(project(it)) }
    "benchmarkImplementation"(libs.compose.foundation)
    "benchmarkImplementation"(libs.kotlinx.serialization.json)
}
