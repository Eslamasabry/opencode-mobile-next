plugins { id("oc.kmp.feature") }

// :feature:devservices (BUILD-RUNTIME-A; Flutter lib/ui/screens/development_services_screen.dart).
// One project's
// development services (saved commands started as owned shells) and the dev servers found running
// on the phone.
// The rules live in :engine:runtime (services, devservices); this module arranges kit parts over
// them.
kotlin {
    sourceSets.commonMain.dependencies {
        implementation(project(":ui:kit-chat"))
        implementation(project(":engine:runtime"))
    }
}
