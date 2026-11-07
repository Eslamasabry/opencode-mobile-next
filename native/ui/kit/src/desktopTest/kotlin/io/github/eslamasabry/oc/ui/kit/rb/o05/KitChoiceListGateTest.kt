package io.github.eslamasabry.oc.ui.kit.rb.o05

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.choice.KitChoice
import io.github.eslamasabry.oc.ui.kit.choice.KitChoiceList
import io.github.eslamasabry.oc.ui.kit.choice.KitChoiceListMulti
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.state.KitStateKind
import io.github.eslamasabry.oc.ui.kit.state.KitStateView
import kotlin.test.Test

/**
 * The scenes of test/goldens/kit/kit_choice_list_golden_test.dart the native part can draw with the Dart inputs:
 * loading, empty, a disabled choice and the multi list, each inside the scene's 16 dp rails (`_rails`).
 *
 * Not rendered yet (no native API, so no faithful scene): the choice sheet (`default*`, showKitChoiceSheet), the
 * "something else" answer (`other`), the receipt states (`sending`, `answered`, `answered_elsewhere`), a choice's row
 * menu (`pending_change`) and KitPickerRow (`picker_row`, `picker_disabled`).
 */
class KitChoiceListGateTest {
    private val deploy = listOf(
        KitChoice("staging", "Staging", supporting = "staging.example"),
        KitChoice("production", "Production", recommended = true),
        KitChoice("canary", "Canary", supporting = "5% of traffic"),
    )

    @Composable
    private fun Rails(content: @Composable () -> Unit) {
        Box(Modifier.padding(horizontal = 16.dp)) { content() } // F: test/goldens/kit/kit_choice_list_golden_test.dart:27
    }

    private val loading: @Composable () -> Unit = {
        Rails { KitChoiceList(choices = emptyList<KitChoice<String>>(), selected = null, semanticsLabel = "", loading = true, onSelect = {}) }
    }

    private val empty: @Composable () -> Unit = {
        Rails {
            KitChoiceList(
                choices = emptyList<KitChoice<String>>(),
                selected = null,
                semanticsLabel = "",
                empty = {
                    KitStateView(
                        kind = KitStateKind.Empty,
                        title = "No voices downloaded yet",
                        body = "Download a voice to hear replies read aloud.",
                        icon = KitIcons.mic,
                    )
                },
                onSelect = {},
            )
        }
    }

    private val choiceDisabled: @Composable () -> Unit = {
        Rails {
            KitChoiceList(
                choices = listOf(
                    KitChoice("staging", "Staging"),
                    KitChoice("production", "Production", enabled = KitEnabled.Disabled("Needs a release tag first")),
                    KitChoice("canary", "Canary"),
                ),
                selected = "staging",
                semanticsLabel = "",
                onSelect = {},
            )
        }
    }

    private val multi: @Composable () -> Unit = {
        Rails { KitChoiceListMulti(choices = deploy, selected = setOf("staging", "canary"), semanticsLabel = "", onChange = {}) }
    }

    @Test fun scenes() {
        FlutterGoldenGate.check("kit_choice_list_loading", content = loading)
        FlutterGoldenGate.check("kit_choice_list_empty", content = empty)
        FlutterGoldenGate.check("kit_choice_list_choice_disabled", content = choiceDisabled)
        FlutterGoldenGate.check("kit_choice_list_multi", content = multi)
    }

    @Test fun matrix() {
        FlutterGoldenGate.matrix("part-kit_choice_list", caps = mapOf("KitChoiceList" to "list", "KitChoiceRow" to "list")) {
            KitChoiceList(choices = deploy, selected = "production", semanticsLabel = "Where to deploy", onSelect = {})
        }
    }
}
