plugins { id("oc.kmp.library") }

// The agents engine (plan 17 §1, plan 15 S3-AGENTSE): catalog and verified pins, readiness, install pipeline, phone
// check, sign-in model, auto-approval, merged feed, memories and the AgentHost seam. Common code only; the Android
// binding of AgentHost lives in :android:agenthost (S3-AGENTHOST).
// :core:data is not a dependency yet: it does not compile on native/m0 while the core contract is reconciled. Storage
// goes through the narrow ports in store/AgentPorts.kt; their KeyValueStore/SecretStore adapters bind above both.
kotlin {
    sourceSets.commonMain.dependencies {
        api(project(":core:common"))
        api(project(":core:model"))
        api(project(":core:domain"))
        implementation(project(":core:security"))
    }
}
