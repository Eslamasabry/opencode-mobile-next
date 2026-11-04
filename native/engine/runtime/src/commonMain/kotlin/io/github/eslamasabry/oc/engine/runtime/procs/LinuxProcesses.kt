package io.github.eslamasabry.oc.engine.runtime.procs

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RuntimeProblem
import kotlin.math.roundToLong

/**
 * Port of lib/builtin/linux_manager/linux_processes.dart (what runs inside the app's own Linux,
 * read from `/proc` by one shell script, [LINUX_PROCESS_SCAN_SCRIPT], and parsed by
 * [LinuxProcessScan.parse]; the stop-by-identity script [linuxStopScript] and its result parser
 * [LinuxStopResult.parse]).
 *
 * Why `/proc` and not `ps`: the base image may lack procps, and the identity of a process for a
 * later Stop is its pid together with its start time (a pid alone can be reused between the list
 * and the tap).
 */
public enum class LinuxProcessKind {
    OpenCodeServer,
    Paseo,
    Agent,
    DevServer,
    Other,
}

/** One process in the in-app Linux. */
public data class LinuxProcess(
    val pid: Int,
    val ppid: Int,
    /** The program's short name ("node", "paseo"). */
    val name: String,
    /** The full command line, arguments separated by one space. */
    val cmd: String,
    /**
     * Kernel start time in clock ticks (100 per second): with [pid] it names this process and no
     * later one.
     */
    val startTicks: Long,
    /** Processor time used so far, in clock ticks. */
    val cpuTicks: Long,
    val rssKb: Long,
    val elapsedSeconds: Long,
    val cwd: String? = null,
) {
    /**
     * The program a person would recognise: the script a runner runs ("vite"), `npm run dev`, or
     * the name.
     */
    val programName: String
        get() {
            val tokens = cmd.split(' ').filter { it.isNotEmpty() }
            if (tokens.isEmpty()) return name
            if (name in PACKAGE_RUNNERS)
                return tokens.take(3).joinToString(" ") { it.split('/').last() }
            if (name in RUNNERS) {
                for (t in tokens.drop(1)) {
                    if (t.startsWith("-") || t == "run" || t == "exec") continue
                    val base = t.split('/').lastOrNull { it.isNotEmpty() } ?: continue
                    return base.replaceFirst(SCRIPT_SUFFIX, "")
                }
            }
            return name
        }

    /** The identity a stop is sent with: `pid:startTicks`. */
    val identity: String
        get() = "$pid:$startTicks"

    val cpuSeconds: Long
        get() = cpuTicks / 100

    val memoryMb: Long?
        get() {
            if (rssKb <= 0) return null
            val mb = (rssKb + 512) / 1024
            return if (mb < 1) 1 else mb
        }

    /** What this process is, from its command line. */
    val kind: LinuxProcessKind
        get() =
            when {
                SERVER.containsMatchIn(cmd) && SERVE.containsMatchIn(cmd) ->
                    LinuxProcessKind.OpenCodeServer
                PASEO.containsMatchIn(cmd) -> LinuxProcessKind.Paseo
                AGENT.containsMatchIn(cmd) -> LinuxProcessKind.Agent
                DEV.containsMatchIn(cmd) -> LinuxProcessKind.DevServer
                else -> LinuxProcessKind.Other
            }

    /** The OpenCode server is stopped from This phone's server controls, never from this list. */
    val protected: Boolean
        get() = kind == LinuxProcessKind.OpenCodeServer

    private companion object {
        val RUNNERS =
            setOf("node", "nodejs", "python", "python3", "sh", "bash", "dash", "env", "npx")
        val PACKAGE_RUNNERS = setOf("npm", "yarn", "pnpm")
        val SCRIPT_SUFFIX = Regex("""\.(m?js|cjs|ts|py)$""")
        val SERVER = Regex("""(^|[/ ])opencode2?( |$)|/opt/opencode2?/|@opencode/cli""")
        val SERVE = Regex("""(^| )serve( |$)""")
        val PASEO = Regex("""(^|[/ ])paseo( |$)|oc-paseo|/paseo/""")
        val AGENT =
            Regex(
                """(^|[/ ])(claude|claude-code|codex|gemini|aider|goose)( |$)|""" +
                    """@anthropic-ai/claude-code|/opt/oc-claude|/opt/aiteam|(^|[/ ])(gc|dolt|bd)( |$)|""" +
                    """(^|[/ ])opencode2? (acp|run)( |$)"""
            )
        val DEV =
            Regex(
                """(^|[/ ])(vite|next|nuxt|astro|webpack|webpack-dev-server|parcel|""" +
                    """esbuild|nodemon|ts-node-dev|http-server|live-server|serve|uvicorn|""" +
                    """gunicorn|flask|django-admin|rails|puma|php|hugo|jekyll|mkdocs)( |$)|""" +
                    """npm (run )?(dev|start|serve|watch)|yarn (run )?(dev|start)|""" +
                    """pnpm (run )?(dev|start)|python3? -m http\.server|manage\.py runserver|""" +
                    """cargo (run|watch)|go run|dotnet watch"""
            )
    }
}

