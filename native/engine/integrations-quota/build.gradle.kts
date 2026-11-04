plugins { id("oc.kmp.library") }

// :engine:integrations-quota (unit P12-BUILD-INTEGRATIONS-B, scope module :engine:integrations). Provider quota: the
// `/ocmn/quota/v1` snapshot parser, the bounded quota client over a transport port, the in-app (loopback 4195, Bearer)
// versus remote (origin, Basic) choice, and the In-app Ubuntu collector host's token and files. Core only, like every
// engine: HTTP, the Linux runtime, files and randomness arrive through ports.
kotlin {
    sourceSets.commonMain.dependencies {
        api(project(":core:common"))
        api(project(":core:data"))
    }
}
