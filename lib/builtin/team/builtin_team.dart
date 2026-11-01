import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:flutter/foundation.dart'
    show ValueListenable, VoidCallback, visibleForTesting;

import '../../domain/team_directories.dart' show aiTeamHome;
import '../../state/team_model.dart' show isValidTeamModel;
import '../../state/profiles.dart'
    show
        OrchestrationConfig,
        OrchestrationHostKind,
        OrchestrationHostMode,
        OrchestrationProvider;
import '../../ui/kit/kit_redact.dart';
import '../builtin_linux.dart';
import '../process_budget.dart';
import '../setup/aiteam_scripts.dart';
import 'builtin_team_start_progress.dart';
export 'builtin_team_start_progress.dart';

/// AI Team inside the app: a Gas City team for the projects of the in-app
/// OpenCode, with no Termux.
///
/// The programs come from the `aiteam` setup component (install only). This
/// class does the rest, per project and on request, never as part of the
/// first setup (a team needs a project, which only exists after it):
/// - [turnOn] makes the team's store once, adds the project to it (with a
///   git `origin` on the phone when it has none, where the agents push their
///   work), starts the supervisor and waits until it answers;
/// - [start] / [stop] run the supervisor as the app's second long-running
///   service ([BuiltinLinux.startService]), beside the OpenCode server, so
///   it lives as long as the app does and not as long as some script;
/// - [config] is the AI Team plugin config the in-app profile gets, which
///   is what makes the Team card, the Inbox gates and the plugin screens
///   show this team.
///
/// Security: every app on an Android phone shares 127.0.0.1, so the
/// supervisor listens there only (explicitly, not by default), on its own
/// port so it never meets a Termux team on 8372, and accepts no other Host
/// header. It has no password of its own yet; see the report for what that
/// leaves open.
class BuiltinTeam {
  BuiltinTeam({
    BuiltinLinux? linux,
    Future<String?> Function(Uri url)? httpGet,
    this.pollInterval = const Duration(seconds: 1),
    this.agentsWait = const Duration(seconds: 45),
    this.recoveryStoreTimeout = const Duration(seconds: 60),
    BuiltinTeamStartProgress? progress,
  }) : _linux = linux ?? BuiltinLinux(),
       _get = httpGet ?? _loopbackGet,
       _progress = progress ?? BuiltinTeamStartProgress.shared;

  final BuiltinLinux _linux;
  final Future<String?> Function(Uri url) _get;
  final Duration pollInterval;

  /// How long the last start step waits for the agents to be listed before
  /// it lets go (an empty list is not a failure).
  final Duration agentsWait;

  /// Background recovery observes the legacy store for a bounded minute.
  /// A slow database remains unavailable; an explicit start can wait longer.
  final Duration recoveryStoreTimeout;
  final BuiltinTeamStartProgress _progress;

  /// Where the team is in coming up, for the AI Team page: the same steps
  /// for the automatic start after an app restart and the explicit one.
  ValueListenable<BuiltinTeamStartProgress> get startProgress =>
      _StartProgressListenable(_progress);

  static const serviceName = 'aiteam';

  /// Runtime-wide intent, outside the team's removable state. No credential
  /// or profile data is stored here. Removing Ubuntu also removes this marker.
  static const disabledFile = '/root/.oc-builtin/aiteam.disabled';
  static const disableScript =
      'set -eu\nmkdir -p /root/.oc-builtin\n: > $disabledFile\n';
  static const enableScript = 'set -eu\nrm -f $disabledFile\n';
  static const disabledCheckScript =
      'set -eu\n'
      'if [ -e /root/.oc-builtin ]; then\n'
      '  [ -d /root/.oc-builtin ] && [ -r /root/.oc-builtin ] '
      '&& [ -x /root/.oc-builtin ] || exit 1\n'
      'fi\n'
      '[ ! -e $disabledFile ] && [ ! -L $disabledFile ] || exit 42\n';

  // Every instance controls the same native service. Keep explicit starts,
  // background recovery and removals from resurrecting one another mid-flight.
  static Future<void> _operation = Future<void>.value();

  static Future<void> _exclusive(Future<void> Function() work) {
    final next = _operation.then((_) => work());
    _operation = next.then<void>((_) {}, onError: (Object _, StackTrace _) {});
    return next;
  }

  /// Not Termux's 8372: the two runtimes can both be on one phone.
  static const port = 8472;
  static const url = 'http://127.0.0.1:$port';
  static const city = 'phone';

  /// Also the rule that keeps the team's own sessions out of the person's
  /// lists (team_directories.dart), so the two cannot drift apart.
  static const home = aiTeamHome;
  static const cityDir = '$home/city';

  /// Bare repositories on the phone that stand in for `origin` when a
  /// project has none. Outside /root/projects, so they never show up as
  /// projects themselves.
  static const originsDir = '$home/origins';

  /// What the phone-side origins' hook did with each merge, one line per
  /// push to a project's branch (see [originHook]); the Plugins section
  /// reads the newest line per project.
  static const pullLog = '$home/pull.log';

  /// True for the plugin config [config] writes: the team inside this app.
  static bool isBuiltinConfig(OrchestrationConfig? config) =>
      config != null &&
      config.provider == OrchestrationProvider.gascity &&
      _samePort(config.url);

  static bool _samePort(String value) {
    final uri = Uri.tryParse(value);
    return uri != null &&
        uri.scheme == 'http' &&
        uri.host == '127.0.0.1' &&
        uri.port == port;
  }

  /// The plugin config for the in-app profile: this phone, loopback, no
  /// front; the supervisor honours `X-GC-Request` on loopback, which gives
  /// the app its controls (create a task, give it to an agent).
  static OrchestrationConfig config({DateTime? now}) => OrchestrationConfig(
    provider: OrchestrationProvider.gascity,
    url: url,
    city: city,
    hostMode: OrchestrationHostMode.phone,
    hostKind: OrchestrationHostKind.phone,
    enabledAt: (now ?? DateTime.now()).toUtc(),
  );

  /// The team's name for the project at [path] (its folder name, made
  /// safe): Gas City calls it a rig.
  static String rigName(String path) {
    final trimmed = path.endsWith('/')
        ? path.substring(0, path.length - 1)
        : path;
    final base = trimmed.substring(trimmed.lastIndexOf('/') + 1);
    var name = base
        .replaceAll(RegExp(r'[^A-Za-z0-9_-]'), '-')
        .replaceAll(RegExp(r'^-+|-+$'), '');
    if (name.length > 40) name = name.substring(0, 40);
    return name.isEmpty ? 'project' : name;
  }

  // ---- scripts (static, so tests can run and read them) -------------------

  /// The supervisor's settings: loopback only, its own port, and loopback
  /// Host headers only. Rewritten on every start, so a changed default in a
  /// later Gas City can never open it to the network.
  static const supervisorConfig =
      '[supervisor]\n'
      'bind = "127.0.0.1"\n'
      'port = $port\n'
      'allowed_hosts = ["127.0.0.1", "localhost"]\n';

  /// The agents [_cityPatches] and [_rigPatches] keep suspended, by their
  /// kind (the pool's last part): the app keeps them off so the phone can
  /// run the team, so they are never the person's pause and the app never
  /// offers to wake them (waking them runs past Android's process limit
  /// and the team stops answering).
  static const keptOffKinds = {'mayor', 'deacon', 'boot', 'witness'};

