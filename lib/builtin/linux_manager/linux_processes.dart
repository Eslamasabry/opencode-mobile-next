/// What runs inside the app's own Linux, read from `/proc` by one shell
/// script ([linuxProcessScanScript]) and parsed here ([LinuxProcessScan]).
///
/// Pure Dart: the script text, the parser, the classification by what a
/// process is, the stop script and its result parser. The channel work
/// lives in `linux_manager.dart`.
///
/// Why `/proc` and not `ps`: the base image may lack procps, and the
/// identity of a process for a later Stop is its pid together with its
/// start time (a pid alone can be reused between the list and the tap).
library;

/// What a process is, in the order the page lists them.
enum LinuxProcessKind { openCodeServer, paseo, agent, devServer, other }

/// One process in the in-app Linux.
class LinuxProcess {
  const LinuxProcess({
    required this.pid,
    required this.ppid,
    required this.name,
    required this.cmd,
    required this.startTicks,
    required this.cpuTicks,
    required this.rssKb,
    required this.elapsedSeconds,
  });

  final int pid;
  final int ppid;

  /// The program's short name ("node", "paseo").
  final String name;

  /// The full command line, arguments separated by one space.
  final String cmd;

  /// Kernel start time in clock ticks (100 per second): with [pid] it names
  /// this process and no later one that reuses the pid.
  final int startTicks;

  /// Processor time used so far, in clock ticks.
  final int cpuTicks;
  final int rssKb;
  final int elapsedSeconds;

  static const _runners = {
    'node',
    'nodejs',
    'python',
    'python3',
    'sh',
    'bash',
    'dash',
    'env',
    'npx',
  };

  /// The program a person would recognise: the script a runner runs
  /// ("vite" for `node …/vite --port 5173`), `npm run dev`, or the name.
  String get programName {
    final tokens = cmd.split(' ').where((t) => t.isNotEmpty).toList();
    if (tokens.isEmpty) return name;
    if (const {'npm', 'yarn', 'pnpm'}.contains(name)) {
      return tokens.take(3).map((t) => t.split('/').last).join(' ');
    }
    if (_runners.contains(name)) {
      for (final t in tokens.skip(1)) {
        if (t.startsWith('-') || t == 'run' || t == 'exec') continue;
        final base = t.split('/').where((s) => s.isNotEmpty).lastOrNull;
        if (base == null) continue;
        return base.replaceFirst(RegExp(r'\.(m?js|cjs|ts|py)$'), '');
      }
    }
    return name;
  }

  /// The identity a stop is sent with: `pid:startTicks`.
  String get identity => '$pid:$startTicks';

  int get cpuSeconds => cpuTicks ~/ 100;

  int? get memoryMb {
    if (rssKb <= 0) return null;
    final mb = (rssKb + 512) ~/ 1024;
    return mb < 1 ? 1 : mb;
  }

  static final _server = RegExp(
    r'(^|[/ ])opencode2?( |$)|/opt/opencode2?/|@opencode/cli',
  );
  static final _serve = RegExp(r'(^| )serve( |$)');
  static final _paseo = RegExp(r'(^|[/ ])paseo( |$)|oc-paseo|/paseo/');
  static final _agent = RegExp(
    r'(^|[/ ])(claude|claude-code|codex|gemini|agy|antigravity|aider|goose)( |$)|'
    r'@anthropic-ai/claude-code|/opt/oc-claude|/opt/aiteam|(^|[/ ])(gc|dolt|bd)( |$)|'
    r'(^|[/ ])opencode2? (acp|run)( |$)',
  );
  static final _dev = RegExp(
    r'(^|[/ ])(vite|next|nuxt|astro|webpack|webpack-dev-server|parcel|'
    r'esbuild|nodemon|ts-node-dev|http-server|live-server|serve|uvicorn|'
    r'gunicorn|flask|django-admin|rails|puma|php|hugo|jekyll|mkdocs)( |$)|'
    r'npm (run )?(dev|start|serve|watch)|yarn (run )?(dev|start)|'
    r'pnpm (run )?(dev|start)|python3? -m http\.server|manage\.py runserver|'
    r'cargo (run|watch)|go run|dotnet watch',
  );

  /// What this process is, from its command line.
  LinuxProcessKind get kind {
    if (_server.hasMatch(cmd) && _serve.hasMatch(cmd)) {
      return LinuxProcessKind.openCodeServer;
    }
    if (_paseo.hasMatch(cmd)) return LinuxProcessKind.paseo;
    if (_agent.hasMatch(cmd)) return LinuxProcessKind.agent;
    if (_dev.hasMatch(cmd)) return LinuxProcessKind.devServer;
    return LinuxProcessKind.other;
  }

