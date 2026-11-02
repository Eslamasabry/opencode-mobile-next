plugins { id("oc.kmp.feature") }

kotlin {
    sourceSets.commonMain.dependencies {
        implementation(libs.kotlinx.datetime) // Calendar ranges in the device's own time zone (B-PROTO-122).
    }
}
