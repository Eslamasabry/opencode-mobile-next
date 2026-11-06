package io.github.eslamasabry.oc.feature.chat

import io.github.eslamasabry.oc.core.model.transcript.MessageErrorKind
import io.github.eslamasabry.oc.feature.chat.presentation.ErrorFix
import io.github.eslamasabry.oc.feature.chat.presentation.ErrorRecovery
import io.github.eslamasabry.oc.feature.chat.presentation.ReplyFailureKind
import io.github.eslamasabry.oc.feature.chat.world.ComposerModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ErrorRecoveryTest {
    private fun model(provider: String, id: String, enabled: Boolean = true) =
        ComposerModel(provider, id, id, supportsAttachments = false, enabled = enabled)

    private val models = listOf(model("openai", "gpt-5"), model("openai", "gpt-5-mini", false))

    @Test
    fun `B-CHAT-300 a did-you-mean model that this server offers is suggested`() {
        val raw = "Model not found: openai/gpt-6. Did you mean: gpt-5?"
        assertEquals("gpt-5", ErrorRecovery.suggestedModel(raw, models)?.modelId)
    }

    @Test
    fun `B-CHAT-301 the model that just failed is never suggested and disabled ones are skipped`() {
        val same = "Model not found: openai/gpt-5. Did you mean: gpt-5?"
        assertNull(ErrorRecovery.suggestedModel(same, models))
        val off = "Model not found: openai/x. Did you mean: gpt-5-mini?"
        assertNull(ErrorRecovery.suggestedModel(off, models))
    }

    @Test
    fun `B-CHAT-302 a full context offers Shorten only where the server can`() {
        val kind = MessageErrorKind.ContextOverflow
        assertEquals(
            listOf(ErrorFix.Compact),
            ErrorRecovery.fixes(kind, ReplyFailureKind.Other, "", false, canCompact = true),
        )
        assertEquals(
            emptyList(),
            ErrorRecovery.fixes(kind, ReplyFailureKind.Other, "", false, canCompact = false),
        )
    }

    @Test
    fun `B-CHAT-303 sign-in and no-credit failures always have a way on`() {
        assertEquals(
            listOf(ErrorFix.OpenProviders, ErrorFix.ChooseModel),
            ErrorRecovery.fixes(null, ReplyFailureKind.SignIn, "401", false, false),
        )
        assertEquals(
            listOf(ErrorFix.OpenAccount, ErrorFix.ChooseModel),
            ErrorRecovery.fixes(null, ReplyFailureKind.NoCredit, "402", false, false),
        )
        assertEquals(
            listOf(ErrorFix.UseModel),
            ErrorRecovery.fixes(
                MessageErrorKind.ModelNotFound,
                ReplyFailureKind.Model,
                "",
                hasSuggestion = true,
                canCompact = false,
            ),
        )
    }
}
