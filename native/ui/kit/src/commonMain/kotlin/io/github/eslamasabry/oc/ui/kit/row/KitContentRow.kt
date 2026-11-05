package io.github.eslamasabry.oc.ui.kit.row

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens

/**
 * A noninteractive row with Flutter KitRow.below on its text rail (commands, previews and details).
 */
@Composable
public fun KitContentRow(
    title: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    below: @Composable () -> Unit,
) {
    Column(modifier.fillMaxWidth()) {
        KitRow(
            title,
            supporting = supporting,
            titleMaxLines = 2, // F: lib/ui/screens/host_management_screen.dart:131
            supportingMaxLines = 4, // F: lib/ui/screens/host_management_screen.dart:133
        ) // F: lib/ui/screens/host_management_screen.dart:131,133
        Box(
            Modifier.fillMaxWidth()
                .padding(horizontal = LocalKitTokens.current.gutter.dp, vertical = Space.S2.dp)
        ) {
            below()
        }
    }
}
