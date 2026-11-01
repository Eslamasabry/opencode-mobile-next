plugins { id("oc.kmp.library") }

// :engine:composer (plan 09 §10, plan 15 S3-COMPE as amended by C15-1): drafts, the attachment vault, saved prompts
// (stash), prompt history, phrase chips, review references and the photo recovery record. No UI.
// Storage is reached through the module's own ports (ComposerPrefs, PayloadStore): :core:data did not compile on
// 2026-11-01 while the core contract was reconciled, so the KeyValueStore adapter is a follow-up (S3-COMPE report).
kotlin {
    sourceSets.commonMain.dependencies {
        implementation(project(":core:common"))
        api(project(":core:model")) // PromptAttachment is in the public API (the send pipeline's attachment type)
        api(project(":core:security")) // Redactor: secret checks for phrases, saved-prompt identities, photos
        implementation(libs.kotlinx.io.core) // FilePayloadStore: the Flutter vault layout on disk
    }
}
