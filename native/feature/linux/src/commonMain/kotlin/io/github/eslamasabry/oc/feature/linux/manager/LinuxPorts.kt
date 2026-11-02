package io.github.eslamasabry.oc.feature.linux.manager

/*
 * Which ports have something listening inside the app's own Linux (port of lib/builtin/linux_manager/linux_ports.dart
 * and the shared detection in lib/domain/listening_ports.dart). Android hides /proc/net/tcp on recent versions, so the
 * script collects candidate ports (the kernel table when readable, the numbers socket-holding programs name, the usual
 * dev-server ports) and then connects to each one on loopback, the way the preview will. A port is listed only when
 * that connection worked, so a listing is a fact, not a guess (B-LINUX-085..087).
 */

/** What a program in Linux is, from its command line (port of `LinuxProcess.kind`, B-LINUX-082). */
public enum class LinuxProgramKind { OpenCodeServer, Paseo, Agent, DevServer, Other }

public object LinuxPrograms {
    private val server = Regex("""(^|[/ ])opencode2?( |$)|/opt/opencode2?/|@opencode/cli""")
    private val serve = Regex("""(^| )serve( |$)""")
    private val paseo = Regex("""(^|[/ ])paseo( |$)|oc-paseo|/paseo/""")
    private val agent =
        Regex(
            """(^|[/ ])(claude|claude-code|codex|gemini|aider|goose)( |$)|""" +
                """@anthropic-ai/claude-code|/opt/oc-claude|/opt/aiteam|(^|[/ ])(gc|dolt|bd)( |$)|""" +
                """(^|[/ ])opencode2? (acp|run)( |$)""",
        )
    private val dev =
        Regex(
            """(^|[/ ])(vite|next|nuxt|astro|webpack|webpack-dev-server|parcel|""" +
                """esbuild|nodemon|ts-node-dev|http-server|live-server|serve|uvicorn|""" +
                """gunicorn|flask|django-admin|rails|puma|php|hugo|jekyll|mkdocs)( |$)|""" +
                """npm (run )?(dev|start|serve|watch)|yarn (run )?(dev|start)|""" +
                """pnpm (run )?(dev|start)|python3? -m http\.server|manage\.py runserver|""" +
                """cargo (run|watch)|go run|dotnet watch""",
        )

    public fun kindOf(cmd: String): LinuxProgramKind =
        when {
            server.containsMatchIn(cmd) && serve.containsMatchIn(cmd) -> LinuxProgramKind.OpenCodeServer
            paseo.containsMatchIn(cmd) -> LinuxProgramKind.Paseo
            agent.containsMatchIn(cmd) -> LinuxProgramKind.Agent
            dev.containsMatchIn(cmd) -> LinuxProgramKind.DevServer
            else -> LinuxProgramKind.Other
        }

    private val runners = setOf("node", "nodejs", "python", "python3", "sh", "bash", "dash", "env", "npx")
    private val scriptExt = Regex("""\.(m?js|cjs|ts|py)$""")

    /** The program a person would recognise: the script a runner runs ("vite"), `npm run dev`, or the name. */
    public fun programName(cmd: String): String {
        val tokens = cmd.split(' ').filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return ""
        val name = tokens.first().substringAfterLast('/')
        if (name in setOf("npm", "yarn", "pnpm")) return tokens.take(3).joinToString(" ") { it.substringAfterLast('/') }
        if (name in runners) {
            for (t in tokens.drop(1)) {
                if (t.startsWith("-") || t == "run" || t == "exec") continue
                val base = t.split('/').lastOrNull { it.isNotEmpty() } ?: continue
                return base.replace(scriptExt, "")
            }
        }
        return name
    }
}

/** The process that names a port: enough to say who uses it. */
public data class PortOwner(val pid: Int, val cmd: String) {
    val kind: LinuxProgramKind get() = LinuxPrograms.kindOf(cmd)
    val name: String get() = LinuxPrograms.programName(cmd)
}

/** One port with something listening. */
public data class LinuxPort(
    val port: Int,
    val owner: PortOwner? = null,
    /** True when it listens on every address, false when only on the phone, null when the table was unreadable. */
    val allInterfaces: Boolean? = null,
) {
    /** What the page calls the owner, or null when nothing names it. */
    val kind: LinuxProgramKind?
        get() = if (port == OWN_SERVER_PORT) LinuxProgramKind.OpenCodeServer else owner?.kind

    /** Dev servers and anything unknown can be previewed. The OpenCode server and Paseo do not serve web pages. */
    val canPreview: Boolean
        get() = kind != LinuxProgramKind.OpenCodeServer && kind != LinuxProgramKind.Paseo

    /** The loopback address the preview opens (app-authored, never from the guest). */
    val previewUrl: String get() = "http://127.0.0.1:$port/"

    public companion object {
        /** The in-app OpenCode server's own port. */
        public const val OWN_SERVER_PORT: Int = 4097
    }
}

