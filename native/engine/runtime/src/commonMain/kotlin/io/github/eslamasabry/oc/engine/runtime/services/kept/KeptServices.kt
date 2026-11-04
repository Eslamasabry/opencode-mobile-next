package io.github.eslamasabry.oc.engine.runtime.services.kept

import io.github.eslamasabry.oc.core.common.Outcome
import kotlin.time.Instant

public enum class KeptPhase {
    Off,
    Starting,
    Running,
    Backoff,
    PortBusy,
    Failed,
    Exited,
    Stopping,
}

public enum class KeptRestart {
    Always,
    OnFailure,
    Never,
}

public enum class KeptAct {
    Start,
    Stop,
    Restart,
    Remove,
}

public enum class KeptField {
    Name,
    Command,
    Directory,
    Port,
    Limit,
}

/**
 * Same files and validation as Flutter's LinuxServiceDef. No saved value is executable metadata.
 */
public data class KeptServiceDef(
    val name: String,
    val command: String,
    val title: String = name,
    val directory: String = "/root",
    val port: Int? = null,
    val restart: KeptRestart = KeptRestart.OnFailure,
    val stopSeconds: Int = 10,
    val catalog: String? = null,
    val environment: Map<String, String> = emptyMap(),
) {
    public fun invalid(others: List<KeptServiceDef>, new: Boolean): KeptField? {
        if (
            !validName(name) || title.isBlank() || !oneLine(title) || others.any { it.name == name }
        )
            return KeptField.Name
        if (new && others.size >= 16) return KeptField.Limit
        if (
            command.isBlank() ||
                command.length > 4096 ||
                !oneLine(command) ||
                stopSeconds !in 1..120
        )
            return KeptField.Command
        if (!directory.startsWith('/') || !oneLine(directory) || ".." in directory.split('/'))
            return KeptField.Directory
        if (
            environment.any {
                !Regex("^[A-Za-z_][A-Za-z0-9_]{0,63}$").matches(it.key) || !oneLine(it.value)
            }
        )
            return KeptField.Command
        if (
            port != null &&
                (port !in 1024..65535 || port in reservedPorts || others.any { it.port == port })
        )
            return KeptField.Port
        return null
    }

    public fun encode(): String = buildString {
        append("version=1\nname=$name\ntitle=$title\ncommand=$command\ndir=$directory\n")
        port?.let { append("port=$it\n") }
        append(
            "restart=${when (restart) { KeptRestart.Always -> "always"
 KeptRestart.OnFailure -> "on-failure"
 KeptRestart.Never -> "never" }}\nstop_timeout=$stopSeconds\n"
        )
        catalog?.takeIf(::oneLine)?.let { append("catalog=$it\n") }
        environment.forEach { (key, value) -> append("env.$key=$value\n") }
    }

    public companion object {
        public val reservedPorts: Set<Int> = setOf(4096, 4097, 6767, 8372, 8472)

        public fun oneLine(text: String): Boolean = text.none {
            it == '\n' || it == '\r' || it == '\u0000'
        }

        public fun validName(name: String): Boolean =
            Regex("^[a-z][a-z0-9-]{1,23}$").matches(name) &&
                name !in setOf("server", "svc", "linux-job", "phone-engine") &&
                listOf("agent-", "aiteam", "oc-").none(name::startsWith)

        public fun decode(name: String, text: String): KeptServiceDef? {
            if (!validName(name)) return null
            val values = fields(text)
            if (values["name"] != name) return null
            val def =
                KeptServiceDef(
                    name,
                    values["command"] ?: return null,
                    values["title"].orEmpty().ifBlank { name },
                    values["dir"] ?: "/root",
                    values["port"]?.toIntOrNull(),
                    when (values["restart"]) {
                        "always" -> KeptRestart.Always
                        "never" -> KeptRestart.Never
                        else -> KeptRestart.OnFailure
                    },
                    values["stop_timeout"]?.toIntOrNull() ?: 10,
                    values["catalog"],
                    values
                        .filterKeys { it.startsWith("env.") }
                        .mapKeys { it.key.removePrefix("env.") },
                )
            return def.takeIf { it.invalid(emptyList(), false) == null }
        }

        public fun fields(text: String): Map<String, String> =
            text
                .lineSequence()
                .filter { !it.startsWith('#') && it.indexOf('=') > 0 }
                .associate { it.substringBefore('=') to it.substringAfter('=') }
    }
}

