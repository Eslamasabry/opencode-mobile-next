package io.github.eslamasabry.oc.ui.kit.lamp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.request.KitRequest
import io.github.eslamasabry.oc.ui.kit.request.KitRequestKind
import io.github.eslamasabry.oc.ui.kit.request.KitRequestWords
import io.github.eslamasabry.oc.ui.kit.row.KitReorderWords
import io.github.eslamasabry.oc.ui.kit.status.KitStepTreeKind
import io.github.eslamasabry.oc.ui.kit.status.KitStepTreeWords
import io.github.eslamasabry.oc.ui.kit.status.KitTreeStep
import io.github.eslamasabry.oc.ui.kit.status.KitTreeStepState
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors

/** The edge light panel's test words, models and host (K2): the prototype's own scenes, plain values only. */
internal object K2 {
    val styles = listOf(Style.Solid, Style.Glass, Style.Soft, Style.Brutal, Style.Paper)

    val request = KitRequestWords("Allow once", "Deny", "Answer…", { "Allow once: $it" }, { "Deny: $it" }, { "Answer: $it" })

    fun duration(s: Long): String = if (s < 60) "${s}s" else "${s / 60}:${(s % 60).toString().padStart(2, '0')}"

    val steps =
        KitStepTreeWords(
            working = "Working",
            notStarted = "Not started",
            needsYou = "Needs you",
            paused = "Paused until you answer",
            denied = "Denied",
            failed = "Failed",
            moreInside = { "+$it more inside" },
            more = { "+$it more" },
            duration = ::duration,
            stop = { "Stop “$it”" },
            spoken = { s, said -> listOfNotNull(s.name, said.ifBlank { null }, s.fact).joinToString(", ") },
            unfold = "Show sub-agents",
            fold = "Hide sub-agents",
            request = request,
        )

    val reorder = KitReorderWords({ "Reorder $it" }, "Move up", "Move down")

    val panel =
        KitLampPanelWords(
            title = "Conversations",
            edit = "Edit",
            done = "Done",
            all = { "All conversations · $it" },
            allShort = { "All · $it" },
            newConversation = "New conversation",
            hidden = { "Hidden · $it" },
            showAgain = "Show again",
            more = { "$it more in All conversations" },
            empty = "No other conversations yet.",
            pin = { "Pin to top: $it" },
            unpin = { "Unpin: $it" },
            rename = { "Rename conversation: $it" },
            hide = { "Hide from this list: $it" },
            stop = { "Stop “$it”" },
            subAgents = { "$it sub-agents" },
            edgeLight = "Edge light",
            move = "Move…",
            turnOff = "Turn off",
            reorder = reorder,
            steps = steps,
            request = request,
        )

    val place = KitLampPlaceWords("Where the edge light sits", "Tap a spot. The edge light moves there now.", "Left", "Right", "High", "Middle", "Low", "Done")

    /** Picture nested-1: Deploy preview to staging, its sub-agents in parallel (timers off unless [live]). */
    fun deployChildren(live: Boolean = false, review: KitTreeStepState = KitTreeStepState.NotStarted): List<KitTreeStep> {
        fun t(s: Long) = if (live) s else null
        return listOf(
            KitTreeStep("assets", "assets", KitTreeStepState.Done, fact = "42 images", seconds = 34),
            KitTreeStep(
                "build", "build", KitTreeStepState.Working, fact = "4 of 6", verb = "Testing", seconds = t(73), progress = 0.66f, stoppable = true,
                children = listOf(
                    KitTreeStep("lint", "lint", KitTreeStepState.Done, fact = "0 problems", seconds = 8),
                    KitTreeStep(
                        "unit", "unit tests", KitTreeStepState.Working, fact = "182/214", verb = "Testing", seconds = t(42), progress = 0.85f, stoppable = true,
                        children = listOf(KitTreeStep("u1", "fixtures", KitTreeStepState.Working), KitTreeStep("u2", "report", KitTreeStepState.NotStarted)),
                    ),
                ),
            ),
            KitTreeStep("docs", "docs", KitTreeStepState.Working, fact = "CHANGELOG.md", verb = "Reading", seconds = t(20), stoppable = true),
            KitTreeStep(
                "review", "review", review,
                request = if (review == KitTreeStepState.NeedsYou) KitRequest("req-review", "Asks to run: npm run e2e") else null,
            ),
        )
    }

