package io.github.eslamasabry.oc.feature.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.engine.integrations.usage.ComposerLimitNotice
import io.github.eslamasabry.oc.engine.integrations.usage.ComposerLimitPresenter
import io.github.eslamasabry.oc.engine.integrations.usage.UsageConnection
import io.github.eslamasabry.oc.engine.integrations.usage.UsageConnectionKind
import io.github.eslamasabry.oc.engine.integrations.usage.UsageReading
import io.github.eslamasabry.oc.engine.integrations.usage.UsageReadingStatus
import io.github.eslamasabry.oc.engine.integrations.usage.UsageSnapshot
import io.github.eslamasabry.oc.engine.integrations.usage.UsageWindow
import io.github.eslamasabry.oc.engine.integrations.usage.mergeUsageSubscriptions
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.feature.chat.composer.BasicComposer
import io.github.eslamasabry.oc.feature.chat.composer.ComposerChrome
import io.github.eslamasabry.oc.feature.chat.composer.PresenterComposerLimits
import io.github.eslamasabry.oc.feature.chat.composer.presentation.AttachKind
import io.github.eslamasabry.oc.feature.chat.composer.presentation.BasicComposerActions
import io.github.eslamasabry.oc.feature.chat.composer.presentation.BasicComposerUi
import io.github.eslamasabry.oc.feature.chat.world.ComposerModel
import io.github.eslamasabry.oc.feature.chat.world.ComposerModels
import io.github.eslamasabry.oc.feature.chat.world.DraftFacts
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHost
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * B-181 (owner 2026-11-08): the composer box carries the "Approaching limit" strip over its field for the open
 * conversation's provider and model, fed by the Usage readings. ✕ hides it; no readings, no strip.
 */
@OptIn(ExperimentalTestApi::class)
class S25ComposerLimitStripTest {
    private class Actions : BasicComposerActions {
        override fun draftChanged(text: String) {}

        override suspend fun send(text: String): Boolean = true

        override fun attachFile() {}

        override fun attach(kind: AttachKind) {}
    }

    private object Fixed : Clock {
        override fun now(): Instant = NOW
    }

    private fun ui() =
        BasicComposerUi(
            available = true,
            sending = false,
            steers = false,
            draft = DraftFacts(ready = true),
            targetKey = "pc/s1",
            models =
                ComposerModels(
                    selected = ComposerModel("anthropic", "claude-sonnet-4-5", "Claude Sonnet 4.5", supportsAttachments = true)
                ),
        )

    private fun claude(used: Double): UsageSnapshot =
        mergeUsageSubscriptions(
            now = NOW,
            connections = listOf(UsageConnection("pc", "PC", UsageConnectionKind.OpenCodeServer)),
            readings =
                listOf(
                    UsageReading(
                        providerId = "claude",
                        displayName = "Claude",
                        connectionId = "pc",
                        status = UsageReadingStatus.Ok,
                        fetchedAt = NOW - 1.minutes,
                        windows =
                            listOf(UsageWindow("five_hour", used, durationSeconds = 18_000, resetsAt = NOW + 2.hours + 10.minutes)),
                        accountRef = "acct-1",
                        method = io.github.eslamasabry.oc.engine.integrations.usage.SignInMethod.Plan,
                    )
                ),
        )

    private val chrome = ComposerChrome({ false }, { false }, {}, { null }, {}, { null }, {}, {}, { false })

    @Composable
    private fun Composer(limits: PresenterComposerLimits) {
        KitTheme(
            platform = DesktopKitPlatform(),
            appearance = KitAppearance(motion = MotionLevel.Off),
            stillFrame = true,
            localeTag = "en",
        ) {
            KitSheetHost {
                Box(Modifier.width(412.dp)) {
                    BasicComposer(
                        ui(),
                        Actions(),
                        chrome,
                        conversationRef = ConversationRef(ProfileId("pc"), SessionId("s1")),
                        limits = limits,
                    )
                }
            }
        }
    }

