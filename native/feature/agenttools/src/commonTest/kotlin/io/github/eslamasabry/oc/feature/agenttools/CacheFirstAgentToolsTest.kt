package io.github.eslamasabry.oc.feature.agenttools

import io.github.eslamasabry.oc.core.model.AgentFeature
import io.github.eslamasabry.oc.core.model.AgentFeatureKind
import io.github.eslamasabry.oc.core.model.AgentFeatureOption
import io.github.eslamasabry.oc.feature.agenttools.presentation.AgentModelChoice
import io.github.eslamasabry.oc.feature.agenttools.presentation.AgentToolsUi
import io.github.eslamasabry.oc.feature.agenttools.presentation.KeptAgentTools
import io.github.eslamasabry.oc.feature.agenttools.presentation.hasRows
import io.github.eslamasabry.oc.feature.agenttools.presentation.seeded
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.collections.immutable.persistentListOf
import kotlinx.serialization.json.Json

class CacheFirstAgentToolsTest {
    private val features =
        listOf(
            AgentFeature("a", "Auto review", "Reviews edits", AgentFeatureKind.Toggle, on = true),
            AgentFeature("m", "Mode", null, AgentFeatureKind.Choice, selected = "x", options = persistentListOf(AgentFeatureOption("x", "Fast"))),
        )
    private val models = listOf(AgentModelChoice("gpt", "GPT", true))

    private fun kept() =
        Json.decodeFromString(KeptAgentTools.serializer(), Json.encodeToString(KeptAgentTools.serializer(), KeptAgentTools.of(features, models)))

    @Test
    fun `CF-87 the first frame with kept tools has the live features and models, with no skeleton`() {
        val first = AgentToolsUi(connected = true, loading = true, canOptions = true).seeded(kept())
        assertEquals(features, first.features.toList())
        assertEquals(models, first.models.toList())
        assertTrue(first.hasRows())
    }

    @Test
    fun `CF-88 with nothing kept the first-ever read has no rows, so the skeleton shows`() {
        assertFalse(AgentToolsUi(loading = true).seeded(null).hasRows())
    }

    @Test
    fun `CF-89 kept tools are capped`() {
        val many = List(100) { AgentFeature("$it", "f", null, AgentFeatureKind.Toggle) }
        assertEquals(KeptAgentTools.MAX_FEATURES, KeptAgentTools.of(many, emptyList()).features.size)
    }
}
