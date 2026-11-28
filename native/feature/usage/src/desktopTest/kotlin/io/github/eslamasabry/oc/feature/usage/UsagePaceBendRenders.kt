package io.github.eslamasabry.oc.feature.usage

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.integrations.usage.SubscriptionState
import io.github.eslamasabry.oc.engine.integrations.usage.UsageConnection
import io.github.eslamasabry.oc.engine.integrations.usage.UsageConnectionKind
import io.github.eslamasabry.oc.engine.integrations.usage.UsageSnapshot
import io.github.eslamasabry.oc.engine.integrations.usage.UsageSubscription
import io.github.eslamasabry.oc.engine.integrations.usage.UsageWindow
import io.github.eslamasabry.oc.feature.usage.presentation.LimitsActions
import io.github.eslamasabry.oc.feature.usage.presentation.LimitsUi
import io.github.eslamasabry.oc.feature.usage.presentation.limitsUi
import io.github.eslamasabry.oc.feature.usage.ui.LimitsContent
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kitchat.LocalKitRedactor
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.time.Instant

private const val RENDER_PHASE = "after"

/**
 * 360 dp proof renders for Usage › Limits pace bend chart (B-508): light and dark, text 1.0 and 1.3.
 * Writes PNGs to `native/feature/usage/build/renders/usage-pace-bend/`.
 */
@OptIn(ExperimentalTestApi::class)
class UsagePaceBendRenders {
    private val now = Instant.parse("2026-11-07T12:00:00Z").toEpochMilliseconds()
    private val minute = 60_000L
    private val hour = 60 * minute
    private val day = 24 * hour
    private val phone = UsageConnection(UsageConnection.PHONE_ID, "", UsageConnectionKind.PhoneAgents)

    private object NoActions : LimitsActions {
        override fun refresh() = Unit
    }

    private data class Variant(val dark: Boolean, val scale: Float) {
        val label: String
            get() = "${if (dark) "dark" else "light"}-$scale"
    }

    private val variants =
        listOf(
            Variant(dark = false, scale = 1.0f),
            Variant(dark = true, scale = 1.0f),
            Variant(dark = false, scale = 1.3f),
            Variant(dark = true, scale = 1.3f),
        )

    private fun subscription(
        key: String,
        name: String,
        state: SubscriptionState,
        plan: String?,
        windows: List<UsageWindow>,
    ): UsageSubscription =
        UsageSubscription(
            key = key,
            providerId = key,
            displayName = name,
            state = state,
            planLabel = plan,
            windows = windows,
            fetchedAt = Instant.fromEpochMilliseconds(now - minute),
            readFrom = phone,
        )

    private fun sceneState(): LimitsUi {
        val codex =
            subscription(
                key = "codex",
                name = "Codex",
                state = SubscriptionState.NearLimit,
                plan = "Plus",
                windows =
                    listOf(
                        UsageWindow("5h", 85.0, 18_000, Instant.fromEpochMilliseconds(now + 3 * hour)),
                        UsageWindow("weekly", 50.0, 604_800, Instant.fromEpochMilliseconds(now + 5 * day)),
                    ),
            )
        val claude =
            subscription(
                key = "claude",
                name = "Claude",
                state = SubscriptionState.Ok,
                plan = "Max",
                windows =
                    listOf(
                        UsageWindow("five_hour", 30.0, 18_000, Instant.fromEpochMilliseconds(now + 2 * hour)),
                        UsageWindow("seven_day", 20.0, 604_800, Instant.fromEpochMilliseconds(now + 4 * day)),
                    ),
            )
        val early =
            subscription(
                key = "gemini",
                name = "Gemini",
                state = SubscriptionState.Ok,
                plan = null,
                windows =
                    listOf(
                        UsageWindow("weekly", 1.0, 604_800, Instant.fromEpochMilliseconds(now + 6 * day)),
                        UsageWindow("no_reset", 40.0, 18_000, resetsAt = null),
                    ),
            )
        val outAndHighLeft =
            subscription(
                key = "cursor",
                name = "Cursor",
                state = SubscriptionState.Exhausted,
                plan = "Pro",
                windows =
                    listOf(
                        UsageWindow("five_hour", 100.0, 18_000, Instant.fromEpochMilliseconds(now + 3 * hour)),
                        UsageWindow("seven_day", 10.0, 604_800, Instant.fromEpochMilliseconds(now + 4 * day)),
                    ),
            )
        return limitsUi(
            snapshot = UsageSnapshot(subscriptions = listOf(codex, claude, early, outAndHighLeft), loaded = true),
            refreshing = false,
            nowMs = now,
            timezone = "UTC",
        )
    }

    @Composable
    private fun Themed(dark: Boolean, scale: Float, content: @Composable () -> Unit) {
        KitTheme(
            appearance =
                KitAppearance(
                    mode = if (dark) AppearanceMode.Dark else AppearanceMode.Light,
                    motion = MotionLevel.Off,
                ),
            platform = DesktopKitPlatform(),
            localeTag = "en",
            stillFrame = true,
        ) {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, scale),
                LocalKitRedactor provides Redactor(),
            ) {
                Box(Modifier.fillMaxSize().background(LocalKitColors.current.ground)) { content() }
            }
        }
    }

    @Test
    fun `B-508 render 360dp limits pace bend`() {
        val out = File("build/renders/usage-pace-bend").also { it.mkdirs() }
        val saved = Locale.getDefault()
        val ui = sceneState()
        try {
            Locale.setDefault(Locale.ENGLISH)
            for (v in variants) {
                runDesktopComposeUiTest(360, 3_400) {
                    setContent { Themed(v.dark, v.scale) { LimitsContent(ui, NoActions) } }
                    waitForIdle()
                    mainClock.advanceTimeBy(1_000)
                    waitForIdle()
                    val file = File(out, "$RENDER_PHASE-${v.label}.png")
                    ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", file)
                }
            }
        } finally {
            Locale.setDefault(saved)
        }
    }
}
