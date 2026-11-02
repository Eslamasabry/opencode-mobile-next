plugins { id("oc.android.application") }

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
            ":feature:prompts",
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
