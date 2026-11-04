package io.github.eslamasabry.oc.engine.runtime.devservers

/*
 * Dev servers running in the in-app Linux whoever started them: the script that lists them and the parser for what it
 * prints (port of lib/domain/listening_ports.dart). Pure Kotlin, so Development services, the dev preview, the
 * conversation menu and Linux › Ports share one reading.
 *
 * Android 10+ denies apps /proc/net/tcp{,6}, so the socket tables are a bonus, not the source. The script finds the
 * processes that hold a socket (the fd links under /proc/<pid>/fd), collects the ports they name (command line, *PORT= variables, their
 * log file) and tries each of those plus [ListeningPorts.COMMON_DEV_PORTS] on loopback; a port is listed only when that
 * connection worked. The script runs under /bin/sh (dash on Ubuntu); only the loopback probe runs in bash.
 *
 * Scripts are written with `¤` for the shell's dollar sign so Kotlin templates never touch them.
 */

/** One listening TCP socket from a /proc/net/tcp table. */
public data class ListeningSocket(val port: Int, val inode: Long, val loopbackOnly: Boolean)

/** A program on this machine that listens on one or more ports. */
public data class LocalServerProcess(
    /** Zero when only a connection probe found the port: no process known. */
    val pid: Int,
    val command: String,
    val ports: List<Int>,
    val cwd: String? = null,
    /**
     * The regular file its standard output goes to (a server started by `cmd > file`); null for a
     * terminal or pipe.
     */
    val logPath: String? = null,
    val loopbackOnly: Boolean = true,
) {
    val hasProcess: Boolean
        get() = pid > 0
}

public object ListeningPorts {
    /** The in-app OpenCode server's own port: never listed as a dev server. */
    public const val OWN_SERVER_PORT: Int = 4097

    /**
     * The usual dev-server ports, always tried: framework defaults (Vite, Next, Astro, Storybook,
     * Hugo, Flask…).
     */
    public val COMMON_DEV_PORTS: List<Int> =
        listOf(
            1313,
            3000,
            3001,
            3333,
            4000,
            4173,
            4200,
            4321,
            5000,
            5001,
            5173,
            5174,
            5500,
            6006,
            7000,
            8000,
            8001,
            8008,
            8080,
            8081,
            8088,
            8888,
            9000,
            9090,
        )

    private val plumbing =
        setOf("sshd", "opencode", "opencode2", ".opencode", "paseo", "proot", "dropbear")
    private val clients = setOf("curl", "wget", "ssh", "scp", "nc", "ncat", "telnet", "git")

    /**
     * POSIX sh that prints `::n <pid> <port>` for each port process `$p` names and adds them to
     * `$oc_named`. Only numbers leave it.
     */
    private val namedPortsShell =
        """
oc_n=¤( {
  tr '\000' '\n' <"/proc/¤p/cmdline" 2>/dev/null | tr -c '0-9\n' '\n'
  tr '\000' '\n' <"/proc/¤p/environ" 2>/dev/null | sed -n 's/^[A-Z_]*PORT=\([0-9][0-9]*\)¤/\1/p'
  for oc_f in 1 2; do
    oc_t=¤(readlink "/proc/¤p/fd/¤oc_f" 2>/dev/null)
    case "¤oc_t" in
      /dev/*|/proc/*|/sys/*|*' (deleted)') ;;
      /*) [ -f "¤oc_t" ] && tail -c 8192 "¤oc_t" 2>/dev/null | grep -oE '(:|[Pp]ort )[0-9]+' | tr -c '0-9\n' '\n' ;;
    esac
  done
} | grep -E '^[0-9]{4,5}¤' | sort -un | head -n 16)
for oc_q in ¤oc_n; do
  [ "¤oc_q" -ge 1024 ] && [ "¤oc_q" -le 65535 ] || continue
  printf '::n %s %s\n' "¤p" "¤oc_q"
  oc_named="¤oc_named ¤oc_q"
done
"""
            .removePrefix("\n")

    /**
     * Bash, from sh: prints `<marker><port>` for each of [fixed] and `$oc_named` that accepts a
     * loopback connection.
     */
    public fun loopbackProbeShell(fixed: Iterable<Int>, marker: String = ""): String {
        val ports = fixed.filter { it in 1..65535 }.toSortedSet().joinToString(" ")
        return ("bash -c 'for q in ¤(printf \"%s\\n\" \"¤@\" | sort -un); do " +
                ": 2>/dev/null 3<>/dev/tcp/127.0.0.1/¤q && echo \"{MARKER}¤q\"; done' " +
                "oc-ports {PORTS} ¤oc_named 2>/dev/null")
            .dollars()
            .replace("{MARKER}", marker)
            .replace("{PORTS}", ports)
    }