/** One program as the list shows it: a leader and the helper processes that belong with it. */
public data class LinuxProcessGroup(
    /** The process the row stands for (details, copy command, row key). */
    val leader: LinuxProcess,
    /** Every process in the group, the leader included, by pid. */
    val members: List<LinuxProcess>,
) {
    val count: Int
        get() = members.size

    val protected: Boolean
        get() = leader.protected

    /** What a stop is asked of: the members whose parent is not a member. */
    val roots: List<LinuxProcess>
        get() {
            val pids = members.map { it.pid }.toSet()
            return members.filter { it.ppid !in pids }
        }

    val memoryMb: Long
        get() = members.sumOf { it.memoryMb ?: 0L }

    val elapsedSeconds: Long
        get() = members.maxOfOrNull { it.elapsedSeconds }?.coerceAtLeast(0) ?: 0
}

/** One reading of the in-app Linux's processes. */
public data class LinuxProcessScan(
    val processes: List<LinuxProcess>,
    val uptimeSeconds: Double? = null,
) {
    /**
     * Processor use of [process] between [previous] and this reading, as a share of one core, or
     * null.
     */
    public fun cpuPercent(process: LinuxProcess, previous: LinuxProcessScan?): Double? {
        val before =
            previous?.processes?.firstOrNull {
                it.pid == process.pid && it.startTicks == process.startTicks
            }
        val t0 = previous?.uptimeSeconds
        val t1 = uptimeSeconds
        if (before == null || t0 == null || t1 == null || t1 - t0 < 0.5) return null
        val pct = (process.cpuTicks - before.cpuTicks).toDouble() / 100 / (t1 - t0) * 100
        return if (pct < 0) 0.0 else pct
    }

    /**
     * [roots] with everything they started, children before parents. Protected processes are never
     * included.
     */
    public fun withDescendants(roots: Iterable<LinuxProcess>): List<LinuxProcess> {
        val byParent = LinkedHashMap<Int, MutableList<LinuxProcess>>()
        for (p in processes) byParent.getOrPut(p.ppid) { mutableListOf() }.add(p)
        val out = mutableListOf<LinuxProcess>()
        val seen = mutableSetOf<Int>()
        fun visit(p: LinuxProcess) {
            if (!seen.add(p.pid) || p.protected) return
            for (child in byParent[p.pid].orEmpty()) visit(child)
            out.add(p)
        }
        for (root in roots) visit(root)
        return out
    }

    /**
     * [processes] as programs: a child joins its parent's row when it is the same kind of thing or
     * a plain helper ("other") of a recognised program, and every Paseo process is one row. A shell
     * with a dev server in it stays two.
     */
    public fun groups(): List<LinuxProcessGroup> {
        val byPid = processes.associateBy { it.pid }
        fun rootOf(p: LinuxProcess): LinuxProcess {
            var cur = p
            val seen = mutableSetOf(cur.pid)
            while (true) {
                val parent = byPid[cur.ppid]
                if (parent == null || !seen.add(parent.pid)) return cur
                val joins =
                    cur.kind == parent.kind ||
                        (cur.kind == LinuxProcessKind.Other &&
                            parent.kind != LinuxProcessKind.Other)
                if (!joins) return cur
                cur = parent
            }
        }
        val members = LinkedHashMap<Int, MutableList<LinuxProcess>>()
        for (p in processes) members.getOrPut(rootOf(p).pid) { mutableListOf() }.add(p)
        // Every Paseo tree is one row, whoever started each part.
        val paseoRoots = members.keys.filter { byPid.getValue(it).kind == LinuxProcessKind.Paseo }
        if (paseoRoots.size > 1) {
            val first = paseoRoots.first()
            for (r in paseoRoots.drop(1)) members
                .getValue(first)
                .addAll(members.remove(r).orEmpty())
        }
        return members.map { (pid, list) ->
            LinuxProcessGroup(byPid.getValue(pid), list.sortedBy { it.pid })
        }
    }

    public companion object {
        /**
         * Parses the output of [LINUX_PROCESS_SCAN_SCRIPT]. Lines that are not the script's own are
         * ignored (a stray warning must not break the list). Android's processes, proot, zombies
         * and the scan itself are left out.
         */
        public fun parse(output: String): LinuxProcessScan {
            var self: Int? = null
            var uptime: Double? = null
            val rows = mutableListOf<List<String>>()
            for (line in output.split('\n')) {
                when {
                    line.startsWith("::self ") -> self = line.substring(7).trim().toIntOrNull()
                    line.startsWith("::uptime ") ->
                        uptime = line.substring(9).trim().toDoubleOrNull()
                    line.startsWith("::p ") -> {
                        val parts = line.substring(4).split('\t')
                        if (parts.size >= 2) rows.add(parts)
                    }
                }
            }
            // A process's age is measured against the scan's own start, both read from the same
            // /proc clock.
            // /proc/uptime may count time differently (suspend, proot, emulator snapshots).
            var selfStart: Long? = null
            for (parts in rows) {
                if (parts[0].trim().toIntOrNull() == self) selfStart = startTicks(parts[1])
            }
            val out = mutableListOf<LinuxProcess>()
            for (parts in rows) {
                val pid = parts[0].trim().toIntOrNull()
                if (pid == null || pid <= 1 || pid == self) continue
                val cmdline = parts.getOrNull(2).orEmpty().trim()
                val process = fromStat(pid, parts[1], cmdline, uptime, selfStart) ?: continue
                if (process.ppid == self) continue
                if (isForeign(process)) continue
                out.add(process.copy(cwd = parts.getOrNull(3)?.takeIf { it.isNotBlank() }))
            }
            return LinuxProcessScan(out, uptime)
        }

        private val SPACES = Regex("""\s+""")
        private val ANDROID_PACKAGE = Regex("""^[a-z][a-z0-9_]*(\.[a-z0-9_]+){2,}""")

        private fun startTicks(stat: String): Long? {
            val close = stat.lastIndexOf(')')
            if (close < 0) return null
            val f = stat.substring(close + 1).trim().split(SPACES)
            return if (f.size < 20) null else f[19].toLongOrNull()
        }

        private fun fromStat(
            pid: Int,
            stat: String,
            cmdline: String,
            uptime: Double?,
            selfStart: Long?,
        ): LinuxProcess? {
            val open = stat.indexOf('(')
            val close = stat.lastIndexOf(')')
            if (open < 0 || close < open) return null
            val comm = stat.substring(open + 1, close)
            val f = stat.substring(close + 1).trim().split(SPACES)
            // After ")": state ppid … utime(11) stime(12) … starttime(19) … rss(21).
            if (f.size < 22 || f[0] == "Z" || f[0] == "X") return null
            val ppid = f[1].toIntOrNull()
            val utime = f[11].toLongOrNull()
            val stime = f[12].toLongOrNull()
            val start = f[19].toLongOrNull()
            val rssPages = f[21].toLongOrNull()
            if (ppid == null || utime == null || stime == null || start == null) return null
            val cmd = cmdline.replace(SPACES, " ").trim()
            val first = if (cmd.isEmpty()) comm else cmd.split(' ').first()
            val base =
                first.split('/').lastOrNull { it.isNotEmpty() }?.replaceFirst(Regex("^-"), "")
            val elapsed =
                when {
                    selfStart != null ->
                        ((selfStart - start) / 100.0).coerceIn(0.0, 1e12).roundToLong()
                    uptime == null -> 0L
                    else -> (uptime - start / 100.0).coerceIn(0.0, 1e12).roundToLong()
                }
            return LinuxProcess(
                pid = pid,
                ppid = ppid,
                name = if (base.isNullOrEmpty()) comm else base,
                cmd = cmd.ifEmpty { comm },
                startTicks = start,
                cpuTicks = utime + stime,
                rssKb = (rssPages ?: 0) * 4,
                elapsedSeconds = elapsed,
            )
        }

        /** Android's own processes and proot are not "in Linux". */
        private fun isForeign(p: LinuxProcess): Boolean {
            val first = p.cmd.split(' ').first()
            if (p.cmd.isEmpty() || (p.rssKb == 0L && p.cmd == p.name)) return true
            if (first.contains("proot") || p.cmd.contains("libproot")) return true
            if (
                first.startsWith("/system/") ||
                    first.startsWith("/apex/") ||
                    first.startsWith("/vendor/") ||
                    first.startsWith("/data/app/") ||
                    first == "zygote" ||
                    first == "zygote64" ||
                    first == "system_server" ||
                    first.startsWith("<pre-initialized>")
            ) {
                return true
            }
            return ANDROID_PACKAGE.containsMatchIn(first)
        }
    }
}

