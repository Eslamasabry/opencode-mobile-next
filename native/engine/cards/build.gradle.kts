plugins { id("oc.kmp.library") }

// :engine:cards (P12-CHAT-CARDS; plan 09 §13): agent cards. The card validator and its revision (byte-compatible with
// the Flutter normaliser), card state from the authoritative transcript, the answer wire format, the held answer
// with Undo and send-once delivery, availability (desired vs effective), bounded history reads, recovery coverage and
// the show-card permission rule. No UI and no transport: the chat feature hands it parts and a sender.
// Not in ModuleRules' engine order, so it may use :core only (no other engine); every edge it needs is a port here.
kotlin {
    sourceSets.commonMain.dependencies {
        implementation(project(":core:common"))
        api(project(":core:model")) // Part, MessageWithParts and PromptAttachment are in the public API
        api(libs.kotlinx.collections.immutable)
        api(libs.kotlinx.serialization.json) // tool input arrives as a JsonObject
    }
}
