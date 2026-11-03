package io.github.eslamasabry.oc.android.widgets

import android.content.Context
import io.github.eslamasabry.oc.android.widgets.snapshot.UsageWidgetSnapshotRows
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.data.android.AndroidDataStores
import io.github.eslamasabry.oc.core.data.kv.string
import io.github.eslamasabry.oc.core.data.lane.DefaultWriterLanes
import io.github.eslamasabry.oc.core.data.migration.StateMigrationGate
import io.github.eslamasabry.oc.core.data.scope.DefaultProfileScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** The usage widget's per-server key is a profile key, so deleting a server drops it (AGENTS rule 9). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class UsageWidgetProfileSweepTest {
    @Test
    fun `B-ANDR-100 deleting a server drops only its usage widget entry`() = runTest {
        val context: Context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE).edit().clear().commit()
        val store = AndroidDataStores.flutterPrefs(context, StateMigrationGate.alwaysOpen(), UnconfinedTestDispatcher(testScheduler))
        store.warm()
        val lanes = DefaultWriterLanes()
        val profiles = DefaultProfileScope(store, NoSecrets, lanes.app)
        val storage =
            WidgetPersistence(
                store,
                lanes,
                profiles,
                this,
                object : kotlin.time.Clock {
                    override fun now() = kotlin.time.Instant.fromEpochMilliseconds(1000)
                },
            )
        val gone = UsageWidgetSnapshotRows.key("gone")
        val kept = UsageWidgetSnapshotRows.key("kept")
        assertEquals("oc.widgetUsage.gone", gone)
        val written = store.commit(lanes.app) {
            putString(gone, """{"name":"A","rows":{}}""")
            putString(kept, """{"name":"B","rows":{}}""")
        }
        assertTrue(written is Outcome.Ok)
        assertTrue(profiles.scopedKeys(ProfileId("gone")).contains(gone))

        lanes.closeProfile(ProfileId("gone"))
        lanes.drainProfile(ProfileId("gone"))
        profiles.sweep(ProfileId("gone"))
        assertNull(store.string(gone))
        assertEquals("""{"name":"B","rows":{}}""", store.string(kept))

        assertTrue(storage.removeProfileKeys("kept"))
        assertNull(store.string(kept))
    }
}
