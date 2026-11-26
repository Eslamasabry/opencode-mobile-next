package io.github.eslamasabry.oc.feature.chat

import io.github.eslamasabry.oc.core.model.transcript.MessageErrorKind
import io.github.eslamasabry.oc.feature.chat.presentation.ErrorFix
import io.github.eslamasabry.oc.feature.chat.presentation.ErrorRecovery
import io.github.eslamasabry.oc.feature.chat.presentation.ReplyFailureKind
import kotlin.test.Test
import kotlin.test.assertEquals

class LimitNotModelGoneTest {
    private val free =
        "Free usage limit reached. Connect a provider or subscribe to OpenCode Go to keep going, or switch models."
    private val country = "This model is not available in your country."
    private val noRoute = "Model unavailable: opencode/glm-5.3-flash-free"
    private val weekly = "You've hit your weekly limit · resets Nov 20, 1am (UTC)"

    @Test
    fun `B-523-014 the free daily limit offers Connect a provider only`() {
        assertEquals(
            listOf(ErrorFix.ConnectProvider),
            ErrorRecovery.fixes(null, ReplyFailureKind.of(free), free, hasSuggestion = true, canCompact = true),
        )
    }

    @Test
    fun `B-523-015 a plan's usage limit offers Choose model, never Use model`() {
        assertEquals(
            listOf(ErrorFix.ChooseModel),
            ErrorRecovery.fixes(null, ReplyFailureKind.of(weekly), weekly, hasSuggestion = true, canCompact = false),
        )
    }

    @Test
    fun `B-523-016 a country refusal of one model offers Use model first`() {
        assertEquals(
            listOf(ErrorFix.UseModel, ErrorFix.ChooseModel),
            ErrorRecovery.fixes(
                MessageErrorKind.ProviderAuth,
                ReplyFailureKind.of(country),
                country,
                hasSuggestion = true,
                canCompact = false,
            ),
        )
    }

    @Test
    fun `B-523-017 a gone model still offers Use model then Choose`() {
        assertEquals(
            listOf(ErrorFix.UseModel, ErrorFix.ChooseModel),
            ErrorRecovery.fixes(null, ReplyFailureKind.of(noRoute), noRoute, hasSuggestion = true, canCompact = false),
        )
        assertEquals(
            listOf(ErrorFix.UseModel, ErrorFix.ChooseModel),
            ErrorRecovery.fixes(
                MessageErrorKind.ModelNotFound,
                ReplyFailureKind.of("Model not found"),
                "Model not found",
                hasSuggestion = true,
                canCompact = false,
            ),
        )
    }
}
