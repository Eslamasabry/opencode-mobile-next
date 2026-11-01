plugins { id("oc.kmp.feature") }

// Slice 0 / P3: the chat transcript only (plan 09 §7). The ChatViewModel, entries, composer and status line are S2-CHAT.
kotlin {
    sourceSets.commonMain.dependencies {
        implementation(project(":engine:transcript")) // ← the engine this feature reads (public API only)
        implementation(project(":ui:kit-chat"))
    }
}
