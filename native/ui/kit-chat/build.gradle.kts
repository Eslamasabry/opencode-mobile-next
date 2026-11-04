plugins {
    id("oc.kmp.compose")
    alias(
        libs.plugins.roborazzi
    ) // S2-KCB: renderer goldens recorded and verified through the plugin (plan 06 §15.3)
}

// Kit parts of the chat (plan 09 §0.3, plan 08 §5.1). Slice 0 / P3: the transcript list, follow +
// jump, the reveal
// clock, the draw-phase fade and the grow-only modifier. S2-KCB: the renderers (Markdown blocks,
// code blocks, the diff
// view, tool step rows, the permission card shell). No copy here: labels come in as parameters.

// Artifacts the catalog has no entries for yet (S2-KCB report, "catalog requests"); test versions
// come from the
// catalog's pins, as in :ui:kit (P7).
val cmp = libs.versions.cmp.asProvider().get()
val roborazzi = libs.versions.roborazzi.get()
val syntaxmp =
    "0.3.0" // plan 09 §9.2 decision; catalog request: syntaxmp = "0.3.0", libs.syntaxmp.tokenizer

kotlin {
    sourceSets.commonMain.dependencies {
        implementation(project(":core:common"))
        implementation(
            project(":core:security")
        ) // mono surfaces display Redactor.redact(source) (plan 09 §9.2, R18)
        implementation(
            project(":core:markdown")
        ) // P12-CHAT-FEEL: transcript rows parse into KitMdBlock (KitMdSource)
        implementation(project(":ui:design"))
        implementation(project(":ui:kit"))
        implementation(project(":ui:words"))
        implementation(libs.compose.material3) // kit modules only (ExternalRules)
        implementation(libs.kotlinx.collections.immutable)
        implementation(
            "com.gallatinapps.syntaxmp:syntaxmp-tokenizer:$syntaxmp"
        ) // behind CodeTokenizer (swap seam)
    }
    sourceSets.getByName("androidHostTest").dependencies {
        implementation(libs.junit4)
        implementation(libs.robolectric)
        implementation("org.jetbrains.compose.ui:ui-test-junit4:$cmp")
        implementation(
            "androidx.compose.ui:ui-test-manifest:$cmp"
        ) // ComponentActivity for the compose rule
        implementation("io.github.takahirom.roborazzi:roborazzi:$roborazzi")
        implementation("io.github.takahirom.roborazzi:roborazzi-compose:$roborazzi")
        implementation("io.github.takahirom.roborazzi:roborazzi-junit-rule:$roborazzi")
    }
}

roborazzi {
    outputDir.set(layout.projectDirectory.dir("src/screenshots"))
}

// Robolectric 4.17 on the JDK 21 launcher (audit #10), as in :ui:kit (P7): host tests need these
// opens.
tasks
    .withType<Test>()
    .matching { it.name.contains("AndroidHostTest") }
    .configureEach {
        jvmArgs(
            "--add-exports=java.base/jdk.internal.access=ALL-UNNAMED",
            "--add-opens=java.base/jdk.internal.access=ALL-UNNAMED",
            "--add-opens=java.base/java.io=ALL-UNNAMED",
            "--add-opens=java.base/java.lang=ALL-UNNAMED",
        )
    }
