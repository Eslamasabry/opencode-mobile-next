plugins { id("oc.android.application") }

android {
    // Slice-0 P5 (native/proof-p5-2026-11-01): the real package id, signed like the owner-lineage Flutter build (the
    // local key, which is this machine's debug keystore), so `adb install -r` updates Flutter 2.1.0+57 in place.
    productFlavors {
        create("switch") {
            dimension = "channel"
            signingConfig = signingConfigs.getByName("debug")
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
    // P5: frozen-FQCN surfaces and the migration proof, both from library modules (switch flavour only)
    "switchImplementation"(project(":android:surfaces"))
    "switchImplementation"(project(":android:migration"))
}