  /// The lean team: one worker per project and the planner, the patrols and
  /// the witness off. The phone runs a handful of processes, not thirty
  /// (Android stops an app's child processes past 32).
  static const _cityPatches =
      '[[patches.agent]]\nname = "gastown.mayor"\nsuspended = true\n'
      '[[patches.agent]]\nname = "gastown.deacon"\nsuspended = true\n'
      '[[patches.agent]]\nname = "gastown.boot"\nsuspended = true\n';

  static String _rigPatches(String rig) =>
      '[[patches.agent]]\nname = "gastown.witness"\ndir = "$rig"\n'
      'suspended = true\n'
      '[[patches.agent]]\nname = "gastown.polecat"\ndir = "$rig"\n'
      'max_active_sessions = 1\n';

  /// Gas City tuned for a phone. Android stops an app's child processes
  /// past 32 in all, oldest first, and the oldest is the OpenCode server
  /// (seen on the Android 14 emulator: a default team peaked at 40 and
  /// Android killed OpenCode and the team). The defaults poll every 30 s,
  /// run up to 8 store probes at once and give each session its own
  /// polling process; here:
  /// - patrols every minute;
  /// - one store probe and one session start at a time;
  /// - queued nudges delivered inside the supervisor, not by a process
  ///   per session;
  /// - each agent's OpenCode replaces the shell that starts it (`exec`), one
  ///   process per agent instead of two;
  /// - the maintenance orders a single phone project does not need are
  ///   left out, among them the two that wake an AI "dog" (a whole
  ///   OpenCode session, and model usage) for digests and stale stores;
  /// - the upkeep orders it does need run one after another, in the
  ///   `phone-upkeep` order ([upkeepOrder]), never side by side.
  ///
  /// Measured on the Android 15 emulator (docs/qa/aiteam-builtin-2026-09-24,
  /// Run 3): at the tallest moment (37) the orders were 18 of the
  /// processes, 11 of them `dolt-health` alone (a report nobody reads on a
  /// phone; the supervisor's own watchdog keeps Dolt up), with
  /// `nudge-on-route` and `cascade-nudge-on-blocker-close` firing on the
  /// agents' own bead updates at the same moment. A task given with
  /// "Send to an agent" goes straight to the project's worker, and the
  /// worker wakes the merger itself, so those nudges add nothing here.
  ///
  /// And an agent's first start is given time: Gas City gives `opencode acp`
  /// 30 s to answer its handshake and 60 s to start, then kills it and tries
  /// again from nothing on a later tick. A cold OpenCode under proot needs
  /// about that long (22 s when it got through on the owner's phone,
  /// 2026-09-25), so a task waited two minutes while three starts were cut
  /// off before the fourth got through (docs/qa/team-hot-2026-09-26). Three
  /// minutes to answer and four to start cost nothing while a start goes
  /// well; a start that truly hangs is found later.
  static const phoneTuning =
      '[daemon]\n'
      'patrol_interval = "60s"\n'
      'max_restarts = 5\n'
      'restart_window = "1h"\n'
      'shutdown_timeout = "5s"\n'
      'nudge_dispatcher = "supervisor"\n'
      'probe_concurrency = 1\n'
      'max_wakes_per_tick = 1\n'
      '[orders]\n'
      'skip = ["digest-generate", "mol-dog-stale-db", "mol-dog-backup", '
      '"mol-dog-compactor", "mol-dog-phantom-db", "mol-dog-doctor", '
      '"spawn-storm-detect", "cross-rig-deps", "jsonl-export", '
      '"dolt-remotes-patrol", "prune-branches", "wisp-compact", '
      '"dolt-health", "nudge-on-route", "cascade-nudge-on-blocker-close", '
      // The controller runs this sweep itself every five minutes.
      '"nudge-mail-sweep"]\n'
      // Run by phone-upkeep, one at a time, not on their own schedules.
      '[[orders.overrides]]\nname = "beads-health"\ntrigger = "manual"\n'
      '[[orders.overrides]]\nname = "order-tracking-sweep"\n'
      'trigger = "manual"\n'
      '[[orders.overrides]]\nname = "gate-sweep"\ntrigger = "manual"\n'
      '[[orders.overrides]]\nname = "orphan-sweep"\ntrigger = "manual"\n'
      '[[orders.overrides]]\nname = "reaper"\ntrigger = "manual"\n'
      '[session]\n'
      'startup_timeout = "4m"\n'
      '[session.acp]\n'
      '$handshakeTimeout\n';

  /// The line of [phoneTuning] a team tuned by this version has; see
  /// [tuneScript].
  static const handshakeTimeout = 'handshake_timeout = "3m"';

  /// Each agent's OpenCode replaces the shell Gas City starts it with
  /// (`sh -c "<command>"`): one process per agent instead of two. A line of
  /// the city's `[providers.opencode]` (Gas City 1.4.1 does not take it as
  /// a `[[patches.provider]]`: "unknown field").
  static const acpCommand = 'acp_command = "exec opencode"';

  /// The city's own order that runs the core pack's upkeep orders the phone
  /// keeps (store health, order tracking and gate sweeps every two minutes;
  /// the orphan sweep every ten, the reaper every thirty) one after
  /// another, as `gc order run` so each gets its own pack's settings,
  /// instead of each on its own cooldown, where they fire on the same tick
  /// and add up. It fails when the store's health check fails, like
  /// `beads-health` on its own did.
  static const upkeepOrder =
      '[order]\n'
      'description = "Phone upkeep: the core sweeps one at a time '
      '(written by OpenCode Mobile)"\n'
      'trigger = "cooldown"\n'
      'interval = "2m"\n'
      'timeout = "10m"\n'
      'exec = "exec sh $cityDir/assets/phone-upkeep.sh"\n';

  static const upkeepScript = r'''#!/bin/sh
# Phone upkeep (written by OpenCode Mobile on every AI Team start).
cd /root/aiteam/city || exit 1
state=/root/aiteam/city/.gc/runtime/packs/phone
mkdir -p "$state"
# One run at a time: Gas City starts the next run on the cooldown even when
# the last one is still going (a slow orphan sweep outlasts two minutes),
# and two runs side by side is what this order exists to avoid. The lock
# goes with the process, so a run killed at its timeout leaves none behind.
exec 9>"$state/upkeep.lock"
flock -n 9 || exit 0
n=$(cat "$state/upkeep.count" 2>/dev/null || echo 0)
case $n in *[!0-9]*|'') n=0 ;; esac
n=$((n + 1))
echo "$n" > "$state/upkeep.count"
rc=0
gc order run beads-health >/dev/null 2>&1 || rc=$?
gc order run order-tracking-sweep >/dev/null 2>&1 || true
gc order run gate-sweep >/dev/null 2>&1 || true
if [ $((n % 5)) -eq 0 ]; then gc order run orphan-sweep >/dev/null 2>&1 || true; fi
if [ $((n % 15)) -eq 0 ]; then gc order run reaper >/dev/null 2>&1 || true; fi
exit "$rc"
''';

