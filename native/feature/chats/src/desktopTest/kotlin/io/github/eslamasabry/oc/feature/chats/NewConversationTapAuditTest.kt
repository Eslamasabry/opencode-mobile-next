package io.github.eslamasabry.oc.feature.chats

import androidx.compose.ui.test.ExperimentalTestApi
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.domain.ImportableConversation
import io.github.eslamasabry.oc.core.model.ModelRef
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationActions
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationModel
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationPhone
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationPhoneAgent
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationProject
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationServer
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationSetup
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationSheet
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationUi
import io.github.eslamasabry.oc.feature.chats.ui.NewConversationScreen
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.gate.TapAudit
import io.github.eslamasabry.oc.ui.gate.flicker.kitUiTest
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Issue 147, priority 1 ("buttons do not respond"): the tap audit over the New conversation page in each state a person
 * meets on the way to Start. Every control that is drawn must act on the first tap, or, drawn disabled, answer the tap
 * with its reason; nothing may take a tap meant for another control; every target is 48 dp.
 */
@OptIn(ExperimentalTestApi::class)
class NewConversationTapAuditTest {
    private object Actions : NewConversationActions {
        override fun back() {}
        override fun addServer() {}
        override fun chooseServer(profile: ProfileId) {}
        override fun draftChanged(text: String) {}
        override fun chooseDirectory(directory: String) {}
        override fun chooseModel(model: ModelRef?) {}
        override fun chooseAgent(agent: String?) {}
        override fun separateCopy(on: Boolean) {}
        override fun sheet(sheet: NewConversationSheet?) {}
        override fun start() {}
        override fun import(conversation: ImportableConversation) {}
        override fun retryLoad() {}
    }

    private val p1 = ProfileId("p1")
    private val server = NewConversationServer(p1, "This phone", connected = true)
    private val shop = NewConversationProject("/root/projects/shop", "shop", false)
    private val models =
        listOf(
            NewConversationModel(ModelRef("anthropic", "sonnet"), "Sonnet"),
            NewConversationModel(ModelRef("anthropic", "opus"), "Opus"),
        )
    private val claude = NewConversationPhoneAgent("claude", "Claude Code", "claude")
    private val ready =
        NewConversationUi(
            servers = listOf(server),
            profile = p1,
            setup = NewConversationSetup("/root/projects/shop", listOf(shop), models, emptyList(), model = models[0].model),
            directory = "/root/projects/shop",
            model = models[0].model,
        )
    private val phoneSetup =
        ready.setup!!.copy(phone = NewConversationPhone(ProfileId("helper"), listOf(claude), emptyList(), selected = "claude"))

    private fun states(): List<Pair<String, NewConversationUi>> =
        listOf(
            "first open (page getting ready)" to NewConversationUi(servers = listOf(server), profile = p1, busy = true),
            "first open, slow (page still getting ready)" to
                NewConversationUi(servers = listOf(server), profile = p1, busy = true, preparingSlow = true),
            "ready, nothing typed" to ready,
            "ready, typed" to ready.copy(text = "Find the bug in the login page"),
            "no project chosen" to ready.copy(directory = null, text = "hello"),
            "page could not be read" to
                NewConversationUi(servers = listOf(server), profile = p1, failure = Failure.Unavailable(FeatureId("x"))),
            "phone agent, models loading, typed" to
                ready.copy(
                    setup = phoneSetup,
                    phoneAgent = "claude",
                    phoneModels = emptyList(),
                    phoneModelsBusy = true,
                    text = "Find the bug in the login page",
                ),
            "phone agent, no project" to
                ready.copy(setup = phoneSetup, phoneAgent = "claude", directory = "/data/elsewhere", text = "hello"),
            "this phone's server is off" to ready.copy(phoneOff = true),
            "project sheet open" to ready.copy(sheet = NewConversationSheet.Projects, text = "hello"),
            "model sheet open" to ready.copy(sheet = NewConversationSheet.Models, text = "hello"),
            "agent sheet open" to ready.copy(setup = phoneSetup, sheet = NewConversationSheet.Agents, text = "hello"),
        )

    @Test
    fun `every control on the New conversation page answers its first tap in every state`() {
        val found = ArrayList<TapAudit.Offender>()
        for ((name, ui) in states()) {
            val audit = TapAudit(name)
            kitUiTest(412, 915) {
                setContent {
                    KitTheme(
                        platform = DesktopKitPlatform(),
                        localeTag = "en",
                        appearance = KitAppearance(motion = MotionLevel.Off),
                    ) {
                        audit.provide { NewConversationScreen(ui, Actions) }
                    }
                }
                found += audit.run(this, 412, 915)
            }
        }
        assertTrue(found.isEmpty(), "tap audit offenders (${found.size}):\n" + found.joinToString("\n"))
    }
}
