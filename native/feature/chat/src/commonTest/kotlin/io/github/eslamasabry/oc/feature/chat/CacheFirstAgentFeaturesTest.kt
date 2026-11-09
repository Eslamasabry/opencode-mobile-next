package io.github.eslamasabry.oc.feature.chat

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.feature.chat.agentfeatures.presentation.AgentFeatureOptionUi
import io.github.eslamasabry.oc.feature.chat.agentfeatures.presentation.AgentFeatureUi
import io.github.eslamasabry.oc.feature.chat.agentfeatures.presentation.AgentFeaturesBody
import io.github.eslamasabry.oc.feature.chat.agentfeatures.presentation.AgentFeaturesSnapshot
import io.github.eslamasabry.oc.feature.chat.agentfeatures.presentation.AgentFeaturesUi
import io.github.eslamasabry.oc.feature.chat.agentfeatures.presentation.KeptAgentFeatures
import io.github.eslamasabry.oc.feature.chat.agentfeatures.presentation.body
import io.github.eslamasabry.oc.feature.chat.agentfeatures.presentation.readFailed
import io.github.eslamasabry.oc.feature.chat.agentfeatures.presentation.seeded
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.serialization.json.Json

class CacheFirstAgentFeaturesTest {
    private val live =
        AgentFeaturesSnapshot(
            "Codex",
            listOf(
                AgentFeatureUi("a", "Auto review", "Reviews edits", on = true),
                AgentFeatureUi("m", "Mode", null, selected = "x", options = listOf(AgentFeatureOptionUi("x", "Fast"))),
            ),
        )

    private fun kept() =
        Json.decodeFromString(KeptAgentFeatures.serializer(), Json.encodeToString(KeptAgentFeatures.serializer(), KeptAgentFeatures.of(live)))

    @Test
    fun `CF-80 the first frame with kept features shows the live rows with no skeleton`() {
        val first = AgentFeaturesUi(available = true, visible = true, loading = true).seeded(kept())
        assertEquals(live, first.snapshot)
        assertEquals(AgentFeaturesBody.Rows, first.body())
    }

    @Test
    fun `CF-81 a failed read keeps the kept features and says nothing`() {
        val ui = AgentFeaturesUi(snapshot = kept().toSnapshot(), loading = true).readFailed(Failure.Network(), wasRead = true)
        assertNull(ui.failure)
        assertEquals(AgentFeaturesBody.Rows, ui.body())
        assertEquals(Failure.Network(), AgentFeaturesUi(snapshot = live, loading = true).readFailed(Failure.Network(), wasRead = false).failure)
    }

    @Test
    fun `CF-82 with nothing kept the first-ever read is the skeleton`() {
        assertEquals(AgentFeaturesBody.Skeleton, AgentFeaturesUi(loading = true).seeded(null).body())
    }

    @Test
    fun `CF-83 kept features are capped`() {
        val many = AgentFeaturesSnapshot(null, List(200) { AgentFeatureUi("$it", "f$it", null) })
        assertEquals(KeptAgentFeatures.MAX_FEATURES, KeptAgentFeatures.of(many).features.size)
    }
}