  /// Brings a team made by an older version up to [phoneTuning] and writes
  /// the upkeep order; run before every supervisor start. The tuning's own
  /// tables (`[daemon]`, `[orders]` and its overrides, `[session]` and
  /// `[session.acp]`) are dropped from `city.toml` and written again at the
  /// end, and `[providers.opencode]` gets [acpCommand]; nothing else in it
  /// is touched. A team that has this version's tuning ([acpCommand] and
  /// [handshakeTimeout]) is left as is. `gc` itself writes none of those
  /// tables; the phone's `city.toml` is the app's.
  static String get tuneScript =>
      'mkdir -p $cityDir/orders $cityDir/assets\n'
      "cat > $cityDir/orders/phone-upkeep.toml <<'OC_EOF'\n"
      '${upkeepOrder}OC_EOF\n'
      "cat > $cityDir/assets/phone-upkeep.sh <<'OC_EOF'\n"
      '${upkeepScript}OC_EOF\n'
      'if [ -f $cityDir/city.toml ] && '
      '{ ! grep -qx \'$acpCommand\' $cityDir/city.toml || '
      '! grep -qx \'$handshakeTimeout\' $cityDir/city.toml; }; then\n'
      "  awk '/^[[:space:]]*\\[/ { h = \$0; gsub(/[[:space:]]/, \"\", h); "
      'skip = (h == "[daemon]" || h == "[orders]" || '
      'h == "[[orders.overrides]]" || h == "[[patches.provider]]" || '
      'h == "[session]" || h == "[session.acp]"); '
      'prov = (h == "[providers.opencode]") } '
      'skip { next } '
      'prov && /^[[:space:]]*acp_command[[:space:]]*=/ { next } '
      '{ print } '
      r'''prov && /^[[:space:]]*\[/ { print "acp_command = \"exec opencode\"" }' '''
      '$cityDir/city.toml > $cityDir/city.toml.oc-new &&\n'
      "  cat >> $cityDir/city.toml.oc-new <<'OC_EOF' &&\n"
      '\n${phoneTuning}OC_EOF\n'
      '  mv $cityDir/city.toml.oc-new $cityDir/city.toml || '
      'rm -f $cityDir/city.toml.oc-new\n'
      'fi\n';

  /// The environment of every team script and of the supervisor (so of its
  /// agents too). The three programs send usage metrics by default; this
  /// app sends nothing about its users' work anywhere, so all of them are
  /// told not to.

  static const _env =
      'export HOME=/root GC_BIN=/usr/local/bin/gc\n'
      'export DO_NOT_TRACK=1 GC_DISABLE_USAGE_METRICS=1 BD_DISABLE_METRICS=1 '
      'DOLT_DISABLE_EVENT_FLUSH=1\n'
      'command -v gc >/dev/null || '
      '{ echo "[oc] AI Team is not installed" >&2; exit 69; }\n';

  /// Makes the team's store once (`gc init`); does nothing when it exists.
  static String get cityScript =>
      'set -eu\n'
      '$_env'
      'mkdir -p $home /root/.gc\n'
      "cat > /root/.gc/supervisor.toml <<'OC_EOF'\n"
      '${supervisorConfig}OC_EOF\n'
      'if [ -f $home/city.ready ] && [ -f $cityDir/city.toml ]; then\n'
      '  echo city-ready\n'
      '  exit 0\n'
      'fi\n'
      "cat > $home/city.toml <<'OC_EOF'\n"
      '[workspace]\n'
      'provider = "opencode"\n'
      'install_agent_hooks = ["opencode"]\n'
      '[providers]\n'
      '[providers.opencode]\n'
      'base = "builtin:opencode"\n'
      '$acpCommand\n'
      'ready_delay_ms = 0\n'
      '[defaults]\n'
      '[defaults.rig]\n'
      '[defaults.rig.imports]\n'
      '[defaults.rig.imports.gastown]\n'
      'source = "${AiTeamPins.packSource}"\n'
      'version = "${AiTeamPins.packVersion}"\n'
      '$phoneTuning'
      'OC_EOF\n'
      // Under proot on a busy phone the store's first start can outlast
      // Gas City's own wait and leave it half made ("dirty tables"); a
      // clean second try works. The leftovers of a try (its database
      // server) are stopped first; the bracket keeps the pattern from
      // matching this script's own command line.
      'cd $home\n'
      'try=1\n'
      'while :; do\n'
      "  pkill -f 'dolt[ ]sql-server --config $cityDir' 2>/dev/null || true\n"
      "  pkill -f '__gc[-]managed-dolt' 2>/dev/null || true\n"
      '  rm -rf $cityDir $home/city.ready\n'
      '  if gc init --file ./city.toml --name $city --no-start city && '
      '[ -d $cityDir/.gc ]; then break; fi\n'
      '  [ "\$try" -lt 3 ] || '
      '{ echo "[oc] The team store could not be made" >&2; exit 70; }\n'
      '  try=\$((try + 1))\n'
      '  echo "[oc] Making the team store again (try \$try of 3)"\n'
      '  sleep 3\n'
      'done\n'
      'touch $home/city.ready\n'
      'echo city-ready\n';

  /// Adds the project at [path] to the team as [rig]; does nothing for one
  /// already there.
  ///
  /// The agents work on branches they push to `origin`; a project without
  /// one gets a bare repository on the phone, and one with no commit yet
  /// gets an empty first commit, since a branch needs something to start
  /// from. A project that has an origin keeps it. A bead store left in the
  /// project by an earlier team is moved aside, never deleted.
  static String rigScript(String path, String rig) {
    if (!path.startsWith('/') || path.contains('\n')) {
      throw ArgumentError.value(path, 'path', 'Must be an absolute path.');
    }
    if (!RegExp(r'^[A-Za-z0-9_-]+$').hasMatch(rig)) {
      throw ArgumentError.value(rig, 'rig', 'Letters, digits, - and _ only.');
    }
    return 'set -eu\n'
        '$_env'
        'project=${_quote(path)}\n'
        'rig=$rig\n'
        '[ -d "\$project/.git" ] || '
        '{ echo "[oc] \$project is not a git project" >&2; exit 65; }\n'
        '[ -f $cityDir/city.toml ] || '
        '{ echo "[oc] The team store is missing" >&2; exit 70; }\n'
        'cd "\$project"\n'
        'git rev-parse -q --verify HEAD >/dev/null || '
        "git commit -q --allow-empty -m 'Start'\n"
        'if ! git remote get-url origin >/dev/null 2>&1; then\n'
        '  origin=$originsDir/\$rig.git\n'
        '  mkdir -p $originsDir\n'
        '  [ -d "\$origin" ] || git init -q --bare "\$origin"\n'
        '  git remote add origin "\$origin"\n'
        '  git push -q origin HEAD\n'
        'fi\n'
        'cd $cityDir\n'
        'if ! grep -qx "name = \\"\$rig\\"" city.toml; then\n'
        '  if [ -e "\$project/.beads" ]; then\n'
        '    mv "\$project/.beads" "\$project/.beads.before-aiteam-\$(date +%s)"\n'
        '  fi\n'
        // As with the store itself, a slow first start can leave the
        // project's store half made; each retry starts clean, under a new
        // prefix (a new database), since the half-made one cannot be
        // initialised over.
        '  try=1\n'
        '  prefix=\n'
        '  until gc rig add "\$project" --name "\$rig" \$prefix; do\n'
        '    [ "\$try" -lt 3 ] || '
        '{ echo "[oc] The project could not be added to the team" >&2; exit 70; }\n'
        '    try=\$((try + 1))\n'
        '    echo "[oc] Adding the project again (try \$try of 3)"\n'
        '    gc rig remove "\$rig" >/dev/null 2>&1 || true\n'
        '    rm -rf "\$project/.beads"\n'
        '    prefix="--prefix r\$try\$(printf %s "\$rig" | tr -cd a-z | cut -c1-4)"\n'
        '    sleep 3\n'
        '  done\n'
        'fi\n'
        // `gc rig add` commits the project's new bead settings; an origin
        // on the phone gets that commit too, or the agents start from a
        // master that lacks it and spend their first minutes working out
        // why (seen on the emulator). Once the team has merged work, the
        // origin is ahead of the project and the push is refused, which is
        // fine.
        //
        // An origin made here also gets the hook that brings the team's
        // merged work into the project folder (rewritten on every run, so
        // a project added by an older version gets it too), and the work
        // merged before it existed is brought in once now.
        'case "\$(git -C "\$project" remote get-url origin)" in\n'
        '  $originsDir/*) git -C "\$project" push -q origin HEAD || true ;;\n'
        'esac\n'
        '$_hookFunctions'
        'oc_install_hook "\$project" "\$rig" || true\n'
        'gc import install\n'
        'grep -q \'name = "gastown.mayor"\' city.toml || '
        "printf '\\n%s' ${_quote(_cityPatches)} >> city.toml\n"
        'grep -qx "dir = \\"\$rig\\"" city.toml || '
        "printf '\\n%s' ${_quote(_rigPatches(rig))} >> city.toml\n"
        'echo rig-ready\n';
  }