    /**
     * The script for the in-app Linux (POSIX sh). Prints sections the parser reads; every read may
     * fail silently. [socketTables] exists for tests that hide them, as Android does.
     */
    public fun script(
        probe: List<Int> = COMMON_DEV_PORTS,
        socketTables: List<String> = listOf("/proc/net/tcp", "/proc/net/tcp6"),
    ): String {
        val t4 = socketTables.getOrNull(0) ?: "/nonexistent"
        val t6 = socketTables.getOrNull(1) ?: "/nonexistent"
        return """
echo '#T4'
grep -E '^ *[0-9]+: [0-9A-Fa-f]+:[0-9A-Fa-f]+ [0-9A-Fa-f:]+ 0A ' {T4} 2>/dev/null
echo '#T6'
grep -E '^ *[0-9]+: [0-9A-Fa-f]+:[0-9A-Fa-f]+ [0-9A-Fa-f:]+ 0A ' {T6} 2>/dev/null
echo '#S'
socks=¤(ls -ld /proc/[0-9]*/fd/* 2>/dev/null | grep -F 'socket:[')
printf '%s\n' "¤socks"
echo '#C'
oc_named=
for p in ¤(printf '%s\n' "¤socks" | sed -n 's#^.* /proc/\([0-9]*\)/fd/.*#\1#p' | sort -un); do
  c=¤(tr '\000\t\n' '   ' </proc/¤p/cmdline 2>/dev/null | cut -c1-400)
  [ -n "¤c" ] || continue
  printf '%s\t%s\t%s\t%s\n' "¤p" "¤c" "¤(readlink /proc/¤p/cwd 2>/dev/null)" "¤(readlink /proc/¤p/fd/1 2>/dev/null)"
{NAMED}done
echo '#P'
{PROBE}
true
"""
            .removePrefix("\n")
            .dollars()
            .replace("{T4}", t4)
            .replace("{T6}", t6)
            .replace("{NAMED}", namedPortsShell.dollars())
            .replace("{PROBE}", loopbackProbeShell(probe))
    }

    private fun String.dollars(): String = replace('¤', '$')

    private val whitespace = Regex("""\s+""")

    /** A listening socket row of /proc/net/tcp or tcp6, or null. */
    public fun parseSocketLine(line: String): ListeningSocket? {
        val f = line.trim().split(whitespace)
        if (f.size < 10 || f[3] != "0A") return null
        val local = f[1]
        val colon = local.lastIndexOf(':')
        if (colon < 0) return null
        val address = local.substring(0, colon)
        val port = local.substring(colon + 1).toIntOrNull(16)
        val inode = f[9].toLongOrNull()
        if (port == null || port < 1 || port > 65535 || inode == null) return null
        return ListeningSocket(port, inode, loopback(address.uppercase()))
    }

    private fun loopback(hex: String): Boolean =
        when (hex.length) {
            8 -> hex.endsWith("7F")
            32 ->
                hex == "00000000000000000000000001000000" ||
                    // ::ffff:127.x.x.x
                    (hex.startsWith("0000000000000000FFFF0000") && hex.endsWith("7F"))
            else -> false
        }

    private val inodePattern = Regex("""/proc/(\d+)/fd/\d+ -> socket:\[(\d+)]""")

