package io.github.eslamasabry.oc.feature.chat

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.domain.capabilities.ServerCapabilities
import io.github.eslamasabry.oc.core.model.PromptAttachment
import io.github.eslamasabry.oc.engine.transcript.state.FooterRow
import io.github.eslamasabry.oc.engine.transcript.state.TranscriptRow
import io.github.eslamasabry.oc.feature.chat.presentation.ChatConfirm
import io.github.eslamasabry.oc.feature.chat.presentation.ChatPresenter
import io.github.eslamasabry.oc.feature.chat.presentation.ErrorFix
import io.github.eslamasabry.oc.feature.chat.presentation.ErrorRecovery
import io.github.eslamasabry.oc.feature.chat.presentation.LimitSwitchOffer
import io.github.eslamasabry.oc.feature.chat.presentation.ReplyFailureKind
import io.github.eslamasabry.oc.feature.chat.presentation.SwitchModelRules
import io.github.eslamasabry.oc.feature.chat.presentation.SwitchUi
import io.github.eslamasabry.oc.feature.chat.world.ChatLink
import io.github.eslamasabry.oc.feature.chat.world.ComposerModel
import io.github.eslamasabry.oc.feature.chat.world.ComposerModels
import io.github.eslamasabry.oc.feature.chat.world.ModelKey
import io.github.eslamasabry.oc.feature.chat.world.StoppedPrompt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * B-169 (docs/designs/b169-quota-switch.md, Acceptance): when a limit stops a reply in an OpenCode 2 conversation,
 * Switch model picks another model and sends the stopped prompt again with it, history kept.
 *
 * The limit texts are the documented shapes, PENDING A LIVE CAPTURE (see AgentRunFailureOutTest).
 */
class B169LimitSwitchTest {
    private object Now : Clock {
        override fun now(): Instant = Instant.fromEpochMilliseconds(1_762_600_000_000L)
    }

    private val perMinute = "AI_APICallError: Rate limit exceeded: free-models-per-min. (429)"
    private val daily =
        "AI_APICallError: Rate limit exceeded: free-models-per-day. Add 10 credits to unlock 1000 free model " +
            "requests per day. (429)"

    private val failedOn =
        ComposerModel("openrouter", "qwen3-coder:free", "Qwen3 Coder", true, inputCost = 0.0, outputCost = 0.0)
    private val other = ComposerModel("anthropic", "sonnet", "Sonnet", true, contextLimit = 200_000)
    private val small = ComposerModel("openai", "mini", "Mini", true, contextLimit = 8_000)

    private inner class LimitWorld : FakeChatWorld() {
        val modelFlow = MutableStateFlow(ComposerModels(choices = listOf(failedOn, other, small), selected = failedOn))
        override val models: StateFlow<ComposerModels> = modelFlow
        var prompts = HashMap<String, StoppedPrompt>()
        val resent = mutableListOf<Pair<StoppedPrompt, String?>>()
        val composerFiles = mutableListOf<List<PromptAttachment>>()

        override fun stoppedPrompt(turnId: String): StoppedPrompt? = prompts[turnId]

        override suspend fun selectModel(choice: ComposerModel?, variant: String): Outcome<Unit> {
            calls += "select ${choice?.modelId}"
            modelFlow.value = modelFlow.value.copy(selected = choice)
            return Outcome.Ok(Unit)
        }

        override suspend fun sendAgainWith(prompt: StoppedPrompt): Outcome<Unit> {
            calls += "sendAgainWith ${prompt.text}"
            resent += prompt to modelFlow.value.selected?.modelId
            return Outcome.Ok(Unit)
        }

        override suspend fun replaceAttachments(files: List<PromptAttachment>): Outcome<Unit> {
            composerFiles += files
            return Outcome.Ok(Unit)
        }
    }

    private val world = LimitWorld().apply { capabilities.value = ServerCapabilities(promptModelChoice = true) }
    private val presenter = ChatPresenter(world, RecordingNavigator(), Immediate, clock = Now)

    private fun failedTurn(n: Int, reason: String, text: String = "fix the build"): List<TranscriptRow> =
        listOf(prompt("u$n", text), block("u$n", "a$n", "p$n", 0, "…"), FooterRow("footer:u$n", "u$n", true, reason))

    private fun show(vararg turns: List<TranscriptRow>) {
        world.transcript.value = stateOf(*turns.flatMap { it }.toTypedArray())
    }

    private val photo = PromptAttachment("image/png", "shot.png", "data:image/png;base64,AAAA")