  /// The `post-receive` hook of a phone-side origin (only the ones
  /// [rigScript] makes): the refinery merges into the origin, and this
  /// brings that merge into the project folder the person's own
  /// conversations work in, so nobody has to `git pull`.
  ///
  /// It only ever fast-forwards the branch the project has checked out,
  /// and leaves the project alone when that is not safe:
  /// - `dirty`: the project has changes of its own to tracked files (the
  ///   team's own bookkeeping, `.beads/` and the lines Gas City adds to
  ///   `.gitignore`, does not count; git itself still refuses to overwrite
  ///   any of it);
  /// - `diverged`: the project has commits the origin does not;
  /// - `skipped`: the project is not on a branch;
  /// - `failed`: git refused (the reason is logged).
  /// Otherwise `brought-in`, or `up-to-date`. Every outcome is one line in
  /// [pullLog]: time, project, outcome, commit, detail (tab-separated).
  ///
  /// It never fails the push: the refs are in already when it runs, and it
  /// always exits 0. The project and its team name come from the origin's
  /// own settings (`oc-mobile.project`, `oc-mobile.rig`, written with the
  /// hook) or from `OC_PROJECT` / `OC_RIG`. Run with `--now` (the app's
  /// "Bring the team's work in", [bringInScript]) it acts without a push
  /// and prints the line.
  static const originHook =
      '#!/bin/sh\n'
      '# Written by OpenCode Mobile (AI Team) on every team start: brings\n'
      '# the work merged here into the project folder.\n'
      'log=$pullLog\n'
      r'''project=${OC_PROJECT:-$(git config --get oc-mobile.project 2>/dev/null)}
rig=${OC_RIG:-$(git config --get oc-mobile.rig 2>/dev/null)}
[ -n "$project" ] && [ -n "$rig" ] || exit 0
now=
[ "${1:-}" = --now ] && now=1
refs=
[ -n "$now" ] || refs=$(cat)
# A hook runs with the origin's GIT_DIR and friends set; the project is
# another repository.
unset GIT_DIR GIT_WORK_TREE GIT_INDEX_FILE GIT_QUARANTINE_PATH \
  GIT_OBJECT_DIRECTORY GIT_ALTERNATE_OBJECT_DIRECTORIES GIT_PREFIX \
  GIT_COMMON_DIR GIT_NAMESPACE 2>/dev/null || true
oc_log() {
  line=$(printf '%s\t%s\t%s\t%s\t%s' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
    "$rig" "$1" "$2" "$(printf '%s' "$3" | tr '\t\n' '  ' | cut -c1-200)")
  mkdir -p "$(dirname "$log")"
  printf '%s\n' "$line" >> "$log"
  if [ "$(wc -l < "$log")" -gt 400 ]; then
    tail -n 200 "$log" > "$log.tmp" && mv "$log.tmp" "$log"
  fi
  [ -z "$now" ] || printf '%s\n' "$line"
}
g() { git -C "$project" "$@"; }
if ! branch=$(g symbolic-ref -q --short HEAD 2>/dev/null); then
  [ -n "$now" ] && oc_log skipped - "the project is not on a branch"
  exit 0
fi
if [ -z "$now" ]; then
  printf '%s\n' "$refs" | grep -q " refs/heads/$branch\$" || exit 0
fi
if ! out=$(g fetch -q origin "+refs/heads/$branch:refs/remotes/origin/$branch" 2>&1); then
  oc_log failed - "could not read the team's copy: $out"
  exit 0
fi
new=$(g rev-parse -q --verify "refs/remotes/origin/$branch^{commit}") || exit 0
short=$(g rev-parse --short "$new")
subject=$(g log -1 --format=%s "$new")
if g merge-base --is-ancestor "$new" HEAD; then
  oc_log up-to-date "$short" "$subject"
  exit 0
fi
if ! g merge-base --is-ancestor HEAD "$new"; then
  oc_log diverged "$short" "$branch has commits the team's copy does not"
  exit 0
fi
changes=$(g status --porcelain --untracked-files=no -- . ':(exclude).beads' ':(exclude).gitignore' | cut -c4- | head -n 5 | tr '\n' ' ')
if [ -n "$changes" ]; then
  oc_log dirty "$short" "$changes"
  exit 0
fi
if out=$(g merge -q --ff-only "$new" 2>&1); then
  oc_log brought-in "$short" "$subject"
else
  oc_log failed "$short" "$out"
fi
exit 0
''';

  /// `oc_install_hook PROJECT RIG`: gives the project's origin [originHook]
  /// when that origin is one of this phone's ([originsDir]; an origin of
  /// the project's own is never touched), then brings in once whatever the
  /// team merged before the hook was there. Idempotent.
  static const _hookFunctions =
      'oc_install_hook() {\n'
      '  oc_origin=\$(git -C "\$1" remote get-url origin 2>/dev/null) '
      '|| return 0\n'
      '  case "\$oc_origin" in $originsDir/*) ;; *) return 0 ;; esac\n'
      '  git -C "\$oc_origin" config oc-mobile.project "\$1" &&\n'
      '  git -C "\$oc_origin" config oc-mobile.rig "\$2" &&\n'
      '  mkdir -p "\$oc_origin/hooks" &&\n'
      "  cat > \"\$oc_origin/hooks/post-receive\" <<'OC_HOOK' &&\n"
      '${originHook}OC_HOOK\n'
      '  chmod 755 "\$oc_origin/hooks/post-receive" || return 1\n'
      '  OC_PROJECT="\$1" OC_RIG="\$2" '
      'sh "\$oc_origin/hooks/post-receive" --now </dev/null >/dev/null 2>&1 '
      '|| true\n'
      '}\n';

