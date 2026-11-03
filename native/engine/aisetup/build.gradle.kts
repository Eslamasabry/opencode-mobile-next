plugins { id("oc.kmp.library") }

// :engine:aisetup (unit BUILD-INTEGRATIONS-A). AI setup: planner, reviewed transactions with verify and undo, the setup controller and review states (B-AGENT-211, 230..236).
// Core only, like every engine: gateways, storage and the clock arrive through ports.
kotlin {
    sourceSets.commonMain.dependencies {
        api(project(":core:common"))
        api(project(":core:model"))
        api(project(":core:domain"))
        api(project(":core:data"))
    }
}
