package io.github.eslamasabry.oc.feature.devservices

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.common.pace.Pacer
import io.github.eslamasabry.oc.core.common.pace.TimerSpec
import io.github.eslamasabry.oc.engine.runtime.devservers.PhoneScriptResult
import io.github.eslamasabry.oc.engine.runtime.devservers.PhoneScriptRunner
import io.github.eslamasabry.oc.engine.runtime.services.DevelopmentServiceSettings
import io.github.eslamasabry.oc.engine.runtime.services.DevelopmentServiceStatus
import io.github.eslamasabry.oc.engine.runtime.services.ManagedShell
import io.github.eslamasabry.oc.engine.runtime.services.ManagedShellList
import io.github.eslamasabry.oc.engine.runtime.services.ManagedShellOutput
import io.github.eslamasabry.oc.engine.runtime.services.ManagedShellPort
import io.github.eslamasabry.oc.engine.runtime.services.ManagedShellStatus
import io.github.eslamasabry.oc.feature.devservices.presentation.DevServicesAsk
import io.github.eslamasabry.oc.feature.devservices.presentation.DevServicesContext
import io.github.eslamasabry.oc.feature.devservices.presentation.DevServicesHost
import io.github.eslamasabry.oc.feature.devservices.presentation.DevServicesModel
import io.github.eslamasabry.oc.feature.devservices.presentation.DevServicesReceipt
import io.github.eslamasabry.oc.feature.devservices.presentation.DevServicesRules
import io.github.eslamasabry.oc.feature.devservices.presentation.NameError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/** The Development services page over the real :engine:runtime engines and fake app ports (B-LINUX-054..064). */
class DevServicesModelTest {
    private val profile = ProfileId("p1")
    private val folder = "/work/shopfront"

    private class Settings : DevelopmentServiceSettings {
        val values = mutableMapOf<String, String>()

        override suspend fun read(key: String): String? = values[key]

        override suspend fun write(key: String, value: String): Outcome<Unit> = Unit.ok().also { values[key] = value }

        override suspend fun remove(key: String): Outcome<Unit> = Unit.ok().also { values.remove(key) }
    }

    private class Shells : ManagedShellPort {
        val shells = mutableMapOf<String, ManagedShell>()
        val calls = mutableListOf<String>()

        override suspend fun start(command: String, directory: String, ownerToken: String): Outcome<ManagedShell> {
            calls += "start $command"
            val shell = ManagedShell("sh_${shells.size + 1}", command, ManagedShellStatus.Running, 1_000, directory = directory, ownerToken = ownerToken)
            shells[shell.id] = shell
            return shell.ok()
        }

        override suspend fun running(): Outcome<ManagedShellList> = ManagedShellList(true, shells.values.filter { it.running }).ok()

        override suspend fun get(id: String): Outcome<ManagedShell?> = shells[id].ok()

        override suspend fun output(id: String, cursor: Long, limit: Int): Outcome<ManagedShellOutput> =
            ManagedShellOutput("ready on http://localhost:5173\n", cursor, 30, false).ok()

        override suspend fun stop(id: String): Outcome<Unit> {
            calls += "stop $id"
            shells.remove(id)
            return Unit.ok()
        }
    }

    private object NoTicks : Pacer {
        override fun ticks(spec: TimerSpec): Flow<Unit> = emptyFlow()
    }

    private object FixedClock : Clock {
        override fun now(): Instant = Instant.parse("2026-11-03T12:00:00Z")
    }

    private var ids = 0

    private fun host(
        settings: Settings,
        shells: Shells?,
        supported: Boolean = true,
        phone: PhoneScriptRunner? = null,
        resolved: MutableList<Unit> = mutableListOf(),
    ) = DevServicesHost { _, _ ->
        DevServicesContext(
            settings = settings,
            canWrite = { true },
            workspace = null,
            supported = { supported },
            shells = { resolved += Unit; shells },
            phone = phone,
            isCurrent = { true },
            newId = { "id${++ids}" },
            openLink = { true },
            clock = FixedClock,
        )
    }

    private fun TestScope.model(host: DevServicesHost, scope: CoroutineScope = backgroundScope) =
        DevServicesModel(profile, folder, host, NoTicks, scope, onBack = {}).also {
            it.shown(true)
            runCurrent()
        }

    private fun DevServicesModel.register(name: String, command: String, url: String = "") {
        add()
        edit(name, command, url)
        save()
    }