  /// Gives every project of the team its origin hook (a team made by an
  /// older version has none); part of every start, from the projects Gas
  /// City lists in `.gc/site.toml`.
  static const hooksScript =
      '$_hookFunctions'
      'if [ -f $cityDir/.gc/site.toml ]; then\n'
      "  awk '/^name = / { n = \$0; sub(/^name = \"/, \"\", n); "
      'sub(/"\$/, "", n) } '
      '/^path = / { p = \$0; sub(/^path = "/, "", p); sub(/"\$/, "", p); '
      "if (n != \"\") print n \"\\t\" p; n = \"\" }' "
      '$cityDir/.gc/site.toml |\n'
      "  while IFS='\t' read -r oc_rig oc_project; do\n"
      '    oc_install_hook "\$oc_project" "\$oc_rig" || true\n'
      '  done\n'
      'fi\n';

  /// Brings the team's merged work into the project at [path] now, as the
  /// origin's hook does after every merge; prints the outcome line.
  static String bringInScript(String path, String rig) {
    if (!path.startsWith('/') || path.contains('\n')) {
      throw ArgumentError.value(path, 'path', 'Must be an absolute path.');
    }
    if (!RegExp(r'^[A-Za-z0-9_-]+$').hasMatch(rig)) {
      throw ArgumentError.value(rig, 'rig', 'Letters, digits, - and _ only.');
    }
    return 'OC_PROJECT=${_quote(path)} OC_RIG=$rig\n'
        'export OC_PROJECT OC_RIG\n'
        'set -- --now\n'
        '$originHook';
  }

  /// The supervisor, as the long-running service. `exec`, so stopping the
  /// service stops the supervisor itself, and with it the store and the
  /// agents it started.
  static String get serviceScript =>
      'set -eu\n'
      '[ ! -e $disabledFile ] || exit 0\n'
      '$_env'
      // The agents' own `opencode` first (AiTeamScripts.agentWrapperScript).
      'export PATH=${AiTeamScripts.agentBin}:\$PATH\n'
      'cd $cityDir\n'
      'mkdir -p /root/.gc\n'
      "cat > /root/.gc/supervisor.toml <<'OC_EOF'\n"
      '${supervisorConfig}OC_EOF\n'
      '$tuneScript'
      '$hooksScript'
      '${AiTeamScripts.refreshAgentWrapperScript}'
      '${AiTeamScripts.lowPriorityExec('gc supervisor run')}';

  /// Registers the team with the running supervisor; a team registered
  /// already is fine. `gc register` records the team at once and then waits
  /// for it to come up, which under proot can take minutes; that wait is
  /// cut after a minute and left to the health check that follows (as the
  /// Termux runtime does).
  static String get registerScript =>
      'set -u\n'
      '$_env'
      'cd $cityDir\n'
      'rc=0\n'
      'out=\$(timeout -k 5 60 gc register $cityDir --name $city --yes 2>&1) '
      '|| rc=\$?\n'
      'case \$rc in 0|124|137) echo registered; exit 0 ;; esac\n'
      'case \$out in *already*|*exists*) echo registered; exit 0 ;; esac\n'
      'printf \'%s\\n\' "\$out" >&2\n'
      'exit 1\n';

  /// What is there: `installed`, `city` and one `rig <name>` per project.
  static String get statusScript =>
      // Installed means what the setup component's own check says (the
      // pinned versions), so an outdated install reads as not installed and
      // Add tools brings it up to date.
      // A shell of its own: `set -e` is ignored inside a subshell on the left
      // of &&, which would make every check pass.
      "if sh -s >/dev/null 2>&1 <<'OC_CHECK'\n"
      '${AiTeamScripts.checkScript}'
      'OC_CHECK\n'
      'then echo installed; fi\n'
      '[ -f $home/city.ready ] && [ -f $cityDir/city.toml ] && echo city\n'
      '[ -f $cityDir/city.toml ] && '
      "awk '/^\\[\\[rigs\\]\\]/ { r = 1; next } "
      "r && /^name = / { gsub(/\"/, \"\", \$3); print \"rig \" \$3; r = 0 }' "
      '$cityDir/city.toml\n'
      // The newest bring-in outcome per project (originHook).
      '[ -f $pullLog ] && '
      "awk -F '\\t' 'NF >= 3 { last[\$2] = \$0 } "
      "END { for (r in last) print \"pull\\t\" last[r] }' $pullLog\n"
      'exit 0\n';

  static BuiltinTeamState parseStatus(String output, {bool running = false}) {
    final lines = output.split('\n').map((line) => line.trim());
    return BuiltinTeamState(
      installed: lines.contains('installed'),
      hasCity: lines.contains('city'),
      rigs: [
        for (final line in lines)
          if (line.startsWith('rig ')) line.substring(4).trim(),
      ],
      bringIns: {
        for (final pull in [
          for (final line in lines)
            if (line.startsWith('pull\t'))
              ?BuiltinTeamBringIn.parse(line.substring(5)),
        ])
          pull.rig: pull,
      },
      running: running,
    );
  }

  // ---- operations ---------------------------------------------------------

  Future<BuiltinTeamState> status() async {
    final linux = await _linux.status();
    if (!linux.installed) return const BuiltinTeamState();
    final result = await _linux.run(
      statusScript,
      timeout: const Duration(seconds: 30),
    );
    return parseStatus(
      result.output,
      running: linux.serviceRunning(serviceName),
    );
  }

  /// Turns the team on for the project at [path]: store, project, supervisor,
  /// health, in that order, each safe to repeat. [onStage] hears where it
  /// is. Throws [BuiltinTeamException] with the stage that failed.
  Future<void> turnOn(
    String path, {
    required String notice,
    void Function(BuiltinTeamStage stage)? onStage,
    Duration healthTimeout = const Duration(minutes: 6),
  }) => _exclusive(() async {
    onStage?.call(BuiltinTeamStage.preparing);
    await _script(
      BuiltinTeamStage.preparing,
      cityScript,
      const Duration(minutes: 5),
    );
    onStage?.call(BuiltinTeamStage.addingProject);
    await _script(
      BuiltinTeamStage.addingProject,
      rigScript(path, rigName(path)),
      const Duration(minutes: 6),
    );
    await _start(
      notice: notice,
      onStage: onStage,
      healthTimeout: healthTimeout,
    );
  });

  Future<void>? _preparing;

  /// Makes the team's store now, in the background, when AI Team is
  /// installed and has none yet, so that turning it on for a project later
  /// skips that step (2 to 3 of the ~9 minutes on the emulator). One at a
  /// time; [turnOn] waits for it. A failure is left for [turnOn] to meet
  /// again, with its own retries and its own message. The store is not the
  /// team: nothing is started or registered, and nothing keeps running.
  Future<void> prepare() => _preparing ??= _exclusive(() async {
    try {
      if (await isTurnedOff()) return;
      await _script(
        BuiltinTeamStage.preparing,
        cityScript,
        const Duration(minutes: 5),
      );
    } catch (_) {
      // turnOn runs the same script again and says what went wrong.
    } finally {
      _preparing = null;
    }
  });

  /// The model the team's agents start on, one `provider/model` line the
  /// agents' `opencode` wrapper reads at each start
  /// (AiTeamScripts.agentWrapperScript). Absent: OpenCode's own default.
  static const modelFile = '$home/model';

  /// Writes (or, for null, removes) [modelFile] and refreshes the wrapper
  /// that reads it. Takes effect for the next agent start; a running agent
  /// keeps the model it started with.
  static String modelScript(String? model) {
    if (model != null && !isValidTeamModel(model)) {
      throw ArgumentError.value(model, 'model', 'not a provider/model');
    }
    final write = model == null
        ? 'rm -f $modelFile\n'
        : "printf '%s\\n' '$model' > $modelFile\n";
    return 'set -eu\n'
        'mkdir -p $home\n'
        '$write'
        '${AiTeamScripts.refreshAgentWrapperScript}';
  }

