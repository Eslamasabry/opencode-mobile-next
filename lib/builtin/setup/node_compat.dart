/// Node.js in a safe mode on phones whose system crashes it inside the app's
/// own Linux (issue #117, #119).
///
/// On some ROMs (a Poco M4 Pro on a custom Android 17) programs that start
/// threads and initialise OpenSSL die with SIGSEGV under proot, while apt,
/// dpkg, tar and git (which do neither) work. curl was the first victim
/// (2.1.3 downloads with Android's networking instead); Node.js is the next.
///
/// The Node.js step therefore checks the phone itself:
///
/// 1. Before Node's 58 MB download, the cheap checks ([earlyProbes]): a
///    threaded `sort` and `openssl sha256` with and without
///    `OPENSSL_armcap=0`. When plain OpenSSL crashes and the safe one does
///    not, the safe mode is chosen up front; when all of them crash, the
///    step stops with [NodeCompatFailure.unsupported] before downloading.
/// 2. After unpacking, Node and npm are started. If they crash, every probe
///    in [nodeProbes] runs (each with a time limit, exit codes only, no
///    output kept) and the table goes into the step's log, so Details and
///    Report this failure carry it. The first variant in
///    [NodeCompatVariant.values] whose probe passes, and that then runs Node
///    and npm, is kept ([chooseNodeCompat] is the same rule in Dart).
/// 3. The choice is stored inside Linux ([NodeCompat.stateFile],
///    [NodeCompat.envFile]); `/usr/local/bin/node` becomes a small wrapper
///    that applies it, so every later `node`, `npm` and `npx` (setup, agents,
///    anything on the PATH) runs the same way. The app mirrors it in a
///    preference ([NodeCompatStore]) and gives the OpenCode server the same
///    environment ([NodeCompat.serverExports]).
///
/// A phone where nothing works ends the step with exit 139, so the native
/// runner tries the whole step once more in compatibility mode; if that
/// fails too, the person is told plainly and offered to connect a computer.
library;

import 'package:shared_preferences/shared_preferences.dart';

/// One way to run Node.js, in the order they are tried.
enum NodeCompatVariant {
  /// OpenSSL without its ARM CPU-feature probing and assembly paths.
  armcap(env: {'OPENSSL_armcap': '0'}),

  /// As few threads as Node can run with: V8 without background tasks
  /// (`--single-threaded`, a command-line flag only: Node refuses it in
  /// NODE_OPTIONS) and libuv with one worker. `--v8-pool-size=1` carries a
  /// small V8 pool to child Node processes started by path; 0 would mean
  /// "pick for me", not "none".
  threads(
    env: {'UV_THREADPOOL_SIZE': '1', 'NODE_OPTIONS': '--v8-pool-size=1'},
    flags: ['--single-threaded'],
  ),

  /// Both of the above.
  both(
    env: {
      'OPENSSL_armcap': '0',
      'UV_THREADPOOL_SIZE': '1',
      'NODE_OPTIONS': '--v8-pool-size=1',
    },
    flags: ['--single-threaded'],
  );

  const NodeCompatVariant({required this.env, this.flags = const []});

  /// Environment for every Node process.
  final Map<String, String> env;

  /// Flags the `node` wrapper puts before the caller's arguments.
  final List<String> flags;

  /// What the OpenCode server and the agents it starts get: [env] without
  /// NODE_OPTIONS, which is Node's alone (the server is not Node, and the
  /// wrapper sets it for every Node it starts).
  Map<String, String> get serverEnv => {
    for (final entry in env.entries)
      if (entry.key != 'NODE_OPTIONS') entry.key: entry.value,
  };

  static NodeCompatVariant? parse(String? text) {
    final name = text?.trim();
    for (final variant in values) {
      if (variant.name == name) return variant;
    }
    return null;
  }
}

/// The chosen variant, and whether it was found only in the runner's
/// compatibility mode (proot without seccomp, glibc without rseq).
typedef NodeCompatState = ({NodeCompatVariant variant, bool prootCompat});

