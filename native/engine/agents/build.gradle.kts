plugins { id("oc.kmp.library") }

// The agents engine (plan 17 §1): the OpenCode `ChatFeed` (feed/**), plus the S3-AGENTSE port: catalog and verified
// pins, readiness, install pipeline, phone check, sign-in model, auto-approval, memories and the AgentHost seam.
// Common code only; Android bindings live in the app's AgentsWiring. Core only, like every engine.
kotlin {
    sourceSets.commonMain.dependencies {
        api(project(":core:common"))
        api(project(":core:model"))
        api(project(":core:domain"))
        implementation(project(":core:security"))
        implementation(libs.kotlinx.datetime) // FeedDay: the Today / Earlier boundary on the person's clock
    }
}
