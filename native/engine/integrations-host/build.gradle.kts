plugins { id("oc.kmp.library") }

// :engine:integrations-host (unit BUILD-INTEGRATIONS-B, cluster HOST). Bring-your-own Ubuntu host over the tailnet:
// target and tailnet rules, the pinned SSH host key, the Keystore signer bridge, the SSH runner over the built-in Linux
// shell, the host-record store (metadata in preferences, the secret envelope in the SecretStore) and the adoption
// controller. Core only, like every engine: the shell, files, DNS, signer channel and loopback HTTP arrive as ports.
kotlin {
    sourceSets.commonMain.dependencies {
        api(project(":core:common"))
        api(project(":core:data"))
    }
}
