plugins { id("oc.kmp.library") }

// :engine:byohost (unit BUILD-INTEGRATIONS-A). Bring-your-own VPS host: bundle pins, key-only enrollment, adoption, retry, local removal vs revoke (B-AGENT-044..049).
// Core only, like every engine: gateways, storage and the clock arrive through ports.
kotlin {
    sourceSets.commonMain.dependencies {
        api(project(":core:common"))
        api(project(":core:model"))
        api(project(":core:domain"))
        api(project(":core:data"))
    }
}
