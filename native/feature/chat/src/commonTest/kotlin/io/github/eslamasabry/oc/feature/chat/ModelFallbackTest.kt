package io.github.eslamasabry.oc.feature.chat

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.model.transcript.MessageErrorKind
import io.github.eslamasabry.oc.engine.transcript.state.FooterRow
import io.github.eslamasabry.oc.feature.chat.presentation.ChatLauncher
import io.github.eslamasabry.oc.feature.chat.presentation.ChatPresenter
import io.github.eslamasabry.oc.feature.chat.presentation.ErrorFix
import io.github.eslamasabry.oc.feature.chat.presentation.ErrorRecovery
import io.github.eslamasabry.oc.feature.chat.presentation.ReplyFailureKind
import io.github.eslamasabry.oc.feature.chat.world.ComposerModel
import io.github.eslamasabry.oc.feature.chat.world.ComposerModels
import io.github.eslamasabry.oc.feature.chat.world.ModelFallback
import io.github.eslamasabry.oc.feature.chat.world.ModelKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class ModelFallbackTest {
    private fun model(provider: String, id: String, enabled: Boolean = true) =
        ComposerModel(provider, id, id, supportsAttachments = false, enabled = enabled)

    @Test
    fun `B-523-001 pick prefers answered enabled models, skips gone and disabled, then same provider`() {
        val gone = ModelKey("prov", "gone")
        val models =
            listOf(
                model("other", "o1", enabled = true),
                model("prov", "gone", enabled = true),
                model("prov", "off", enabled = false),
                model("prov", "m1", enabled = true),
                model("prov", "m2", enabled = true),
            )
        val answered =
            listOf(
                gone,
                ModelKey("prov", "off"),
                ModelKey("prov", "unlisted"),
                ModelKey("other", "o1"),
                ModelKey("prov", "m2"),
            )
        // First usable answered model wins, even from another provider that already answered here.
        assertEquals("o1", ModelFallback.pick(gone, answered, models)?.modelId)

        // When no answered model is usable, fall back to the gone provider's first enabled listed model.
        assertEquals(
            "m1",
            ModelFallback.pick(gone, listOf(gone, ModelKey("prov", "off"), ModelKey("prov", "missing")), models)
                ?.modelId,
        )

        // Never pick another provider's model unless it already answered in this chat.
        val otherOnly = listOf(model("other", "o1", enabled = true), model("prov", "off", enabled = false))
        assertNull(ModelFallback.pick(gone, emptyList(), otherOnly))

        // With gone == null, only answered models qualify.
        assertEquals("m2", ModelFallback.pick(null, listOf(ModelKey("prov", "m2")), models)?.modelId)
        assertNull(ModelFallback.pick(null, emptyList(), models))
    }

    @Test
    fun `B-523-002 answered keeps only unfailed unstopped footers with both ids, newest first, no repeats`() {
        val rows =
            listOf(
                prompt("u1", "hi"),
                block("u1", "a1", "p1", 0, "hello"),
                FooterRow("footer:u1", "u1", failed = false, createdMs = 100L, providerId = "prov", modelId = "m1"),
                FooterRow("footer:u2", "u2", failed = true, createdMs = 500L, providerId = "prov", modelId = "failed"),
                FooterRow(
                    "footer:u3",
                    "u3",
                    failed = false,
                    stopped = true,
                    createdMs = 400L,
                    providerId = "prov",
                    modelId = "stopped",
                ),
                FooterRow("footer:u4", "u4", failed = false, createdMs = 300L, providerId = " ", modelId = "blank-p"),
                FooterRow("footer:u5", "u5", failed = false, createdMs = 300L, providerId = "prov", modelId = ""),
                FooterRow("footer:u6", "u6", failed = false, createdMs = null, providerId = "prov", modelId = "no-ts"),
                FooterRow("footer:u7", "u7", failed = false, createdMs = 200L, providerId = "other", modelId = "m2"),
                FooterRow("footer:u8", "u8", failed = false, createdMs = 350L, providerId = "prov", modelId = "m1"),
            )
        assertEquals(
            listOf(
                ModelKey("prov", "m1"),
                ModelKey("other", "m2"),
                ModelKey("prov", "no-ts"),
            ),
            ModelFallback.answered(rows),
        )
    }

    @Test
    fun `B-523-003 fixes offers UseModel and ChooseModel with a suggestion and ChooseModel alone without`() {
        assertEquals(
            listOf(ErrorFix.UseModel, ErrorFix.ChooseModel),
            ErrorRecovery.fixes(
                MessageErrorKind.ModelNotFound,
                ReplyFailureKind.Model,
                "",
                hasSuggestion = true,
                canCompact = false,
            ),
        )
        assertEquals(
            listOf(ErrorFix.ChooseModel),
            ErrorRecovery.fixes(
                MessageErrorKind.ModelNotFound,
                ReplyFailureKind.Model,
                "",
                hasSuggestion = false,
                canCompact = false,
            ),
        )
        assertEquals(
            listOf(ErrorFix.UseModel, ErrorFix.ChooseModel),
            ErrorRecovery.fixes(null, ReplyFailureKind.Model, "", hasSuggestion = true, canCompact = false),
        )
        assertEquals(
            listOf(ErrorFix.ChooseModel),
            ErrorRecovery.fixes(null, ReplyFailureKind.Model, "", hasSuggestion = false, canCompact = false),
        )
    }

    @Test
    fun `B-523-004 two taps on useModelAndResend while selectModel waits run one select and one sendAgain`() =
        runTest {
            val gate = CompletableDeferred<Outcome<Unit>>()
            val world =
                object : FakeChatWorld() {
                    private var waitOnGate = true
                    override val models: StateFlow<ComposerModels> =
                        MutableStateFlow(ComposerModels(choices = listOf(model("prov", "m2"))))

                    override suspend fun selectModel(choice: ComposerModel?, variant: String): Outcome<Unit> {
                        calls += "selectModel ${choice?.providerId}/${choice?.modelId}"
                        return if (waitOnGate) {
                            waitOnGate = false
                            gate.await()
                        } else {
                            Outcome.Ok(Unit)
                        }
                    }
                }
            world.transcript.value = stateOf(prompt("u1", "go"))
            val presenter = ChatPresenter(world, RecordingNavigator(), ChatLauncher { launch { it() } })

            presenter.useModelAndResend("u1", "prov", "m2")
            runCurrent()
            presenter.useModelAndResend("u1", "prov", "m2")
            runCurrent()

            gate.complete(Outcome.Ok(Unit))
            runCurrent()

            assertEquals(listOf("selectModel prov/m2", "sendAgain go"), world.calls)

            // After the first call finishes, another tap works again.
            presenter.useModelAndResend("u1", "prov", "m2")
            runCurrent()
            assertEquals(
                listOf("selectModel prov/m2", "sendAgain go", "selectModel prov/m2", "sendAgain go"),
                world.calls,
            )
        }
}
