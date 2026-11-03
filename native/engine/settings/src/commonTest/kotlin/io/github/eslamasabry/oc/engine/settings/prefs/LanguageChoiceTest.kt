package io.github.eslamasabry.oc.engine.settings.prefs

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.data.kv.string
import io.github.eslamasabry.oc.core.data.lane.WriterLane
import io.github.eslamasabry.oc.core.data.scope.DefaultProfileScope
import io.github.eslamasabry.oc.core.data.scope.ProfileKeyRule
import io.github.eslamasabry.oc.core.data.secret.SecretStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The app language as storage acknowledges it (Flutter `test/app_locale_test.dart`). Native notification and widget
 * words follow [Preferences.locale] — `AppPreferences.storedLocaleTag` maps it to the tag `ChannelRegistry.relocalize`
 * and the Activity's recreate collect — so what that flow publishes is what Android text is refreshed with.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class LanguageChoiceTest {
    private val app = WriterLane("app")

    private fun TestScope.fresh(disk: TestDisk = TestDisk()) = storeOver(disk, StandardTestDispatcher(testScheduler))

    @Test
    fun `B-L10N-002 native text is refreshed only after an acknowledged save, and a refused save changes nothing`() =
        runTest {
            val disk = TestDisk()
            val store = fresh(disk)
            store.seed(DevicePreferenceKeys.LOCALE to "en")
            val owner = Preferences(store, app, backgroundScope)
            val refreshed = mutableListOf<String?>() // what the notification refresh saw, read back from storage
            backgroundScope.launch {
                owner.locale.map { it.code }.distinctUntilChanged().drop(1).collect {
                    refreshed += store.string(DevicePreferenceKeys.LOCALE)
                }
            }
            runCurrent()
            assertIs<Outcome.Ok<*>>(owner.set(PreferenceChange.Locale(LocaleChoice.Ar)))
            runCurrent()
            assertIs<Outcome.Ok<*>>(owner.set(PreferenceChange.Locale(LocaleChoice.System)))
            runCurrent()
            assertEquals(listOf("ar", null), refreshed)

            disk.refuse = { key, _ -> key == DevicePreferenceKeys.LOCALE }
            val refused = owner.set(PreferenceChange.Locale(LocaleChoice.En))
            runCurrent()
            assertIs<Failure.StorageRefused>(assertIs<Outcome.Failed>(refused).failure)
            assertEquals(listOf("ar", null), refreshed, "a refused save never refreshes native text")
            assertEquals(LocaleChoice.System, owner.locale.value)
        }

    @Test
    fun `B-L10N-002 a refused write or removal keeps the last acknowledged language`() = runTest {
        val disk = TestDisk()
        val store = fresh(disk)
        store.seed(DevicePreferenceKeys.LOCALE to "en")
        val owner = Preferences(store, app, backgroundScope)
        disk.refuse = { _, _ -> true }
        assertIs<Outcome.Failed>(owner.set(PreferenceChange.Locale(LocaleChoice.Ar)))
        assertEquals(LocaleChoice.En, owner.locale.value)
        assertEquals("en", store.string(DevicePreferenceKeys.LOCALE))
        assertIs<Outcome.Failed>(owner.set(PreferenceChange.Locale(LocaleChoice.System)), "removing is a write too")
        assertEquals(LocaleChoice.En, owner.locale.value)
        assertEquals("en", store.string(DevicePreferenceKeys.LOCALE))
        disk.refuse = null
        assertIs<Outcome.Ok<*>>(owner.set(PreferenceChange.Locale(LocaleChoice.Ar)))
        assertEquals(LocaleChoice.Ar, owner.locale.value)
        // An Arabic choice survives a restart; System removes the key.
        assertEquals(LocaleChoice.Ar, Preferences(fresh(TestDisk(disk.persisted())), app, backgroundScope).locale.value)
        assertIs<Outcome.Ok<*>>(owner.set(PreferenceChange.Locale(LocaleChoice.System)))
        assertNull(store.string(DevicePreferenceKeys.LOCALE))
        assertFalse(disk.persisted().containsKey("flutter.${DevicePreferenceKeys.LOCALE}"))
    }

    @Test
    fun `B-L10N-002 rapid choices are written in order and the newest wins`() = runTest {
        val gated = GatedStore(fresh())
        gated.seed(DevicePreferenceKeys.LOCALE to "en")
        val owner = Preferences(gated, app, backgroundScope)
        val seen = mutableListOf<LocaleChoice>()
        backgroundScope.launch { owner.locale.collect { seen += it } }
        runCurrent()
        val gate = CompletableDeferred<Unit>().also { gated.gate = it }
        val first = async { owner.set(PreferenceChange.Locale(LocaleChoice.Ar)) }
        val second = async { owner.set(PreferenceChange.Locale(LocaleChoice.Ja)) }
        runCurrent()
        assertEquals(LocaleChoice.En, owner.locale.value, "nothing shows before storage answers")
        gate.complete(Unit)
        assertIs<Outcome.Ok<*>>(first.await())
        assertIs<Outcome.Ok<*>>(second.await())
        runCurrent()
        val writes = gated.calls.mapNotNull { it[DevicePreferenceKeys.LOCALE] }
        assertEquals(listOf("en", "ar", "ja"), writes, "the seed, then the two choices in the order they were made")
        assertEquals(LocaleChoice.Ja, owner.locale.value)
        assertEquals("ja", gated.string(DevicePreferenceKeys.LOCALE))
        assertEquals(LocaleChoice.Ja, seen.last())
    }

    @Test
    fun `B-L10N-002 an unsupported stored language follows the phone and cannot be chosen`() = runTest {
        val store = fresh()
        store.seed(DevicePreferenceKeys.LOCALE to "de")
        val owner = Preferences(store, app, backgroundScope)
        assertEquals(LocaleChoice.System, owner.locale.value)
        assertNull(LocaleChoice.of("de"), "no choice exists for a language the app does not ship")
        assertEquals(LocaleChoice.Ar, LocaleChoice.of("ar-EG"), "a region keeps its language")
    }

    @Test
    fun `B-SEC-009 deleting a server profile never removes the global language choice`() = runTest {
        val store = fresh()
        store.seed(
            DevicePreferenceKeys.LOCALE to "ar",
            DefaultProfileScope.PROFILES_KEY to """[{"id":"server-id"},{"id":"appLocale"}]""",
            "oc.model.server-id" to "m",
            "oc.draft.chat.server-id" to "hi",
        )
        val owner = Preferences(store, app, backgroundScope)
        val scope = DefaultProfileScope(store, NoSecrets, app)
        assertFalse(DevicePreferenceKeys.LOCALE in scope.scopedKeys(ProfileId("server-id")))
        // Even a profile whose id spells the key's own name does not own it: app-wide keys have no owner segment.
        assertFalse(DevicePreferenceKeys.LOCALE in scope.scopedKeys(ProfileId("appLocale")))
        assertFalse(DevicePreferenceKeys.LOCALE in ProfileKeyRule.scopedKeys("server-id", store.keys("oc."), emptySet()))
        val receipt = assertIs<Outcome.Ok<io.github.eslamasabry.oc.core.data.scope.SweepReceipt>>(scope.sweep(ProfileId("server-id")))
        assertEquals(2, receipt.value.removedKeys)
        assertNull(store.string("oc.model.server-id"))
        assertEquals("ar", store.string(DevicePreferenceKeys.LOCALE))
        assertEquals(LocaleChoice.Ar, owner.locale.value)
        assertTrue(scope.sweep(ProfileId("appLocale")) is Outcome.Ok)
        assertEquals("ar", store.string(DevicePreferenceKeys.LOCALE))
    }

    private object NoSecrets : SecretStore {
        override suspend fun read(name: String): Outcome<String?> = Outcome.Ok(null)
        override suspend fun write(name: String, value: String): Outcome<Unit> = Outcome.Ok(Unit)
        override suspend fun delete(name: String): Outcome<Unit> = Outcome.Ok(Unit)
        override suspend fun names(prefix: String): Outcome<Set<String>> = Outcome.Ok(emptySet())
    }
}