    private fun stopped(turn: String, steps: Int = 0, files: List<PromptAttachment> = listOf(photo)) =
        StoppedPrompt(
            turnId = turn,
            text = "fix the build @explore",
            attachments = files,
            agent = "plan",
            model = ModelKey.of(failedOn),
            toolSteps = steps,
            contextTokens = 12_000,
        )

    @Test
    fun `B-169-010 a per-minute 429 shows no switch`() {
        show(failedTurn(1, perMinute))
        assertNull(presenter.current().limitSwitch["u1"]?.offer)
        assertEquals(
            listOf(ErrorFix.ChooseModel),
            ErrorRecovery.fixes(null, ReplyFailureKind.Busy, perMinute, false, false),
        )
    }

    @Test
    fun `B-169-011 a daily limit shows Switch model, the one switch control of its turn`() {
        show(failedTurn(1, daily))
        val offer = presenter.current().limitSwitch["u1"]?.offer
        assertEquals(LimitSwitchOffer.Switch, offer)
        val fixes = ErrorRecovery.limitFixes(offer!!, ReplyFailureKind.Busy, daily, canCompact = true)
        assertEquals(listOf(ErrorFix.SwitchModel), fixes)
        assertTrue(ErrorFix.ChooseModel !in fixes && ErrorFix.UseModel !in fixes)
    }

    @Test
    fun `B-169-012 choosing B re-sends text, attachments, mentions and agent with B`() {
        show(failedTurn(1, daily))
        world.prompts["u1"] = stopped("u1")
        // The row names the model that stopped ("Qwen3 Coder is used up for now").
        assertEquals("Qwen3 Coder", presenter.current().limitSwitch["u1"]?.modelName)
        presenter.switchModel("u1")
        val sheet = assertNotNull(presenter.current().switching)
        assertEquals(ModelKey.of(failedOn), sheet.failed)
        presenter.switchTo("anthropic", "sonnet")
        assertNull(presenter.current().switching)
        assertEquals(listOf("select sonnet", "sendAgainWith fix the build @explore"), world.calls.takeLast(2))
        val (sent, model) = world.resent.single()
        assertEquals("sonnet", model)
        assertEquals(listOf(photo), sent.attachments)
        assertEquals("plan", sent.agent)
        // The words carry the `@explore` mention; the world reads it back from them when it dispatches.
        assertTrue("@explore" in sent.text)
    }

    @Test
    fun `B-169-013 a file that can no longer be read goes to the composer instead of auto-sending`() {
        show(failedTurn(1, daily))
        val gone = PromptAttachment("application/pdf", "spec.pdf", "")
        world.prompts["u1"] = stopped("u1", files = listOf(photo, gone))
        presenter.switchModel("u1")
        presenter.switchTo("anthropic", "sonnet")
        assertTrue(world.resent.isEmpty())
        val ui = presenter.current()
        assertEquals("fix the build @explore", ui.insert?.text)
        assertEquals("spec.pdf", ui.attachAgain?.file)
        assertEquals("u1", ui.attachAgain?.turnId)
        assertEquals(listOf(listOf(photo)), world.composerFiles)
    }

    @Test
    fun `B-169-014 a turn that ran tools asks first, and Cancel sends nothing`() {
        show(failedTurn(1, daily))
        world.prompts["u1"] = stopped("u1", steps = 4)
        presenter.switchModel("u1")
        presenter.switchTo("anthropic", "sonnet")
        val ask = assertIs<ChatConfirm.SendAgainAfterSteps>(presenter.current().confirm)
        assertEquals(4, ask.steps)
        assertEquals("Sonnet", ask.modelLabel)
        assertTrue(world.resent.isEmpty())
        presenter.dismissConfirm()
        presenter.confirmSwitch()
        assertTrue(world.resent.isEmpty())

        presenter.switchModel("u1")
        presenter.switchTo("anthropic", "sonnet")
        presenter.confirmSwitch()
        assertEquals("sonnet", world.resent.single().second)
    }

    @Test
    fun `B-169-015 a model too small for the conversation cannot be chosen`() {
        show(failedTurn(1, daily))
        world.prompts["u1"] = stopped("u1")
        presenter.switchModel("u1")
        presenter.switchTo("openai", "mini") // 8k window, 12k conversation
        assertTrue(world.resent.isEmpty())
        assertNotNull(presenter.current().switching)
    }