    /**
     * Reads what [script] printed. One entry per process, its ports sorted; ports found only by the
     * probe come last with pid 0. The in-app server's port and system plumbing are left out.
     */
    public fun parse(output: String): List<LocalServerProcess> {
        var section = ""
        val sockets = mutableListOf<ListeningSocket>()
        val owners = mutableMapOf<Long, MutableSet<Int>>() // inode -> pids
        val info = mutableMapOf<Int, List<String>>() // pid -> [cmd, cwd, out]
        val probed = linkedSetOf<Int>()
        val named = mutableMapOf<Int, MutableSet<Int>>() // port -> pids that name it
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
                if (pid != null && port != null && port in 1..65535)
                    named.getOrPut(port) { mutableSetOf() } += pid
                continue
            }
            when (section) {
                "#T4",
                "#T6" -> parseSocketLine(line)?.let { sockets += it }
                "#S" ->
                    inodePattern.find(line)?.let { m ->
                        val inode = m.groupValues[2].toLongOrNull()
                        val pid = m.groupValues[1].toIntOrNull()
                        if (inode != null && pid != null)
                            owners.getOrPut(inode) { mutableSetOf() } += pid
                    }
                "#C" -> {
                    val parts = line.split('\t')
                    val pid = parts.first().toIntOrNull()
                    if (pid != null && parts.size >= 2) {
                        info[pid] =
                            listOf(
                                parts[1].trim(),
                                parts.getOrElse(2) { "" },
                                parts.getOrElse(3) { "" },
                            )
                    }
                }
                "#P" -> line.toIntOrNull()?.takeIf { it in 1..65535 }?.let { probed += it }
            }
        }
        val byPid = mutableMapOf<Int, MutableSet<Int>>()
        val reachable = mutableMapOf<Int, Boolean>()
        val known = mutableSetOf<Int>()
        for (socket in sockets) {
            known += socket.port
            for (pid in owners[socket.inode].orEmpty()) {
                byPid.getOrPut(pid) { mutableSetOf() } += socket.port
                reachable[pid] = (reachable[pid] ?: false) || !socket.loopbackOnly
            }
        }
        // Without the tables (Android 10+), a port that answered belongs to the process that named
        // it: the newest
        // one, the child that listens, and never a client such as curl.
        for (port in probed) {
            if (port in known || port == OWN_SERVER_PORT) continue
            val owner = namer(named[port], info) ?: continue
            known += port
            byPid.getOrPut(owner) { mutableSetOf() } += port
        }
        val out = mutableListOf<LocalServerProcess>()
        val claimed = mutableSetOf<Int>()
        for (pid in byPid.keys.sorted()) {
            val details = info[pid] ?: continue
            val ports = byPid.getValue(pid).sorted().filter { it != OWN_SERVER_PORT }
            if (ports.isEmpty() || isPlumbing(details[0])) continue
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
        probed
            .filter { it != OWN_SERVER_PORT && it !in claimed && it !in known }
            .sorted()
            .forEach { out += LocalServerProcess(pid = 0, command = "", ports = listOf(it)) }
        return out
    }

    private fun firstWord(command: String): String =
        command.trim().split(' ').first().substringAfterLast('/')

    private fun namer(pids: Set<Int>?, info: Map<Int, List<String>>): Int? =
        pids
            .orEmpty()
            .filter { pid -> info[pid]?.let { firstWord(it[0]) !in clients } == true }
            .maxOrNull()

    private fun isPlumbing(command: String): Boolean = firstWord(command) in plumbing

    /**
     * [target] when it is an absolute path that can be a plain log file; null for terminals, pipes
     * and system paths.
     */
    public fun regularFileOrNull(target: String?): String? {
        val t = target?.trim() ?: return null
        if (!t.startsWith("/") || t.length > 1024 || t.contains('\n')) return null
        if (listOf("/dev/", "/proc/", "/sys/").any { t.startsWith(it) }) return null
        if (t.endsWith(" (deleted)")) return null
        return t
    }

    private fun quote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

    /**
     * Reads the end of [path] (at most 64 KB). Refuses anything that is not a plain readable file.
     */
    public fun tailScript(path: String): String {
        val safe = regularFileOrNull(path) ?: return "exit 2"
        return "[ -f ${quote(safe)} ] || exit 3; tail -c 65536 -- ${quote(safe)}"
    }

    /**
     * Signals [pid] only while its command line still starts with what the person saw, so a reused
     * pid is never hit. TERM, then KILL if it holds on after two seconds. Exit 4: the pid now runs
     * something else; 5: it did not end.
     */
    public fun stopScript(pid: Int, expectedCommand: String): String {
        if (pid <= 1 || expectedCommand.isBlank()) return "exit 2"
        val want =
            if (expectedCommand.length > 200) expectedCommand.substring(0, 200) else expectedCommand
        return """
now=¤(tr '\0\t\n' '   ' </proc/{PID}/cmdline 2>/dev/null)
[ -n "¤now" ] || exit 0
case "¤now" in {WANT}*) ;; *) exit 4;; esac
kill -TERM {PID} 2>/dev/null
for i in 1 2 3 4; do [ -d /proc/{PID} ] || exit 0; sleep 0.5; done
kill -KILL {PID} 2>/dev/null
sleep 0.3
[ -d /proc/{PID} ] && exit 5
exit 0
"""
            .removePrefix("\n")
            .dollars()
            .replace("{PID}", pid.toString())
            .replace("{WANT}", quote(want))
    }

    private val localLink =
        Regex(
            """https?://(?:localhost|127\.0\.0\.1|0\.0\.0\.0|\[::1]):(\d{1,5})[^\s<>()\[\]`'"]*""",
            RegexOption.IGNORE_CASE,
        )

    /** The ports mentioned as local links in [text] (a server's own output), sorted, once each. */
    public fun portsInOutput(text: String): List<Int> =
        localLink
            .findAll(text)
            .mapNotNull { it.groupValues[1].toIntOrNull()?.takeIf { port -> port in 1..65535 } }
            .toSortedSet()
            .toList()
}
