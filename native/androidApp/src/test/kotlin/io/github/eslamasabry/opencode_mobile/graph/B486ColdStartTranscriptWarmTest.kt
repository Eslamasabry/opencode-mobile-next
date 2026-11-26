package io.github.eslamasabry.opencode_mobile.graph

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.data.cache.FileLastKnown
import io.github.eslamasabry.oc.core.data.kv.FlutterPrefsCodec
import io.github.eslamasabry.oc.core.data.kv.InMemoryPrefsBackend
import io.github.eslamasabry.oc.core.data.kv.JavaListDecoder
import io.github.eslamasabry.oc.core.data.kv.PrefsKeyValueStore
import io.github.eslamasabry.oc.core.data.lane.DefaultWriterLanes
import io.github.eslamasabry.oc.core.data.migration.StateMigrationGate
import io.github.eslamasabry.oc.core.data.secret.SecretStore
import io.github.eslamasabry.oc.core.model.BackendKind
import io.github.eslamasabry.oc.core.model.transcript.MessageInfo
import io.github.eslamasabry.oc.core.model.transcript.MessageWithParts
import io.github.eslamasabry.oc.core.model.transcript.MsgTime
import io.github.eslamasabry.oc.core.model.transcript.Part
import io.github.eslamasabry.oc.core.model.transcript.PartType
import io.github.eslamasabry.oc.core.model.transcript.Role
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.core.testing.TestClock
import io.github.eslamasabry.oc.engine.profiles.PhoneServerProfile
import io.github.eslamasabry.oc.engine.profiles.ProfileStore
import io.github.eslamasabry.oc.engine.profiles.SecretEdit
import io.github.eslamasabry.oc.engine.profiles.StoredProfile
import java.io.File
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.io.files.Path

/**
 * B-486: reading a saved phone-agent transcript during cold start before ProfileStore.load() has
 * completed must load profiles first so the saved conversation is never dropped to a blank screen.
 */
class B486ColdStartTranscriptWarmTest {
    private val rootDir =
        File(System.getProperty("java.io.tmpdir"), "b486-cold-lk-${Random.nextLong().toULong()}")
    private val phoneId = ProfileId("phone")
    private val helperId = ProfileId("phone-agents")

    @AfterTest
    fun cleanUp() {
        rootDir.deleteRecursively()
    }

    private class MemorySecrets : SecretStore {
        val map = LinkedHashMap<String, String>()

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

    private fun sampleMessages(sessionId: String): List<MessageWithParts> =
        listOf(
            MessageWithParts(
                MessageInfo("u1", sessionId, Role.User, providerId = "claude-code", time = MsgTime(10, 11)),
                listOf(Part("p1", "u1", sessionId, PartType.Text, "text", text = "Explain the crash"))
                    .toImmutableList(),
            ),
            MessageWithParts(
                MessageInfo(
                    "a1",
                    sessionId,
                    Role.Assistant,
                    providerId = "claude-code",
                    time = MsgTime(12, 14),
                    finish = "stop",
                ),
                listOf(Part("p2", "a1", sessionId, PartType.Text, "text", text = "Found the root cause."))
                    .toImmutableList(),
            ),
        )

    @Test
    fun `B-CHAT-486 cold-start transcript read loads profiles before looking up server mark`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val clock = TestClock()
            val backend = InMemoryPrefsBackend()
            val secrets = MemorySecrets()
            val lanes = DefaultWriterLanes()
            val redactor = Redactor()
            val ref = ConversationRef(helperId, SessionId("claude-cold"))

