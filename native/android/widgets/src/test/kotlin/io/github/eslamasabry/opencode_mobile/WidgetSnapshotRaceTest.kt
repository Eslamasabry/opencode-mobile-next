package io.github.eslamasabry.opencode_mobile

import android.content.Context
import io.github.eslamasabry.oc.android.widgets.NoSecrets
import io.github.eslamasabry.oc.android.widgets.WidgetFeed
import io.github.eslamasabry.oc.android.widgets.WidgetPersistence
import io.github.eslamasabry.oc.android.widgets.bindWidgetTestPersistence
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.data.kv.KeyValueStore
import io.github.eslamasabry.oc.core.data.kv.string
import io.github.eslamasabry.oc.core.data.lane.WriterLane
import io.github.eslamasabry.oc.core.data.scope.DefaultProfileScope
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WidgetSnapshotRaceTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private val row =
        JSONObject()
            .put("id", "a")
            .put("requestID", "q")
            .put("busy", true)
            .put("startedAt", 100L)
            .put("runMarker", "run")
    private val initial =
        JSONObject()
            .put("version", 2)
            .put("profileID", "p")
            .put("scopeToken", "s")
            .put("sessions", JSONArray().put(row))
    private val payload =
        WidgetV2Bridge.identity(initial, row).toMutableMap().apply {
            put("action", "allowOnce")
            put("id", WidgetActionPolicy.token(this))
        }

    private class RacingStore(val original: KeyValueStore) : KeyValueStore by original {
        var publish: (suspend () -> Unit)? = null

        override suspend fun compareAndSet(
            lane: WriterLane,
            key: String,
            expected: String?,
            next: String?,
        ): Outcome<Boolean> {
            val update = publish
            publish = null
            update?.invoke()
            return original.compareAndSet(lane, key, expected, next)
        }
    }

    private fun storage(): Pair<WidgetPersistence, RacingStore> {
        context
            .getSharedPreferences(WidgetV2Bridge.PREFS, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .putString(WidgetV2Bridge.SNAPSHOT_KEY, initial.toString())
            .commit()
        val base = bindWidgetTestPersistence(context)
        val race = RacingStore(base.store)
        val storage =
            WidgetPersistence(
                race,
                base.lanes,
                DefaultProfileScope(race, NoSecrets, base.lanes.app),
                base.scope,
                base.clock,
            )
        WidgetFeed.persistence = storage
        return storage to race
    }

    @Test
    fun `widgets-MAJOR-3 foreground publication wins over a late settlement`() = runTest {
        val (storage, race) = storage()
        val newer =
            JSONObject(initial.toString())
                .apply { getJSONArray("sessions").put(JSONObject().put("id", "b")) }
                .toString()
        race.publish = {
            race.original.commit(storage.lanes.app) { putString("oc.widgetSessions", newer) }
        }
        val patch =
            mapOf(
                "version" to 2,
                "profileID" to "p",
                "scopeToken" to "s",
                "sessions" to listOf(mapOf("id" to "a", "busy" to false)),
            )
        assertFalse(WidgetActionSnapshot.apply(context, payload, patch))
        assertEquals(newer, storage.store.string("oc.widgetSessions"))
    }

    @Test
    fun `widgets-MAJOR-3 foreground publication wins over an idle patch`() = runTest {
        val (storage, race) = storage()
        initial.getJSONArray("sessions").getJSONObject(0).remove("requestID")
        race.original.commit(storage.lanes.app) {
            putString("oc.widgetSessions", initial.toString())
        }
        val newer = JSONObject(initial.toString()).put("lastProjectID", "new-project").toString()
        race.publish = {
            race.original.commit(storage.lanes.app) { putString("oc.widgetSessions", newer) }
        }
        val ticket =
            mapOf(
                "profileID" to "p",
                "scopeToken" to "s",
                "sessionID" to "a",
                "runMarker" to "run",
                "startedAt" to 100L,
            )
        assertFalse(WidgetActionSnapshot.applyIdleStatus(context, ticket, "settled"))
        assertEquals(newer, storage.store.string("oc.widgetSessions"))
    }

    @Test
    fun `widgets-MAJOR-3 foreground publication wins over a result banner`() = runTest {
        val (storage, race) = storage()
        val newer = JSONObject(initial.toString()).put("lastProjectID", "new-project").toString()
        race.publish = {
            race.original.commit(storage.lanes.app) { putString("oc.widgetSessions", newer) }
        }
        WidgetActionBridge.publishResult(
            context,
            payload,
            WidgetActionBridge.result(payload, "rejected", "unavailable"),
        )
        assertEquals(newer, storage.store.string("oc.widgetSessions"))
    }
}
