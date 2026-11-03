package io.github.eslamasabry.oc.android.widgets

import android.content.Context
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.data.android.AndroidDataStores
import io.github.eslamasabry.oc.core.data.kv.string
import io.github.eslamasabry.oc.core.data.lane.DefaultWriterLanes
import io.github.eslamasabry.oc.core.data.migration.StateMigrationGate
import io.github.eslamasabry.oc.core.data.scope.DefaultProfileScope
import io.github.eslamasabry.oc.core.data.secret.SecretStore
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class WidgetPersistenceTest {
    @Test
    fun `widgets-MAJOR-2 publication enters the warm store and generic profile sweep`() = runTest {
        val context: Context = RuntimeEnvironment.getApplication()
        context
            .getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        val store =
            AndroidDataStores.flutterPrefs(
                context,
                StateMigrationGate.alwaysOpen(),
                UnconfinedTestDispatcher(testScheduler),
            )
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
        WidgetFeed.persistence = storage
        val surface = AndroidWidgetSurface(context, storage) {}
        val snapshot = """{"version":2,"profileID":"p","scopeToken":"s","sessions":[]}"""
        assertEquals(PublishResult.Written, surface.publish(snapshot, "p", "{}"))
        assertEquals("{}", store.string("oc.widgetTarget.p"))
        assertEquals(
            setOf(
                "oc.widgetTarget.",
                "oc.widgetStyle.",
                "oc.widgetActions.",
                "oc.widgetFollowup.",
                "oc.widgetUsage.",
                "oc.widgetSessions",
            ),
            profiles.shapes().map { it.prefix }.toSet(),
        )
        assertNull(
            io.github.eslamasabry.opencode_mobile.WidgetActionBridge.claim(
                context,
                mapOf("profileID" to "p", "id" to "tap"),
            )
        )
        assertTrue(profiles.scopedKeys(ProfileId("p")).contains("oc.widgetTarget.p"))
        lanes.closeProfile(ProfileId("p"))
        lanes.drainProfile(ProfileId("p"))
        profiles.sweep(ProfileId("p"))
        assertNull(store.string("oc.widgetTarget.p"))
        assertNull(store.string("oc.widgetActions.p"))
        assertEquals(
            "unavailable",
            io.github.eslamasabry.opencode_mobile.WidgetActionBridge.claim(
                context,
                mapOf("profileID" to "p", "id" to "late"),
            ),
        )
        assertEquals(PublishResult.Refused, surface.publish(snapshot, "p", "{ }"))
        assertNull(store.string("oc.widgetTarget.p"))
    }

    @Test
    fun `widgets-MAJOR-2 refused persistence publishes neither data nor changes nor a redraw`() =
        runTest {
            val context: Context = RuntimeEnvironment.getApplication()
            val backend =
                object : io.github.eslamasabry.oc.core.data.kv.InMemoryPrefsBackend() {
                    var refuse = true

                    override fun commit(
                        changes: Map<String, io.github.eslamasabry.oc.core.data.kv.RawPref?>
                    ): Boolean {
                        super.commit(changes)
                        return if (refuse) {
                            refuse = false
                            false
                        } else true
                    }
                }
            val store =
                io.github.eslamasabry.oc.core.data.kv.PrefsKeyValueStore(
                    backend,
                    io.github.eslamasabry.oc.core.data.kv.FlutterPrefsCodec(
                        io.github.eslamasabry.oc.core.data.kv.JavaListDecoder.Unsupported
                    ),
                    StateMigrationGate.alwaysOpen(),
                    UnconfinedTestDispatcher(testScheduler),
                )
            store.warm()
            val lanes = DefaultWriterLanes()
            val base = bindWidgetTestPersistence(context)
            val storage =
                WidgetPersistence(
                    store,
                    lanes,
                    DefaultProfileScope(store, NoSecrets, lanes.app),
                    this,
                    base.clock,
                )
            WidgetFeed.persistence = storage
            val events = mutableListOf<Set<String>>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                store.changes.collect { events.add(it) }
            }
            var redraws = 0
            val surface = AndroidWidgetSurface(context, storage) { redraws++ }
            assertEquals(PublishResult.Refused, surface.publish("{}", "p", "{}"))
            assertNull(store.string("oc.widgetTarget.p"))
            assertNull(store.string("oc.widgetSessions"))
            assertFalse(backend.persisted().containsKey("flutter.oc.widgetTarget.p"))
            assertTrue(events.isEmpty())
            assertEquals(0, redraws)
            assertEquals(PublishResult.Written, surface.publish("{}", "p", "{}"))
            assertEquals(listOf(setOf("oc.widgetTarget.p"), setOf("oc.widgetSessions")), events)
            assertEquals(1, redraws)
        }

    @Test
    fun `widgets-MAJOR-2 profile cleaner removes embedded snapshot after lane closure`() = runTest {
        val context: Context = RuntimeEnvironment.getApplication()
        context
            .getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        runCatching {
            androidx.work.WorkManager.initialize(
                context,
                androidx.work.Configuration.Builder().setExecutor { it.run() }.build(),
            )
        }
        val storage = bindWidgetTestPersistence(context)
        val surface = AndroidWidgetSurface(context, storage) {}
        surface.publish(
            """{"version":2,"profileID":"p","scopeToken":"s","sessions":[]}""",
            "p",
            "{}",
        )
        storage.store.commit(storage.lanes.app) {
            putString("oc.widgetTarget.other", "{}")
            putString("oc.widgetFollowup.p", "{}")
        }
        storage.lanes.closeProfile(ProfileId("p"))
        storage.lanes.drainProfile(ProfileId("p"))
        assertEquals(
            Outcome.Ok(1),
            WidgetProfileCleaner(surface)
                .clear(ProfileId("p"), io.github.eslamasabry.oc.engine.profiles.DeletionOptions()),
        )
        assertNull(storage.string("oc.widgetSessions"))
        assertNull(storage.string("oc.widgetFollowup.p"))
        assertEquals("{}", storage.string("oc.widgetTarget.other"))
    }
}

internal object NoSecrets : SecretStore {
    override suspend fun read(name: String): Outcome<String?> = Outcome.Ok(null)

    override suspend fun write(name: String, value: String): Outcome<Unit> = Outcome.Ok(Unit)

    override suspend fun delete(name: String): Outcome<Unit> = Outcome.Ok(Unit)

    override suspend fun names(prefix: String): Outcome<Set<String>> = Outcome.Ok(emptySet())
}
