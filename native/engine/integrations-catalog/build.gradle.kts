plugins { id("oc.kmp.library") }

// :engine:integrations-catalog (unit P12-BUILD-INTEGRATIONS-B). The connector, MCP, plugin, skill and provider rules of
// the Flutter library screens: connector-search results, plugin inventory, MCP drafts/runtime/chat connect, skill
// activation and authoring, provider lists, sign-in links and provider logos. Core only, like every engine: the server
// arrives through small ports (CatalogTransport, gateway lambdas), so the rules run and are tested without a network.
kotlin {
    sourceSets.commonMain.dependencies {
        api(project(":core:common"))
        api(project(":core:model"))
        api(project(":core:domain"))
        api(project(":core:security"))
        api(libs.kotlinx.serialization.json) // JsonElement/JsonObject appear in the parsers' signatures
    }
}
