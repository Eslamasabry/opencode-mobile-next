package io.github.eslamasabry.oc.engine.runtime.procs

/**
 * Port of lib/domain/listening_ports.dart (dev servers running on the in-app Linux whoever started
 * them: the script that lists them, [ListeningPorts.script], and the parser for what it prints,
 * [ListeningPorts.parse]).
 *
 * Android 10+ denies apps `/proc/net/tcp{,6}` (SELinux), so the socket tables are a bonus, not the
 * source. The script finds the processes that hold a socket (`/proc/<pid>/fd/`), collects the ports
 * they name (command line, `PORT=`-style variables — only the number is printed — and ports their
 * log file mentions), then tries each of those plus [COMMON_DEV_PORTS] on loopback; a port is
 * listed only when that connection worked. The script is POSIX sh; only the loopback probe runs in
 * bash (for `/dev/tcp`).
 */
public data class ListeningSocket(
    val port: Int,
    val inode: Long,
    /** Bound to 127.x or ::1 only, so another machine cannot reach it. */
    val loopbackOnly: Boolean,
)

/** A program on this machine that listens on one or more ports. */
public data class LocalServerProcess(
    /** Zero when only a connection probe found the port: no process known. */
    val pid: Int,
    val command: String,
    val ports: List<Int>,
    val cwd: String? = null,
    /**
     * The regular file this program's standard output is written to, when it is one. Null for a
     * terminal or pipe.
     */
    val logPath: String? = null,
    val loopbackOnly: Boolean = true,
) {
    val hasProcess: Boolean
        get() = pid > 0
}

/**
 * The usual dev-server ports, always tried: framework defaults (Vite, Next, Astro, Storybook, Hugo,
 * Flask…).
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

private val PORT_IN_COMMAND =
    Regex("""(?:--port[= ]|-p[= ]?|PORT=|:)(\d{2,5})(?!\d)|(?:^| )(\d{4,5})(?= |$)""")

/**
 * Port numbers a command line names (`--port 5173`, `-p 3000`, `PORT=8081`, `host:9229`, a bare
 * number word).
 */
public fun portsNamedIn(cmd: String): Set<Int> =
    PORT_IN_COMMAND.findAll(cmd)
        .mapNotNull { m ->
            val raw = m.groups[1]?.value ?: m.groups[2]?.value
            raw?.toIntOrNull()?.takeIf { it in 1024..65535 }
        }
        .toCollection(LinkedHashSet())

/**
 * POSIX sh that prints `::n <pid> <port>` for each port process `$p` names and adds them to
 * `$oc_named`. Only numbers leave it. Verbatim from listening_ports.dart `namedPortsShell`.
 */
public const val NAMED_PORTS_SHELL: String =
    $$"""oc_n=$( {
  tr '\000' '\n' <"/proc/$p/cmdline" 2>/dev/null | tr -c '0-9\n' '\n'
  tr '\000' '\n' <"/proc/$p/environ" 2>/dev/null | sed -n 's/^[A-Z_]*PORT=\([0-9][0-9]*\)$/\1/p'
  for oc_f in 1 2; do
    oc_t=$(readlink "/proc/$p/fd/$oc_f" 2>/dev/null)
    case "$oc_t" in
      /dev/*|/proc/*|/sys/*|*' (deleted)') ;;
      /*) [ -f "$oc_t" ] && tail -c 8192 "$oc_t" 2>/dev/null | grep -oE '(:|[Pp]ort )[0-9]+' | tr -c '0-9\n' '\n' ;;
    esac
  done
} | grep -E '^[0-9]{4,5}$' | sort -un | head -n 16)
for oc_q in $oc_n; do
  [ "$oc_q" -ge 1024 ] && [ "$oc_q" -le 65535 ] || continue
  printf '::n %s %s\n' "$p" "$oc_q"
  oc_named="$oc_named $oc_q"
done
"""

/**
 * Bash, from sh: prints `<marker><port>` for each of [fixed] and `$oc_named` that accepts a
 * loopback connection.
 */
public fun loopbackProbeShell(fixed: Iterable<Int>, marker: String = ""): String {
    val ports = fixed.filter { it in 1..65535 }.toSortedSet()
    return $$"""bash -c 'for q in $(printf "%s\n" "$@" | sort -un); do : 2>/dev/null 3<>/dev/tcp/127.0.0.1/$q && echo "$$marker$q"; done' oc-ports $${ports.joinToString(" ")} $oc_named 2>/dev/null"""
}

/** The one place that knows the script and how to read its answer. */
public object ListeningPorts {
    /** The in-app OpenCode server's own port: never listed as a dev server. */
    public const val OWN_SERVER_PORT: Int = 4097

