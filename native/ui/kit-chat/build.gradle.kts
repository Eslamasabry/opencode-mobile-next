plugins { id("oc.kmp.compose") }

// Kit parts of the chat (plan 09 §0.3, plan 08 §5.1). Slice 0 / P3: the transcript list, follow + jump, the reveal
// clock, the draw-phase fade and the grow-only modifier. No copy here: labels come in as parameters.
kotlin {
    sourceSets.commonMain.dependencies {
        implementation(project(":core:common"))
        implementation(project(":ui:design"))
        implementation(project(":ui:kit"))
        implementation(libs.compose.material3) // kit modules only (ExternalRules)
    }
}
