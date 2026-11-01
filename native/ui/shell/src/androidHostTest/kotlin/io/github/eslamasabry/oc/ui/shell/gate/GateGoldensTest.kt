package io.github.eslamasabry.oc.ui.shell.gate

import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitScreen
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.AndroidKitPlatform
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * S2-GATEUI finish line (plan 15; 05 W7): every gate screen in light/dark × en/ar × text 100 %/200 %, under Robolectric
 * native graphics on the kit's golden device (plan 06 §15.3: 412 × 915 dp at 420 dpi, Graphite, motion Off).
 * The phone stays English; Arabic comes from the in-app locale ([applyGateLocale] + `KitTheme(localeTag)`), the
 * shell half of 05 §10's `GateLocaleTest`.
 *
 * Record: `:ui:shell:recordRoborazziAndroidHostTest`; verify: `:ui:shell:verifyRoborazziAndroidHostTest`.
 * Goldens: `src/screenshots/Gate/<scene>-<variant>.png`.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [37], qualifiers = "w412dp-h915dp-420dpi")
class GateGoldensTest(private val variant: Variant) {
    data class Variant(val dark: Boolean, val localeTag: String, val textScale: Float) {
        val name: String = (if (dark) "dark" else "light") + "-" + localeTag + "-" + (textScale * 100).toInt()
        override fun toString(): String = name
    }

    @get:Rule val compose = createEmptyComposeRule()

    private fun capture(scene: String, state: GateUi, content: @Composable () -> Unit = {}) {
        RuntimeEnvironment.setFontScale(variant.textScale)
        val previous = applyGateLocale(variant.localeTag)
        try {
            show(state, content).use {
                compose.onNodeWithTag(SCENE).captureRoboImage("src/screenshots/Gate/$scene-${variant.name}.png")
            }
        } finally {
            applyGateLocale(previous)
        }
    }

    private fun show(state: GateUi, content: @Composable () -> Unit): ActivityScenario<ComponentActivity> {
        // The scale is set before the activity starts: changing it on a running activity recreates it and drops the
        // content (blank captures, seen while recording).
        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        scenario.onActivity { activity ->
            activity.setContent {
                KitTheme(
                    appearance = KitAppearance(
                        mode = if (variant.dark) AppearanceMode.Dark else AppearanceMode.Light,
                        motion = MotionLevel.Off,
                    ),
                    platform = AndroidKitPlatform(View(ApplicationProvider.getApplicationContext())),
                    localeTag = variant.localeTag,
                    stillFrame = true,
                ) {
                    Box(Modifier.fillMaxSize().testTag(SCENE)) { GateScreen(state, {}, {}, {}, {}, {}, content) }
                }
            }
        }
        compose.waitUntil(TIMEOUT_MS) { compose.onAllNodesWithTag(SCENE).fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
        // Guard against a blank window capture (seen while recording, before the gate skipped AnimatedContent under
        // reduced motion): wait for the scene to draw, and fail rather than record a blank golden.
        var tries = 0
        while (!groundDrawn() && tries++ < SETTLE_TRIES) {
            compose.mainClock.advanceTimeBy(FRAME_SETTLE_MS)
            compose.waitForIdle()
        }
        check(groundDrawn()) { "the scene never drew its ground (${variant.name})" }
        return scenario
    }

    /** Something of the scene is drawn: the picture is not the window's uniform blank (#FAFAFA). */
    private fun groundDrawn(): Boolean {
        val image = compose.onNodeWithTag(SCENE).captureToImage().toPixelMap()
        val blank = Color(0xFFFAFAFA)
        for (y in 0 until image.height step 16) {
            for (x in 0 until image.width step 16) if (image[x, y] != blank) return true
        }
        return false
    }

    @Test
    fun `B-KIT-003 B-L10N-010 Opening`() = capture("opening", GateUi.Opening(wordless = false, offerRetry = false))

    @Test
    fun `B-L10N-010 Opening wordless mark`() = capture("opening-wordless", GateUi.Opening(wordless = true, offerRetry = false))

    @Test
    fun `B-KIT-003 B-L10N-010 Updating`() = capture("updating", GateUi.Updating(offerRetry = false))

    @Test
    fun `B-MIGR-010 Updating after 8 s offers Try again`() = capture("updating-slow", GateUi.Updating(offerRetry = true))

    @Test
    fun `B-MIGR-010 failed with every way out`() =
        capture(
            "failed",
            GateUi.Failed(
                GateFailureScreen.Failed,
                GatePresenter.rowsFor(GateBlockReason.StepCrashed, restoreAvailable = true),
                detail = "M2 · UnwrapFailed ×1",
            ),
        )

    @Test
    fun `B-MIGR-010 phone almost full`() =
        capture(
            "low-space",
            GateUi.Failed(
                GateFailureScreen.LowSpace,
                GatePresenter.rowsFor(GateBlockReason.LowSpace, restoreAvailable = true),
                neededBytes = 120L * 1024 * 1024,
                detail = "S0 · LowSpace",
            ),
        )

    @Test
    fun `B-MIGR-010 could not save`() =
        capture(
            "save-failed",
            GateUi.Failed(
                GateFailureScreen.SaveFailed,
                GatePresenter.rowsFor(GateBlockReason.WriteRefused, restoreAvailable = false),
            ),
        )

    @Test
    fun `B-KIT-015 Start fresh confirmation at 200 percent`() =
        capture(
            "start-fresh-confirm",
            GateUi.Failed(
                GateFailureScreen.Failed,
                GatePresenter.rowsFor(GateBlockReason.PrefsUnreadable, restoreAvailable = false),
                confirming = GateRow.StartFresh,
            ),
        )

    @Test
    fun `B-MIGR-006 B-MIGR-011 re-enter sheet`() =
        capture(
            "reenter-sheet",
            GateUi.Open(
                reenter = listOf(
                    GateReentry("p1", "Home server", GateReentryKind.Password),
                    GateReentry("p2", "Work", GateReentryKind.Token),
                    GateReentry(null, "GitHub", GateReentryKind.GitHubSignIn),
                ),
                deferredNotice = false,
            ),
        ) { AppStandIn() }

    @Test
    fun `02-3_7-4 deferred-features notice`() =
        capture("deferred-notice", GateUi.Open(reenter = emptyList(), deferredNotice = true)) { AppStandIn() }

    /** A plain kit screen standing in for the app behind the open gate. */
    @Composable
    private fun AppStandIn() {
        Box(Modifier.fillMaxSize()) {
            KitScreen(title = "Conversations") { KitText("…", Modifier.padding(4.dp)) }
        }
    }

    companion object {
        private const val FRAME_SETTLE_MS = 100L
        private const val TIMEOUT_MS = 5_000L
        private const val SETTLE_TRIES = 50
        private const val SCENE = "gate-scene"

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun variants(): List<Array<Any>> =
            listOf(false, true).flatMap { dark ->
                listOf("en", "ar").flatMap { tag -> listOf(1f, 2f).map { arrayOf<Any>(Variant(dark, tag, it)) } }
            }
    }
}
