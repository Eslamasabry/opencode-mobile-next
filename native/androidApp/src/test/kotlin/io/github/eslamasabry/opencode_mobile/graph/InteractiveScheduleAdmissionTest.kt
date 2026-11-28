package io.github.eslamasabry.opencode_mobile.graph

import android.app.Application
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.data.kv.FlutterPrefsCodec
import io.github.eslamasabry.oc.core.data.kv.InMemoryPrefsBackend
import io.github.eslamasabry.oc.core.data.kv.JavaListDecoder
import io.github.eslamasabry.oc.core.data.kv.PrefsKeyValueStore
import io.github.eslamasabry.oc.core.data.lane.DefaultWriterLanes
import io.github.eslamasabry.oc.core.data.migration.StateMigrationGate
import io.github.eslamasabry.oc.core.data.secret.SecretStore
import io.github.eslamasabry.oc.core.model.BackendKind
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.core.testing.TestDispatchers
import io.github.eslamasabry.oc.engine.profiles.PhoneServerProfile
import io.github.eslamasabry.oc.engine.profiles.ProfileStore
import io.github.eslamasabry.oc.engine.profiles.SecretEdit
import io.github.eslamasabry.oc.engine.profiles.StoredProfile
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * B-399 (F-044): renaming a phone agents helper profile's display name must not falsely refuse sends as StaleScope,
 * while real connection changes (origin, backend, or profile removal) still fail closed as StaleScope.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class InteractiveScheduleAdmissionTest {
    private class MemorySecrets : SecretStore {
        private val map = LinkedHashMap<String, String>()

        override suspend fun read(name: String): Outcome<String?> = map[name].ok()

        override suspend fun write(name: String, value: String): Outcome<Unit> {
            map[name] = value
            return Unit.ok()
        }

        override suspend fun delete(name: String): Outcome<Unit> {
            map.remove(name)
            return Unit.ok()
        }

        override suspend fun names(prefix: String): Outcome<Set<String>> =
            map.keys.filterTo(LinkedHashSet()) { it.startsWith(prefix) }.ok()
    }

    @Test
    fun `B-399-1f display-name update on phone helper allows sends while origin change refuses StaleScope`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val dispatchers = TestDispatchers(dispatcher)
            val prefs =
                PrefsKeyValueStore(
                    InMemoryPrefsBackend(),
                    FlutterPrefsCodec(JavaListDecoder.Unsupported),
                    StateMigrationGate.alwaysOpen(),
                    dispatcher,
                )
            val profiles =
                ProfileStore(
                    prefs,
                    MemorySecrets(),
                    DefaultWriterLanes(),
                    Redactor(),
                    phoneServer = { PhoneServerProfile.managedOnPhone(it, 4098) },
                )
            prefs.warm()
            profiles.load()
            val helperId = ProfileId("phone-agents")
            val stored =
                StoredProfile(
                    helperId,
                    "Phone agents",
                    "ws://127.0.0.1:4099",
                    backend = BackendKind.Paseo,
                    codexDirectory = "/root/projects",
                )
            profiles.upsert(stored, SecretEdit.Set("pw-helper"))
            val connectedProfile = profiles.profile(helperId)!!.toServerProfile(false)!!

            // Renaming the display label (e.g. "Phone agents" -> "This phone") must NOT invalidate live sends.
            profiles.upsert(stored.copy(name = "This phone"), SecretEdit.Keep)
            val admission = InteractiveScheduleAdmission(RuntimeEnvironment.getApplication(), profiles, dispatchers)
            assertEquals(Outcome.Ok(Unit), admission.check(connectedProfile))

            // Changing the actual server origin MUST still fail closed as StaleScope.
            profiles.upsert(stored.copy(name = "This phone", baseUrl = "ws://127.0.0.1:4199"), SecretEdit.Keep)
            val refused = assertIs<Outcome.Failed>(admission.check(connectedProfile))
            assertEquals(RefusalReason.StaleScope, assertIs<Failure.Refused>(refused.failure).reason)
        }
}
