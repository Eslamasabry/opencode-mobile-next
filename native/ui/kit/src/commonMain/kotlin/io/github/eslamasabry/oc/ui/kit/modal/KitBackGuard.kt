package io.github.eslamasabry.oc.ui.kit.modal

import androidx.compose.runtime.Composable

/**
 * Page Back protection; enabled only while the page has something to dismiss or save. It answers at the page's
 * (default) priority: an open sheet, menu, dialog or picker on top always answers Back first ([KitPredictiveBack]).
 */
@Composable
public fun KitBackGuard(enabled: Boolean, onBack: () -> Unit) {
    KitPredictiveBack(enabled, overlay = false, onBack = onBack)
}
