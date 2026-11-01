plugins { id("oc.android.application") }

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
}
