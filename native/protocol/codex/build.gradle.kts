plugins { id("oc.kmp.library") }

// :protocol:codex — the Codex app-server client (JSON-RPC over one WebSocket, pinned to app-server 0.153.4), a port of
// lib/codex/*.dart and lib/state/codex_connection_probe.dart. Depends on the frozen core contract only (ADR §6.1:
// protocol → common/model/domain/security).
kotlin {
    sourceSets.commonMain.dependencies {
        api(project(":core:domain"))
    }
}