  /// Sets the model the team's agents use from their next start.
  Future<void> applyModel(String? model) =>
      _exclusive(() => _manageScript('model', modelScript(model)));

  /// Starts the supervisor unless it runs, registers the team and waits for
  /// it to answer. A supervisor that runs but does not answer is restarted
  /// once.
  Future<void> start({
    required String notice,
    void Function(BuiltinTeamStage stage)? onStage,
    Duration healthTimeout = const Duration(minutes: 6),
  }) => _exclusive(
    () =>
        _start(notice: notice, onStage: onStage, healthTimeout: healthTimeout),
  );

  Future<void> _start({
    required String notice,
    void Function(BuiltinTeamStage stage)? onStage,
    required Duration healthTimeout,
  }) async {
    final gen = _progress.begin();
    try {
      await _manageScript('enable', enableScript);
      _requireCurrent(gen, BuiltinTeamStage.starting);
      onStage?.call(BuiltinTeamStage.starting);
      _progress.enter(gen, BuiltinTeamStartStep.service);
      final linux = await _linux.status();
      _requireCurrent(gen, BuiltinTeamStage.starting);
      if (!linux.serviceRunning(serviceName) || !await supervisorAnswers()) {
        _requireCurrent(gen, BuiltinTeamStage.starting);
        // Held while the phone has no room, is hot or low on battery.
        await _admit();
        _requireCurrent(gen, BuiltinTeamStage.starting);
        await _linux.startService(
          serviceName,
          serviceScript,
          port: port,
          notice: notice,
        );
      }
      _progress.enter(gen, BuiltinTeamStartStep.answering);
      await _waitFor(
        BuiltinTeamStage.starting,
        supervisorAnswers,
        const Duration(seconds: 90),
        gen: gen,
      );
      _progress.enter(gen, BuiltinTeamStartStep.store);
      _requireCurrent(gen, BuiltinTeamStage.starting);
      // A live healthy city is already registered. Re-registering invokes a
      // blocking CLI under proot even though the authoritative health is ready.
      if (await _boundedProbe(
            cityAnswers,
            DateTime.now().add(const Duration(seconds: 5)),
            gen,
          ) !=
          true) {
        _requireCurrent(gen, BuiltinTeamStage.starting);
        await _script(
          BuiltinTeamStage.starting,
          registerScript,
          const Duration(minutes: 2),
        );
      }
      _requireCurrent(gen, BuiltinTeamStage.starting);
      onStage?.call(BuiltinTeamStage.waiting);
      await _waitFor(
        BuiltinTeamStage.waiting,
        cityAnswers,
        healthTimeout,
        gen: gen,
      );
      _progress.enter(gen, BuiltinTeamStartStep.agents);
      await _waitAgents(gen, agentsWait);
      _progress.finish(gen);
    } catch (error) {
      _progress.fail(gen, error);
      rethrow;
    }
  }

  /// The team is the heaviest thing the app runs: it starts only within
  /// the process budget (process_budget.dart).
  Future<void> _admit() =>
      (admitTeam ?? ProcessBudgetGuard.instance.admit)(HeavyJob.aiTeam);

  /// Test seam for the process budget.
  @visibleForTesting
  static Future<void> Function(HeavyJob job)? admitTeam;

  /// Starts the supervisor when it is not running, without waiting: for the
  /// app coming back after Android stopped it. The team answers a little
  /// later, and the Team card shows it as reconnecting meanwhile. With
  /// [observe] the coming-up is also watched, in the background and bounded,
  /// and reported through [startProgress] like an explicit [start].
  Future<void> ensureRunning({required String notice, bool observe = false}) =>
      _exclusive(() async {
        try {
          final linux = await _linux.status();
          if (!linux.installed) return;
          final already = linux.serviceRunning(serviceName);
          if (already && !observe) return;
          if (await isTurnedOff()) return;
          final gen = observe ? _progress.begin() : null;
          if (gen != null) _progress.enter(gen, BuiltinTeamStartStep.service);
          if (!already) {
            try {
              await _admit();
              await _linux.startService(
                serviceName,
                serviceScript,
                port: port,
                notice: notice,
              );
            } catch (error) {
              if (gen != null) _progress.fail(gen, error);
              rethrow;
            }
          }
          if (gen != null) unawaited(_observe(gen));
        } on BuiltinLinuxException {
          // The next open of the team says what is wrong.
        }
      });

  /// Watches a coming-up someone else started: supervisor, city, agents.
  Future<void> _observe(int gen) async {
    try {
      _progress.enter(gen, BuiltinTeamStartStep.answering);
      await _waitFor(
        BuiltinTeamStage.starting,
        supervisorAnswers,
        const Duration(minutes: 3),
        gen: gen,
      );
      _progress.enter(gen, BuiltinTeamStartStep.store);
      await _waitFor(
        BuiltinTeamStage.waiting,
        cityAnswers,
        recoveryStoreTimeout,
        gen: gen,
      );
      _progress.enter(gen, BuiltinTeamStartStep.agents);
      await _waitAgents(gen, agentsWait);
      _progress.finish(gen);
    } catch (error) {
      _progress.fail(gen, error);
    }
  }

  /// Temporarily stops the service. Recovery may restart it; use [turnOff]
  /// for a durable user choice.
  Future<void> stop() {
    _progress.cancel();
    return _exclusive(() => _linux.stopService(serviceName));
  }

  /// Whether automatic recovery is disabled. Errors fail closed: callers must
  /// not interpret a failed marker read as permission to start the team.
  Future<bool> isTurnedOff() async {
    try {
      final result = await _linux.run(disabledCheckScript);
      if (result.exitCode == 42) return true;
      if (result.ok) return false;
    } on BuiltinLinuxException {
      // Do not expose native error details from the marker read.
    }
    throw const BuiltinLinuxException(
      'Could not read AI Team enabled state.',
      code: 'team_state_unavailable',
    );
  }

  /// Disables recovery durably, then stops AI Team. Programs, team state and
  /// projects survive. Explicit [start] or [turnOn] enables recovery again.
  ///
  /// The UI also clears each built-in profile's orchestration config through
  /// ProfileStore.upsert, then calls ConnectionController.syncOrchestration.
  /// The runtime marker prevents stale profile copies from restarting it.
  Future<void> turnOff() {
    _progress.cancel();
    return _exclusive(_turnOff);
  }

  Future<void> _turnOff() async {
    await _manageScript('disable', disableScript);
    try {
      await _linux.stopService(serviceName);
    } on BuiltinLinuxException catch (error) {
      throw BuiltinLinuxException(
        KitRedact.text(error.message),
        code: error.code,
      );
    }
  }

  /// Turns off the service and executes the setup component's remove script.
  /// Projects survive. Team tasks/settings and phone-side bare origins under
  /// /root/aiteam do not; projects may retain an origin URL into that folder.
  /// No files are removed when disabling or stopping the service fails.
  Future<void> remove() {
    _progress.cancel();
    return _exclusive(() async {
      await _turnOff();
      await _manageScript('remove', AiTeamScripts.removeScript);
    });
  }

