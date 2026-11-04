plugins { id("oc.kmp.feature") }

kotlin {
    sourceSets.commonMain.dependencies {
        implementation(project(":engine:git"))
        implementation(
            project(":engine:agents")
        ) // ChatFeed and the return brief (17 §1.6, 07 §5 B15; S2-CHATS)
        implementation(project(":engine:attention"))
        implementation(project(":engine:settings"))
    }
}