/**
 * Reads `/proc` once (verbatim from linux_processes.dart `linuxProcessScanScript`). The first lines
 * name the scan itself so it can leave itself (and what it started) out of the list. Run it with
 * `sh -c` inside the Linux.
 */
public const val LINUX_PROCESS_SCAN_SCRIPT: String =
    $$"""# oc-linux-scan
printf '::self %s\n' "$$"
printf '::uptime %s\n' "$(cut -d ' ' -f1 /proc/uptime)"
for d in /proc/[0-9]*; do
  p=${d#/proc/}
  s=
  IFS= read -r s 2>/dev/null < "$d/stat" || continue
  c=$(tr '\000\t\n' '   ' 2>/dev/null < "$d/cmdline" | head -c 600)
  printf '::p %s\t%s\t%s\t%s\n' "$p" "$s" "$c" "$(readlink "$d/cwd" 2>/dev/null | tr -d '\n' | tr '\t' ' ')"
done
exit 0
"""

/** What a stop did to each process it was sent. */
public enum class LinuxStopOutcome(public val wire: String) {
    Stopped("stopped"),
    Forced("forced"),
    Remaining("remaining"),
    Gone("gone"),

    /** No longer the process asked about (the pid had been reused): left alone. */
    Changed("changed"),
}

public data class LinuxStopResult(
    /** Keyed by `pid:startTicks`. */
    val outcomes: Map<String, LinuxStopOutcome>
) {
    val ended: Int
        get() =
            outcomes.values.count {
                it == LinuxStopOutcome.Stopped ||
                    it == LinuxStopOutcome.Forced ||
                    it == LinuxStopOutcome.Gone
            }

    val forced: Int
        get() = outcomes.values.count { it == LinuxStopOutcome.Forced }

    val remaining: Int
        get() = outcomes.values.count { it == LinuxStopOutcome.Remaining }

    val changed: Int
        get() = outcomes.values.count { it == LinuxStopOutcome.Changed }

    public companion object {
        /** Reads what [linuxStopScript] printed; other lines are ignored. */
        public fun parse(output: String): LinuxStopResult {
            val out = LinkedHashMap<String, LinuxStopOutcome>()
            for (line in output.split('\n')) {
                if (!line.startsWith("::stop ")) continue
                val parts = line.substring(7).trim().split(' ')
                if (parts.size != 2) continue
                val outcome =
                    LinuxStopOutcome.entries.firstOrNull { it.wire == parts[0] } ?: continue
                out[parts[1]] = outcome
            }
            return LinuxStopResult(out)
        }
    }
}