/// One bounded check: [id] is its name in the log table (shell-safe),
/// [label] what it runs in words people can repeat in a terminal, [command]
/// the shell words (with `$oc_nc_node` for Node's own program).
typedef NodeProbe = ({String id, String label, String command});

/// The reasons the Node.js step ends a failed run with (its last log line),
/// so [describeSetupFailure] can say in plain words what went wrong.
abstract final class NodeCompatFailure {
  static const unsupported =
      "[oc] Node.js cannot run on this phone's system, also in a safe mode";
}

/// Exit code of a program killed by a signal (128 + the signal).
bool nodeProbeCrashed(int? code) => code != null && code >= 128 && code != 255;

/// [results] are the probes' exit codes by [NodeProbe.id]. The first
/// variant, in [NodeCompatVariant.values] order, whose own probe passed;
/// null when none did (the step gives up).
NodeCompatVariant? chooseNodeCompat(Map<String, int> results) {
  for (final variant in NodeCompatVariant.values) {
    if (results[variant.name] == 0) return variant;
  }
  return null;
}

/// What the early check (before Node's download) decides: a variant to set
/// up front, give up ([giveUp]), or nothing to change.
({NodeCompatVariant? variant, bool giveUp}) chooseEarlyNodeCompat(
  Map<String, int> results,
) {
  final crashed = [
    for (final probe in NodeCompat.earlyProbes)
      if (nodeProbeCrashed(results[probe.id])) probe.id,
  ];
  if (crashed.length == NodeCompat.earlyProbes.length) {
    return (variant: null, giveUp: true);
  }
  if (nodeProbeCrashed(results['openssl']) && results['opensslArmcap'] == 0) {
    return (variant: NodeCompatVariant.armcap, giveUp: false);
  }
  return (variant: null, giveUp: false);
}

/// The probe table the scripts print (`[oc] probe <id> <exit> # <label>`),
/// read back: exit codes by probe id, the last run's value winning (the
/// compatibility-mode retry prints its own table after the first).
Map<String, int> parseNodeProbeTable(String log) {
  final results = <String, int>{};
  for (final match in _probeLine.allMatches(log)) {
    results[match.group(1)!] = int.parse(match.group(2)!);
  }
  return results;
}

final _probeLine = RegExp(r'^\[oc\] probe (\w+) (\d+)\b', multiLine: true);

abstract final class NodeCompat {
  /// The chosen variant's name inside Linux (a second line `proot-compat`
  /// when it was found in compatibility mode).
  static const stateFile = '/etc/oc-setup/node-compat';

  /// The environment the `node` wrapper sources.
  static const envFile = '/etc/oc-setup/node-compat.env';

  /// Creating this file inside Linux makes every probe report a crash: the
  /// way to see the give-up path on a phone where Node runs (QA only).
  static const testCrashFile = '/etc/oc-setup/node-compat.test-crash';

  /// Seconds each probe may take before it counts as hung (exit 124).
  static const probeSeconds = 30;

  /// Exercises what crashed on the reporter's phone: OpenSSL (a hash) and
  /// libuv's thread pool (an async read and random bytes).
  static const _work =
      'const c=require("crypto");c.createHash("sha256").update("x").digest("hex");'
      'c.randomBytes(8,()=>{});require("fs").readFile("/etc/hostname",()=>{})';

  /// Before Node's download: what the phone can run at all.
  static const earlyProbes = <NodeProbe>[
    (
      id: 'sort',
      label: 'seq 1 400000 | sort --parallel=4 -S 64M -rn',
      command: "sh -c 'seq 1 400000 | sort --parallel=4 -S 64M -rn >/dev/null'",
    ),
    (
      id: 'openssl',
      label: 'openssl sha256 /etc/hostname',
      command: 'openssl sha256 /etc/hostname',
    ),
    (
      id: 'opensslArmcap',
      label: 'OPENSSL_armcap=0 openssl sha256 /etc/hostname',
      command: 'env OPENSSL_armcap=0 openssl sha256 /etc/hostname',
    ),
  ];

