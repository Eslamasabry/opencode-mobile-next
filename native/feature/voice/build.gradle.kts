plugins { id("oc.kmp.feature") }

// P12-VOICE: voice typing in the composer (tap / hold / slide to cancel, live words, the auto-send
// countdown), read
// aloud on replies and Settings › Voice typing. Engines: :engine:voice (models, dictation, read
// aloud). The composer
// and the message menu reach these through kit-chat's voice slots, so no feature imports another.
kotlin {
    sourceSets.commonMain.dependencies {
        implementation(project(":engine:voice"))
        implementation(project(":engine:settings"))
        implementation(project(":ui:kit-chat"))
        implementation(libs.kotlinx.collections.immutable)
    }
}
