package io.github.eslamasabry.oc.android.platform

import android.content.ComponentName
import android.content.Intent
import androidx.core.content.IntentCompat
import io.github.eslamasabry.oc.engine.entry.EntryExtras
import io.github.eslamasabry.oc.engine.entry.EntryIntentParser
import io.github.eslamasabry.oc.engine.entry.EntryOrigin
import io.github.eslamasabry.oc.engine.entry.EntryRequest
import io.github.eslamasabry.oc.engine.entry.IntentFields
import kotlin.time.Clock

/**
 * 07 §7.1: the only code that reads an entry `Intent` (`MainActivity` and the non-exported `.InternalEntryActivity`).
 * It copies plain values out of the intent and hands them to the common [EntryIntentParser], which whitelists and
 * bounds every field (the Flutter-era `SessionLinkIngress`, `LaunchShortcutChannel` and `ScheduledPromptPolicy` rules,
 * table-tested on the desktop). Nothing is logged with its value; a hostile Bundle that fails to unparcel reads as
 * "no extras", never as a crash.
 *
 * Usage (07 §7.1): `read` → `strip` → `setIntent` → `entryRouter.submit` each request. A restored activity
 * (`savedInstanceState != null` or `FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY`) does not call `read` at all.
 */
public object EntryIntentReader {
    /**
     * [origin] is [EntryOrigin.Internal] only when the receiving component is the non-exported
     * `.InternalEntryActivity` (Astra M2, 16 §1.8.1); `MainActivity` always passes [EntryOrigin.Untrusted].
     */
    public fun read(intent: Intent?, origin: EntryOrigin, clock: Clock): List<EntryRequest> {
        if (intent == null) return emptyList()
        return EntryIntentParser.parse(fields(intent), origin, clock.now())
    }

    /** Removes our extras, sets action MAIN and clears data/type, so a configuration change cannot replay anything. */
    public fun strip(intent: Intent?) {
        if (intent == null) return
        guarded(Unit) { EntryExtras.owned.forEach(intent::removeExtra) }
        if (intent.action != null && intent.action != Intent.ACTION_MAIN) intent.action = Intent.ACTION_MAIN
        intent.data = null // also clears the type
    }

    internal fun fields(intent: Intent): IntentFields =
        IntentFields(
            action = intent.action,
            type = intent.type,
            data = intent.dataString,
            strings = guarded(emptyMap()) { stringExtras(intent) },
            widgetReply = guarded(false) { intent.getBooleanExtra(EntryExtras.WIDGET_REPLY, false) },
            component =
                guarded(null) {
                    IntentCompat.getParcelableExtra(intent, Intent.EXTRA_COMPONENT_NAME, ComponentName::class.java)
                        ?.let { it.packageName to it.className }
                },
        )

    /** Only the extras the entry contract owns, and only as strings (a non-string value reads as absent). */
    private fun stringExtras(intent: Intent): Map<String, String?> =
        EntryExtras.owned
            .filter { it != EntryExtras.WIDGET_REPLY && it != EntryExtras.COMPONENT_NAME && intent.hasExtra(it) }
            .associateWith { intent.getStringExtra(it) }

    /** Unparcelling a foreign Bundle can throw (BadParcelableException, ClassNotFound wrapped in RuntimeException). */
    private inline fun <T> guarded(fallback: T, block: () -> T): T =
        try {
            block()
        } catch (_: RuntimeException) {
            fallback
        }
}
