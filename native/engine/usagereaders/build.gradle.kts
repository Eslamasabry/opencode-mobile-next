plugins { id("oc.kmp.library") }

// :engine:usagereaders (unit BUILD-INTEGRATIONS-A). Plan usage readers: the computer usage source over a terminal, the Codex/Paseo sources and the in-Ubuntu quota collector host (B-AGENT-071..076, 312, 313, B-LINUX-176..178).
// Core only, like every engine: gateways, storage and the clock arrive through ports.
kotlin {
    sourceSets.commonMain.dependencies {
        api(project(":core:common"))
        api(project(":core:model"))
        api(project(":core:domain"))
        api(project(":core:data"))
    }
}
