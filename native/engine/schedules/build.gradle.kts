plugins { id("oc.kmp.library") }

// :engine:schedules (plan 07 §5 K, plan 15 S4-SCHED; unit P12-SCHEDULES). Scheduled prompts: the Flutter-compatible
// store (`oc.scheduledPrompts.<profile>`, schemaVersion 1, the same CAS envelope), occurrence claims
// (`oc.scheduleClaims.<profile>`), recurrence, the one-dispatch runner and the editor/list engine. Core only, like
// every engine: the gateway, the profile fingerprint and the OS scheduler arrive through ports (ScheduleWorld).
kotlin {
    sourceSets.commonMain.dependencies {
        api(project(":core:common"))
        api(project(":core:model"))
        api(project(":core:domain"))
        api(project(":core:data"))
        api(libs.kotlinx.datetime) // TimeZone is in ScheduleEngine's constructor; wall-clock times across zone and DST changes
    }
}
