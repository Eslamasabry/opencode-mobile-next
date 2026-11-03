package io.github.eslamasabry.oc.engine.project.devserver

/*
 * Reading what the in-app Linux's listening-ports script printed (port of ListeningPorts.parse in
 * lib/domain/listening_ports.dart). Sections: #T4/#T6 socket tables, #S socket owners, #C processes
 * (`pid\tcmd\tcwd\tstdout`), #P ports that answered on loopback, and `::n pid port` lines for ports a process names.
 */

/** A program on this machine that listens on one or more ports. [pid] is 0 when only a probe found the port. */
public data class LocalServerProcess(
    val pid: Int,
    val command: String,
    val ports: List<Int>,
    val cwd: String? = null,
    val logPath: String? = null,
    val loopbackOnly: Boolean = true,
) {
    val hasProcess: Boolean get() = pid > 0

    /** Never prints the command line: it may hold a person's arguments. */
    override fun toString(): String = "LocalServerProcess(pid=$pid, ports=$ports)"
}

public object ListeningPorts {
    /** The in-app OpenCode server's own port: never listed as a dev server. */
    public const val OWN_SERVER_PORT: Int = 4097

    private val PLUMBING = setOf("sshd", "opencode", "paseo", "proot", "dropbear")
    private val CLIENTS = setOf("curl", "wget", "ssh", "scp", "nc", "ncat", "telnet", "git")
    private val INODE = Regex("/proc/(\\d+)/fd/\\d+ -> socket:\\[(\\d+)]")
    private val SPACES = Regex("\\s+")

    private data class Socket(val port: Int, val inode: Int, val loopbackOnly: Boolean)

    private fun parseSocket(line: String): Socket? {
        val f = line.trim().split(SPACES)
        if (f.size < 10 || f[3] != "0A") return null
        val local = f[1]
        val colon = local.lastIndexOf(':')
        if (colon < 0) return null
        val port = local.substring(colon + 1).toIntOrNull(16) ?: return null
        val inode = f[9].toIntOrNull() ?: return null
        if (port !in 1..65535) return null
        return Socket(port, inode, loopback(local.substring(0, colon).uppercase()))
    }

    private fun loopback(hex: String): Boolean =
        when (hex.length) {
            8 -> hex.endsWith("7F")
            32 -> hex == "00000000000000000000000001000000" || (hex.startsWith("0000000000000000FFFF0000") && hex.endsWith("7F"))
            else -> false
        }

    /** One entry per process, its ports sorted; probe-only ports last with pid 0. The app's server and plumbing are left out. */
    public fun parse(output: String): List<LocalServerProcess> {
        var section = ""
        val sockets = ArrayList<Socket>()
        val owners = HashMap<Int, MutableSet<Int>>()
        val info = HashMap<Int, List<String>>()
        val probed = LinkedHashSet<Int>()
        val named = HashMap<Int, MutableSet<Int>>()
        for (raw in output.split('\n')) {
            val line = raw.trimEnd()
            if (line.startsWith("#") && line.length <= 3) {
                section = line
                continue
            }
            if (line.isEmpty()) continue
            if (line.startsWith("::n ")) {
                val parts = line.substring(4).trim().split(' ')
                val pid = if (parts.size == 2) parts[0].toIntOrNull() else null
                val port = if (parts.size == 2) parts[1].toIntOrNull() else null
                if (pid != null && port != null && port in 1..65535) named.getOrPut(port) { HashSet() }.add(pid)
                continue
            }
            when (section) {
                "#T4", "#T6" -> parseSocket(line)?.let { sockets += it }
                "#S" -> INODE.find(line)?.let { owners.getOrPut(it.groupValues[2].toInt()) { HashSet() }.add(it.groupValues[1].toInt()) }
                "#C" -> {
                    val parts = line.split('\t')
                    val pid = parts.first().toIntOrNull()
                    if (pid != null && parts.size >= 2) info[pid] = listOf(parts[1].trim(), parts.getOrElse(2) { "" }, parts.getOrElse(3) { "" })
                }
                "#P" -> line.toIntOrNull()?.takeIf { it in 1..65535 }?.let { probed += it }
            }
        }
        val byPid = HashMap<Int, MutableSet<Int>>()
        val reachable = HashMap<Int, Boolean>()
        val known = HashSet<Int>()
        for (socket in sockets) {
            known += socket.port
            for (pid in owners[socket.inode].orEmpty()) {
                byPid.getOrPut(pid) { HashSet() }.add(socket.port)
                reachable[pid] = (reachable[pid] ?: false) || !socket.loopbackOnly
            }
        }
        // Without the tables (Android 10+), a port that answered belongs to the newest non-client process that named it.
        for (port in probed) {
            if (port in known || port == OWN_SERVER_PORT) continue
            val owner = namer(named[port], info) ?: continue
            known += port
            byPid.getOrPut(owner) { HashSet() }.add(port)
        }
        val out = ArrayList<LocalServerProcess>()
        val claimed = HashSet<Int>()
        for (pid in byPid.keys.sorted()) {
            val details = info[pid] ?: continue
            val ports = byPid.getValue(pid).sorted().filter { it != OWN_SERVER_PORT }
            if (ports.isEmpty() || firstWord(details[0]) in PLUMBING) continue
            claimed += ports
            out +=
                LocalServerProcess(
                    pid = pid,
                    command = details[0],
                    ports = ports,
                    cwd = details[1].ifEmpty { null },
                    logPath = regularFileOrNull(details[2]),
                    loopbackOnly = !(reachable[pid] ?: false),
                )
        }
        for (port in probed.filter { it != OWN_SERVER_PORT && it !in claimed && it !in known }.sorted()) {
            out += LocalServerProcess(pid = 0, command = "", ports = listOf(port))
        }
        return out
    }

    private fun firstWord(command: String): String = command.trim().split(' ').first().substringAfterLast('/')

    private fun namer(pids: Set<Int>?, info: Map<Int, List<String>>): Int? =
        pids.orEmpty().filter { pid -> info[pid]?.let { firstWord(it[0]) !in CLIENTS } ?: false }.maxOrNull()

    /** [target] when it can be a plain log file; null for terminals, pipes, sockets and system paths. */
    public fun regularFileOrNull(target: String?): String? {
        val t = target?.trim() ?: return null
        if (!t.startsWith("/") || t.length > 1024 || '\n' in t) return null
        if (listOf("/dev/", "/proc/", "/sys/").any { t.startsWith(it) } || t.endsWith(" (deleted)")) return null
        return t
    }
}