  /// After a crash: Node's own probes, then one per variant (its id is the
  /// variant's name), then the early ones again for the report.
  static List<NodeProbe> get nodeProbes => [
    (
      id: 'version',
      label: 'node --version',
      command: r'"$oc_nc_node" --version',
    ),
    (id: 'plain', label: 'node -e 1', command: r'"$oc_nc_node" -e 1'),
    (
      id: 'single',
      label: 'node --single-threaded -e 1',
      command: r'"$oc_nc_node" --single-threaded -e 1',
    ),
    (
      id: 'armcapOnly',
      label: 'OPENSSL_armcap=0 node -e 1',
      command: r'env OPENSSL_armcap=0 "$oc_nc_node" -e 1',
    ),
    (
      id: 'uv',
      label: 'UV_THREADPOOL_SIZE=1 node -e 1',
      command: r'env UV_THREADPOOL_SIZE=1 "$oc_nc_node" -e 1',
    ),
    for (final variant in NodeCompatVariant.values)
      (
        id: variant.name,
        label:
            '${_envWords(variant)} node ${[...variant.flags, '-e <work>'].join(' ')}',
        command:
            'env ${_envWords(variant)} "\$oc_nc_node" '
            '${[...variant.flags, '-e', "'$_work'"].join(' ')}',
      ),
    ...earlyProbes,
  ];

  static String _envWords(NodeCompatVariant variant) => _words(variant.env);

  static String _words(Map<String, String> env) => [
    for (final entry in env.entries) '${entry.key}=${entry.value}',
  ].join(' ');

  /// The environment file's text for [variant] (sourced by the wrapper).
  static String envFileText(NodeCompatVariant variant) => [
    '# OpenCode Mobile: Node.js in a safe mode on this phone (issue #117).',
    for (final entry in variant.env.entries)
      'export ${entry.key}=${entry.value}',
    "OC_NODE_FLAGS='${variant.flags.join(' ')}'",
  ].join('\n');

  /// Shell lines for the OpenCode server: [state]'s environment when the
  /// app knows it, and in any case whatever Linux itself has on file (a
  /// phone whose preference was lost still runs the same way).
  static String serverExports(NodeCompatState? state) {
    final buffer = StringBuffer()
      ..writeln('case "\$(head -n 1 $stateFile 2>/dev/null || true)" in');
    for (final variant in NodeCompatVariant.values) {
      buffer.writeln(
        '  ${variant.name}) export ${_words(variant.serverEnv)} ;;',
      );
    }
    buffer
      ..writeln('esac')
      ..writeln(
        "! grep -qx proot-compat $stateFile 2>/dev/null || "
        'export GLIBC_TUNABLES=glibc.pthread.rseq=0',
      );
    if (state != null) {
      buffer.writeln('export ${_words(state.variant.serverEnv)}');
      if (state.prootCompat) {
        buffer.writeln('export GLIBC_TUNABLES=glibc.pthread.rseq=0');
      }
    }
    return buffer.toString();
  }

  /// Reads [stateFile]'s text (null or empty: none).
  static NodeCompatState? parseState(String? text) {
    final lines = (text ?? '').trim().split('\n');
    final variant = NodeCompatVariant.parse(lines.first);
    if (variant == null) return null;
    return (
      variant: variant,
      prootCompat: lines.skip(1).any((line) => line.trim() == 'proot-compat'),
    );
  }