    @Test
    fun `B-169-016 the third failed switch stops offering more`() {
        val text = "fix the build @explore"
        var turns = listOf(failedTurn(1, daily, text))
        show(*turns.toTypedArray())
        for (n in 1..3) {
            world.prompts["u$n"] = stopped("u$n")
            assertEquals(LimitSwitchOffer.Switch, presenter.current().limitSwitch["u$n"]?.offer, "switch $n")
            presenter.switchModel("u$n")
            presenter.switchTo("anthropic", "sonnet")
            // The switched prompt fails on its limit too.
            turns = turns + listOf(failedTurn(n + 1, daily, text))
            show(*turns.toTypedArray())
        }
        assertEquals(3, world.resent.size)
        assertEquals(LimitSwitchOffer.Spent, presenter.current().limitSwitch["u4"]?.offer)
        assertEquals(
            listOf(ErrorFix.ConnectProvider),
            ErrorRecovery.limitFixes(LimitSwitchOffer.Spent, ReplyFailureKind.Busy, daily, canCompact = false),
        )
        world.prompts["u4"] = stopped("u4")
        presenter.switchModel("u4")
        assertNull(presenter.current().switching)
    }

    @Test
    fun `B-169-017 non-OC2 chats show no Switch model`() {
        world.capabilities.value = ServerCapabilities(promptModelChoice = false)
        show(failedTurn(1, daily))
        world.prompts["u1"] = stopped("u1")
        assertTrue(presenter.current().limitSwitch.isEmpty())
        presenter.switchModel("u1")
        assertNull(presenter.current().switching)
        // The default capabilities (every other gateway) leave it off too.
        assertEquals(false, ServerCapabilities().promptModelChoice)
    }

    @Test
    fun `B-169-018 offline, Switch model is disabled`() {
        show(failedTurn(1, daily))
        world.prompts["u1"] = stopped("u1")
        world.link.value = ChatLink.Offline
        assertEquals(false, presenter.current().switchOnline)
        presenter.switchModel("u1")
        assertNull(presenter.current().switching)
    }

    @Test
    fun `B-169-019 a model a limit stopped is left out of the next switch in this conversation`() {
        show(failedTurn(1, daily), failedTurn(2, daily))
        world.prompts["u1"] = stopped("u1")
        presenter.switchModel("u1")
        presenter.dismissSwitch()
        world.prompts["u2"] = stopped("u2").copy(model = ModelKey.of(other))
        presenter.switchModel("u2")
        val sheet = assertNotNull(presenter.current().switching)
        assertEquals(ModelKey.of(other), sheet.failed)
        assertTrue(ModelKey.of(failedOn) in sheet.usedUp)
    }

    @Test
    fun `B-169-021 the sheet drops stopped and used-up models, says Free only at 0, guards the context`() {
        val unknownPrice = ComposerModel("x", "unpriced", "Unpriced", true)
        val sheet =
            SwitchModelRules.sheet(
                listOf(failedOn, other, small, unknownPrice),
                SwitchUi("u1", ModelKey.of(failedOn), setOf(ModelKey.of(unknownPrice)), contextTokens = 12_000),
            )
        assertEquals(listOf("sonnet", "mini"), sheet.rows.map { it.model.modelId })
        assertTrue(sheet.rows.none { it.free })
        assertEquals(listOf(false, true), sheet.rows.map { it.tooLong })
        assertTrue(SwitchModelRules.isFree(failedOn))
        assertTrue(!SwitchModelRules.isFree(unknownPrice), "an unknown price is never free")
        // An unknown window (0) is allowed.
        assertTrue(!SwitchModelRules.tooLong(failedOn, 10_000_000))
    }

    @Test
    fun `B-169-022 the suggestion is another provider under 80 percent, else a recent model, else picker order`() {
        val gpt = ComposerModel("openai", "gpt", "GPT", true)
        val choices = listOf(failedOn, gpt, other)
        fun open(recent: List<ComposerModel>) =
            SwitchUi("u1", ModelKey.of(failedOn), emptySet(), null, recent.map(ModelKey::of))
        val roomy = SwitchModelRules.sheet(choices, open(listOf(gpt)), mapOf("claude" to 40.0, "codex" to 95.0))
        assertEquals(ModelKey.of(other), roomy.suggestion)
        val recent = SwitchModelRules.sheet(choices, open(listOf(other, gpt)))
        assertEquals(ModelKey.of(other), recent.suggestion)
        val plain = SwitchModelRules.sheet(choices, open(emptyList()))
        assertEquals(ModelKey.of(gpt), plain.suggestion)
    }
}
