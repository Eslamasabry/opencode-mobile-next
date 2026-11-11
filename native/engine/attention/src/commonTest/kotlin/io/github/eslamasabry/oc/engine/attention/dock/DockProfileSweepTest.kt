package io.github.eslamasabry.oc.engine.attention.dock

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.data.kv.FlutterPrefsCodec
import io.github.eslamasabry.oc.core.data.kv.InMemoryPrefsBackend
import io.github.eslamasabry.oc.core.data.kv.JavaListDecoder
import io.github.eslamasabry.oc.core.data.kv.PrefsKeyValueStore
import io.github.eslamasabry.oc.core.data.lane.DefaultWriterLanes
import io.github.eslamasabry.oc.core.data.migration.StateMigrationGate
import io.github.eslamasabry.oc.core.data.scope.DefaultProfileScope
import io.github.eslamasabry.oc.core.data.secret.SecretStore
import io.github.eslamasabry.oc.engine.attention.activity.AttentionKeyStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest

/** The nickname key follows the app's `oc.<what>.<profileId>` rule, so the profile deletion sweep finds it. */
class DockProfileSweepTest {
    private object NoSecrets : SecretStore {
        override suspend fun read(name: String): Outcome<String?> = (null as String?).ok()

        override suspend fun write(name: String, value: String): Outcome<Unit> = Unit.ok()

        override suspend fun delete(name: String): Outcome<Unit> = Unit.ok()

        override suspend fun names(prefix: String): Outcome<Set<String>> = emptySet<String>().ok()
    }

    @Test
    fun `deleting a profile removes its nicknames and leaves the other profile's`() = runTest {
        val kv = PrefsKeyValueStore(InMemoryPrefsBackend(), FlutterPrefsCodec(JavaListDecoder.Unsupported), StateMigrationGate.alwaysOpen(), StandardTestDispatcher(testScheduler))
        val lanes = DefaultWriterLanes()
        val scope = DefaultProfileScope(kv, NoSecrets, lanes.app)
        DockKeys.register(scope)
        val gone = ProfileId("1735000000000001")
        val kept = ProfileId("1735000000000002")
        val names = DockNicknames(
            object : AttentionKeyStore {
                override suspend fun read(key: String): String? = (kv.get(key) as? io.github.eslamasabry.oc.core.data.kv.PrefValue.Str)?.value

                override suspend fun write(key: String, value: String): Boolean = kv.commit(lanes.app) { putString(key, value) } is Outcome.Ok

                override suspend fun remove(key: String): Boolean = kv.commit(lanes.app) { remove(key) } is Outcome.Ok
            },
        )
        names.set(ConversationRef(gone, SessionId("s")), "Mine")
        names.set(ConversationRef(kept, SessionId("s")), "Theirs")

        assertEquals(setOf("oc.dockNames.${gone.raw}"), scope.scopedKeys(gone).filter { it.startsWith("oc.dockNames.") }.toSet())
        assertTrue(DockKeys.SHAPES.all { it in scope.shapes() }, "the key shape is registered")

        val swept = scope.sweep(gone)
        assertTrue(swept is Outcome.Ok)
        assertEquals(null, kv.get("oc.dockNames.${gone.raw}"))
        assertEquals("""{"s":"Theirs"}""", (kv.get("oc.dockNames.${kept.raw}") as io.github.eslamasabry.oc.core.data.kv.PrefValue.Str).value)
    }
}
