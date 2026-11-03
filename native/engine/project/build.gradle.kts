plugins { id("oc.kmp.library") }

// :engine:project (unit P12-BUILD-PROJECT-ENTRY-CHATS): the project-side engines of lib/state + lib/domain that the
// Flutter app keeps per project — shell output, terminal key bar and escape handling, local terminal sessions, project
// scan, git link repair, isolated task launch, worktree inspection, dev server line, session notes, pins, export and
// context usage. Pure Kotlin over ports; platform bridges are bound by the app graph.
kotlin {
    sourceSets.commonMain.dependencies {
        implementation(project(":core:common"))
        implementation(project(":core:model"))
        implementation(project(":core:security"))
        implementation(project(":core:data"))
    }
}
