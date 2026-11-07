package io.github.eslamasabry.oc.ui.kit.rb.o29

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.generated.FlutterKitTokens
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTextRole
import io.github.eslamasabry.oc.ui.kit.scene.KitFoldersOpenScene
import io.github.eslamasabry.oc.ui.kit.scene.KitPortalScene
import io.github.eslamasabry.oc.ui.kit.scene.SceneArt
import io.github.eslamasabry.oc.ui.kit.scene.SceneIllustration
import io.github.eslamasabry.oc.ui.kit.scene.ServersLinkScene
import io.github.eslamasabry.oc.ui.kit.scene.ServersLinkState
import io.github.eslamasabry.oc.ui.kit.scene.ServersWelcomeScene
import io.github.eslamasabry.oc.ui.kit.scene.SetupPhoneScene
import io.github.eslamasabry.oc.ui.kit.scene.SetupReadyScene
import io.github.eslamasabry.oc.ui.kit.scene.SetupSceneHalt
import io.github.eslamasabry.oc.ui.kit.scene.SetupSceneStage
import io.github.eslamasabry.oc.ui.kit.scene.SetupStepsScene
import io.github.eslamasabry.oc.ui.kit.scene.SetupUnpluggedScene
import kotlin.test.Test

/**
 * The order-29 shots of test/goldens/kit/kit_scenes_golden_test.dart at FLUTTER_REF: the contact sheets of the
 * portal/folders, setup and servers scenes (412x915, both modes), the mirrored scenes' sheet in Arabic, and the
 * default page state at every gallery size, in Arabic and at 2.0 text. The states/working/team sheets belong to
 * order 30. The mirrored sheet also draws `TeamDiscoverRelayScene`, which is order 30's: until it lands that slot is
 * left out (see evidence/29.md).
 */
@OptIn(ExperimentalLayoutApi::class)
class KitScenesGateTest {
    /** The contact sheet: a centred Wrap with space4 between scenes, each at its gallery width. */
    @Composable
    private fun ContactSheet(scenes: List<Pair<SceneArt, Int>>) {
        val gap = FlutterKitTokens.SPACE4.dp
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(gap, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(gap, Alignment.CenterVertically),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            for ((scene, width) in scenes) SceneIllustration(scene, width = width.dp)
        }
    }

    private class Copy(val title: String, val body: String, val action: String)

    private val en = Copy("No projects yet", "Open a folder on studio-pc to start working with an agent.", "Open a project")
    private val ar = Copy("لا توجد مشاريع بعد", "افتح مجلدًا على studio-pc لتبدأ العمل مع وكيل.", "افتح مشروعًا")

    /** The default page state: KitPortalScene at illustrationPage above a title and the one primary action. */
    @Composable
    private fun DefaultPage(copy: Copy) {
        Column(Modifier.fillMaxWidth().padding(horizontal = FlutterKitTokens.GUTTER.dp)) {
            Box(Modifier.fillMaxWidth()) {
                SceneIllustration(
                    KitPortalScene(),
                    Modifier.align(Alignment.CenterStart),
                    width = FlutterKitTokens.ILLUSTRATION_PAGE.dp,
                    ambient = true,
                )
            }
            Spacer(Modifier.height(FlutterKitTokens.SPACE5.dp))
            KitText(copy.title, KitTextRole.Title)
            Spacer(Modifier.height(FlutterKitTokens.SPACE2.dp))
            KitText(copy.body, tone = TextTone.Secondary)
            Spacer(Modifier.height(FlutterKitTokens.SPACE6.dp))
            // CrossAxisAlignment.stretch hands the button a tight width; KitMaxWidth then centres its 400 dp cap.
            Box(Modifier.fillMaxWidth(), propagateMinConstraints = true) { KitButton(copy.action, onClick = {}) }
        }
    }

    private val portalFolders: List<Pair<SceneArt, Int>> = listOf(KitPortalScene() to 100, KitFoldersOpenScene() to 100)

    private val setup: List<Pair<SceneArt, Int>> =
        listOf(
            SetupPhoneScene() to 130,
            SetupReadyScene() to 130,
            SetupUnpluggedScene() to 130,
            SetupStepsScene(SetupSceneStage.Download, done = 3, total = 5, fraction = .6f) to 170,
            SetupStepsScene(SetupSceneStage.Download, done = 3, total = 5, fraction = .6f, halted = SetupSceneHalt.Failed) to 170,
        )

    private val servers: List<Pair<SceneArt, Int>> =
        listOf(ServersLinkScene(ServersLinkState.Linking) to 170, ServersWelcomeScene() to 130)

    /** The three mirrorsInRtl scenes; the third, TeamDiscoverRelayScene (170), is order 30's and not drawn yet. */
    private val mirrored: List<Pair<SceneArt, Int>> =
        listOf(SetupStepsScene(SetupSceneStage.Start, done = 5, total = 6) to 190, ServersLinkScene(ServersLinkState.Idle) to 170)

    private val dark = listOf("dark")

    @Test fun kit_scenes_portal_folders() { FlutterGoldenGate.check("kit_scenes_portal_folders") { ContactSheet(portalFolders) } }

    @Test fun kit_scenes_setup() { FlutterGoldenGate.check("kit_scenes_setup") { ContactSheet(setup) } }

    @Test fun kit_scenes_servers() { FlutterGoldenGate.check("kit_scenes_servers") { ContactSheet(servers) } }

    @Test fun kit_scenes_mirrored_ar() { FlutterGoldenGate.check("kit_scenes_mirrored_ar", modes = dark) { ContactSheet(mirrored) } }

    @Test
    fun kit_scenes_mirrored_ar_1280x800() {
        FlutterGoldenGate.check("kit_scenes_mirrored_ar_1280x800", modes = dark) { ContactSheet(mirrored) }
    }

    @Test fun kit_scenes_default() { FlutterGoldenGate.check("kit_scenes_default") { DefaultPage(en) } }

    @Test fun kit_scenes_default_360x800() { FlutterGoldenGate.check("kit_scenes_default_360x800") { DefaultPage(en) } }

    @Test fun kit_scenes_default_800x1280() { FlutterGoldenGate.check("kit_scenes_default_800x1280") { DefaultPage(en) } }

    @Test fun kit_scenes_default_1280x800() { FlutterGoldenGate.check("kit_scenes_default_1280x800") { DefaultPage(en) } }

    @Test fun kit_scenes_default_1600x1000() { FlutterGoldenGate.check("kit_scenes_default_1600x1000") { DefaultPage(en) } }

    @Test fun kit_scenes_default_ar() { FlutterGoldenGate.check("kit_scenes_default_ar", modes = dark) { DefaultPage(ar) } }

    @Test
    fun kit_scenes_default_ar_1280x800() {
        FlutterGoldenGate.check("kit_scenes_default_ar_1280x800", modes = dark) { DefaultPage(ar) }
    }

    @Test fun kit_scenes_default_text2() { FlutterGoldenGate.check("kit_scenes_default_text2", modes = dark) { DefaultPage(en) } }

    @Test
    fun kit_scenes_default_text2_1280x800() {
        FlutterGoldenGate.check("kit_scenes_default_text2_1280x800", modes = dark) { DefaultPage(en) }
    }
}
