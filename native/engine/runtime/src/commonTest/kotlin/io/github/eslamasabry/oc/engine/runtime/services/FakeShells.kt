package io.github.eslamasabry.oc.engine.runtime.services

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.NetworkWhy
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ok
import kotlinx.coroutines.CompletableDeferred

/** The connected server's managed shells in memory (port of test/support/development_service_fakes.dart). */
class FakeShells : ManagedShellPort {
    val shells = linkedMapOf<String, ManagedShell>()
    val stops = mutableListOf<String>()
    val cursors = mutableListOf<Long>()
    var starts = 0
    var output = "VITE ready in 410 ms\nLocal: http://localhost:5173/\n"
    var createGate: CompletableDeferred<Unit>? = null
    var infoGate: CompletableDeferred<Unit>? = null
    var loseCreateResponse = false
    var failReads = false

    override suspend fun start(command: String, directory: String, ownerToken: String): Outcome<ManagedShell> {
        starts++
        val shell =
            ManagedShell(
                id = "sh_$starts",
                command = command,
                directory = directory,
                ownerToken = ownerToken,
                status = ManagedShellStatus.Running,
                startedAtMillis = starts * 1000L,
            )
        shells[shell.id] = shell
        createGate?.await()
        if (loseCreateResponse) return Failure.Network(NetworkWhy.Reset).failed()
        return shell.ok()
    }

    override suspend fun get(id: String): Outcome<ManagedShell?> {
        infoGate?.await()
        if (failReads) return Failure.Server(503).failed()
        return shells[id].ok()
    }

    override suspend fun running(): Outcome<ManagedShellList> =
        ManagedShellList(supported = true, shells = shells.values.filter { it.running }).ok()

    override suspend fun stop(id: String): Outcome<Unit> {
        stops += id
        shells.remove(id)
        return Unit.ok()
    }

    override suspend fun output(id: String, cursor: Long, limit: Int): Outcome<ManagedShellOutput> {
        cursors += cursor
        val bytes = output.encodeToByteArray()
        val end = minOf(bytes.size.toLong(), cursor + limit).toInt()
        return ManagedShellOutput(
            text = bytes.decodeToString(cursor.toInt(), end),
            cursor = end.toLong(),
            size = bytes.size.toLong(),
            truncated = cursor > 0,
        ).ok()
    }
}

/** The preferences store in memory. */
class MapSettings : DevelopmentServiceSettings {
    val values = mutableMapOf<String, String>()

    override suspend fun read(key: String): String? = values[key]

    override suspend fun write(key: String, value: String): Outcome<Unit> {
        values[key] = value
        return Unit.ok()
    }

    override suspend fun remove(key: String): Outcome<Unit> {
        values.remove(key)
        return Unit.ok()
    }
}
