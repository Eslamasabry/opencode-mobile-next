package io.github.eslamasabry.oc.ui.shell.gate

/**
 * The gate's in-app language (plan 05 §8.2): the gate screens read their words in [localeTag] (the `app-locale`
 * mirror `MainActivity` reads), not in the phone's language. Call it before `setContent`, and pass the same tag to
 * `KitTheme(localeTag = …)`, which sets the layout direction. Null = follow the system list (nothing changes).
 *
 * CMP resources read `Locale.current`, which is the process default on every platform, not a composition local
 * (measured in S2-GATEUI: a `LocalConfiguration` override leaves the words English). So this sets the process
 * default, the same thing the `LocaleHost` (03 §12.3, S21) does once `:engine:settings` runs; H1 then changes nothing.
 * Returns the previous default's tag so tests can put it back.
 */
public expect fun applyGateLocale(localeTag: String?): String
