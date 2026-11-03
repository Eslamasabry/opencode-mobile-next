plugins { id("oc.kmp.feature") }

// :feature:sessiontools (unit P12-BUILD-PROJECT-ENTRY-CHATS): chat ⋯ › Export conversation and Notes, over the
// session engines of :engine:project (SessionExporter, SessionNotes).
kotlin {
    sourceSets.commonMain.dependencies {
        implementation(project(":engine:project"))
    }
}
