plugins { id("oc.kmp.library") }

// :engine:connectors (unit BUILD-INTEGRATIONS-A). Chat connectors (MCP): capabilities, catalogue, connector search and its loopback bridge, admission and runtime add (B-AGENT-130, 132..137, 212).
// Core only, like every engine: gateways, storage and the clock arrive through ports.
kotlin {
    sourceSets.commonMain.dependencies {
        api(project(":core:common"))
        api(project(":core:model"))
        api(project(":core:domain"))
        api(project(":core:data"))
    }
}
