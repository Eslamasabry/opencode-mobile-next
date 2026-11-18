package io.github.eslamasabry.oc.ui.kit.foundation

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged

/** Reports the part's laid-out height in pixels whenever it changes (what a page reads to give a card its room). */
public fun Modifier.kitOnHeightChanged(onHeight: (Int) -> Unit): Modifier = onSizeChanged { onHeight(it.height) }
