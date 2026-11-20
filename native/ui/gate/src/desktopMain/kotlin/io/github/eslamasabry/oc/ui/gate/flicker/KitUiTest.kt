@file:OptIn(ExperimentalTestApi::class, InternalComposeUiApi::class)

package io.github.eslamasabry.oc.ui.gate.flicker

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.platform.FrameRecomposer
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.SemanticsNodeInteractionCollection
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.unit.Density
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestDispatcher

/**
 * The one way a kit or feature UI test starts Compose (the flicker-rule design, owner 2026-11-20): the same
 * test as `runComposeUiTest` / `runDesktopComposeUiTest` (v2: queued coroutines, 1024 x 768 unless sized), with a
 * frame recorder that takes the semantics tree on every frame the test clock produces and reads it with the flicker
 * rules when the test ends: a node that blinks, words shown twice, words or a node that leave and come back, a
 * jump with no motion around it, a first frame laid out unlike the settled page, and (with a ceiling) a step that
 * recomposes too often.
 *
 * What it found is judged by the module's mode: written down only (report), failing when the module's
 * `flicker-baseline.tsv` does not hold it (fail-new), or failing outright (fail). Intended motion is allowed by name in
 * the test itself, each entry with its reason:
 * `kitUiTest(allow = setOf(Allow.jump("kit-sheet", "the sheet slides in")))`.
 *
 * It cannot see pixels without semantics (a fade, a glow, a colour flash), the device's keyboard and frame timing, or a
 * journey the test does not drive: those stay with pictures and frame-read recordings.
 */
public fun kitUiTest(
    width: Int = DEFAULT_WIDTH,
    height: Int = DEFAULT_HEIGHT,
    allow: Set<Allow> = emptySet(),
    options: KitUiOptions = KitUiOptions(),
    block: suspend KitUiTest.() -> Unit,
) {
    val name = FlickerVerdict.callingTest()
    val mode = FlickerVerdict.mode(options.mode)
    val test = KitUiTest(width, height, options, mode)
    test.runTest { test.recording(block) }
    val found = test.findings()
    FlickerVerdict.judge(name, found, allow, mode)
}

private const val DEFAULT_WIDTH = 1024
private const val DEFAULT_HEIGHT = 768

/**
 * A Compose UI test with the frame recorder (see [kitUiTest]). The test's own steps mark the recording: setting
 * content, finding nodes and waiting each begin a step.
 */
public class KitUiTest internal constructor(
    width: Int,
    height: Int,
    private val options: KitUiOptions,
    private val mode: FlickerMode,
) : SkikoComposeUiTest(
        width = width,
        height = height,
        effectContext = EmptyCoroutineContext,
        runTestContext = EmptyCoroutineContext,
        testTimeout = options.timeout,
        density = Density(1f),
    ) {
    private var recorder: FrameRecorder? = null
    private val dirty = AtomicBoolean(true)
    private var echo = false
    private var lastChanges = -1L
    private var fault: Throwable? = null

    override fun setContent(composable: @Composable () -> Unit) {
        super.setContent(composable)
        recorder?.mark()
    }

    override fun onNode(matcher: SemanticsMatcher, useUnmergedTree: Boolean): SemanticsNodeInteraction {
        recorder?.mark()
        return super.onNode(matcher, useUnmergedTree)
    }

    override fun onAllNodes(matcher: SemanticsMatcher, useUnmergedTree: Boolean): SemanticsNodeInteractionCollection {
        recorder?.mark()
        return super.onAllNodes(matcher, useUnmergedTree)
    }

    override fun waitForIdle() {
        recorder?.mark()
        super.waitForIdle()
    }

    override suspend fun awaitIdle() {
        recorder?.mark()
        super.awaitIdle()
    }

    override fun waitUntil(conditionDescription: String?, timeoutMillis: Long, condition: () -> Boolean) {
        recorder?.mark()
        super.waitUntil(conditionDescription, timeoutMillis, condition)
    }

    /**
     * Runs [step] and fails when the composition recomposed more than [atMost] times during it: the keyboard probe's
     * check (a chat frame that recomposes on every frame of the keyboard's travel), for any step.
     */
    public fun expectRecompositions(atMost: Int, step: () -> Unit) {
        val before = recomposer().changeCount
        step()
        val n = recomposer().changeCount - before
        if (n > atMost) {
            throw AssertionError("RECOMPOSE: the step recomposed $n times, the ceiling is $atMost")
        }
    }

    internal suspend fun recording(block: suspend KitUiTest.() -> Unit) {
        if (mode == FlickerMode.Off) {
            block()
            return
        }
        val rec = FrameRecorder(options.limits)
        recorder = rec
        val observer = Snapshot.registerApplyObserver { _, _ -> dirty.set(true) }
        val scope = CoroutineScope(dispatcher() + Job() + CoroutineExceptionHandler { _, e -> fault = e })
        // Beside the test's own render loop, on the same clock: it samples each frame right after the frame is drawn.
        scope.launch {
            while (isActive) {
                delay(FRAME_MS)
                sample(rec)
            }
        }
        try {
            block()
        } finally {
            scope.cancel()
            observer.dispose()
        }
        fault?.let { throw AssertionError("the frame recorder failed", it) }
    }

    internal fun findings(): List<Finding> = recorder?.finish(options.recompositionsPerStep).orEmpty()

    /** One frame: read again when anything was applied or recomposed since the last (and on the frame after that). */
    private fun sample(rec: FrameRecorder) {
        val changes = recomposer().changeCount
        val changed = dirty.getAndSet(false) || changes != lastChanges
        val fresh = if (changed || echo) runOnUiThread { FrameRecorder.read(rootNodes()) } else null
        echo = changed
        lastChanges = changes
        rec.add(mainClock.currentTime, changes, fresh)
    }

    private fun rootNodes(): List<SemanticsNode> =
        runWithoutImplicitWait { roots().fetchSemanticsNodes(atLeastOneRootRequired = false) }

    /** The roots, asked without marking a step (the recorder's own look is not one of the test's). */
    private fun roots(): SemanticsNodeInteractionCollection = super.onAllNodes(isRoot(), true)

    private val liveRecomposer: Recomposer by lazy {
        (field("frameRecomposer") as FrameRecomposer).compositionContext as Recomposer
    }

    private fun recomposer(): Recomposer = liveRecomposer

    private fun dispatcher(): TestDispatcher = field("compositionCoroutineDispatcher") as TestDispatcher

    private fun field(name: String): Any =
        SkikoComposeUiTest::class.java.getDeclaredField(name).let {
            it.isAccessible = true
            it.get(this)
        }

    private companion object {
        /** The test clock's frame (SkikoComposeUiTest's FRAME_DELAY_MILLIS). */
        const val FRAME_MS = 16L
    }
}
