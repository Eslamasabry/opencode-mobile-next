plugins { id("oc.kmp.feature") }

kotlin {
    sourceSets.commonMain.dependencies {
        implementation(project(":engine:agents")) // FeedFolders: which folders are projects (P12)
    }
}
