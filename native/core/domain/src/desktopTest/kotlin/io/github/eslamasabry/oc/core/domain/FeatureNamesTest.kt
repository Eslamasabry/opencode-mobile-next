package io.github.eslamasabry.oc.core.domain

import io.github.eslamasabry.oc.core.domain.capabilities.ServerCapabilities
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

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
        val unknown = names - flags
        assertTrue(unknown.isEmpty(), "not capability flags: $unknown")
    }
}
