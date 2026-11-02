plugins { id("oc.kmp.feature") }

// :feature:review (unit P12-REVIEW): Changes (a turn's or the conversation's, Looks good / Undo all), one file's diff,
// Undo from this prompt?, and Review the undo. Reached from the chat's message menu and its Changes action.
kotlin {
    sourceSets.getByName("desktopTest").dependencies {
        implementation(libs.compose.desktop.currentos)
    }
    sourceSets.commonMain.dependencies {
        implementation(project(":engine:review"))
        implementation(project(":engine:connection")) // the conversation's own connection graph (ReviewHub)
        implementation(project(":ui:kit-chat")) // KitDiffView, DiffModel and the chat environment's redactor
        implementation(libs.kotlinx.collections.immutable)
    }
}
