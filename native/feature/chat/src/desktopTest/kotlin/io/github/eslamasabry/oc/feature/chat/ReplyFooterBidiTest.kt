package io.github.eslamasabry.oc.feature.chat

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.core.common.text.NumberSymbols
import io.github.eslamasabry.oc.core.common.text.Numbers
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.feature.chat.presentation.TranscriptReadingUi
import io.github.eslamasabry.oc.feature.chat.presentation.TurnFactsUi
import io.github.eslamasabry.oc.feature.chat.presentation.TurnMetadataUi
import io.github.eslamasabry.oc.feature.chat.ui.TurnMetadata
import io.github.eslamasabry.oc.feature.chat.ui.replyFooterWords
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.platform.KitPlatform
import io.github.eslamasabry.oc.ui.kitchat.LocalKitRedactor
import io.github.eslamasabry.oc.ui.kitchat.transcript.KitReplyMeta
import java.util.Locale
import kotlin.test.Test

internal val completedReplyFacts = TurnFactsUi(
    reading = TranscriptReadingUi(
        metadata = mapOf(
            "u1" to TurnMetadataUi(listOf("Model A"), 32_500, 0.0, latest = true,
                endedAtMs = 74_160_000, tookMs = 15_500),
        ),
    ),
)

@OptIn(ExperimentalTestApi::class)
class ReplyFooterBidiTest {
    @Test
    fun `B-CHAT-304 a footer number keeps its unit in right-to-left text`() {
        val originalLocale = Locale.getDefault()
        val originalSymbols = Numbers.pinned
        try {
            Numbers.pinned = NumberSymbols.Latin
            checkLines("en", "8:36 PM · 15.5s · 32.5k tokens", "Model A · 32,500 tokens")
            checkLines(
                "ar",
                "\u20688:36 PM\u2069 · \u206815.5 ثانية\u2069 · \u206832.5 ألف رمز\u2069",
                "\u2068Model A\u2069 · \u206832,500 رمز\u2069",
            )
        } finally {
            Locale.setDefault(originalLocale)
            Numbers.pinned = originalSymbols
        }
    }

    private fun checkLines(tag: String, footer: String, details: String) {
        Locale.setDefault(Locale.forLanguageTag(tag))
        val platform = object : KitPlatform by DesktopKitPlatform() {
            override fun messageTime(createdMs: Long, nowMs: Long): String = "8:36 PM"
        }
        val metadata = completedReplyFacts.reading.metadata.getValue("u1")
        runComposeUiTest {
            setContent {
                KitTheme(platform = platform, localeTag = tag) {
                    CompositionLocalProvider(LocalKitRedactor provides Redactor()) {
                        Column {
                            KitReplyMeta(replyFooterWords(metadata))
                            TurnMetadata(metadata)
                        }
                    }
                }
            }
            onNodeWithText(footer).assertExists()
            onNodeWithText(details).assertExists()
        }
    }
}
