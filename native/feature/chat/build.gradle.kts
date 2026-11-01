plugins { id("oc.kmp.feature") }

// S2-CHAT (plan 09 §6–§7, plan 07 §3.4/§2.5, plan 15 row S2-CHAT): the chat screen, its ViewModel and entry, the
// status line, find, message actions, BasicComposerHost, arrivals and the Back guard. Request files belong to S2-REQ.
kotlin {
    sourceSets.commonMain.dependencies {
        implementation(project(":engine:connection")) // ← engines this feature reads (public API only)
        implementation(project(":engine:transcript"))
        implementation(project(":ui:kit-chat"))
        implementation(project(":engine:requests")) // S2-REQ: the request slot (permissions, one-question asks)
        implementation(libs.kotlinx.collections.immutable)
    }
}
