plugins { id("oc.kmp.library") }

// :engine:integrations-usage (unit P12-BUILD-INTEGRATIONS-B). The usage subscriptions model behind Usage › Plans,
// its alerts and the home-widget snapshot: the merge of every connection's readings and sign-ins into one list, the
// cached readings, alert thresholds, budgets and the profile sweep. Core only, like every engine: readers arrive
// through ports (UsageSource), storage through KeyValueStore.
kotlin {
    sourceSets.commonMain.dependencies {
        api(project(":core:common"))
        api(project(":core:model"))
        api(project(":core:data"))
    }
}
