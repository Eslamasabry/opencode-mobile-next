plugins { id("oc.kmp.library") }

// :engine:review (unit P12-REVIEW): the change review of one conversation. Turn files from the server's tool records,
// the staged undo (stage / clear / commit) and the one-step undo through the core gateway facets, and the message
// count of an undo. No UI. ADR §6.2: "review" is not in the engine order yet, so this engine uses core only; the
// feature resolves the conversation's connection and hands the gateways in.
kotlin {
    sourceSets.commonMain.dependencies {
        implementation(project(":core:common"))
        api(project(":core:model"))
        api(project(":core:domain"))
        implementation(libs.kotlinx.collections.immutable)
    }
}
