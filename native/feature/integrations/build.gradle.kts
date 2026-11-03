plugins { id("oc.kmp.feature") }

// :feature:integrations (unit BUILD-INTEGRATIONS-A): the Connectors page (Settings › Agent › Tools) over the live
// server's MCP connectors (OC2 `GET /api/mcp`, connect/disconnect at the current location), and the MCP catalogue
// (public registry as switches; add until restart / remove at the current location) over :engine:connectors.
kotlin {
    sourceSets.commonMain.dependencies {
        // api: the app's wiring builds the engine's CatalogueStore and RegistryTransport for this feature's port.
        api(project(":engine:connectors"))
    }
}
