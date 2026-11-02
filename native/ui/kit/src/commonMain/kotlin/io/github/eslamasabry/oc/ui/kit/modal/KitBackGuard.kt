package io.github.eslamasabry.oc.ui.kit.modal

import androidx.compose.runtime.Composable

/** Page Back protection; enabled only while the page has something to dismiss or save. */
@Composable
public fun KitBackGuard(enabled: Boolean, onBack: () -> Unit) {
    KitPredictiveBack(enabled, onBack)
}
