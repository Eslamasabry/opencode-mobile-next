package io.github.eslamasabry.oc.ui.shell.rb.o53

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.generated.FlutterKitTokens
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.scene.KitDrawnIllustration
import io.github.eslamasabry.oc.ui.kit.scene.KitDrawnScene
import io.github.eslamasabry.oc.ui.kit.scene.StatesFolderScene
import io.github.eslamasabry.oc.ui.kit.scene.StatesSearchScene
import io.github.eslamasabry.oc.ui.kit.scene.StatesSheetScene
import io.github.eslamasabry.oc.ui.kit.scene.StatesTerminalScene
import io.github.eslamasabry.oc.ui.kit.scene.StatesTrayScene
import io.github.eslamasabry.oc.ui.kit.scene.StatesUnpluggedScene
import io.github.eslamasabry.oc.ui.kit.scene.StatesWorkingScene
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import kotlin.test.Test

/**
 * The empty, quiet and failure drawings the shell's pages show (order 53, `test/goldens/states_*`), with the inputs of
 * test/kit_states_scenes_test.dart:58-86: each scene's finished frame at the page illustration width (160 dp), padded
 * 16 dp on the theme's surface (the page ground), captured by its boundary alone (192 x 192 at 1x) from flutter_test's
 * default view (device pixel ratio 3).
 */
class StatesScenesGateTest {
    private fun shot(name: String, scene: KitDrawnScene) {
        val side = FlutterKitTokens.ILLUSTRATION_PAGE + 2 * PAD // F: test/kit_states_scenes_test.dart:74-76
        FlutterGoldenGate.check(
            "states_$name",
            frame = FlutterGoldenGate.Frame.Screen,
            window = side to side,
            density = 1f,
            pixelRatio = VIEW_RATIO,
            wrap = { FlutterCapture(it) },
        ) {
            Box(Modifier.fillMaxSize().background(LocalKitColors.current.ground).padding(PAD.dp)) {
                KitDrawnIllustration(scene, width = FlutterKitTokens.ILLUSTRATION_PAGE.dp)
            }
        }
    }

    @Test fun states_sheet() = shot("sheet", StatesSheetScene)

    @Test fun states_folder() = shot("folder", StatesFolderScene)

    @Test fun states_tray() = shot("tray", StatesTrayScene)

    @Test fun states_search() = shot("search", StatesSearchScene)

    @Test fun states_terminal() = shot("terminal", StatesTerminalScene())

    @Test fun states_terminal_ended() = shot("terminal_ended", StatesTerminalScene(ended = true))

    @Test fun states_unplugged() = shot("unplugged", StatesUnpluggedScene)

    @Test fun states_working() = shot("working", StatesWorkingScene)

    private companion object {
        const val PAD = 16 // F: test/kit_states_scenes_test.dart:76
        const val VIEW_RATIO = 3f // NV: flutter_test's default view device pixel ratio (no override in the test)
    }
}
