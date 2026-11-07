package io.github.eslamasabry.oc.ui.kit.rb.o30

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import io.github.eslamasabry.oc.ui.kit.scene.KitDrawnFrame
import io.github.eslamasabry.oc.ui.kit.scene.KitDrawnScene
import io.github.eslamasabry.oc.ui.kit.scene.TeamAgentPose
import io.github.eslamasabry.oc.ui.kit.scene.TeamBoardScene
import io.github.eslamasabry.oc.ui.kit.scene.TeamCast
import io.github.eslamasabry.oc.ui.kit.scene.TeamCast.agent
import io.github.eslamasabry.oc.ui.kit.scene.TeamIdleScene
import io.github.eslamasabry.oc.ui.kit.scene.TeamMergedScene
import io.github.eslamasabry.oc.ui.kit.scene.TeamNudgeScene
import io.github.eslamasabry.oc.ui.kit.scene.TeamPlanningScene
import io.github.eslamasabry.oc.ui.kit.scene.TeamRestScene
import io.github.eslamasabry.oc.ui.kit.scene.TeamScenesAll
import io.github.eslamasabry.oc.ui.kit.scene.TeamWakingScene
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Every constant and rule of lib/ui/kit/scenes/team_scenes.dart. */
class TeamScenesParityTest {
    init {
        ensureGraphics()
    }

    @Test fun `the seven team drawings, in the gallery's order, with their boxes`() {
        assertEquals(
            listOf(
                TeamBoardScene,
                TeamPlanningScene,
                TeamWakingScene,
                TeamMergedScene,
                TeamNudgeScene,
                TeamRestScene,
                TeamIdleScene,
            ),
            TeamScenesAll,
        )
        assertEquals(
            listOf(
                Size(120f, 120f),
                Size(120f, 84f),
                Size(140f, 92f),
                Size(160f, 100f),
                Size(56f, 40f),
                Size(120f, 100f),
                Size(64f, 44f),
            ),
            TeamScenesAll.map { it.box },
        )
        TeamScenesAll.forEach { assertFalse(it.mirrorsInRtl, "${it::class.simpleName} is a picture, it never mirrors") }
    }

    @Test fun `the cast's body, head and hands`() {
        assertEquals(28f, TeamCast.BODY_WIDTH)
        assertEquals(20f, TeamCast.BODY_HEIGHT)
        assertEquals(9f, TeamCast.HEAD_RADIUS)
        assertEquals(31f, TeamCast.HEAD_LIFT, "body 20 + neck 2 + head 9")
        assertEquals(Offset(15f, -4f), TeamCast.REST_HAND)
        assertEquals(Offset(21f, -32f), TeamCast.RAISED_HAND)
        assertEquals(4f, TeamCast.penWidth(1f), "the outline is .8 of the stroke")
        assertEquals(5f, TeamCast.penWidth(.8f), "and keeps its width when the agent is drawn smaller")
    }

    @Test fun `a pose defaults to a full, open-eyed, still agent`() {
        val pose = TeamAgentPose(base = Offset(10f, 20f))
        assertEquals(1f, pose.scale)
        assertEquals(1f, pose.draw)
        assertEquals(1f, pose.show)
        assertEquals(1f, pose.eyes)
        assertEquals(Offset.Zero, pose.look)
        assertEquals(null, pose.leftHand)
        assertEquals(null, pose.rightHand)
        assertFalse(pose.lead)
        assertFalse(pose.joy)
    }

    @Test fun `the eyes look up to 2_2 across and 1_6 up or down and open from a line to a dot`() {
        assertEquals(Offset(0f, -30.5f), TeamCast.eyeCentre(Offset.Zero))
        assertEquals(Offset(2.2f, -30.5f - 1.6f), TeamCast.eyeCentre(Offset(3f, -5f)), "look is clamped to -1..1")
        assertNear(.9f, TeamCast.eyeHeight(0f))
        assertNear(3.6f, TeamCast.eyeHeight(1f))
        assertNear(3.6f, TeamCast.eyeHeight(2f))
    }

    @Test fun `a hand moves from rest to raised on its side, scaled`() {
        val base = Offset(90f, 114f)
        assertEquals(base + Offset(15f, -4f), TeamCast.hand(base, 1f, 1f, 0f))
        assertEquals(base + Offset(-21f, -32f), TeamCast.hand(base, 1f, -1f, 1f))
        assertEquals(base + Offset(18f, -18f) * .5f, TeamCast.hand(base, .5f, 1f, .5f))
    }

    @Test fun `a card's lines start 4_5 in and the last one is shorter`() {
        val lines = TeamCast.cardLines(18f, 13f, 2)
        assertEquals(2, lines.size)
        assertNear(-4.5f, lines[0].first)
        assertNear(18f * .28f, lines[0].second)
        assertNear(-13f / 2 + 13f / 3, lines[0].third)
        assertNear(18f * .1f, lines[1].second)
        assertEquals(1, TeamCast.cardLines(30f, 21f, 1).size)
        assertNear(30f * .1f, TeamCast.cardLines(30f, 21f, 1)[0].second)
    }

