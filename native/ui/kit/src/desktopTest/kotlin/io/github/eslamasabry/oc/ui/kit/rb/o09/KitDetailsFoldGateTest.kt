package io.github.eslamasabry.oc.ui.kit.rb.o09

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.common.Redacted
import io.github.eslamasabry.oc.core.common.RedactorApi
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate.Frame
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHost
import io.github.eslamasabry.oc.ui.kit.rb.o06.FlutterCapture
import io.github.eslamasabry.oc.ui.kit.state.KitDetailsFold
import io.github.eslamasabry.oc.ui.kit.state.KitTechnicalDetailsSheet
import io.github.eslamasabry.oc.ui.kit.state.KitTechnicalValue
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitWindow
import kotlin.test.Test

/**
 * test/goldens/kit/kit_details_fold_golden_test.dart: every scene of the fold on a `surface1` panel (`_openOnPanel`,
 * a page with 16 dp around a panel of 18 dp corners and 16 dp inside), the fold in a sheet, and the raw-error sheet.
 */
@OptIn(RedactorApi::class)
class KitDetailsFoldGateTest {
    private val values = listOf(
        KitTechnicalValue("Address", Redacted("100.64.0.3:4096"), spoken = "100.64.0.3, port 4096"),
        KitTechnicalValue("Working folder", Redacted("/home/dev/code/opencode-mobile/.worktrees/details-fold/lib/ui/kit")),
        KitTechnicalValue("Branch", Redacted("revamp/kit-KitDetailsFold")),
        KitTechnicalValue("Interaction id", Redacted("ses_7f3a9c21e4b0"), copyable = false),
    )

    private val error = (
        listOf(
            "POST http://127.0.0.1:4096/session/ses_7f3a9c21e4b0/message",
            "HTTP 502 Bad Gateway",
            "x-request-id: 3f1c9a7e-5d2b-4c8e-9a61-0b7d2e4f8c13",
            "Authorization: Bearer FAKETOKENFAKETOKEN0123456789",
        ) + (1..26).map { "  at Session.prompt (src/session/index.ts:${100 + it}:${it + 7})" }
        ).joinToString("\n")

    /**
     * `_openOnPanel`: the page (16 dp all round) and the surface1 panel that hosts the fold last. The gallery frame
     * already pads 16 dp top and bottom (kit_gallery.dart:766); the page is the whole window wide, as Flutter's pushed
     * route is, so it reaches past the frame's 720 dp body on a wide window.
     */
    @Composable private fun panel(fold: @Composable () -> Unit) {
        Box(Modifier.requiredWidth(LocalKitWindow.current.widthDp.dp).padding(horizontal = 16.dp)) {
            Column(Modifier.fillMaxWidth().background(LocalKitColors.current.surface1, RoundedCornerShape(18.dp)).padding(16.dp)) {
                KitText("Couldn't reach the server", role = TypeRole.RowTitle)
                Spacer(Modifier.height(4.dp))
                KitText("Details below help when you report it.", role = TypeRole.Secondary)
                Spacer(Modifier.height(8.dp))
                fold()
            }
        }
    }

    /** The Dart scene's Column(crossAxisAlignment: stretch) forces the fold to the panel's width; its cap centres it. */
    private val stretch = Modifier.fillMaxWidth()

    private fun shot(name: String, fold: @Composable () -> Unit) {
        FlutterGoldenGate.check(name, wrap = { FlutterCapture(it) }) { panel(fold) }
    }

    private val openValues: @Composable () -> Unit = {
        KitDetailsFold(
            label = "Details",
            copyLabel = "Copy all",
            modifier = stretch,
            initiallyExpanded = true,
            notes = listOf("Check that the server is running, then try again."),
            values = values,
        )
    }

    @Test fun kit_details_fold_collapsed() = shot("kit_details_fold_collapsed") {
        KitDetailsFold(label = "Details", copyLabel = "Copy all", stretch, values = values)
    }

    @Test fun kit_details_fold_empty() = shot("kit_details_fold_empty") {
        KitDetailsFold(label = "Details", copyLabel = "Copy all", stretch)
    }

    @Test fun kit_details_fold_open_values() = shot("kit_details_fold_open_values", openValues)
    @Test fun kit_details_fold_open_values_1280x800() = shot("kit_details_fold_open_values_1280x800", openValues)
    @Test fun kit_details_fold_open_values_text2() = shot("kit_details_fold_open_values_text2", openValues)
    @Test fun kit_details_fold_open_values_text2_1280x800() = shot("kit_details_fold_open_values_text2_1280x800", openValues)

    @Test fun kit_details_fold_open_text() = shot("kit_details_fold_open_text") {
        KitDetailsFold(label = "Details", copyLabel = "Copy all", stretch, initiallyExpanded = true, text = Redacted(error))
    }

    @Test fun kit_details_fold_open_child() = shot("kit_details_fold_open_child") {
        KitDetailsFold(
            label = "Details",
            copyLabel = "Copy all",
            modifier = stretch,
            initiallyExpanded = true,
            values = listOf(KitTechnicalValue("Process", Redacted("opencode serve · pid 4127"))),
            child = {
                Box(Modifier.fillMaxWidth().background(LocalKitColors.current.ground, RoundedCornerShape(10.dp)).padding(12.dp)) {
                    KitText(
                        "12:01:07 listening on 127.0.0.1:4096\n12:01:09 GET /session 200 4 ms\n12:01:12 SSE client connected",
                        role = TypeRole.Mono,
                    )
                }
            },
        )
    }

    @Test fun kit_details_fold_open_values_in_sheet() {
        FlutterGoldenGate.check("kit_details_fold_open_values_in_sheet", Frame.Screen, wrap = { FlutterCapture(it) }) {
            KitSheetHost {
                KitSheet(visible = true, onDismissRequest = {}, title = "Server") {
                    KitText("Connected over Tailscale.", role = TypeRole.Body, tone = TextTone.Secondary)
                    Spacer(Modifier.height(8.dp))
                    KitDetailsFold(label = "Details", copyLabel = "Copy all", stretch, initiallyExpanded = true, values = values)
                }
            }
        }
    }

    @Test fun kit_details_fold_technical_details_sheet() {
        FlutterGoldenGate.check("kit_details_fold_technical_details_sheet", Frame.Screen, wrap = { FlutterCapture(it) }) {
            KitSheetHost {
                KitTechnicalDetailsSheet(
                    visible = true,
                    onDismissRequest = {},
                    title = "Couldn't send",
                    text = Redacted(error),
                    values = listOf(
                        KitTechnicalValue("Address", Redacted("100.64.0.3:4096")),
                        KitTechnicalValue("Status", Redacted("502")),
                    ),
                )
            }
        }
    }

    @Test fun matrix() {
        FlutterGoldenGate.matrix(
            "part-kit_details_fold",
            caps = mapOf("KitDetailsFold" to "reading", "showKitTechnicalDetails" to "sheet"),
            wrap = { FlutterCapture(it) },
            content = openValues,
        )
    }
}
