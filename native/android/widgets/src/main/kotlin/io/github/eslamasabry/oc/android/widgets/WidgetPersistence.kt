package io.github.eslamasabry.oc.android.widgets

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.getOrNull
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.data.kv.KeyValueStore
import io.github.eslamasabry.oc.core.data.kv.PrefType
import io.github.eslamasabry.oc.core.data.kv.string
import io.github.eslamasabry.oc.core.data.lane.WriterLanes
import io.github.eslamasabry.oc.core.data.scope.KeyScope
import io.github.eslamasabry.oc.core.data.scope.KeyShape
import io.github.eslamasabry.oc.core.data.scope.ProfileScope
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.sync.Mutex

/**
 * One graph-owned adapter over the SAME acknowledged store and lanes as native readers and
 * deletion. Bind after the migration gate opens. No fallback writer or second store is created by a
 * component.
 */
public class WidgetPersistence(
    internal val store: KeyValueStore,
    internal val lanes: WriterLanes,
    profiles: ProfileScope,
    internal val scope: CoroutineScope,
    internal val clock: Clock,
) {
    internal val claims: Mutex = Mutex()
    internal val publications: Mutex = Mutex()

    init {
        for (prefix in
            listOf(
                "oc.widgetTarget.",
                "oc.widgetStyle.",
                "oc.widgetActions.",
                "oc.widgetFollowup.",
                "oc.widgetUsage.",
            )) {
            profiles.register(
                KeyShape(
                    prefix,
                    KeyScope.Profile,
                    PrefType.Str,
                    ":android:widgets",
                    "Widget private state",
                )
            )
        }
        profiles.register(
            KeyShape(
                SNAPSHOT,
                KeyScope.App,
                PrefType.Str,
                ":android:widgets",
                "Public widget snapshot; profile cleaner",
            )
        )
    }

    internal fun string(key: String): String? = store.string(key)

    internal suspend fun cas(
        profile: String,
        key: String,
        expected: String?,
        next: String?,
    ): Boolean {
        if (profile.isBlank()) return false
        return store
            .compareAndSet(lanes.forProfile(ProfileId(profile)), key, expected, next)
            .getOrNull() == true
    }

    internal suspend fun removeProfileKeys(profile: String): Boolean =
        store.commit(lanes.app) {
            for (prefix in
                listOf(
                    "oc.widgetStyle.",
                    "oc.widgetActions.",
                    "oc.widgetTarget.",
                    "oc.widgetFollowup.",
                    "oc.widgetUsage.",
                )) {
                remove(prefix + profile)
            }
        } is Outcome.Ok

    internal companion object {
        const val SNAPSHOT = "oc.widgetSessions"
    }
}