  /// The OpenCode server is stopped from This phone's server controls, never
  /// from this list.
  bool get protected => kind == LinuxProcessKind.openCodeServer;
}

/// One program as the list shows it: a leader and the helper processes that
/// belong with it (its children, and for Paseo its supervisor and daemon).
class LinuxProcessGroup {
  const LinuxProcessGroup(this.leader, this.members);

  /// The process the row stands for (details, copy command, row key).
  final LinuxProcess leader;

  /// Every process in the group, the leader included.
  final List<LinuxProcess> members;

  int get count => members.length;
  bool get protected => leader.protected;

  /// What a stop is asked of: the members whose parent is not a member.
  List<LinuxProcess> get roots {
    final pids = {for (final m in members) m.pid};
    return [
      for (final m in members)
        if (!pids.contains(m.ppid)) m,
    ];
  }

  int get memoryMb => members.fold(0, (sum, p) => sum + (p.memoryMb ?? 0));
  int get elapsedSeconds =>
      members.fold(0, (m, p) => p.elapsedSeconds > m ? p.elapsedSeconds : m);
}

/// One reading of the in-app Linux's processes.
class LinuxProcessScan {
  const LinuxProcessScan(this.processes, {this.uptimeSeconds});

  final List<LinuxProcess> processes;
  final double? uptimeSeconds;

  /// Processor use of [process] between [previous] and this reading, as a
  /// share of one core, or null when the earlier reading cannot say.
  double? cpuPercent(LinuxProcess process, LinuxProcessScan? previous) {
    final before = previous?.processes
        .where(
          (p) => p.pid == process.pid && p.startTicks == process.startTicks,
        )
        .firstOrNull;
    final t0 = previous?.uptimeSeconds;
    final t1 = uptimeSeconds;
    if (before == null || t0 == null || t1 == null || t1 - t0 < 0.5) {
      return null;
    }
    final pct = (process.cpuTicks - before.cpuTicks) / 100 / (t1 - t0) * 100;
    return pct < 0 ? 0 : pct;
  }

  /// [roots] with everything they started, children before parents, from the
  /// processes of this reading. Protected processes are never included.
  List<LinuxProcess> withDescendants(Iterable<LinuxProcess> roots) {
    final byParent = <int, List<LinuxProcess>>{};
    for (final p in processes) {
      byParent.putIfAbsent(p.ppid, () => []).add(p);
    }
    final out = <LinuxProcess>[];
    final seen = <int>{};
    void visit(LinuxProcess p) {
      if (!seen.add(p.pid) || p.protected) return;
      for (final child in byParent[p.pid] ?? const <LinuxProcess>[]) {
        visit(child);
      }
      out.add(p);
    }

    for (final root in roots) {
      visit(root);
    }
    return out;
  }

  /// [processes] as programs: a child joins its parent's row when it is the
  /// same kind of thing or a plain helper ("other") of a recognised program,
  /// and every Paseo process (supervisor, daemon, their node children) is one
  /// row. A shell with a dev server in it stays two rows.
  List<LinuxProcessGroup> groups() {
    final byPid = {for (final p in processes) p.pid: p};
    LinuxProcess rootOf(LinuxProcess p) {
      var cur = p;
      final seen = <int>{cur.pid};
      while (true) {
        final parent = byPid[cur.ppid];
        if (parent == null || !seen.add(parent.pid)) return cur;
        final joins =
            cur.kind == parent.kind ||
            (cur.kind == LinuxProcessKind.other &&
                parent.kind != LinuxProcessKind.other);
        if (!joins) return cur;
        cur = parent;
      }
    }

    final members = <int, List<LinuxProcess>>{};
    for (final p in processes) {
      members.putIfAbsent(rootOf(p).pid, () => []).add(p);
    }
    // Every Paseo tree is one row, whoever started each part.
    final paseoRoots = [
      for (final e in members.entries)
        if (byPid[e.key]!.kind == LinuxProcessKind.paseo) e.key,
    ];
    if (paseoRoots.length > 1) {
      final first = paseoRoots.first;
      for (final r in paseoRoots.skip(1)) {
        members[first]!.addAll(members.remove(r)!);
      }
    }
    return [
      for (final e in members.entries)
        LinuxProcessGroup(
          byPid[e.key]!,
          [...e.value]..sort((a, b) => a.pid.compareTo(b.pid)),
        ),
    ];
  }

