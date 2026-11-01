plugins { id("oc.kmp.library") }

// :engine:entry (plan 07 §7, plan 15 S2-ENTRY). The one entry mechanism: every launch request waits here until
// bootstrap and the migration gate open, is decided by a pure table (EntryDecider) and handed to the shell exactly once.
// Depends on :core:common only; everything it reads about profiles, connections, setup and schedules arrives through
// the EntryWorld snapshot and the EntryEffects port, bound by the app graph (no engine below it is required yet).
kotlin {
    sourceSets.commonMain.dependencies {
        implementation(project(":core:common"))
    }
}