    /** Program names that are the machine's own plumbing, not dev servers. */
    private val PLUMBING =
        setOf("sshd", "opencode", "opencode2", ".opencode", "paseo", "proot", "dropbear")
    private val CLIENTS = setOf("curl", "wget", "ssh", "scp", "nc", "ncat", "telnet", "git")
    private val SPACES = Regex("""\s+""")
    private val INODE = Regex("""/proc/(\d+)/fd/\d+ -> socket:\[(\d+)]""")

    /**
     * The script for the in-app Linux (POSIX sh). Prints sections the parser reads; every read may
     * fail silently. [socketTables] exists for tests that hide them, as Android does.
     */
    public fun script(
        probe: List<Int> = COMMON_DEV_PORTS,
        socketTables: List<String> = listOf("/proc/net/tcp", "/proc/net/tcp6"),
    ): String {
        val t4 = socketTables.firstOrNull() ?: "/nonexistent"
        val t6 = if (socketTables.size < 2) "/nonexistent" else socketTables[1]
        return $$"""echo '#T4'
grep -E '^ *[0-9]+: [0-9A-Fa-f]+:[0-9A-Fa-f]+ [0-9A-Fa-f:]+ 0A ' $$t4 2>/dev/null
echo '#T6'
grep -E '^ *[0-9]+: [0-9A-Fa-f]+:[0-9A-Fa-f]+ [0-9A-Fa-f:]+ 0A ' $$t6 2>/dev/null
echo '#S'
socks=$(ls -ld /proc/[0-9]*/fd/* 2>/dev/null | grep -F 'socket:[')
printf '%s\n' "$socks"
echo '#C'
oc_named=
for p in $(printf '%s\n' "$socks" | sed -n 's#^.* /proc/\([0-9]*\)/fd/.*#\1#p' | sort -un); do
  c=$(tr '\000\t\n' '   ' </proc/$p/cmdline 2>/dev/null | cut -c1-400)
  [ -n "$c" ] || continue
  printf '%s\t%s\t%s\t%s\n' "$p" "$c" "$(readlink /proc/$p/cwd 2>/dev/null)" "$(readlink /proc/$p/fd/1 2>/dev/null)"
$${NAMED_PORTS_SHELL}
done
echo '#P'
$${loopbackProbeShell(probe)}
true
"""
    }

    /** A listening socket row of `/proc/net/tcp` or `tcp6`, or null. */
    public fun parseSocketLine(line: String): ListeningSocket? {
        val f = line.trim().split(SPACES)
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
            // ::1, or ::ffff:127.x.x.x
            32 ->
                hex == "00000000000000000000000001000000" ||
                    (hex.startsWith("0000000000000000FFFF0000") && hex.endsWith("7F"))
            else -> false
        }