private val IDENTITY = Regex("""^[0-9]{2,9}:[0-9]{1,18}$""")

/** Whether [target] is a process identity a stop may carry (`pid:startTicks`, never pid 1). */
public fun isLinuxProcessIdentity(target: String): Boolean =
    IDENTITY.matches(target) && !target.startsWith("1:")

/**
 * The script that stops exactly [targets] (each `pid:startTicks`): asks politely, waits a few
 * seconds, then forces what is left. A process whose start time no longer matches is not touched.
 * Fails with `Failure.Runtime(RuntimeProblem.Generic)` for an empty list or anything that is not an
 * identity, so no text from a process ever reaches the shell (Flutter throws ArgumentError there).
 */
public fun linuxStopScript(targets: Iterable<String>): Outcome<String> {
    val list = targets.toList()
    if (list.isEmpty() || list.any { !isLinuxProcessIdentity(it) }) {
        return Outcome.Failed(Failure.Runtime(RuntimeProblem.Generic))
    }
    return Outcome.Ok(
        $$"""# oc-linux-stop
oc_start() {
  oc_st=
  IFS= read -r oc_st 2>/dev/null < "/proc/$1/stat" || return 1
  set -- ${oc_st##*) }
  shift 19
  echo "$1"
}
oc_live() {
  [ "$(oc_start "${1%%:*}")" = "${1##*:}" ] || return 1
  oc_cmd=$(tr '\000' ' ' 2>/dev/null < "/proc/${1%%:*}/cmdline")
  case "$oc_cmd" in *opencode*' serve'*) return 1;; esac
  return 0
}
oc_list="$${list.joinToString(" ")}"
for t in $oc_list; do
  if ! [ -r "/proc/${t%%:*}/stat" ]; then echo "::stop gone $t"; continue; fi
  if oc_live "$t"; then kill -TERM "${t%%:*}" 2>/dev/null; else echo "::stop changed $t"; fi
done
oc_wait=0
while [ $oc_wait -lt 10 ]; do
  oc_left=
  for t in $oc_list; do oc_live "$t" && oc_left=1; done
  [ -z "$oc_left" ] && break
  sleep 0.5
  oc_wait=$((oc_wait + 1))
done
for t in $oc_list; do
  if oc_live "$t"; then
    kill -KILL "${t%%:*}" 2>/dev/null
    sleep 0.3
    if oc_live "$t"; then echo "::stop remaining $t"; else echo "::stop forced $t"; fi
  elif [ -r "/proc/${t%%:*}/stat" ]; then
    echo "::stop changed $t"
  else
    echo "::stop stopped $t"
  fi
done
exit 0
"""
    )
}