public data class KeptService(
    val definition: KeptServiceDef,
    val wanted: Boolean,
    val phase: KeptPhase,
    val pid: Int? = null,
    val since: Instant? = null,
    val lastExitAt: Instant? = null,
    val nextTry: Instant? = null,
    val restarts: Int = 0,
    val lastExit: Int? = null,
) {
    public val showsStop: Boolean
        get() = wanted && phase !in setOf(KeptPhase.Failed, KeptPhase.Exited)

    public val paused: Boolean
        get() = wanted && phase == KeptPhase.Off

    public val urgency: Int
        get() =
            when (phase) {
                KeptPhase.Failed -> 0
                KeptPhase.PortBusy -> 1
                KeptPhase.Backoff -> 2
                KeptPhase.Starting,
                KeptPhase.Running,
                KeptPhase.Stopping -> 3
                else -> if (paused) 3 else 4
            }

    public companion object {
        public fun combine(
            def: KeptServiceDef,
            wanted: Boolean,
            text: String?,
            running: Boolean,
        ): KeptService {
            val fields = text?.let(KeptServiceDef::fields).orEmpty()
            val parsed =
                when (fields["phase"]) {
                    "off" -> KeptPhase.Off
                    "starting" -> KeptPhase.Starting
                    "running" -> KeptPhase.Running
                    "backoff" -> KeptPhase.Backoff
                    "port_busy" -> KeptPhase.PortBusy
                    "failed" -> KeptPhase.Failed
                    "exited" -> KeptPhase.Exited
                    "stopping" -> KeptPhase.Stopping
                    else -> null
                }.takeIf { fields.containsKey("held") }
            val phase =
                if (!running || parsed == null) {
                    if (wanted) KeptPhase.Starting else KeptPhase.Off
                } else if (wanted && fields["held"] != "1" && parsed == KeptPhase.Off)
                    KeptPhase.Starting
                else parsed
            fun instant(key: String): Instant? =
                fields[key]
                    ?.toLongOrNull()
                    ?.takeIf { it > 0 && it < Long.MAX_VALUE / 1000 }
                    ?.let { Instant.fromEpochMilliseconds(it * 1000) }
            val hasPid = running && phase in setOf(KeptPhase.Running, KeptPhase.Stopping)
            return KeptService(
                def,
                wanted,
                phase,
                fields["pid"]?.toIntOrNull()?.takeIf { hasPid && it > 1 },
                instant("since").takeIf { hasPid },
                instant("last_exit_at"),
                instant("next_try").takeIf {
                    phase in setOf(KeptPhase.Backoff, KeptPhase.PortBusy)
                },
                fields["restarts"]?.toIntOrNull()?.coerceAtLeast(0) ?: 0,
                fields["last_exit"]?.toIntOrNull(),
            )
        }
    }
}

/** Device-wide serial owner. Files are app-private; no server is launched to read them. */
public interface KeptServices {
    public suspend fun read(): Outcome<List<KeptService>>

    public suspend fun save(definition: KeptServiceDef): Outcome<Unit>

    public suspend fun act(name: String, action: KeptAct): Outcome<Unit>

    public suspend fun log(name: String): Outcome<String>

    public suspend fun copyLog(name: String): Outcome<Unit>

    public suspend fun ensureSupervisor(): Outcome<Unit>
}

/**
 * A platform without built-in Linux refuses every operation and never invents an empty inventory.
 */
public class UnavailableKeptServices : KeptServices {
    private fun unavailable(): Outcome.Failed =
        Outcome.Failed(
            io.github.eslamasabry.oc.core.common.Failure.Unavailable(
                io.github.eslamasabry.oc.core.common.FeatureId("builtinLinux")
            )
        )

    override suspend fun read(): Outcome<List<KeptService>> = unavailable()

    override suspend fun save(definition: KeptServiceDef): Outcome<Unit> = unavailable()

    override suspend fun act(name: String, action: KeptAct): Outcome<Unit> = unavailable()

    override suspend fun log(name: String): Outcome<String> = unavailable()

    override suspend fun copyLog(name: String): Outcome<Unit> = unavailable()

    override suspend fun ensureSupervisor(): Outcome<Unit> = unavailable()
}
