plugins { id("oc.android.application") }

android {
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
            ":android:migration",
            ":engine:migration",
            ":feature:gate",
        )
        .forEach { implementation(project(it)) }
    implementation(libs.androidx.activity.compose)
    implementation(libs.compose.runtime)
    implementation(libs.compose.ui)
    implementation(libs.metrox.viewmodel.compose)
    implementation(libs.kotlinx.io.core) // Real app-private draft payload vault.
    testImplementation(kotlin("test-junit"))
    testImplementation(libs.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
}
