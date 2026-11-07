package io.github.eslamasabry.oc.ui.kitchat.rb.o08

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.ui.design.generated.FlutterKitTokens
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.platform.KitPlatform
import io.github.eslamasabry.oc.ui.kit.platform.LocalKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kitchat.LocalKitRedactor
import io.github.eslamasabry.oc.ui.kitchat.code.CODE_COPY_TAG
import io.github.eslamasabry.oc.ui.kitchat.code.CodeLanguage
import io.github.eslamasabry.oc.ui.kitchat.code.KitCodeBlock
import io.github.eslamasabry.oc.ui.kitchat.code.KitCodeFill
import io.github.eslamasabry.oc.ui.kitchat.code.KitCodeKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test

/**
 * The scenes of test/goldens/kit/kit_code_block_golden_test.dart (412x915 and 1280x800, light and dark, code_header
 * at text 2.0). Each scene is a page body (`Scaffold(body: scene)`, so [FlutterGoldenGate.Frame.Screen]): the block on
 * the ground at the gutter, top start (`_onGround`), or inside a surface2 sheet body (`_inSheet`). Flutter's test has
 * no mouse, so the scroll thumb follows a touch platform here (`KitLayout.finePointer` is false in the golden).
 */
@OptIn(ExperimentalTestApi::class)
class KitCodeBlockGateTest {
    private val wrap: @Composable (@Composable () -> Unit) -> Unit = { content ->
        val base = LocalKitPlatform.current
        val touch = remember(base) {
            object : KitPlatform by base {
                override val signals = MutableStateFlow(base.signals.value.copy(finePointer = false))
            }
        }
        CompositionLocalProvider(LocalKitPlatform provides touch, LocalKitRedactor provides Redactor()) { content() }
    }

    /** `_onGround`: the page ground, the gutter all round, the block at the top start. */
    @Composable
    private fun OnGround(content: @Composable () -> Unit) {
        Box(Modifier.fillMaxSize().padding(FlutterKitTokens.GUTTER.dp)) {
            Box(Modifier.wrapContentSize(Alignment.TopStart)) { content() }
        }
    }

    /** `_inSheet`: a surface2 sheet body with the panel radius and space4 inside, at the gutter. */
    @Composable
    private fun InSheet(content: @Composable () -> Unit) {
        Box(Modifier.fillMaxSize().padding(FlutterKitTokens.GUTTER.dp)) {
            Box(
                Modifier.wrapContentSize(Alignment.TopStart)
                    .background(LocalKitColors.current.surface2, RoundedCornerShape(FlutterKitTokens.PANEL_CORNER_RADIUS.dp))
                    .padding(FlutterKitTokens.SPACE4.dp),
            ) { content() }
        }
    }

    private val sampleDart =
        "class ConnectionController extends ChangeNotifier {\n" +
            "  Future<void> connect(Profile profile) async {\n" +
            "    final client = await _gateway.open(profile);\n" +
            "    _client = client;\n" +
            "    notifyListeners();\n" +
            "  }\n" +
            "}"

    private val adbCommands =
        "adb shell dumpsys deviceidle whitelist +com.termux\n" +
            "adb shell cmd appops set com.termux RUN_ANY_IN_BACKGROUND allow\n" +
            "adb shell device_config put activity_manager max_phantom_processes 2147483647"

    private val codeHeader: @Composable () -> Unit = {
        InSheet {
            KitCodeBlock(
                sampleDart, KitCodeKind.Code, language = CodeLanguage.of("dart"), onCopy = {},
                fileName = "lib/state/connection.dart", added = 4, removed = 1,
            )
        }
    }
    private val command: @Composable () -> Unit = {
        OnGround {
            KitCodeBlock(
                "curl -sS -u opencode:<your password> https://100.64.0.4:4097/api/session/list | jq .",
                KitCodeKind.Command, onCopy = {},
            )
        }
    }
    private val outputCapped: @Composable () -> Unit = {
        OnGround {
            KitCodeBlock((1..40).joinToString("\n") { "line $it: ok" }, KitCodeKind.Output, maxLines = 12, onCopy = {})
        }
    }
    private val wrapped: @Composable () -> Unit = {
        OnGround {
            KitCodeBlock(
                "final message = \"This line is written long on purpose so the gallery shows word wrap instead of a " +
                    "sideways scroller.\";",
                KitCodeKind.Code, wrap = true, onCopy = {},
            )
        }
    }
    private val copied: @Composable () -> Unit = {
        OnGround { KitCodeBlock("flutter test -j 1 test/kit/a_test.dart", KitCodeKind.Code, onCopy = {}) }
    }
    private val fillFind: @Composable () -> Unit = {
        OnGround {
            KitCodeFill(
                sampleDart, Modifier.height(220.dp), language = CodeLanguage.of("dart"),
                marks = listOf(6 until 26, 60 until 67), activeMark = 1,
            )
        }
    }
    private val empty: @Composable () -> Unit = { OnGround { KitCodeBlock("", KitCodeKind.Code, onCopy = {}) } }
    private val commandShort: @Composable () -> Unit = {
        OnGround { KitCodeBlock("flutter run", KitCodeKind.Command, onCopy = {}) }
    }
    private val commandsLabelled: @Composable () -> Unit = {
        InSheet { KitCodeBlock(adbCommands, KitCodeKind.Command, onCopy = {}, copyWords = "Copy commands") }
    }
    private val commandWrapped: @Composable () -> Unit = {
        InSheet { KitCodeBlock(adbCommands, KitCodeKind.Command, wrap = true, onCopy = {}, copyWords = "Copy commands") }
    }