    private fun limits(snapshot: MutableStateFlow<UsageSnapshot>, opened: MutableList<String> = mutableListOf()) =
        PresenterComposerLimits(ComposerLimitPresenter(snapshot, Fixed, { "UTC" }, tick = 24.hours)) { _: ComposerLimitNotice, p ->
            opened += p
        }

    @Test
    fun `B-181-001 the strip shows at 80 percent with its reset, and View usage opens Usage for this server`() =
        runComposeUiTest {
            val opened = mutableListOf<String>()
            setContent { Composer(limits(MutableStateFlow(claude(80.0)), opened)) }
            onNodeWithText("Approaching limit").assertIsDisplayed()
            onNodeWithText("Resets in 2 h 10 min").assertIsDisplayed()
            onNodeWithText("View usage").performClick()
            assertEquals(listOf("pc"), opened)
        }

    @Test
    fun `B-181-002 the cross hides the strip`() = runComposeUiTest {
        setContent { Composer(limits(MutableStateFlow(claude(92.0)))) }
        onNodeWithText("Approaching limit").assertIsDisplayed()
        onNodeWithContentDescription("Dismiss").performClick()
        waitForIdle()
        onNodeWithText("Approaching limit").assertDoesNotExist()
    }

    @Test
    fun `B-181-003 no readings, or a window under 80 percent, shows no strip`() = runComposeUiTest {
        val snapshot = MutableStateFlow(UsageSnapshot())
        setContent { Composer(limits(snapshot)) }
        waitForIdle()
        onNodeWithText("Approaching limit").assertDoesNotExist()
        snapshot.value = claude(79.0)
        waitForIdle()
        onNodeWithText("Approaching limit").assertDoesNotExist()
        snapshot.value = claude(100.0)
        waitForIdle()
        onNodeWithText("Limit reached").assertIsDisplayed()
    }

    /**
     * The whole composer with the strip over its field, dark, on a 412 dp phone: written to
     * `looks-out/s24-limit/composer-with-strip.png` (copied beside the owner's reference in docs/design).
     */
    @Test
    fun `B-181-004 desktop render of the whole composer with the strip`() {
        val out = java.io.File(System.getProperty("user.dir"), "../../../looks-out/s24-limit").also { it.mkdirs() }
        val scale = 2.625f
        androidx.compose.ui.test.v2.runDesktopComposeUiTest((412 * scale).toInt(), (220 * scale).toInt()) {
            setContent {
                androidx.compose.runtime.CompositionLocalProvider(
                    androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(scale)
                ) {
                    KitTheme(
                        platform = DesktopKitPlatform(),
                        appearance =
                            KitAppearance(
                                mode = io.github.eslamasabry.oc.ui.design.effects.AppearanceMode.Dark,
                                motion = MotionLevel.Off,
                            ),
                        stillFrame = true,
                        localeTag = "en",
                    ) {
                        KitSheetHost {
                            androidx.compose.foundation.layout.Column(
                                Modifier.fillMaxSize()
                                    .then(
                                        Modifier.background(
                                            io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors.current.ground
                                        )
                                    )
                                    .then(Modifier.padding(16.dp)),
                                verticalArrangement = androidx.compose.foundation.layout.Arrangement.Bottom,
                            ) {
                                BasicComposer(
                                    ui(),
                                    Actions(),
                                    chrome,
                                    conversationRef = ConversationRef(ProfileId("pc"), SessionId("s1")),
                                    limits = limits(MutableStateFlow(claude(86.0))),
                                )
                            }
                        }
                    }
                }
            }
            waitForIdle()
            onNodeWithText("Approaching limit").assertExists()
            javax.imageio.ImageIO.write(
                onRoot().captureToImage().toAwtImage(),
                "png",
                java.io.File(out, "composer-with-strip.png"),
            )
        }
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-10-13T12:00:00Z")
    }
}
