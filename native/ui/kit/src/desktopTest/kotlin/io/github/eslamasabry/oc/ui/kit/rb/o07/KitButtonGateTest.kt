package io.github.eslamasabry.oc.ui.kit.rb.o07

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.foundation.KitPartGlyphs
import kotlin.test.Test

/** The copied scene of test/goldens/kit/kit_action_block_copied: a primary "Done" over a secondary "Copied" with a check. */
@OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
class KitButtonGateTest {
    private val copied: @Composable () -> Unit = {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            KitButton("Done", {}, Modifier.fillMaxWidth(), role = KitButtonRole.Primary)
            KitButton("Copied", {}, Modifier.fillMaxWidth(), role = KitButtonRole.Secondary, icon = KitPartGlyphs.Check)
        }
    }

    @Test fun kit_action_block_copied() { FlutterGoldenGate.check("kit_action_block_copied", content = copied) }
}
