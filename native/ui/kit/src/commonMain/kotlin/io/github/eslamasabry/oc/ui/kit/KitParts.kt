package io.github.eslamasabry.oc.ui.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.foundation.KitTappable
import io.github.eslamasabry.oc.ui.kit.foundation.kitTappable
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenWidth
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHost
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens

/**
 * The slice-0 screen shape (title, optional Back, a column of content), kept so `:feature:chats` and the shell compile
 * unchanged. It is now the frame part: `frame.KitScreen` with a `KitTopBar`, reading width from medium up.
 *
 * Text: frame (the bar).
 */
@Composable
public fun KitScreen(
    title: String,
    backLabel: String? = null,
    onBack: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val tokens = LocalKitTokens.current
    io.github.eslamasabry.oc.ui.kit.frame.KitScreen(
        topBar = {
            KitTopBar(
                title = title,
                exit = if (onBack != null && backLabel != null) KitTopBarExit.Back else KitTopBarExit.None,
                exitLabel = backLabel,
                onExit = onBack,
            )
        },
        width = KitScreenWidth.Reading,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = tokens.gutter.dp),
            verticalArrangement = Arrangement.spacedBy(Space.S4.dp),
            content = content,
        )
    }
}