  /// Parses the output of [linuxProcessScanScript]. Lines that are not the
  /// script's own are ignored (a stray warning must not break the list).
  /// Android's processes, proot and the scan itself are left out.
  factory LinuxProcessScan.parse(String output) {
    int? self;
    double? uptime;
    final rows = <List<String>>[];
    for (final line in output.split('\n')) {
      if (line.startsWith('::self ')) {
        self = int.tryParse(line.substring(7).trim());
      } else if (line.startsWith('::uptime ')) {
        uptime = double.tryParse(line.substring(9).trim());
      } else if (line.startsWith('::p ')) {
        final parts = line.substring(4).split('\t');
        if (parts.length >= 2) rows.add(parts);
      }
    }
    // A process's age is measured against the scan's own start, both read
    // from the same /proc clock. /proc/uptime may count time differently
    // (suspend, proot, emulator snapshots), which made minutes-old programs
    // read "1 h 12 min".
    int? selfStart;
    for (final parts in rows) {
      if (int.tryParse(parts[0].trim()) == self) {
        selfStart = _startTicks(parts[1]);
      }
    }
    final out = <LinuxProcess>[];
    for (final parts in rows) {
      final pid = int.tryParse(parts[0].trim());
      if (pid == null || pid <= 1 || pid == self) continue;
      final process = _fromStat(
        pid,
        parts[1],
        parts.length > 2 ? parts.sublist(2).join(' ').trim() : '',
        uptime,
        selfStart,
      );
      if (process == null || process.ppid == self) continue;
      if (_isForeign(process)) continue;
      out.add(process);
    }
    return LinuxProcessScan(out, uptimeSeconds: uptime);
  }

  static int? _startTicks(String stat) {
    final close = stat.lastIndexOf(')');
    if (close < 0) return null;
    final f = stat.substring(close + 1).trim().split(RegExp(r'\s+'));
    return f.length < 20 ? null : int.tryParse(f[19]);
  }

  static LinuxProcess? _fromStat(
    int pid,
    String stat,
    String cmdline,
    double? uptime,
    int? selfStart,
  ) {
    final open = stat.indexOf('(');
    final close = stat.lastIndexOf(')');
    if (open < 0 || close < open) return null;
    final comm = stat.substring(open + 1, close);
    final f = stat.substring(close + 1).trim().split(RegExp(r'\s+'));
    // After ")": state ppid … utime(11) stime(12) … starttime(19) … rss(21).
    if (f.length < 22 || f[0] == 'Z' || f[0] == 'X') return null;
    final ppid = int.tryParse(f[1]);
    final utime = int.tryParse(f[11]);
    final stime = int.tryParse(f[12]);
    final start = int.tryParse(f[19]);
    final rssPages = int.tryParse(f[21]);
    if (ppid == null || utime == null || stime == null || start == null) {
      return null;
    }
    final cmd = cmdline.replaceAll(RegExp(r'\s+'), ' ').trim();
    final first = cmd.isEmpty ? comm : cmd.split(' ').first;
    final base = first
        .split('/')
        .where((s) => s.isNotEmpty)
        .lastOrNull
        ?.replaceFirst(RegExp(r'^-'), '');
    return LinuxProcess(
      pid: pid,
      ppid: ppid,
      name: (base == null || base.isEmpty) ? comm : base,
      cmd: cmd.isEmpty ? comm : cmd,
      startTicks: start,
      cpuTicks: utime + stime,
      rssKb: (rssPages ?? 0) * 4,
      elapsedSeconds: selfStart != null
          ? ((selfStart - start) / 100).clamp(0, 1e12).round()
          : uptime == null
          ? 0
          : (uptime - start / 100).clamp(0, 1e12).round(),
    );
  }

  static final _androidPackage = RegExp(r'^[a-z][a-z0-9_]*(\.[a-z0-9_]+){2,}');

  /// Android's own processes and proot are not "in Linux".
  static bool _isForeign(LinuxProcess p) {
    final first = p.cmd.split(' ').first;
    if (p.cmd.isEmpty || p.rssKb == 0 && p.cmd == p.name) return true;
    if (first.contains('proot') || p.cmd.contains('libproot')) return true;
    if (first.startsWith('/system/') ||
        first.startsWith('/apex/') ||
        first.startsWith('/vendor/') ||
        first.startsWith('/data/app/') ||
        first == 'zygote' ||
        first == 'zygote64' ||
        first == 'system_server' ||
        first.startsWith('<pre-initialized>')) {
      return true;
    }
    return _androidPackage.hasMatch(first);
  }
}

