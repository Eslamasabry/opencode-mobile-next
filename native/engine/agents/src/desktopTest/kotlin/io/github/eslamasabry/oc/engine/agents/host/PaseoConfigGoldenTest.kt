package io.github.eslamasabry.oc.engine.agents.host

import io.github.eslamasabry.oc.engine.agents.catalog.AgentCatalog
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PaseoConfigGoldenTest {
    private val golden = checkNotNull(javaClass.getResource("/golden/paseo-config.json")).readText()

    @Test
    fun `B-LINUX-131 the helper config equals the Dart configuration() output and runs ACP agents as custom providers`() {
        val rendered = PaseoConfig.render(AgentCatalog.builtIn).json
        assertEquals(golden, rendered)
        assertTrue("\"kilo\":{\"extends\":\"acp\",\"label\":\"Kilo\",\"command\":[\"/home/oc/.local/bin/kilo\",\"acp\"]}" in rendered)
        assertTrue("\"cline\":{\"extends\":\"acp\",\"label\":\"Cline\",\"command\":[\"/home/oc/.local/bin/cline\",\"--acp\"]}" in rendered)
        // Native routes are not ACP providers; logs never go to /dev/null.
        assertTrue("\"claude\":{" !in rendered && "/dev/null" !in rendered)
        assertTrue("\"listen\":\"127.0.0.1:4199\"" in PaseoConfig.render(port = PaseoConfig.PREVIEW_PORT).json)
    }
}