    @Test fun `the planning card is held between the two at rest and passes while waiting`() {
        assertEquals(.5f, TeamPlanningScene.pass(frameAt(1f)))
        assertNear(60f, TeamPlanningScene.cardAt(.5f).x)
        assertNear(30f, TeamPlanningScene.cardAt(.5f).y)
        assertEquals(Offset(50f, 42f), TeamPlanningScene.cardAt(0f))
        val start = TeamPlanningScene.pass(frameAt(1f, loop = 0f, looping = true))
        assertNear(.5f, start, what = "the loop starts where rest is")
        assertNear(1f, TeamPlanningScene.pass(frameAt(1f, loop = .25f, looping = true)))
    }

    @Test fun `the waking agents open their eyes in turn and each blinks at its own moment`() {
        assertEquals(0f, TeamWakingScene.eyes(frameAt(.7f), 0))
        assertNear(1f, TeamWakingScene.eyes(frameAt(1f), 0), what = "agent 0 open by .86")
        // Agent 2 opens over .86..1.02, so at the end of the entrance it is still a hair from fully open.
        assertTrue(TeamWakingScene.eyes(frameAt(1f), 2) in .95f..1f)
        assertEquals(0f, TeamWakingScene.eyes(frameAt(.78f), 1), "agent 1 starts opening at .78")
        // Looping: agent 0 blinks at loop .2 (closed at .225), agent 2 at loop .8.
        assertNear(0f, TeamWakingScene.eyes(frameAt(1f, loop = .225f, looping = true), 0))
        assertNear(1f, TeamWakingScene.eyes(frameAt(1f, loop = .5f, looping = true), 0))
        assertNear(0f, TeamWakingScene.eyes(frameAt(1f, loop = .825f, looping = true), 2))
        // Agent 1 at loop .5: since = 0, the start of its blink, eyes still open.
        assertNear(1f, TeamWakingScene.eyes(frameAt(1f, loop = .5f, looping = true), 1))
        assertEquals(86f, TeamWakingScene.GROUND)
        assertEquals(Offset(70f, 14f), TeamWakingScene.SPARK)
    }

    @Test fun `the nudge waves twice and is still at the end`() {
        assertEquals(0f, TeamNudgeScene.swing(.5f))
        assertEquals(0f, TeamNudgeScene.swing(1f))
        assertNotEquals(0f, TeamNudgeScene.swing(.6f))
        assertEquals(Offset(22f, 50f), TeamNudgeScene.BASE)
    }

    @Test fun `the merged card, the board and the rest positions`() {
        assertEquals(Offset(80f, 44f), TeamMergedScene.CARD)
        assertEquals(Offset(30f, 96f), TeamMergedScene.LEFT)
        assertEquals(Offset(130f, 96f), TeamMergedScene.RIGHT)
        assertEquals(Offset(30f, 82f), TeamPlanningScene.LEFT)
        assertEquals(Offset(90f, 82f), TeamPlanningScene.RIGHT)
    }

    @Test fun `every team drawing is empty at 0 and finished at 1, the lead in the accent`() {
        for (scene in TeamScenesAll) {
            val name = scene::class.simpleName
            assertEquals(0, inked(paint(scene, frameAt(0f))), "$name draws nothing at entrance 0")
            assertTrue(inked(paint(scene, frameAt(1f))) > 500, "$name is drawn when finished")
        }
        for (scene in listOf(TeamBoardScene, TeamPlanningScene, TeamWakingScene, TeamMergedScene, TeamNudgeScene)) {
            val done = paint(scene, frameAt(1f))
            assertTrue(pixelsOf(done, testPalette.accent) > 10, "${scene::class.simpleName} has its accent")
        }
        for (scene in listOf(TeamRestScene, TeamIdleScene)) {
            val done = paint(scene, frameAt(1f))
            assertEquals(0, pixelsOf(done, testPalette.accent), "${scene::class.simpleName} is all quiet")
        }
    }

    @Test fun `a lead agent is outlined in the accent, a teammate in the muted line`() {
        fun one(lead: Boolean) =
            object : KitDrawnScene {
                override fun DrawScope.paint(frame: KitDrawnFrame) =
                    agent(frame.palette, TeamAgentPose(base = Offset(60f, 100f), lead = lead))
            }
        assertTrue(pixelsOf(paint(one(true), frameAt(1f)), testPalette.accent) > 100)
        assertEquals(0, pixelsOf(paint(one(false), frameAt(1f)), testPalette.accent))
        assertTrue(pixelsOf(paint(one(false), frameAt(1f)), testPalette.muted) > 100)
    }
}
