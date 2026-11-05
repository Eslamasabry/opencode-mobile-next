package io.github.eslamasabry.oc.ui.kitchat.rb.o17

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kitchat.image.KitPictureContent
import io.github.eslamasabry.oc.ui.kitchat.image.KitPictureView
import io.github.eslamasabry.oc.ui.kitchat.markdown.KitMarkdownActions
import io.github.eslamasabry.oc.ui.kitchat.markdown.KitMdBlock
import io.github.eslamasabry.oc.ui.kitchat.markdown.KitPicture
import io.github.eslamasabry.oc.ui.kitchat.markdown.KitPictureTiles
import io.github.eslamasabry.oc.ui.kitchat.stream.KitPromptLiftHost
import io.github.eslamasabry.oc.ui.kitchat.stream.RevealRegistry
import io.github.eslamasabry.oc.ui.kitchat.stream.kitFreshFade
import io.github.eslamasabry.oc.ui.kitchat.stream.kitPromptLift
import io.github.eslamasabry.oc.ui.kitchat.tool.KitStepSignal
import io.github.eslamasabry.oc.ui.kitchat.tool.KitToolKind
import io.github.eslamasabry.oc.ui.kitchat.tool.KitToolNode
import io.github.eslamasabry.oc.ui.kitchat.tool.KitToolStatus
import io.github.eslamasabry.oc.ui.kitchat.tool.KitToolView
import io.github.eslamasabry.oc.ui.kitchat.tool.kitDashedRules
import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The native-only matrix of the order's parts that have no Flutter golden of their own. */
class PartsMatrixGateTest {
    private val wrap: @Composable (@Composable () -> Unit) -> Unit = { androidx.compose.runtime.CompositionLocalProvider(io.github.eslamasabry.oc.ui.kitchat.LocalKitRedactor provides io.github.eslamasabry.oc.core.security.Redactor()) { it() } }

    @Test fun markdown_fresh() {
        FlutterGoldenGate.matrix("part-kit_markdown_fresh") {
            val state = remember { RevealRegistry().stateFor("k", "g", live = false, length = 24) }
            KitText("Words that fade in as they land", Modifier.kitFreshFade({ null }, state))
        }
    }

    @Test fun markdown_image() {
        FlutterGoldenGate.matrix("part-kit_markdown_image") {
            Column(Modifier.padding(16.dp)) {
                KitPictureTiles(
                    KitMdBlock.Pictures("p", persistentListOf(KitPicture("Screenshot", path = "docs/a.png"), KitPicture("Diagram", path = "docs/b.png"))),
                    KitMarkdownActions(onLink = {}),
                )
                KitPictureView(KitPictureContent.Unavailable, "Screenshot", "Loading", "The picture could not be shown")
            }
        }
    }

    @Test fun message_lift() {
        FlutterGoldenGate.matrix("part-kit_message_lift") {
            KitPromptLiftHost { KitText("Fix the login bug", Modifier.kitPromptLift(1, "Fix the login bug")) }
        }
    }

    @Test fun step_node() {
        val view = KitToolView("a", KitToolKind.Shell, "Shell", null, "flutter test", KitToolStatus.Running, null, null, null, "")
        FlutterGoldenGate.matrix("part-kit_step_node", wrap = wrap) { KitToolNode(view, 20.dp) }
    }

    @Test fun step_signal() {
        FlutterGoldenGate.matrix("part-kit_step_signal") {
            Column(Modifier.padding(16.dp).kitDashedRules(io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors.current.text3, 24.dp)) {
                KitText("Ran 3 commands", role = TypeRole.Mono)
            }
        }
    }

    @Test fun step_signal_rules() {
        assertTrue(KitStepSignal.live(KitToolStatus.Running))
        assertEquals(listOf(0f..3f, 6f..9f), KitStepSignal.dashes(10f, 0f, false).take(2))
        assertEquals(0f, KitStepSignal.dashes(100f, 40f, true).first().start)
    }
}