  Future<void> _manageScript(String operation, String script) async {
    try {
      final result = await _linux.run(script);
      if (!result.ok) {
        throw BuiltinLinuxException(
          'AI Team $operation failed (exit ${result.exitCode}).',
          code: 'team_$operation',
        );
      }
    } on BuiltinLinuxException catch (error) {
      throw BuiltinLinuxException(
        KitRedact.text(error.message),
        code: error.code,
      );
    }
  }

  /// Brings the team's merged work into the project at [path] now, on the
  /// same terms as the origin's hook ([originHook]); null when the script
  /// said nothing (the project has no phone-side origin).
  Future<BuiltinTeamBringIn?> bringIn(String path) async {
    final result = await _linux.run(
      bringInScript(path, rigName(path)),
      timeout: const Duration(minutes: 2),
    );
    BuiltinTeamBringIn? last;
    for (final line in result.output.split('\n')) {
      last = BuiltinTeamBringIn.parse(line.trim()) ?? last;
    }
    return last;
  }

  /// The supervisor's own account of its latest agent starts, oldest first,
  /// from the end of its log: how long each start took and why one failed.
  /// Empty when the log says nothing about starts or cannot be read. For the
  /// Team screens ("the worker took 24 s to start") and for measuring a
  /// phone (docs/qa/team-hot-2026-09-26).
  Future<List<BuiltinTeamAgentStart>> agentStarts({
    int tailBytes = 64 * 1024,
  }) async {
    try {
      final log = await _linux.serviceLog(serviceName, tailBytes: tailBytes);
      return BuiltinTeamAgentStart.parseLog(log);
    } on BuiltinLinuxException {
      return const [];
    }
  }

  Future<bool> supervisorAnswers() => _answersOk(Uri.parse('$url/health'));

  Future<bool> cityAnswers() =>
      _answersOk(Uri.parse('$url/v0/city/$city/health'));

  Future<bool> _answersOk(Uri uri) async {
    final body = await _get(uri);
    if (body == null) return false;
    try {
      final decoded = jsonDecode(body);
      return decoded is Map && decoded['status'] == 'ok';
    } on FormatException {
      return false;
    }
  }

  /// Whether the team's agents are listed: the status names an agent count
  /// above zero. A status without an agent count says nothing to wait for.
  Future<bool> agentsListed() async {
    final body = await _get(Uri.parse('$url/v0/city/$city/status'));
    if (body == null) return false;
    try {
      final decoded = jsonDecode(body);
      if (decoded is! Map) return false;
      final agents = decoded['agents'];
      if (agents is! Map) return true;
      final total = agents['total'];
      return total is num && total > 0;
    } on FormatException {
      return false;
    }
  }

  /// The last step: waits for the agents to be listed, up to [timeout]. A
  /// list that stays empty is let go, not a failure; a stopped service is.
  Future<void> _waitAgents(int gen, Duration timeout) async {
    final deadline = DateTime.now().add(timeout);
    while (true) {
      _requireCurrent(gen, BuiltinTeamStage.waiting);
      if (!DateTime.now().isBefore(deadline)) return;
      final listed = await _boundedProbe(agentsListed, deadline, gen);
      _requireCurrent(gen, BuiltinTeamStage.waiting);
      if (listed == true) return;
      // The optional agent list may stay empty, including at its deadline.
      if (listed == null || !DateTime.now().isBefore(deadline)) return;
      final running = await _boundedProbe(
        () async => (await _linux.status()).serviceRunning(serviceName),
        deadline,
        gen,
      );
      _requireCurrent(gen, BuiltinTeamStage.waiting);
      if (running == false) {
        throw BuiltinTeamException(
          BuiltinTeamStage.waiting,
          await _logTail(),
          exited: true,
        );
      }
      _progress.tick(gen);
      await _boundedProbe(
        () async {
          await Future<void>.delayed(pollInterval);
          return true;
        },
        deadline,
        gen,
      );
    }
  }

  void _requireCurrent(int? gen, BuiltinTeamStage stage) {
    if (gen != null && !_progress.isCurrent(gen)) {
      throw BuiltinTeamException(stage, 'Startup canceled.', canceled: true);
    }
  }

  /// Bound every awaited probe, not just the delay between probes. A hanging
  /// health request must not outlive recovery, and Stop cancels observation
  /// immediately without claiming the store became ready.
  Future<bool?> _boundedProbe(
    Future<bool> Function() probe,
    DateTime deadline,
    int? gen,
  ) async {
    final remaining = deadline.difference(DateTime.now());
    if (remaining <= Duration.zero) return null;
    final result = Completer<bool?>();
    final timer = Timer(remaining, () {
      if (!result.isCompleted) result.complete(null);
    });
    void changed() {
      if (gen != null && !_progress.isCurrent(gen) && !result.isCompleted) {
        result.complete(null);
      }
    }

    if (gen != null) _progress.addListener(changed);
    try {
      changed();
      if (!result.isCompleted) {
        Future<bool>.sync(probe).then(
          (answer) {
            if (!result.isCompleted) result.complete(answer);
          },
          onError: (Object error, StackTrace stack) {
            if (!result.isCompleted) result.completeError(error, stack);
          },
        );
      }
      return await result.future;
    } finally {
      timer.cancel();
      if (gen != null) _progress.removeListener(changed);
    }
  }

  Future<void> _waitFor(
    BuiltinTeamStage stage,
    Future<bool> Function() ok,
    Duration timeout, {
    int? gen,
  }) async {
    final deadline = DateTime.now().add(timeout);
    while (true) {
      _requireCurrent(gen, stage);
      final answered = await _boundedProbe(ok, deadline, gen);
      _requireCurrent(gen, stage);
      if (answered == true) return;
      if (!DateTime.now().isBefore(deadline)) {
        throw BuiltinTeamException(
          stage,
          'The team did not answer before the startup deadline.',
          timedOut: true,
        );
      }
      final running = await _boundedProbe(
        () async => (await _linux.status()).serviceRunning(serviceName),
        deadline,
        gen,
      );
      _requireCurrent(gen, stage);
      if (running == false && DateTime.now().isBefore(deadline)) {
        throw BuiltinTeamException(stage, await _logTail(), exited: true);
      }
      if (gen != null) _progress.tick(gen);
      await _boundedProbe(
        () async {
          await Future<void>.delayed(pollInterval);
          return true;
        },
        deadline,
        gen,
      );
    }
  }

  Future<String> _logTail() async {
    try {
      final log = await _linux.serviceLog(serviceName, tailBytes: 2048);
      return log.trim();
    } on BuiltinLinuxException {
      return '';
    }
  }

  Future<void> _script(
    BuiltinTeamStage stage,
    String script,
    Duration timeout,
  ) async {
    final BuiltinLinuxRunResult result;
    try {
      result = await _linux.run(script, timeout: timeout);
    } on BuiltinLinuxException catch (error) {
      throw BuiltinTeamException(stage, error.message);
    }
    if (!result.ok) {
      final output = result.output.trim();
      throw BuiltinTeamException(
        stage,
        output.isEmpty ? 'exit ${result.exitCode}' : _tail(output),
      );
    }
  }

  static String _tail(String text, {int lines = 12}) {
    final all = text.split('\n');
    return all.skip(all.length > lines ? all.length - lines : 0).join('\n');
  }

