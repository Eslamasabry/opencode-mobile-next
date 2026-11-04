plugins { id("oc.kmp.library") }

// :protocol:codex — the Codex app-server client (JSON-RPC over one WebSocket, pinned to app-server 0.153.4), a port of
// lib/codex/*.dart and lib/state/codex_connection_probe.dart (PROTO), plus AGENT-2's bounded Codex reads over
// :core:net's socket API.
kotlin {
    sourceSets.commonMain.dependencies {
        api(project(":core:domain"))
        api(project(":core:common"))
        api(project(":core:net"))
        implementation(project(":core:model"))
        implementation(project(":core:security"))
    }
}
