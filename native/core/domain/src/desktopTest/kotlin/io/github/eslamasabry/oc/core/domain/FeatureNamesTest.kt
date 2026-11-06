package io.github.eslamasabry.oc.core.domain

import io.github.eslamasabry.oc.core.domain.capabilities.ServerCapabilities
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Facets a gateway reports itself rather than through a ServerCapabilities flag: session skills
 * (`SessionSkillGateway.sessionSkillsSupported`, kept off the flags on purpose) and the read-only setup inspection
 * views. Their FeatureIds are used as-is by the protocol modules and the app graph.
 */
private val GatewayFacets = setOf("sessionSkills", "setupInspection", "setupConfigSources")

/** FeatureId contract: every `unavailable("x")` default names a real ServerCapabilities flag. */
class FeatureNamesTest {
    @Test
    fun `every Unavailable default names a ServerCapabilities flag`() {
        val dir = File("src/commonMain/kotlin/io/github/eslamasabry/oc/core/domain")
        val names = dir.walk().filter { it.extension == "kt" }
            .flatMap { Regex("""unavailable\("(\w+)"\)""").findAll(it.readText()).map { m -> m.groupValues[1] } }
            .toSet()
        assertTrue(names.size > 20, "scan found only $names")
        val flags = ServerCapabilities.Unknown.flags().keys
        val unknown = names - flags - GatewayFacets
        assertTrue(unknown.isEmpty(), "not capability flags: $unknown")
    }
}
