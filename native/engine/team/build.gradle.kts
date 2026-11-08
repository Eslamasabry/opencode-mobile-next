plugins { id("oc.kmp.library") }

// :engine:team (owner 2026-11-08: AI Team before the switch; unit s23-team). The AI Team's truth on native: the saved
// team config Flutter left on the profile (`orchestration`), the phone engine's schema-1 workspace (projects, tasks,
// requests, servers, roles) read through the TeamHost port, and one serial lane per profile. Core only, like every
// engine: the HTTP read, the Keystore token and the profile store arrive through TeamHost (androidApp wiring).
kotlin {
    sourceSets.commonMain.dependencies {
        api(project(":core:common"))
    }
}