            // First run: save helper profile and keep its transcript on disk.
            val prefs1 =
                PrefsKeyValueStore(
                    backend,
                    FlutterPrefsCodec(JavaListDecoder.Unsupported),
                    StateMigrationGate.alwaysOpen(),
                    dispatcher,
                )
            prefs1.warm()
            val profiles1 =
                ProfileStore(
                    prefs1,
                    secrets,
                    lanes,
                    redactor,
                    phoneServer = { PhoneServerProfile.managedOnPhone(it, 4098) },
                )
            profiles1.load()
            profiles1.upsert(
                StoredProfile(phoneId, "This phone", "http://127.0.0.1:4098", backend = BackendKind.OpenCode2),
                SecretEdit.Set("pw-phone"),
            )
            profiles1.upsert(
                StoredProfile(
                    helperId,
                    "This phone",
                    "ws://127.0.0.1:4099",
                    backend = BackendKind.Paseo,
                    codexDirectory = "/root/projects",
                ),
                SecretEdit.Set("pw-helper"),
            )
            prefs1.commit(lanes.app) {
                putString("oc.builtinServerOwner", phoneId.raw)
                putString("oc.phoneAgentOwner.${helperId.raw}", phoneId.raw)
            }
            val lk1 = FileLastKnown(Path(rootDir.absolutePath), dispatcher, clock)
            TranscriptLastKnownStore(lk1, profiles1, prefs1) { 1 }
                .keep(ref, "Explain the crash", sampleMessages(ref.sessionId.raw))

            // Cold restart: fresh ProfileStore that has NOT run load() yet when Chat opens.
            val prefs2 =
                PrefsKeyValueStore(
                    backend,
                    FlutterPrefsCodec(JavaListDecoder.Unsupported),
                    StateMigrationGate.alwaysOpen(),
                    dispatcher,
                )
            prefs2.warm()
            val profiles2 =
                ProfileStore(
                    prefs2,
                    secrets,
                    lanes,
                    redactor,
                    phoneServer = { PhoneServerProfile.managedOnPhone(it, 4098) },
                )
            val lk2 = FileLastKnown(Path(rootDir.absolutePath), dispatcher, clock)
            val coldStore = TranscriptLastKnownStore(lk2, profiles2, prefs2) { 1 }

            val kept = coldStore.read(ref)
            assertNotNull(kept, "cold-start read must warm ProfileStore so saved agent transcript shows at once")
            assertEquals("Explain the crash", kept.title)
            assertEquals("Found the root cause.", kept.history.last().parts.single().text)
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `B-LINUX-486 helper start refused before activityResumed wakes immediately on resume`() =
        runTest {
            val joins = HelperJoins(backgroundScope, now = { testScheduler.currentTime })
            val visibilityFront = MutableStateFlow(false)
            val activityResumed = MutableStateFlow(false)
            var tries = 0
            var readyAt: Long? = null
            val attempt: suspend () -> io.github.eslamasabry.oc.core.common.Failure? = {
                tries++
                if (!activityResumed.value) {
                    io.github.eslamasabry.oc.core.common.Failure.Runtime(
                        io.github.eslamasabry.oc.core.common.RuntimeProblem.ServerStartUnavailable
                    )
                } else {
                    readyAt = testScheduler.currentTime
                    null
                }
            }
            val want: (ProfileId) -> Unit = { id ->
                if (joins.running(id)) joins.wake(id)
                else joins.ensure(id, HelperStartRules::keepTrying, {}, attempt)
            }
            backgroundScope.launch { joins.followFront(visibilityFront, { listOf(helperId) }, want) }
            backgroundScope.launch { joins.followFront(activityResumed, { listOf(helperId) }, want) }

            // onActivityStarted fires first at t = 0 before BuiltinLinux.setActivityResumed(true).
            visibilityFront.value = true
            runCurrent()
            assertEquals(1, tries)
            assertEquals(null, readyAt)

            // 50 ms later MainActivity resumes and BuiltinRuntime flips activityResumed = true.
            advanceTimeBy(50)
            activityResumed.value = true
            runCurrent()

            assertEquals(2, tries, "must wake immediately instead of sleeping 5000 ms in QUICK_RETRY_MS")
            assertEquals(50L, readyAt)
            assertTrue(!joins.running(helperId))
        }
}
