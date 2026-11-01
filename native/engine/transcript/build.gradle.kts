plugins { id("oc.kmp.library") }

kotlin {
    sourceSets.commonMain.dependencies {
        implementation(project(":core:common"))
        implementation(project(":core:model"))
        implementation(project(":core:domain"))
        // :engine:connection (above transcript in ADR §6.2) is left out while it does not compile on the M0 branch;
        // only the `ConnectionGraph.transcript` accessor sugar needed it (see docs/native/units/S2-TX.md).
    }
    sourceSets.commonTest.dependencies {
        implementation(project(":core:testing"))
    }
    // P3 fixtures: the OC2 SSE recording is read on the JVM (test resources) and adapted with kotlinx JSON.
    sourceSets.getByName("desktopTest").dependencies {
        implementation(libs.kotlinx.serialization.json)
    }
}
