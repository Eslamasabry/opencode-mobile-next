plugins { id("oc.kmp.library") }

// :engine:migration (P12-MIGRATION): the platform-neutral driver of the day-1 migration. It runs the runner behind the
// :core:data MigrationGate, maps its states onto GateState and onto what the update gate shows, and owns the gate's
// ways out (retry, start fresh, notices). The Android runner itself is :android:migration; :androidApp binds it here
// through MigrationRunnerPort (graph/MigrationWiring.kt). No other engine is needed.
kotlin {
    sourceSets.commonMain.dependencies {
        api(project(":core:common"))
        api(project(":core:data"))
    }
}