  /// The shell functions the Node.js step uses. Every path can be moved for
  /// the script tests (`OC_NODE_*`); the defaults are the real ones.
  static String get prelude {
    final early = earlyProbes.map((p) => _row(p)).join('\n');
    final node = nodeProbes.map((p) => _row(p)).join('\n');
    final candidates = NodeCompatVariant.values.map((v) => v.name).join(' ');
    final envCases = [
      for (final variant in NodeCompatVariant.values)
        "    ${variant.name}) printf '%s\\n' "
            "${envFileText(variant).split('\n').map(_quote).join(' ')} ;;",
    ].join('\n');
    return '''
# Node.js safe modes (issue #117), lib/builtin/setup/node_compat.dart.
oc_nc_dir=\${OC_NODE_COMPAT_DIR:-/etc/oc-setup}
oc_nc_node=\${OC_NODE_BIN:-/opt/node/bin/node}
oc_nc_link=\${OC_NODE_LINK:-/usr/local/bin/node}
oc_nc_npm=\${OC_NPM_BIN:-/usr/local/bin/npm}
oc_nc_secs=\${OC_NODE_PROBE_SECONDS:-$probeSeconds}
# Exit code of a command, quietly: its output is dropped and a crash is not
# reported by this shell (the subshell's own report goes nowhere), so the
# runner does not take a probe's crash for the step's.
oc_nc_try() {
  if [ -e "\$oc_nc_dir/node-compat.test-crash" ]; then echo 139; return 0; fi
  if command -v timeout >/dev/null 2>&1; then
    (oc_nc_x=0; timeout "\$oc_nc_secs" "\$@" >/dev/null 2>&1 </dev/null || oc_nc_x=\$?; echo "\$oc_nc_x") 2>/dev/null
  else
    (oc_nc_x=0; "\$@" >/dev/null 2>&1 </dev/null || oc_nc_x=\$?; echo "\$oc_nc_x") 2>/dev/null
  fi
}
oc_nc_row() {
  oc_nc_id=\$1 oc_nc_label=\$2
  shift 2
  oc_nc_r=\$(oc_nc_try env oc_nc_node="\$oc_nc_node" sh -c "\$*")
  case \$oc_nc_r in ''|*[!0-9]*) oc_nc_r=1 ;; esac
  eval "oc_nc_rc_\$oc_nc_id=\$oc_nc_r"
  printf '[oc] probe %s %s # %s\\n' "\$oc_nc_id" "\$oc_nc_r" "\$oc_nc_label"
}
oc_nc_crashed() { [ "\$1" -ge 128 ] && [ "\$1" != 255 ]; }
oc_nc_in_compat() { case \${GLIBC_TUNABLES:-} in *rseq=0*) return 0 ;; esac; return 1; }
oc_nc_save() {
  mkdir -p "\$oc_nc_dir"
  case \$1 in
$envCases
    *) return 1 ;;
  esac > "\$oc_nc_dir/node-compat.env.new"
  if oc_nc_in_compat; then
    echo 'export GLIBC_TUNABLES=glibc.pthread.rseq=0' >> "\$oc_nc_dir/node-compat.env.new"
    printf '%s\\nproot-compat\\n' "\$1" > "\$oc_nc_dir/node-compat.new"
  else
    printf '%s\\n' "\$1" > "\$oc_nc_dir/node-compat.new"
  fi
  chmod 644 "\$oc_nc_dir/node-compat.env.new" "\$oc_nc_dir/node-compat.new"
  mv -f "\$oc_nc_dir/node-compat.env.new" "\$oc_nc_dir/node-compat.env"
  mv -f "\$oc_nc_dir/node-compat.new" "\$oc_nc_dir/node-compat"
}
# Makes [\$oc_nc_link] (and with it npm and npx) run Node the saved way. A
# phone with nothing saved keeps the plain link.
oc_nc_apply_saved() {
  [ -s "\$oc_nc_dir/node-compat" ] && [ -s "\$oc_nc_dir/node-compat.env" ] || return 0
  rm -f "\$oc_nc_link.new"
  {
    echo '#!/bin/sh'
    echo '# OpenCode Mobile: Node.js in a safe mode on this phone (issue #117).'
    echo "[ ! -r '\$oc_nc_dir/node-compat.env' ] || . '\$oc_nc_dir/node-compat.env'"
    echo "exec '\$oc_nc_node' \\\${OC_NODE_FLAGS:-} \\"\\\$@\\""
  } > "\$oc_nc_link.new"
  chmod 755 "\$oc_nc_link.new"
  mv -f "\$oc_nc_link.new" "\$oc_nc_link"
  echo "[oc] Node.js runs in a safe mode on this phone: \$(head -n 1 "\$oc_nc_dir/node-compat")"
}
oc_nc_works() {
  oc_nc_w=\$(oc_nc_try "\$oc_nc_link" --version)
  [ "\$oc_nc_w" = 0 ] || return "\$oc_nc_w"
  oc_nc_w=\$(oc_nc_try "\$oc_nc_link" -e ${_quote(_work)})
  [ "\$oc_nc_w" = 0 ] || return "\$oc_nc_w"
  oc_nc_w=\$(oc_nc_try "\$oc_nc_npm" --version)
  return "\$oc_nc_w"
}
oc_nc_give_up() {
  echo "[oc] Nothing ran: Node.js needs something this phone's system does not give programs inside Linux."
  echo "${NodeCompatFailure.unsupported}"
  return 139
}
# Before the download: give up early when nothing threaded or OpenSSL runs,
# and choose the OpenSSL safe mode up front when only that helps.
oc_node_phone_check() {
  [ ! -s "\$oc_nc_dir/node-compat" ] || return 0
  echo "[oc] Checking this phone before downloading Node.js (exit codes; 139 = crashed):"
$early
  if oc_nc_crashed "\$oc_nc_rc_sort" && oc_nc_crashed "\$oc_nc_rc_openssl" &&
    oc_nc_crashed "\$oc_nc_rc_opensslArmcap"; then
    oc_nc_give_up
    return 139
  fi
  if oc_nc_crashed "\$oc_nc_rc_openssl" && [ "\$oc_nc_rc_opensslArmcap" = 0 ]; then
    oc_nc_save armcap
  fi
  return 0
}
# After the unpack and links: Node and npm must start. A crash runs every
# probe into the log and keeps the first safe mode that works.
oc_node_adapt() {
  oc_nc_apply_saved
  oc_nc_rc=0
  oc_nc_works || oc_nc_rc=\$?
  oc_nc_crashed "\$oc_nc_rc" || return 0
  echo "[oc] Node.js crashed (signal \$((oc_nc_rc - 128))). Checking what this phone can run (exit codes; 139 = crashed, 124 = took too long):"
$node
  for oc_nc_v in $candidates; do
    eval "oc_nc_v_rc=\\\${oc_nc_rc_\$oc_nc_v:-1}"
    [ "\$oc_nc_v_rc" = 0 ] || continue
    oc_nc_save "\$oc_nc_v"
    oc_nc_apply_saved
    if oc_nc_works; then return 0; fi
    echo "[oc] Node.js still crashed with \$oc_nc_v"
  done
  rm -f "\$oc_nc_dir/node-compat" "\$oc_nc_dir/node-compat.env"
  rm -f "\$oc_nc_link"
  ln -s "\$oc_nc_node" "\$oc_nc_link"
  oc_nc_give_up
  return 139
}
''';
  }

