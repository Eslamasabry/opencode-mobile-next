plugins { id("oc.kmp.library") }

// :engine:factsbook (s23-factsbook, owner 2026-11-08: FactsBook before the switch). The FactsBook App API v1 client
// (`/v1`, a bearer token, the node's error envelope), the pairing read IN PLACE from the Flutter keys
// (`oc.factsbookNode.<id>` in preferences; `oc.factsbook.<id>`, `oc.factsbookOwner.<id>`, `oc.factsbookOutbox.<id>`
// in the secret store, copied there by the day-1 migration under the same names), the phone outbox and the
// controller the pages read. Core only, like every engine: HTTP arrives through [FactsBookHttp].
kotlin {
    sourceSets.commonMain.dependencies {
        api(project(":core:common"))
        api(project(":core:data"))
        implementation(project(":core:model"))
        implementation(project(":core:security"))
        implementation(project(":core:net"))
        api(libs.kotlinx.datetime) // a reminder's local date-time (ReminderTiming)
    }
}
