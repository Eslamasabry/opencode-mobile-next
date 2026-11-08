package io.github.eslamasabry.oc.feature.settings.presentation

/**
 * "Offer crash reports" (Flutter 2.1.5 `CrashReportOffer.enabled`, `oc.crashReports.offer`): whether the app offers a
 * report after it closed unexpectedly. It never sends anything by itself. On unless turned off.
 */
/**
 * About › Developer: "Memory (preview)" (Flutter 2.1.5 `FactsBookFlag`, `oc.dev.factsbook`): whether FactsBook's
 * Memory section shows. Off by default.
 */
public interface MemoryPreviewSetting {
    public fun enabled(): Boolean

    /** False when the answer could not be saved. */
    public suspend fun setEnabled(on: Boolean): Boolean

    /** This phone can keep the memory itself (the built-in Linux): "Use memory on this phone" shows. */
    public val phoneNode: Boolean
}

public interface CrashOfferSetting {
    public fun enabled(): Boolean

    /** False when the answer could not be saved. */
    public suspend fun setEnabled(on: Boolean): Boolean
}
