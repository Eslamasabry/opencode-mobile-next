plugins { id("oc.kmp.library") }

// :engine:plans (unit BUILD-INTEGRATIONS-A). Plan cards, alerts, the quota monitor, the widget feed and quota answers (B-AGENT-314, 315, B-ANDR-065..067, 095..099, B-CHAT-090, 091).
// Core only, like every engine: gateways, storage and the clock arrive through ports.
kotlin {
    sourceSets.commonMain.dependencies {
        api(project(":core:common"))
        api(project(":core:model"))
        api(project(":core:domain"))
        api(project(":core:data"))
    }
    sourceSets.commonTest.dependencies {
        implementation(libs.kotlinx.datetime) // calendar dates in the ported Flutter fixtures
    }
}
