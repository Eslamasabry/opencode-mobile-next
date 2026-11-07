plugins { id("oc.kmp.feature") }

kotlin {
    sourceSets.commonMain.dependencies {
        implementation(project(":engine:integrations-usage"))
        implementation(
            project(":engine:usage")
        ) // Calendar ranges in the device's own time zone (B-PROTO-122).
        // KitLimitBar (pace marker) and KitTrendLine (cost chart), B-159/B-160.
        implementation(project(":ui:kit-content"))
    }
}

