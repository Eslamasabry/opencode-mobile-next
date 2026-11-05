package io.github.eslamasabry.oc.ui.kit.scanner

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.state.*
import io.github.eslamasabry.oc.ui.words.res.*
import kotlin.time.TimeMark

/** Scanner entry point retained for existing callers; page states share one kit layout. */
@Composable
public fun KitScannerState(
    kind: KitStateKind,
    title: String,
    modifier: Modifier = Modifier,
    body: String? = null,
    icon: ImageVector,
    progress: KitProgress? = null,
    primary: KitAction? = null,
    secondary: KitAction? = null,
    since: TimeMark? = null,
    onSlow: List<KitAction> = emptyList(),
    prerequisite: Boolean = false,
) {
    KitPageState(
        kind,
        title,
        modifier,
        body,
        icon,
        progress,
        primary,
        secondary,
        since,
        onSlow,
        prerequisite,
    )
}
