plugins { id("oc.kmp.library") }

// :engine:agents (plan 17 §1). Slice 2 holds only the OpenCode `ChatFeed` (feed/**, S2-CHATS, CR17-5); the agents
// engine owner adds AgentHost, readiness and the Paseo sources in slice 3. Core only, like every engine.
kotlin {
    sourceSets.commonMain.dependencies {
        implementation(project(":core:common"))
        implementation(project(":core:model"))
    }
}
