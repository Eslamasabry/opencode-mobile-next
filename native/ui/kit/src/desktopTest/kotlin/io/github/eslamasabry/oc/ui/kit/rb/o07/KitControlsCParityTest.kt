package io.github.eslamasabry.oc.ui.kit.rb.o07

import androidx.compose.ui.graphics.Color
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.action.ActionBlockRules
import io.github.eslamasabry.oc.ui.kit.action.KitActionLayout
import io.github.eslamasabry.oc.ui.kit.scene.KitDraw
import io.github.eslamasabry.oc.ui.kit.state.SKELETON_REPLY_WIDTHS
import io.github.eslamasabry.oc.ui.kit.status.KitTaskState
import io.github.eslamasabry.oc.ui.kit.status.TaskMarkRules
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.kitMarkPaused
import io.github.eslamasabry.oc.ui.words.res.kitTaskNeedsYou
import io.github.eslamasabry.oc.ui.words.res.kitTaskStopped
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** The constants and rules of kit_buttons.dart, kit_illustration.dart, kit_skeleton_transcript.dart and kit_task_mark.dart. */
class KitControlsCParityTest {
    @Test fun `B-KIT-011 two tertiary actions show, the rest go into More`() {
        val t = (1..5).map { KitAction("A$it", {}) }
        val (shown, more) = ActionBlockRules.split(t)
        assertEquals(listOf("A1", "A2"), shown.map { it.label })
        assertEquals(listOf("A3", "A4", "A5"), more.map { it.label })
    }

    @Test fun `B-KIT-011 compact stacks, medium rows, a destructive tertiary stacks everywhere`() {
        assertEquals(KitActionLayout.Stack, ActionBlockRules.layout(412f, emptyList()))
        assertEquals(KitActionLayout.Row, ActionBlockRules.layout(720f, emptyList()))
        assertEquals(KitActionLayout.Stack, ActionBlockRules.layout(720f, listOf(KitAction("Delete", {}, destructive = true))))
    }

    @Test fun `KitDraw keeps the brand stroke and stages entrances`() {
        assertEquals(5f, KitDraw.STROKE)
        assertEquals(2.5f, KitDraw.HAIRLINE)
        assertEquals(0f, KitDraw.interval(.2f, .25f, .85f))
        assertEquals(1f, KitDraw.interval(.9f, .25f, .85f))
        assertTrue(KitDraw.interval(.5f, .25f, .85f) in 0f..1f)
        assertEquals(0f, KitDraw.wave(0f), 1e-6f)
        assertEquals(1f, KitDraw.wave(.5f), 1e-6f)
        assertEquals(.5f, KitDraw.fade(Color.Black, .5f).alpha, 1f / 128)
    }

    @Test fun `KitSkeletonTranscript reply bars are Flutter's`() {
        assertEquals(listOf(listOf(.94f, .88f, .62f), listOf(.9f, .97f, .8f, .46f)), SKELETON_REPLY_WIDTHS)
    }

    @Test fun `KitTaskMark words and the paused rule`() {
        assertEquals(Res.string.kitTaskNeedsYou, TaskMarkRules.word(KitTaskState.NeedsYou))
        assertEquals(Res.string.kitTaskStopped, TaskMarkRules.word(KitTaskState.Stopped))
        assertEquals(Res.string.kitMarkPaused, TaskMarkRules.word(KitTaskState.Working, paused = true))
        assertTrue(TaskMarkRules.canPause(KitTaskState.Waiting))
        assertFailsWith<IllegalArgumentException> { TaskMarkRules.word(KitTaskState.Done, paused = true) }
    }
}