public object LinuxPortScan {
    /** The usual dev-server ports, always tried (framework defaults), plus the phone server's own. */
    public val usualPorts: List<Int> =
        listOf(
            1313, 3000, 3001, 3333, 4000, 4173, 4200, 4321, 5000, 5001, 5173, 5174, 5500, 6006, 7000, 8000, 8001, 8008,
            8080, 8081, 8088, 8888, 9000, 9090, LinuxPort.OWN_SERVER_PORT,
        )

    private val portInCommand = Regex("""(?:--port[= ]|-p[= ]?|PORT=|:)(\d{2,5})(?!\d)|(?:^| )(\d{4,5})(?= |$)""")

    /** Port numbers a command line names (`--port 5173`, `-p 3000`, `PORT=8081`, `host:9229`, a bare number). 1024+ only. */
    public fun portsNamedIn(cmd: String): Set<Int> =
        portInCommand.findAll(cmd).mapNotNull { m ->
            val text = m.groups[1]?.value ?: m.groups[2]?.value ?: return@mapNotNull null
            text.toIntOrNull()?.takeIf { it in 1024..65535 }
        }.toSet()

    /**
     * The probe script (POSIX sh, bash for the loopback connects). Only numbers and printable command lines leave it.
     * Lines: `::listen <hexaddr> <hexport>`, `::n <pid> <port>`, `::c <pid> <cmdline>`, `::open <port>`.
     */
    public fun script(extra: Iterable<Int> = emptyList()): String {
        val ports = (usualPorts + extra.filter { it in 1024..65535 }).distinct().sorted().joinToString(" ")
        return $$"""# oc-linux-ports
for f in /proc/net/tcp /proc/net/tcp6; do
  [ -r "$f" ] || continue
  while read -r oc_sl oc_local oc_rem oc_st oc_rest; do
    [ "$oc_st" = 0A ] || continue
    printf '::listen %s %s\n' "${oc_local%%:*}" "${oc_local##*:}"
  done < "$f"
done 2>/dev/null
oc_named=
for p in $(ls -ld /proc/[0-9]*/fd/* 2>/dev/null | grep -F 'socket:[' | sed -n 's#^.* /proc/\([0-9]*\)/fd/.*#\1#p' | sort -un); do
oc_n=$( {
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
oc_any=
for oc_q in $oc_n; do
  [ "$oc_q" -ge 1024 ] && [ "$oc_q" -le 65535 ] || continue
  printf '::n %s %s\n' "$p" "$oc_q"
  oc_named="$oc_named $oc_q"
  oc_any=1
done
[ -n "$oc_any" ] && printf '::c %s %s\n' "$p" "$(tr '\000' ' ' <"/proc/$p/cmdline" 2>/dev/null | tr -cd '[:print:]' | cut -c1-240)"
done
bash -c 'for q in $(printf "%s\n" "$@" | sort -un); do : 2>/dev/null 3<>/dev/tcp/127.0.0.1/$q && echo "::open $q"; done' oc-ports $$ports $oc_named 2>/dev/null
exit 0
"""
    }

    /** The ports found by [script]. A listener the kernel shows but the probe could not reach is not offered. */
    public fun parse(output: String): List<LinuxPort> {
        val open = mutableSetOf<Int>()
        val wide = mutableMapOf<Int, Boolean>()
        val named = mutableMapOf<Int, MutableSet<Int>>()
        val cmds = mutableMapOf<Int, String>()
        val zeros = Regex("^0+$")
        for (line in output.split('\n')) {
            when {
                line.startsWith("::n ") -> {
                    val parts = line.substring(4).trim().split(' ')
                    if (parts.size != 2) continue
                    val pid = parts[0].toIntOrNull() ?: continue
                    val port = parts[1].toIntOrNull() ?: continue
                    named.getOrPut(port) { mutableSetOf() }.add(pid)
                }
                line.startsWith("::c ") -> {
                    val rest = line.substring(4)
                    val space = rest.indexOf(' ')
                    if (space <= 0) continue
                    val pid = rest.substring(0, space).toIntOrNull() ?: continue
                    cmds[pid] = rest.substring(space + 1).trim()
                }
                line.startsWith("::open ") -> {
                    val p = line.substring(7).trim().toIntOrNull() ?: continue
                    if (p in 1..65535) open.add(p)
                }
                line.startsWith("::listen ") -> {
                    val parts = line.substring(9).trim().split(' ')
                    if (parts.size != 2) continue
                    val p = parts[1].toIntOrNull(16) ?: continue
                    // 0.0.0.0 / :: are all zeros; 127.0.0.1 is 0100007F.
                    wide[p] = (wide[p] ?: false) || zeros.matches(parts[0])
                }
            }
        }
        return open.sorted().map { port ->
            LinuxPort(
                port = port,
                owner = ownerOf(port, named[port].orEmpty(), cmds),
                allInterfaces = if (wide.isEmpty()) null else (wide[port] ?: false),
            )
        }
    }

    /** The newest process (highest pid) that names the port, from its sockets or its own command line. */
    private fun ownerOf(port: Int, namers: Set<Int>, cmds: Map<Int, String>): PortOwner? =
        cmds.entries
            .filter { (pid, cmd) -> pid in namers || port in portsNamedIn(cmd) }
            .maxByOrNull { it.key }
            ?.let { PortOwner(it.key, it.value) }
}
