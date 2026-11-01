package io.github.eslamasabry.oc.android.widgets

import android.content.Context
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.opencode_mobile.SessionsWidgetProvider
import io.github.eslamasabry.opencode_mobile.UsageWidgetProvider
import io.github.eslamasabry.opencode_mobile.WidgetV2Bridge
import kotlinx.coroutines.sync.withLock

public enum class RedrawReason {
    Start,
    Locale,
    Style,
    PackageReplaced,
    ForceStopped,
    Boot,
}

public enum class PublishResult {
    Unchanged,
    Written,
    Refused,
}

public enum class ClearOutcome {
    NothingToClear,
    Cleared,
    Failed,
}

/** Storage is graph-owned and shared with admission, readers and profile deletion. */
public class AndroidWidgetSurface(
    context: Context,
    private val persistence: WidgetPersistence,
    private val redraw: (Context) -> Unit = ::redrawEveryWidget,
) {
    private val app = context.applicationContext

    /**
     * Target is acknowledged before the public snapshot. Both writes use the profile's closing
     * fence.
     */
    public suspend fun publish(
        snapshotJson: String,
        profileId: String,
        targetJson: String?,
    ): PublishResult =
        persistence.publications.withLock { publishLocked(snapshotJson, profileId, targetJson) }

    private suspend fun publishLocked(
        snapshotJson: String,
        profileId: String,
        targetJson: String?,
    ): PublishResult {
        if (profileId.isBlank()) return PublishResult.Refused
        val store = persistence.store
        val lane = persistence.lanes.forProfile(ProfileId(profileId))
        val targetKey = TARGET_PREFIX + profileId
        val targetChanged = targetJson != null && persistence.string(targetKey) != targetJson
        val snapshotChanged = persistence.string(WidgetPersistence.SNAPSHOT) != snapshotJson
        if (!targetChanged && !snapshotChanged) return PublishResult.Unchanged
        if (
            targetChanged &&
                store.commit(lane) { putString(targetKey, targetJson) } is Outcome.Failed
        )
            return PublishResult.Refused
        if (
            snapshotChanged &&
                store.commit(lane) { putString(WidgetPersistence.SNAPSHOT, snapshotJson) } is
                    Outcome.Failed
        )
            return PublishResult.Refused
        redraw(app)
        return PublishResult.Written
    }

    public fun redrawAll(@Suppress("UNUSED_PARAMETER") reason: RedrawReason) {
        redraw(app)
    }

    public suspend fun invalidateConnection() {
        WidgetV2Bridge.invalidateConnection(app, persistence)
    }

    /** Also exposed as WidgetProfileCleaner for the existing deletion transaction. */
    public suspend fun clearProfile(profileId: String): ClearOutcome {
        if (profileId.isBlank()) return ClearOutcome.NothingToClear
        val owned =
            listOf(STYLE_PREFIX, ACTIONS_PREFIX, TARGET_PREFIX, "oc.widgetFollowup.").any {
                persistence.string(it + profileId) != null
            } ||
                runCatching {
                    org.json
                        .JSONObject(persistence.string("oc.widgetSessions") ?: "")
                        .optString("profileID")
                }
                    .getOrNull() == profileId
        if (!owned) return ClearOutcome.NothingToClear
        return if (WidgetV2Bridge.clearProfile(app, profileId, persistence)) ClearOutcome.Cleared
        else ClearOutcome.Failed
    }

    public companion object {
        internal const val TARGET_PREFIX = "oc.widgetTarget."
        internal const val STYLE_PREFIX = "oc.widgetStyle."
        internal const val ACTIONS_PREFIX = "oc.widgetActions."

        public fun redrawEveryWidget(context: Context) {
            SessionsWidgetProvider.refreshAll(context)
            UsageWidgetProvider.refreshAll(context)
        }
    }
}
