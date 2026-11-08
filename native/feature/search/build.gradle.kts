plugins { id("oc.kmp.feature") }

kotlin {
    sourceSets.commonMain.dependencies {
        implementation(project(":engine:connection"))
        implementation(project(":engine:composer"))
    }
}

// Search B1 proof renders (capture only): `-Poc.searchRenders=<dir>` writes them; otherwise the test returns at once.
tasks.withType<Test>().configureEach {
    systemProperty("oc.search.renders", providers.gradleProperty("oc.searchRenders").orElse("").get())
}
