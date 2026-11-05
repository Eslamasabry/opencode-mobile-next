package io.github.eslamasabry.oc.ui.kit.rb.o40

import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.setup.*
import kotlin.test.Test
import kotlin.test.assertEquals

class SetupScenesTest {
    @Test
    fun `finished frames do not acquire a breath without ambient permission`() {
        val f = SetupSceneFrame(1f, .5f, false)
        assertEquals(0f, f.breath)
        assertEquals(1f, f.interval(.3f, .7f))
        assertEquals(0f, SetupSceneFrame(0f, 0f, false).interval(.3f, .7f))
        assertEquals(1f, SetupSceneFrame(1f, .5f, true).breath)
    }

    @Test fun download() = strip("download", KitSetupSceneStage.Download, 3, 5, .6f)

    @Test fun unpack() = strip("unpack", KitSetupSceneStage.Unpack, 3, 5, .5f)

    @Test fun install() = strip("install", KitSetupSceneStage.Install, 1, 5, .3f)

    @Test fun start() = strip("start", KitSetupSceneStage.Start, 5, 6, null)

    @Test
    fun paused() = strip("paused", KitSetupSceneStage.Download, 3, 5, .6f, KitSetupSceneHalt.Paused)

    @Test
    fun failed() = strip("failed", KitSetupSceneStage.Download, 3, 5, .6f, KitSetupSceneHalt.Failed)

    @Test
    fun unplugged() {
        FlutterGoldenGate.check("kit_setup_unplugged") { KitSetupUnplugged(width = 240.dp) }
    }

    @Test
    fun portal() {
        FlutterGoldenGate.check("kit_portal") { KitSetupPortal() }
    }

    @Test
    fun folders() {
        FlutterGoldenGate.check("kit_folders_open") { KitSetupFolders() }
    }

    private fun strip(
        name: String,
        stage: KitSetupSceneStage,
        done: Int,
        total: Int,
        fraction: Float?,
        halt: KitSetupSceneHalt? = null,
    ) {
        FlutterGoldenGate.check("kit_setup_steps_$name") {
            KitSetupSteps(stage, done, total, fraction = fraction, halted = halt, width = 320.dp)
        }
    }
}
