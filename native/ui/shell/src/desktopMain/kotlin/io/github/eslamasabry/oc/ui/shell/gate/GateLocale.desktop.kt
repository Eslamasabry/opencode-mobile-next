package io.github.eslamasabry.oc.ui.shell.gate

import androidx.compose.runtime.Composable

@Composable
public actual fun ProvideGateLocale(localeTag: String?, content: @Composable () -> Unit) {
    content()
}
