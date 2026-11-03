plugins { id("oc.kmp.feature") }

kotlin {
    sourceSets.commonMain.dependencies {
        implementation(project(":engine:usage")) // Calendar ranges in the device's own time zone (B-PROTO-122).
    }
}