  static String _row(NodeProbe probe) =>
      '  oc_nc_row ${probe.id} ${_quote(probe.label)} ${_quote(probe.command)}';

  /// [text] as one single-quoted shell word.
  static String _quote(String text) => "'${text.replaceAll("'", r"'\''")}'";
}

/// The app's copy of the choice, per device (not per server profile: it is
/// this phone's system that needs it). Key `oc.builtin.nodeCompat.device`.
class NodeCompatStore {
  NodeCompatStore._();

  static const key = 'oc.builtin.nodeCompat.device';

  /// `armcap`, or `armcap proot-compat`.
  static String encode(NodeCompatState state) => state.prootCompat
      ? '${state.variant.name} proot-compat'
      : state.variant.name;

  static NodeCompatState? decode(String? text) =>
      NodeCompat.parseState(text?.replaceAll(' ', '\n'));

  static NodeCompatState? read(SharedPreferences prefs) {
    try {
      return decode(prefs.getString(key));
    } catch (_) {
      return null;
    }
  }

  /// Null clears it: Linux has no safe mode on file (a phone that runs
  /// Node plainly, or a fresh Linux that has not been checked yet keeps the
  /// old answer only until setup finishes).
  static Future<void> write(
    SharedPreferences prefs,
    NodeCompatState? state,
  ) async {
    try {
      if (state == null) {
        if (prefs.containsKey(key)) await prefs.remove(key);
      } else {
        await prefs.setString(key, encode(state));
      }
    } catch (_) {}
  }

  /// [read] without a store at hand; null when there is none.
  static Future<NodeCompatState?> load() async {
    try {
      return read(await SharedPreferences.getInstance());
    } catch (_) {
      return null;
    }
  }
}
