package io.github.eslamasabry.oc.ui.kit.rb.o09

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
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
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.rb.o06.FlutterCapture
import io.github.eslamasabry.oc.ui.kit.state.KitDetailsFold
import io.github.eslamasabry.oc.ui.kit.state.KitTechnicalValue
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import kotlin.test.Test

/** The `collapsed` scene of test/goldens/kit/kit_details_fold_golden_test.dart: the fold last on a surface1 panel. */
@OptIn(RedactorApi::class)
class KitDetailsFoldGateTest {
    private val values = listOf(
        KitTechnicalValue("Address", Redacted("100.64.0.3:4096"), spoken = "100.64.0.3, port 4096"),
        KitTechnicalValue("Working folder", Redacted("/home/dev/code/opencode-mobile/.worktrees/details-fold/lib/ui/kit")),
        KitTechnicalValue("Branch", Redacted("revamp/kit-KitDetailsFold")),
        KitTechnicalValue("Interaction id", Redacted("ses_7f3a9c21e4b0"), copyable = false),
    )

    @Composable private fun panel(fold: @Composable () -> Unit) {
        Column(Modifier.padding(16.dp)) {
            Column(Modifier.fillMaxWidth().background(LocalKitColors.current.surface1, RoundedCornerShape(18.dp)).padding(16.dp)) {
                KitText("Couldn't reach the server", role = TypeRole.RowTitle)
                Spacer(Modifier.height(4.dp))
                KitText("Details below help when you report it.", role = TypeRole.Secondary, tone = TextTone.Secondary)
                Spacer(Modifier.height(8.dp))
                fold()
            }
        }
    }

    @Test fun kit_details_fold_collapsed() {
        FlutterGoldenGate.check(wrap = { FlutterCapture(it) }, name = "kit_details_fold_collapsed", modes = listOf("dark", "light")) {
            panel { KitDetailsFold(label = "Details", copyLabel = "Copy all", values = values) }
        }
    }
}