    /**
     * Reads what [script] printed. One entry per process, its ports sorted; ports found only by the
     * probe come last with pid 0. The in-app server's port and system plumbing are left out.
     */
    public fun parse(output: String): List<LocalServerProcess> {
        var section = ""
        val sockets = mutableListOf<ListeningSocket>()
        val owners = HashMap<Long, MutableSet<Int>>() // inode -> pids
        val info = HashMap<Int, List<String>>() // pid -> [cmd, cwd, out]
        val probed = LinkedHashSet<Int>()
        val named = HashMap<Int, MutableSet<Int>>() // port -> pids that name it
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
                    named.getOrPut(port) { mutableSetOf() }.add(pid)
                continue
            }
            when (section) {
                "#T4",
                "#T6" -> parseSocketLine(line)?.let(sockets::add)
                "#S" ->
                    INODE.find(line)?.let { m ->
                        val inode = m.groupValues[2].toLongOrNull()
                        val pid = m.groupValues[1].toIntOrNull()
                        if (inode != null && pid != null)
                            owners.getOrPut(inode) { mutableSetOf() }.add(pid)
                    }
                "#C" -> {
                    val parts = line.split('\t')
                    val pid = parts.first().toIntOrNull()
                    if (pid != null && parts.size >= 2) {
                        info[pid] =
                            listOf(
                                parts[1].trim(),
                                if (parts.size > 2) parts[2] else "",
                                if (parts.size > 3) parts[3] else "",
                            )
                    }
                }
                "#P" -> line.toIntOrNull()?.takeIf { it in 1..65535 }?.let(probed::add)
            }
        }
        val byPid = HashMap<Int, MutableSet<Int>>()
        val reachable = HashMap<Int, Boolean>()
        val known = mutableSetOf<Int>()
        for (socket in sockets) {
            known.add(socket.port)
            for (pid in owners[socket.inode].orEmpty()) {
                byPid.getOrPut(pid) { mutableSetOf() }.add(socket.port)
                reachable[pid] = (reachable[pid] ?: false) || !socket.loopbackOnly
            }
        }
        // Without the tables (Android 10+), a port that answered belongs to the process that named
        // it: the newest
        // one, the child that listens, and never a client such as curl.
        for (port in probed) {
            if (port in known || port == OWN_SERVER_PORT) continue
            val owner = namer(named[port], info) ?: continue
            known.add(port)
            byPid.getOrPut(owner) { mutableSetOf() }.add(port)
        }
        val out = mutableListOf<LocalServerProcess>()
        val claimed = mutableSetOf<Int>()
        for (pid in byPid.keys.sorted()) {
            val details = info[pid] ?: continue
            val ports = byPid.getValue(pid).sorted().filter { it != OWN_SERVER_PORT }
            if (ports.isEmpty() || isPlumbing(details[0])) continue
            claimed.addAll(ports)
            out.add(
                LocalServerProcess(
                    pid = pid,
                    command = details[0],
                    ports = ports,
                    cwd = details[1].ifEmpty { null },
                    logPath = regularFileOrNull(details[2]),
                    loopbackOnly = !(reachable[pid] ?: false),
                )
            )
        }
        val rest =
            probed.filter { it != OWN_SERVER_PORT && it !in claimed && it !in known }.sorted()
        for (port in rest) out.add(LocalServerProcess(pid = 0, command = "", ports = listOf(port)))
        return out
    }

    private fun firstWord(command: String): String =
        command.trim().split(' ').first().split('/').last()

    private fun namer(pids: Set<Int>?, info: Map<Int, List<String>>): Int? {
        if (pids == null) return null
        var best: Int? = null
        for (pid in pids) {
            val details = info[pid] ?: continue
            if (firstWord(details[0]) in CLIENTS) continue
            if (best == null || pid > best) best = pid
        }
        return best
    }

    private fun isPlumbing(command: String): Boolean = firstWord(command) in PLUMBING

    /**
     * [target] when it is an absolute path that can be a plain log file; null for terminals, pipes,
     * system paths.
     */
    public fun regularFileOrNull(target: String?): String? {
        if (target == null) return null
        val t = target.trim()
        if (!t.startsWith("/") || t.length > 1024 || t.contains('\n')) return null
        if (listOf("/dev/", "/proc/", "/sys/").any { t.startsWith(it) }) return null
        if (t.endsWith(" (deleted)")) return null
        return t
    }

    /** A single-quoted shell word: a quote inside cannot end the quoting. */
    public fun shellQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

    /**
     * Reads the end of [path] (at most 64 KB). Refuses (`exit 2`) anything that is not a plain file
     * path.
     */
    public fun tailScript(path: String): String {
        val safe = regularFileOrNull(path) ?: return "exit 2"
        return "[ -f ${shellQuote(safe)} ] || exit 3; tail -c 65536 -- ${shellQuote(safe)}"
    }

    /**
     * Signals [pid] only while its command line still starts with what the person saw, so a reused
     * pid is never hit. TERM, then KILL if it holds on after two seconds. Exit 0 ended/gone, 4
     * changed, 5 still running, 2 refused.
     */
    public fun stopScript(pid: Int, expectedCommand: String): String {
        if (pid <= 1 || expectedCommand.isBlank()) return "exit 2"
        val want =
            if (expectedCommand.length > 200) expectedCommand.substring(0, 200) else expectedCommand
        return $$"""now=$(tr '\0\t\n' '   ' </proc/$$pid/cmdline 2>/dev/null)
[ -n "$now" ] || exit 0
case "$now" in $${shellQuote(want)}*) ;; *) exit 4;; esac
kill -TERM $$pid 2>/dev/null
for i in 1 2 3 4; do [ -d /proc/$$pid ] || exit 0; sleep 0.5; done
kill -KILL $$pid 2>/dev/null
sleep 0.3
[ -d /proc/$$pid ] && exit 5
exit 0
"""
    }

    private val LOCAL_LINK =
        Regex(
            """https?://(?:localhost|127\.0\.0\.1|0\.0\.0\.0|\[::1]):(\d{1,5})[^\s<>()\[\]`'"]*""",
            RegexOption.IGNORE_CASE,
        )

    /** The ports mentioned as local links in [text] (a server's own output), sorted, each once. */
    public fun portsInOutput(text: String): List<Int> =
        LOCAL_LINK.findAll(text)
            .mapNotNull { it.groupValues[1].toIntOrNull()?.takeIf { p -> p in 1..65535 } }
            .toSortedSet()
            .toList()
}