  static String _quote(String value) => "'${value.replaceAll("'", "'\"'\"'")}'";

  /// A GET on the phone's loopback with a short timeout; null when nothing
  /// answers.
  static Future<String?> _loopbackGet(Uri uri) async {
    final client = HttpClient()..connectionTimeout = const Duration(seconds: 3);
    try {
      final request = await client.getUrl(uri);
      final response = await request.close().timeout(
        const Duration(seconds: 5),
      );
      final body = await response
          .transform(utf8.decoder)
          .join()
          .timeout(const Duration(seconds: 5));
      return response.statusCode == 200 ? body : null;
    } catch (_) {
      return null;
    } finally {
      client.close(force: true);
    }
  }
}

/// Where [BuiltinTeam.turnOn] is.
/// A [ValueListenable] view of the progress, so a page can listen with
/// `ValueListenableBuilder`.
class _StartProgressListenable
    implements ValueListenable<BuiltinTeamStartProgress> {
  const _StartProgressListenable(this._progress);
  final BuiltinTeamStartProgress _progress;

  @override
  BuiltinTeamStartProgress get value => _progress;

  @override
  void addListener(VoidCallback listener) => _progress.addListener(listener);

  @override
  void removeListener(VoidCallback listener) =>
      _progress.removeListener(listener);
}

enum BuiltinTeamStage { preparing, addingProject, starting, waiting }

/// What [BuiltinTeam.status] found.
class BuiltinTeamState {
  const BuiltinTeamState({
    this.installed = false,
    this.hasCity = false,
    this.rigs = const [],
    this.bringIns = const {},
    this.running = false,
  });

  /// The programs are there (the setup component ran).
  final bool installed;

  /// The team's store exists.
  final bool hasCity;

  /// The projects the team works on, by team name ([BuiltinTeam.rigName]).
  final List<String> rigs;

  /// The newest bring-in outcome per project, by team name.
  final Map<String, BuiltinTeamBringIn> bringIns;

  /// The supervisor runs as the app's service.
  final bool running;

  bool hasProject(String path) => rigs.contains(BuiltinTeam.rigName(path));

  BuiltinTeamBringIn? bringInFor(String path) =>
      bringIns[BuiltinTeam.rigName(path)];
}

/// What [BuiltinTeam.originHook] did with the team's merged work.
enum BuiltinTeamBringInOutcome {
  broughtIn('brought-in'),
  upToDate('up-to-date'),
  dirty('dirty'),
  diverged('diverged'),
  skipped('skipped'),
  failed('failed');

  const BuiltinTeamBringInOutcome(this.word);

  /// The word in the log.
  final String word;
}

/// One line of [BuiltinTeam.pullLog].
class BuiltinTeamBringIn {
  const BuiltinTeamBringIn({
    required this.time,
    required this.rig,
    required this.outcome,
    this.commit,
    this.detail = '',
  });

  final DateTime? time;
  final String rig;
  final BuiltinTeamBringInOutcome outcome;

  /// The short hash of the team's newest commit, when known.
  final String? commit;

  /// The commit's subject, the changes that kept it out, or git's reason.
  final String detail;

  /// The project was left behind the team's work.
  bool get leftBehind => switch (outcome) {
    BuiltinTeamBringInOutcome.dirty ||
    BuiltinTeamBringInOutcome.diverged ||
    BuiltinTeamBringInOutcome.failed => true,
    _ => false,
  };

  /// A log line (time, project, outcome, commit, detail; tab-separated), or
  /// null for anything else.
  static BuiltinTeamBringIn? parse(String line) {
    final parts = line.split('\t');
    if (parts.length < 3) return null;
    final outcome = BuiltinTeamBringInOutcome.values
        .where((value) => value.word == parts[2].trim())
        .firstOrNull;
    if (outcome == null) return null;
    final commit = parts.length > 3 ? parts[3].trim() : '';
    return BuiltinTeamBringIn(
      time: DateTime.tryParse(parts[0].trim()),
      rig: parts[1].trim(),
      outcome: outcome,
      commit: commit.isEmpty || commit == '-' ? null : commit,
      detail: parts.length > 4 ? parts.sublist(4).join(' ').trim() : '',
    );
  }
}

/// One agent start, as Gas City logs it (`session lifecycle: op=start …`).
class BuiltinTeamAgentStart {
  const BuiltinTeamAgentStart({
    required this.session,
    required this.template,
    required this.outcome,
    this.duration,
    this.startCall,
    this.error,
  });

  /// The runtime session name, e.g. `gastown__polecat-ph-yqt`.
  final String session;

  /// The agent it starts, e.g. `demo-app/gastown.polecat`.
  final String template;

  /// Gas City's word: `success`, `deadline_exceeded`, `provider_error`, …
  final String outcome;

  /// The whole start, when logged.
  final Duration? duration;

  /// Of which the agent's own start (for OpenCode: the process and its
  /// handshake).
  final Duration? startCall;

  /// Gas City's reason for a failed start, with the agent's own last words
  /// when it gave any.
  final String? error;

  bool get succeeded => outcome == 'success';

  /// Cut off because it took too long (the case the phone tuning's longer
  /// start and handshake times are for).
  bool get timedOut =>
      outcome == 'deadline_exceeded' ||
      (error?.contains('context deadline exceeded') ?? false);

  static final _line = RegExp(
    r'session lifecycle: op=start wave=\d+ session=(\S+) template=(\S+) '
    r'outcome=(\S+)(?: duration=(\S+))?(?: phases=\[([^\]]*)\])?(?: err=(.*))?$',
  );

  /// Every start line of [log], oldest first.
  static List<BuiltinTeamAgentStart> parseLog(String log) => [
    for (final line in log.split('\n'))
      if (_line.firstMatch(line.trim()) case final match?)
        BuiltinTeamAgentStart(
          session: match[1]!,
          template: match[2]!,
          outcome: match[3]!,
          duration: parseGoDuration(match[4]),
          startCall: parseGoDuration(
            RegExp(r'start_call=(\S+)').firstMatch(match[5] ?? '')?[1],
          ),
          error: match[6]?.replaceAll(r'\n', '\n'),
        ),
  ];

  /// A Go duration as Gas City prints it (`850ms`, `22.345s`, `1m2.5s`,
  /// `1h0m0s`); null for anything else.
  static Duration? parseGoDuration(String? value) {
    if (value == null || value.isEmpty) return null;
    final parts = RegExp(
      r'(\d+(?:\.\d+)?)(h|ms|us|µs|ns|m|s)',
    ).allMatches(value).toList();
    if (parts.isEmpty || parts.map((m) => m[0]).join() != value) return null;
    var micros = 0.0;
    for (final part in parts) {
      final number = double.parse(part[1]!);
      micros += switch (part[2]) {
        'h' => number * Duration.microsecondsPerHour,
        'm' => number * Duration.microsecondsPerMinute,
        's' => number * Duration.microsecondsPerSecond,
        'ms' => number * Duration.microsecondsPerMillisecond,
        'ns' => number / 1000,
        _ => number,
      };
    }
    return Duration(microseconds: micros.round());
  }
}

class BuiltinTeamException implements Exception {
  const BuiltinTeamException(
    this.stage,
    this.detail, {
    this.exited = false,
    this.timedOut = false,
    this.canceled = false,
  });

  final BuiltinTeamStage stage;

  /// The technical reason (script output or the supervisor's log tail).
  final String detail;

  /// The supervisor stopped on its own.
  final bool exited;

  /// It did not answer in time.
  final bool timedOut;

  /// Stop, turn-off or removal superseded this start before it became ready.
  final bool canceled;

  @override
  String toString() => 'AI Team (${stage.name}): $detail';
}
