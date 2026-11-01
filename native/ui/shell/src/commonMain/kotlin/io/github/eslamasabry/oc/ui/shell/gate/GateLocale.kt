package io.github.eslamasabry.oc.ui.shell.gate

import androidx.compose.runtime.Composable

/**
 * The gate's in-app language (plan 05 §8.2): the gate screens read their words in [localeTag] (the `app-locale`
 * mirror `MainActivity` reads before `setContent`), not in the phone's language. Null = follow the system list.
 * Pair it with `KitTheme(localeTag = …)`, which sets the layout direction. Android overrides the configuration the
 * resource lookup reads; desktop already reads the JVM default the `LocaleHost` sets, so it passes through.
 */
@Composable
public expect fun ProvideGateLocale(localeTag: String?, content: @Composable () -> Unit)
