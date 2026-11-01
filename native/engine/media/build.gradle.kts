plugins { id("oc.kmp.library") }

// :engine:media (plan 10 §3.1, ADR §6.2 position 13). S3-MEDIAATT owns the attach/, office/, xml/ and preview/
// packages: the attachment pipeline (limits, MIME gate, bounded reads, photo/file/clipboard intake, pending-photo
// recovery), Office files as text, a hardened XML pull reader, and file previews (strict UTF-8, CSV/TSV, static SVG).
// Pure common Kotlin; Android image decode and the clipboard live in :android:media.
kotlin {
    sourceSets.commonMain.dependencies {
        api(project(":core:common")) // Failure.Media, Outcome in every public signature
        api(project(":core:model")) // PromptAttachment
        api(project(":core:domain")) // PickedFile, ActivityResults, PermissionGate

    }
}
