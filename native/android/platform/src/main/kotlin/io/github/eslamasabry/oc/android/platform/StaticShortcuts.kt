package io.github.eslamasabry.oc.android.platform

import io.github.eslamasabry.oc.engine.entry.EntryExtras

/**
 * The static launcher shortcuts (plan 11 §14.4, 07 §7.4 and §7.13 row "Static shortcuts", ANDROID-PLATFORM-29).
 *
 * This module ships the two shortcut files (`res/xml/shortcuts.xml` for the stable package, `res/xml/shortcuts_native.xml`
 * for the native preview package) with their labels and icons; they merge into the app. `:androidApp` adds only the
 * `android.app.shortcuts` meta-data on `.MainActivity`, pointing at the manifest placeholder [MANIFEST_PLACEHOLDER] set
 * to [resourceFor] (static shortcut XML cannot use placeholders itself, so each package has its own file).
 *
 * A shortcut tap is an explicit `MAIN` intent with extra [EXTRA]; it reaches `EntryRouter` through [EntryCapture] as an
 * **untrusted** `Launch` request: `connect` opens Servers, `new_task` opens the new-conversation sheet with **Start**.
 * Nothing connects, creates a session or sends a prompt on its own.
 */
public object StaticShortcuts {
    /** Frozen FQCN (ADR §9.9); the same class name in both packages. */
    public const val MAIN_ACTIVITY: String = "io.github.eslamasabry.opencode_mobile.MainActivity"
    public const val STABLE_PACKAGE: String = EntryExtras.APP_PACKAGE
    public const val PREVIEW_PACKAGE: String = "io.github.eslamasabry.opencode_mobile.native"
    public const val EXTRA: String = EntryExtras.SHORTCUT
    public const val MANIFEST_PLACEHOLDER: String = "appShortcuts"

    /** Shortcut ids, in declaration order; each id is also its `oc.shortcut` value. Names unchanged from Flutter. */
    public val ids: List<String> = listOf("connect", "new_task")

    /** The value of the `${appShortcuts}` manifest placeholder for an application id. */
    public fun resourceFor(applicationId: String): String =
        if (applicationId == PREVIEW_PACKAGE) "@xml/shortcuts_native" else "@xml/shortcuts"
}