/// Reads `/proc` once. The first lines name the scan itself so it can leave
/// itself (and what it started) out of the list.
const linuxProcessScanScript = r'''# oc-linux-scan
printf '::self %s\n' "$$"
printf '::uptime %s\n' "$(cut -d ' ' -f1 /proc/uptime)"
for d in /proc/[0-9]*; do
  p=${d#/proc/}
  s=
  IFS= read -r s 2>/dev/null < "$d/stat" || continue
  c=$(tr '\000' ' ' 2>/dev/null < "$d/cmdline" | head -c 600)
  printf '::p %s\t%s\t%s\n' "$p" "$s" "$c"
done
exit 0
''';

/// What a stop did to each process it was sent.
enum LinuxStopOutcome { stopped, forced, remaining, gone, changed }

class LinuxStopResult {
  const LinuxStopResult(this.outcomes);

  /// Keyed by `pid:startTicks`.
  final Map<String, LinuxStopOutcome> outcomes;

  int get ended => outcomes.values
      .where(
        (o) =>
            o == LinuxStopOutcome.stopped ||
            o == LinuxStopOutcome.forced ||
            o == LinuxStopOutcome.gone,
      )
      .length;
  int get forced =>
      outcomes.values.where((o) => o == LinuxStopOutcome.forced).length;
  int get remaining =>
      outcomes.values.where((o) => o == LinuxStopOutcome.remaining).length;

  /// Processes that were no longer the ones asked about (the pid had been
  /// reused): left alone.
  int get changed =>
      outcomes.values.where((o) => o == LinuxStopOutcome.changed).length;

  factory LinuxStopResult.parse(String output) {
    final out = <String, LinuxStopOutcome>{};
    for (final line in output.split('\n')) {
      if (!line.startsWith('::stop ')) continue;
      final parts = line.substring(7).trim().split(' ');
      if (parts.length != 2) continue;
      final outcome = switch (parts[0]) {
        'stopped' => LinuxStopOutcome.stopped,
        'forced' => LinuxStopOutcome.forced,
        'remaining' => LinuxStopOutcome.remaining,
        'gone' => LinuxStopOutcome.gone,
        'changed' => LinuxStopOutcome.changed,
        _ => null,
      };
      if (outcome != null) out[parts[1]] = outcome;
    }
    return LinuxStopResult(out);
  }
}

final _identity = RegExp(r'^[0-9]{2,9}:[0-9]{1,18}$');

/// The script that stops exactly [targets] (each `pid:startTicks`): asks
/// politely, waits a few seconds, then forces what is left. A process whose
/// start time no longer matches is not touched.
///
/// Throws [ArgumentError] for anything that is not an identity, so no text
/// from a process ever reaches the shell.
String linuxStopScript(Iterable<String> targets) {
  final list = targets.toList();
  if (list.isEmpty) throw ArgumentError('Nothing to stop.');
  for (final t in list) {
    if (!_identity.hasMatch(t) || t.startsWith('1:')) {
      throw ArgumentError.value(t, 'targets', 'Not a process identity.');
    }
  }
  return '''# oc-linux-stop
oc_start() {
  oc_st=
  IFS= read -r oc_st 2>/dev/null < "/proc/\$1/stat" || return 1
  set -- \${oc_st##*) }
  shift 19
  echo "\$1"
}
oc_live() { [ "\$(oc_start "\${1%%:*}")" = "\${1##*:}" ]; }
oc_list="${list.join(' ')}"
for t in \$oc_list; do
  if ! [ -r "/proc/\${t%%:*}/stat" ]; then echo "::stop gone \$t"; continue; fi
  if oc_live "\$t"; then kill -TERM "\${t%%:*}" 2>/dev/null; else echo "::stop changed \$t"; fi
done
oc_wait=0
while [ \$oc_wait -lt 10 ]; do
  oc_left=
  for t in \$oc_list; do oc_live "\$t" && oc_left=1; done
  [ -z "\$oc_left" ] && break
  sleep 0.5
  oc_wait=\$((oc_wait + 1))
done
for t in \$oc_list; do
  if oc_live "\$t"; then
    kill -KILL "\${t%%:*}" 2>/dev/null
    sleep 0.3
    if oc_live "\$t"; then echo "::stop remaining \$t"; else echo "::stop forced \$t"; fi
  elif [ -r "/proc/\${t%%:*}/stat" ]; then
    echo "::stop changed \$t"
  else
    echo "::stop stopped \$t"
  fi
done
exit 0
''';
}
