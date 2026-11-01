package io.github.eslamasabry.oc.android.widgets

import android.content.Context
import io.github.eslamasabry.opencode_mobile.SessionsWidgetProvider
import io.github.eslamasabry.opencode_mobile.UsageWidgetProvider
import io.github.eslamasabry.opencode_mobile.WidgetV2Bridge

/** Why every widget is redrawn (plan 11 §11.3 `WidgetSurface.redrawAll`). */
public enum class RedrawReason { Start, Locale, Style, PackageReplaced, ForceStopped, Boot }

public enum class PublishResult { Unchanged, Written, Refused }

public enum class ClearOutcome { NothingToClear, Cleared, Failed }

/**
 * The Android half of plan 11 §11.3's `WidgetSurface` (the interface itself lands in `:engine:attention` with
 * S4-ATTNE, which then adapts this class to it). The snapshot builder hands over the bytes it built; this class owns
 * the prefs keys the copied renderers read (the Flutter file and `flutter.` prefix, adopted in place by the migration,
 * plan 05) and the redraw fan-out. Calls are serialized on this object; every write is a `commit()`.
 */
public class AndroidWidgetSurface(
    context: Context,
    private val redraw: (Context) -> Unit = ::redrawEveryWidget,
) {
    private val app = context.applicationContext
    private val prefs get() = app.getSharedPreferences(WidgetV2Bridge.PREFS, Context.MODE_PRIVATE)

    /**
     * Writes `oc.widgetTarget.<profile>` first, then `oc.widgetSessions`, and redraws. Bytes equal to the stored ones
     * are neither rewritten nor redrawn (B-ANDR-117). [targetJson] null leaves the target untouched.
     */
    @Synchronized
    public fun publish(snapshotJson: String, profileId: String, targetJson: String?): PublishResult {
        if (profileId.isBlank()) return PublishResult.Refused
        val targetKey = TARGET_PREFIX + profileId
        val targetChanged = targetJson != null && prefs.getString(targetKey, null) != targetJson
        val snapshotChanged = prefs.getString(WidgetV2Bridge.SNAPSHOT_KEY, null) != snapshotJson
        if (!targetChanged && !snapshotChanged) return PublishResult.Unchanged
        if (targetChanged && !prefs.edit().putString(targetKey, targetJson).commit()) return PublishResult.Refused
        if (snapshotChanged && !prefs.edit().putString(WidgetV2Bridge.SNAPSHOT_KEY, snapshotJson).commit()) {
            return PublishResult.Refused
        }
        redraw(app)
        return PublishResult.Written
    }

    /** Every provider in the registry lists, then Status (plan 11 §11.2). */
    @Synchronized
    public fun redrawAll(@Suppress("UNUSED_PARAMETER") reason: RedrawReason) {
        redraw(app)
    }

    /** The live link ended: `connected = false`, titles kept, "last seen" counts from now. */
    @Synchronized
    public fun invalidateConnection() {
        WidgetV2Bridge.invalidateConnection(app)
    }

    /** Profile deletion: style, claims, target and (when it is that profile's) the snapshot; then redraw. */
    @Synchronized
    public fun clearProfile(profileId: String): ClearOutcome {
        if (profileId.isBlank()) return ClearOutcome.NothingToClear
        val owned = listOf(STYLE_PREFIX, ACTIONS_PREFIX, TARGET_PREFIX).any { prefs.contains(it + profileId) } ||
            WidgetV2Bridge.snapshot(app)?.optString("profileID") == profileId
        if (!owned) return ClearOutcome.NothingToClear
        return if (WidgetV2Bridge.clearProfile(app, profileId)) ClearOutcome.Cleared else ClearOutcome.Failed
    }

    public companion object {
        internal const val TARGET_PREFIX: String = "flutter.oc.widgetTarget."
        internal const val STYLE_PREFIX: String = "flutter.oc.widgetStyle."
        internal const val ACTIONS_PREFIX: String = "flutter.oc.widgetActions."

        /** Sessions entries then Status; usage entries then Status (the copied fan-out helpers). */
        public fun redrawEveryWidget(context: Context) {
            SessionsWidgetProvider.refreshAll(context)
            UsageWidgetProvider.refreshAll(context)
        }
    }
}
