plugins { id("oc.kmp.library") }

kotlin {
    sourceSets.commonMain.dependencies {
        implementation(project(":core:common"))
        implementation(project(":core:model"))
        implementation(project(":core:domain"))
        implementation(project(":engine:connection")) // above transcript in ADR §6.2
    }
    // P3 fixtures: the OC2 SSE recording is read on the JVM (test resources) and adapted with kotlinx JSON.
    sourceSets.getByName("desktopTest").dependencies {
        implementation(libs.kotlinx.serialization.json)
    }
}
