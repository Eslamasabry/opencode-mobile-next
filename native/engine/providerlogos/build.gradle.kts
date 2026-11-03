plugins { id("oc.kmp.library") }

// :engine:providerlogos (unit BUILD-INTEGRATIONS-A). Provider logos (models.dev slugs, cache, bundled marks), tool summaries and skill conversations (B-AGENT-205..207, B-NAV-076, B-CHAT-108).
// Core only, like every engine: gateways, storage and the clock arrive through ports.
kotlin {
    sourceSets.commonMain.dependencies {
        api(project(":core:common"))
        api(project(":core:model"))
        api(project(":core:domain"))
        api(project(":core:data"))
    }
}
