plugins { id("oc.kmp.feature") }

kotlin {
    sourceSets.commonMain.dependencies {
        implementation(project(":engine:files"))
        implementation(project(":engine:runtime"))
        implementation(project(":ui:kit-chat"))
        implementation(project(":engine:attention"))
        implementation(project(":engine:agents")) // FeedFolders: which folders are projects (P12)
        implementation(project(":engine:git"))
    }
}