    private fun shot(name: String, content: @Composable () -> Unit, act: (androidx.compose.ui.test.ComposeUiTest.() -> Unit)? = null) {
        FlutterGoldenGate.check(name, frame = FlutterGoldenGate.Frame.Screen, wrap = wrap, act = act, content = content)
    }

    /** Flutter's `tester.tap` is a touch: no hover, no tooltip. */
    private val tapCopy: androidx.compose.ui.test.ComposeUiTest.() -> Unit = {
        onNodeWithTag(CODE_COPY_TAG).performTouchInput { click() }
    }

    @Test fun kit_code_block_code_header() = shot("kit_code_block_code_header", codeHeader)
    @Test fun kit_code_block_code_header_1280x800() = shot("kit_code_block_code_header_1280x800", codeHeader)
    @Test fun kit_code_block_code_header_text2() = shot("kit_code_block_code_header_text2", codeHeader)
    @Test fun kit_code_block_code_header_text2_1280x800() = shot("kit_code_block_code_header_text2_1280x800", codeHeader)
    @Test fun kit_code_block_command() = shot("kit_code_block_command", command)
    @Test fun kit_code_block_command_1280x800() = shot("kit_code_block_command_1280x800", command)
    @Test fun kit_code_block_command_short() = shot("kit_code_block_command_short", commandShort)
    @Test fun kit_code_block_command_short_1280x800() = shot("kit_code_block_command_short_1280x800", commandShort)
    @Test fun kit_code_block_command_wrapped() = shot("kit_code_block_command_wrapped", commandWrapped)
    @Test fun kit_code_block_command_wrapped_1280x800() = shot("kit_code_block_command_wrapped_1280x800", commandWrapped)
    @Test fun kit_code_block_commands_labelled() = shot("kit_code_block_commands_labelled", commandsLabelled)
    @Test fun kit_code_block_commands_labelled_1280x800() = shot("kit_code_block_commands_labelled_1280x800", commandsLabelled)
    @Test fun kit_code_block_copied() = shot("kit_code_block_copied", copied, tapCopy)
    @Test fun kit_code_block_copied_1280x800() = shot("kit_code_block_copied_1280x800", copied, tapCopy)
    @Test fun kit_code_block_empty() = shot("kit_code_block_empty", empty)
    @Test fun kit_code_block_empty_1280x800() = shot("kit_code_block_empty_1280x800", empty)
    @Test fun kit_code_block_fill_find() = shot("kit_code_block_fill_find", fillFind)
    @Test fun kit_code_block_fill_find_1280x800() = shot("kit_code_block_fill_find_1280x800", fillFind)
    @Test fun kit_code_block_output_capped() = shot("kit_code_block_output_capped", outputCapped)
    @Test fun kit_code_block_output_capped_1280x800() = shot("kit_code_block_output_capped_1280x800", outputCapped)
    @Test fun kit_code_block_wrapped() = shot("kit_code_block_wrapped", wrapped)
    @Test fun kit_code_block_wrapped_1280x800() = shot("kit_code_block_wrapped_1280x800", wrapped)

    @Test
    fun matrix() {
        FlutterGoldenGate.matrix(
            "part-kit_code_block",
            caps = mapOf("KitCodeBlock" to "list"),
            wrap = wrap,
        ) {
            KitCodeBlock(
                sampleDart, KitCodeKind.Code, language = CodeLanguage.of("dart"), onCopy = {},
                fileName = "lib/state/connection.dart", added = 4, removed = 1,
            )
        }
    }
}
