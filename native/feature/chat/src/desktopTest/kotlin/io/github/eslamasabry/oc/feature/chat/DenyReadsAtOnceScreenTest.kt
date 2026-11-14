package io.github.eslamasabry.oc.feature.chat

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.feature.chat.presentation.StepRequestReading
import io.github.eslamasabry.oc.feature.chat.presentation.TurnFactsPresenter
import io.github.eslamasabry.oc.feature.chat.requests.presentation.AnswerWords
import io.github.eslamasabry.oc.feature.chat.requests.presentation.CardState
import io.github.eslamasabry.oc.feature.chat.ui.TranscriptFolds
import io.github.eslamasabry.oc.feature.chat.ui.TurnWorkLine
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kitchat.LocalKitRedactor
import kotlin.test.Test
import kotlin.time.Clock
import kotlin.time.Instant

/** A2-01: the step row says "You declined this" the moment Deny is pressed, and never "Waiting for you" after Allow. */
@OptIn(ExperimentalTestApi::class)
class DenyReadsAtOnceScreenTest {
    private object FixedClock : Clock {
        override fun now(): Instant = Instant.fromEpochMilliseconds(0)
    }

    private fun androidx.compose.ui.test.ComposeUiTest.show(state: CardState) {
        val reading = StepRequestReading.of(listOf(permissionCard(state)))
        val work =
            TurnFactsPresenter(FixedClock).present(runningEditFeed(), 58_000, reading.waiting, reading.declined)
                .work.getValue("m1")
        val folds = TranscriptFolds(mutableStateMapOf("work:m1" to true), mutableStateMapOf(), mutableStateMapOf())
        setContent {
            KitTheme(platform = DesktopKitPlatform(), appearance = KitAppearance(motion = MotionLevel.Off), stillFrame = true, localeTag = "en") {
                CompositionLocalProvider(LocalKitRedactor provides Redactor()) { TurnWorkLine(work, 58_000, folds) }
            }
        }
    }

    @Test
    fun `A2-01 a Deny inside the Undo hold reads You declined this on the step, not waiting`() = runComposeUiTest {
        show(CardState.Held(AnswerWords.Rejected))
        onNodeWithText("You declined this").assertExists()
        onNodeWithText("Waiting for you").assertDoesNotExist()
    }

    @Test
    fun `A2-02 an Allow inside the hold no longer says Waiting for you`() = runComposeUiTest {
        show(CardState.Held(AnswerWords.Allowed))
        onNodeWithText("Waiting for you").assertDoesNotExist()
        onNodeWithText("You declined this").assertDoesNotExist()
    }

    @Test
    fun `A2-01 before any answer the step does say Waiting for you`() = runComposeUiTest {
        show(CardState.Waiting)
        onNodeWithText("Waiting for you").assertExists()
    }
}
