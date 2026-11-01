plugins { id("oc.kmp.library") }

// :engine:requests (plan 09 §5, plan 15 S2-REQ as accepted by C15-5 / ADR-09-1): requests that wait on the person.
// Slice 2 holds permissions and one-question asks: the pending list, the 3 s answer hold with Undo, exactly one reply
// per request, the receipts, and the permission wording (PermissionAsk). Forms, approval modes, the always-allow
// invitation and notifications are S3-REQFORMS / S4. No UI.
kotlin {
    sourceSets.commonMain.dependencies {
        implementation(project(":core:common"))
        api(project(":core:model")) // PermissionRequest, PendingQuestion and PermissionReply are in the public API
        api(project(":core:domain")) // the gateway facets and ServerCapabilities the engine is built from
    }
}