    @Test
    fun `B-LINUX-061 a saved service survives reopening and is never started by itself`() = runTest {
        val settings = Settings()
        val shells = Shells()
        val first = model(host(settings, shells))
        first.register("web", "npm run dev", "http://localhost:5173")
        runCurrent()
        assertNull(first.state.value.editor)
        first.close()

        val again = model(host(settings, shells))
        val row = again.state.value.services.single()
        assertEquals("web", row.name)
        assertEquals("npm run dev", row.command)
        assertEquals(DevelopmentServiceStatus.NotStarted, row.status)
        assertTrue(shells.calls.none { it.startsWith("start") }, "${shells.calls}")
        assertTrue(settings.values.keys.single() == "oc.developmentServices.p1")
    }

    @Test
    fun `B-LINUX-061 a server that cannot track commands saves the configuration without shell calls`() = runTest {
        val resolved = mutableListOf<Unit>()
        val page = model(host(Settings(), Shells(), supported = false, resolved = resolved))
        page.register("web", "npm run dev")
        runCurrent()
        assertFalse(page.state.value.supported)
        assertEquals("web", page.state.value.services.single().name)
        assertTrue(resolved.isEmpty(), "no shell port was asked for")
    }

    @Test
    fun `B-LINUX-060 Start makes an owned shell, Stop asks first and then stops only it`() = runTest {
        val shells = Shells()
        val page = model(host(Settings(), shells))
        page.register("web", "npm run dev")
        runCurrent()
        val id = page.state.value.services.single().id
        page.start(id)
        runCurrent()
        assertEquals(listOf("start npm run dev"), shells.calls)
        assertEquals(DevServicesReceipt.Started("web"), page.state.value.receipt)
        assertEquals(DevelopmentServiceStatus.Running, page.state.value.services.single().status)
        assertTrue(page.state.value.services.single().canStop)

        page.askStop(id)
        assertIs<DevServicesAsk.Stop>(page.state.value.ask)
        assertEquals(listOf("start npm run dev"), shells.calls, "asking stops nothing")
        page.confirm()
        runCurrent()
        assertEquals("stop sh_1", shells.calls.last())
        assertNull(page.state.value.ask)
    }

    @Test
    fun `B-LINUX-064 Logs shows the bounded tail with colour codes removed`() = runTest {
        val page = model(host(Settings(), Shells()))
        page.register("web", "npm run dev")
        runCurrent()
        val id = page.state.value.services.single().id
        page.start(id)
        runCurrent()
        page.logs(id)
        runCurrent()
        assertEquals("ready on http://localhost:5173\n", page.state.value.services.single().log)
    }

    @Test
    fun `B-LINUX-060 the form needs a unique name, a command and a safe preview address`() = runTest {
        val page = model(host(Settings(), Shells()))
        page.register("", "")
        assertEquals(NameError.Required, page.state.value.editor?.nameError)
        assertTrue(page.state.value.editor?.commandMissing == true)
        page.edit("web", "npm run dev", "http://user:pw@example.test")
        page.save()
        assertTrue(page.state.value.editor?.urlInvalid == true)
        page.edit("web", "npm run dev", "")
        page.save()
        runCurrent()
        page.register("WEB", "npm start")
        assertEquals(NameError.Duplicate, page.state.value.editor?.nameError)
        assertFalse(DevServicesRules.safePreviewUrl("ftp://example.test"))
        assertTrue(DevServicesRules.safePreviewUrl("http://localhost:5173/app"))
    }

    @Test
    fun `B-LINUX-059 the phone is scanned only when the page has the phone's runner`() = runTest {
        val remote = model(host(Settings(), Shells(), phone = null))
        assertFalse(remote.state.value.scansPhone)
        val onPhone = model(host(Settings(), Shells(), phone = PhoneScriptRunner { PhoneScriptResult(0, "").ok() }))
        assertTrue(onPhone.state.value.scansPhone)
    }

    @Test
    fun `B-LINUX-054 a server shell this project did not save is listed under Also running`() = runTest {
        val shells = Shells()
        shells.shells["sh_9"] = ManagedShell("sh_9", "npx vite --port 5173", ManagedShellStatus.Running, 1_000, directory = folder)
        val page = model(host(Settings(), shells))
        page.refresh()
        runCurrent()
        val found = page.state.value.found.single()
        assertEquals("npx vite --port 5173", found.command)
        assertTrue(found.canStop)
        page.askStopFound(found.id)
        assertIs<DevServicesAsk.StopFound>(page.state.value.ask)
        page.confirm()
        runCurrent()
        assertEquals("stop sh_9", shells.calls.last())
    }

    @Test
    fun `B-LINUX-063 without the live server the page says it is not available and does nothing`() = runTest {
        val page = model(DevServicesHost { _, _ -> null })
        assertFalse(page.state.value.available)
        page.add()
        assertNull(page.state.value.editor)
    }
}