    /** Picture steps-1: Fix the checkout crash, one agent's steps in sequence. */
    val checkoutSteps =
        listOf(
            KitTreeStep("s1", "Read the bug report", KitTreeStepState.Done, fact = "issue #481", seconds = 8),
            KitTreeStep("s2", "Reproduce the crash", KitTreeStepState.Done, fact = "cart.ts:142", seconds = 34),
            KitTreeStep("s3", "Patch the checkout", KitTreeStepState.Working, verb = "Editing", stoppable = true),
            KitTreeStep("s4", "Open PR #482", KitTreeStepState.NotStarted),
            KitTreeStep("s5", "Write the release notes", KitTreeStepState.NotStarted),
        )

    fun row(key: String, title: String, mark: KitLampRowMark, pinned: Boolean = false, stoppable: Boolean = false, newest: String? = "Created $key.txt", state: String? = null) =
        KitLampRow(key, title, mark, state = state, newest = newest, time = "5m", pinned = pinned, stoppable = stoppable)

    /** Pictures list-solid and nested-3: groups top → bottom, Needs you by the thumb. */
    fun model(editing: Boolean = false, live: Boolean = false, pinned: Boolean = false): KitLampPanelModel {
        val needs =
            KitLampRow(
                "notes", "create a file notes.txt with one line", KitLampRowMark.NeedsYou,
                request = KitRequest("req-notes", "Asks to edit: wait7.txt"), time = "now",
            )
        val deploy =
            KitLampRow(
                "deploy", "Deploy preview to staging", KitLampRowMark.Working, progress = 0.4f, state = "2 of 5 done",
                newest = "Building the preview image", stoppable = true, steps = KitStepTreeKind.Parallel, children = deployChildren(live),
            )
        val proof = row("proof", "create a file proof.txt with one line", KitLampRowMark.Working, stoppable = true, state = "for 4m", newest = "Checking the line length")
        val sections =
            listOfNotNull(
                if (pinned) KitLampSection(KitLampGroup.Pinned, "Pinned", listOf(row("rel", "Write the release notes", KitLampRowMark.Quiet, pinned = true), row("bank", "Bank API migration", KitLampRowMark.Quiet, pinned = true))) else null,
                KitLampSection(KitLampGroup.Recent, "Recent", listOf(row("hello", "create a file hello.txt with one line", KitLampRowMark.Quiet), row("plan", "Migration plan drafted in four steps", KitLampRowMark.Quiet))),
                KitLampSection(KitLampGroup.Working, "Working", listOf(proof, deploy)),
                KitLampSection(KitLampGroup.NeedsYou, "Needs you", listOf(needs)),
            )
        return KitLampPanelModel(sections, all = 7, editing = editing, youAreIn = "You are in “name two animals”")
    }
}

/** The kit theme for a test or a picture: style, mode, language, motion, text scale, density. */
@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.k2Host(
    style: Style = Style.Solid,
    dark: Boolean = true,
    rtl: Boolean = false,
    motion: MotionLevel = MotionLevel.Full,
    fontScale: Float = 1f,
    density: Float = 1f,
    platform: FakeKitPlatform = FakeKitPlatform(),
    widthDp: Int = 412,
    heightDp: Int = 800,
    content: @Composable () -> Unit,
) {
    setContent {
        CompositionLocalProvider(LocalDensity provides Density(density, fontScale)) {
            KitTheme(KitAppearance(mode = if (dark) AppearanceMode.Dark else AppearanceMode.Light, style = style, motion = motion), platform, if (rtl) "ar" else "en") {
                Box(Modifier.size(widthDp.dp, heightDp.dp).background(LocalKitColors.current.ground.copy(alpha = 1f))) { content() }
            }
        }
    }
}
