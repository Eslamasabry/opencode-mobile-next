/// AI Team's programs for the in-app Linux, and the scripts of its setup
/// component (components.dart).
///
/// The programs are the upstream projects' own Linux releases, pinned by
/// version and SHA-256: Gas City (`gc`), Beads (`bd`) and Dolt. Inside the
/// app they run under proot like every other Ubuntu program, so the Linux
/// builds fit and nothing from Termux is needed (`gc` and `dolt` are static,
/// `bd` needs only Ubuntu's glibc).
///
/// Android's system-call filter is the known risk: in Termux, without proot,
/// Android 15 killed Go's Linux builds with SIGSYS on `faccessat2`. The
/// install runs each program once and fails with that reason in plain words
/// if it happens, instead of leaving a team that dies later.
library;

import '../abi_shim.dart';

/// One pinned download.
class AiTeamDownload {
  const AiTeamDownload({
    required this.tool,
    required this.url,
    required this.sha256,
    required this.bytes,
    required this.member,
  });

  /// `gc`, `bd` or `dolt`: the command it installs.
  final String tool;
  final String url;
  final String sha256;
  final int bytes;

  /// The program's path inside the archive.
  final String member;

  /// The archive's file name, for a mirror laid out flat (the test override).
  String get fileName => url.substring(url.lastIndexOf('/') + 1);
}

abstract final class AiTeamPins {
  static const gascity = '1.4.1';
  static const beads = '1.2.2';
  static const dolt = '2.3.5';

  /// The Gas City pack the city imports, pinned by commit (the Termux
  /// runtime's pin).
  static const packSource =
      'https://github.com/gastownhall/gascity-packs/tree/main/gastown';
  static const packVersion = 'sha:33d3a430a67d1782ad364556cb566bdb01d0afe3';

  static const _gascity =
      'https://github.com/gastownhall/gascity/releases/download/v$gascity';
  static const _beads =
      'https://github.com/gastownhall/beads/releases/download/v$beads';
  static const _dolt =
      'https://github.com/dolthub/dolt/releases/download/v$dolt';

  /// From each project's release: gascity_1.4.1_checksums.txt and the beads
  /// checksums.txt; Dolt publishes none, so its hashes are the ones GitHub
  /// reports for the assets (checked against downloads, 2026-09-27).
  static const arm64 = [
    AiTeamDownload(
      tool: 'gc',
      url: '$_gascity/gascity_${gascity}_linux_arm64.tar.gz',
      sha256:
          '6620ef51c8ba620821e5ef8b208bb1b3de090fa86ec5e0327da1edd615407e29',
      bytes: 26032922,
      member: 'gc',
    ),
    AiTeamDownload(
      tool: 'bd',
      url: '$_beads/beads_${beads}_linux_arm64.tar.gz',
      sha256:
          '501f38a1070d4b9b3b6261a86a3c92c4a52366869021560430a4bb0036afd83a',
      bytes: 45556402,
      member: 'bd',
    ),
    AiTeamDownload(
      tool: 'dolt',
      url: '$_dolt/dolt-linux-arm64.tar.gz',
      sha256:
          '9ce70fc81e50139e97758ef7f4dc57e9583e4e5ef05ad75d7535c30caa161387',
      bytes: 40789338,
      member: 'dolt-linux-arm64/bin/dolt',
    ),
  ];

  static const x64 = [
    AiTeamDownload(
      tool: 'gc',
      url: '$_gascity/gascity_${gascity}_linux_amd64.tar.gz',
      sha256:
          '8d8c8b511db3fc44931445aab5cb9f212509c0867105c880d6c3d0e6e5d33e42',
      bytes: 28906433,
      member: 'gc',
    ),
    AiTeamDownload(
      tool: 'bd',
      url: '$_beads/beads_${beads}_linux_amd64.tar.gz',
      sha256:
          '8140098a51d3b81d5548d1c5e6db1a2d9930e5d141efe2a4bff7d079c4d321e8',
      bytes: 49107375,
      member: 'bd',
    ),
    AiTeamDownload(
      tool: 'dolt',
      url: '$_dolt/dolt-linux-amd64.tar.gz',
      sha256:
          'c49d4c3e004cf1581ba0d4a00c5023a26f84eb2ec15d5fe876eed36d5343f463',
      bytes: 44023897,
      member: 'dolt-linux-amd64/bin/dolt',
    ),
  ];

  /// A local mirror for testing only, set at build time with
  /// `--dart-define=AITEAM_BASE_URL=http://127.0.0.1:8876/aiteam/`: the
  /// archives are then fetched from `<base><archive file name>`, still
  /// checked against the same SHA-256. Empty (the default) means upstream.
  static const baseUrlOverride = String.fromEnvironment('AITEAM_BASE_URL');

  /// The downloads for this device's CPU; arm64 unless the app runs on
  /// x86_64 (the emulator).
  static List<AiTeamDownload> forDevice([Abi? abi]) =>
      (abi ?? Abi.current()) == Abi.androidX64 ? x64 : arm64;

  static int bytesFor(List<AiTeamDownload> downloads) =>
      downloads.fold(0, (sum, download) => sum + download.bytes);

  /// What adding AI Team downloads on this device: the three archives plus
  /// the few Ubuntu packages (tmux, jq, lsof, procps; about 3 MB).
  static int get deviceDownloadBytes => bytesFor(forDevice()) + 3000000;
}

abstract final class AiTeamScripts {
  /// Where the programs are unpacked; /usr/local/bin gets links to them.
  static const home = '/opt/aiteam';
  static const cache = '/var/cache/oc-setup/aiteam';

  /// Where the agents' `opencode` is: first on the supervisor's PATH only
  /// (BuiltinTeam.serviceScript), so the OpenCode server and the person's
  /// own shell keep the real one.
  static const agentBin = '$home/agent-bin';

  /// The agents' `opencode`: Gas City starts each agent as `opencode acp`
  /// in its work folder under the team's `.gc/worktrees/`, but its OpenCode
  /// provider does not run the pack's step that makes that folder a git
  /// worktree of the project (seen in the Termux spike), so the agent would
  /// start in an empty folder. This makes it one (idempotent), then runs the
  /// real OpenCode.
  ///
  /// - The team's model: the app writes `provider/model` to
  ///   `~/aiteam/model` (Team settings › Model); each start reads it and
  ///   gives OpenCode an inline config (`OPENCODE_CONFIG_CONTENT`) with that
  ///   `model`, so the next worker starts on it. No file: OpenCode's own
  ///   default. A value that is not a plain `provider/model` is ignored.
  ///
  /// Costs no process of its own: `sed` and `ln` run once, before `exec`.
  static const agentWrapperScript = r'''#!/bin/sh
case "$PWD" in */.gc/worktrees/*) in_worktree=1 ;; *) in_worktree= ;; esac
if [ -n "${GC_RIG_ROOT:-}" ] && [ -n "$in_worktree" ] && [ ! -e "$PWD/.git" ]; then
  setup=$(ls -d "$HOME"/.gc/cache/repos/*/gastown/assets/scripts/worktree-setup.sh 2>/dev/null | head -n 1)
  if [ -n "$setup" ]; then
    bash "$setup" "$GC_RIG_ROOT" "$PWD" "${GC_ALIAS##*/}" --sync >> "$HOME/aiteam/agent-worktrees.log" 2>&1 || true
  fi
fi
oc_model=$(head -n 1 "$HOME/aiteam/model" 2>/dev/null || true)
case "$oc_model" in
  ''|*[!A-Za-z0-9._:@+/-]*) ;;
  */*) export OPENCODE_CONFIG_CONTENT="{\"model\":\"$oc_model\"}" ;;
esac
export OPENCODE_DISABLE_MODELS_FETCH=1
# Lowest CPU (and idle I/O) priority: the person's own chat server, at normal
# priority, always wins. ionice is used only where the kernel takes it.
if command -v nice >/dev/null 2>&1; then
  if command -v ionice >/dev/null 2>&1 && ionice -c 3 true 2>/dev/null; then
    exec nice -n 19 ionice -c 3 /usr/local/bin/opencode2 "$@"
  fi
  exec nice -n 19 /usr/local/bin/opencode2 "$@"
fi
exec /usr/local/bin/opencode2 "$@"
''';

  /// `exec`s [command] at the lowest CPU priority (and idle I/O priority
  /// where `ionice` works), for the team's supervisor: everything it starts
  /// (store, agents) inherits it, so the person's chat server (a separate
  /// service at normal priority) wins the phone's CPU. Falls back to plain
  /// `exec` when `nice` is missing.
  static String lowPriorityExec(String command) =>
      'if command -v nice >/dev/null 2>&1; then\n'
      '  if command -v ionice >/dev/null 2>&1 && '
      'ionice -c 3 true 2>/dev/null; then\n'
      '    exec nice -n 19 ionice -c 3 $command\n'
      '  fi\n'
      '  exec nice -n 19 $command\n'
      'fi\n'
      'exec $command\n';

  /// Writes [agentWrapperScript] over the installed one when AI Team is
  /// installed; part of every team start, so a phone installed by an older
  /// version gets this version's wrapper without installing again. Running
  /// agents are not affected (their wrapper already `exec`ed OpenCode). A
  /// failed write keeps the old wrapper, which still works.
  static const refreshAgentWrapperScript =
      'if [ -d $agentBin ]; then\n'
      "  { cat > $agentBin/opencode.oc-new <<'OC_EOF'\n"
      '${agentWrapperScript}OC_EOF\n'
      '  } && chmod 755 $agentBin/opencode.oc-new && '
      'mv -f $agentBin/opencode.oc-new $agentBin/opencode || '
      'rm -f $agentBin/opencode.oc-new\n'
      'fi\n';

  /// Passes only when all three programs answer with their pinned version
  /// and the tools the team uses are there, so a pin bump finds something
  /// to update. The last line is Gas City's version.
  static String get checkScript =>
      'set -e\n'
      '[ "\$(gc version 2>/dev/null)" = \'${AiTeamPins.gascity}\' ]\n'
      'bd --version 2>/dev/null | grep -q \'^bd version ${AiTeamPins.beads}\'\n'
      '[ "\$(dolt version 2>/dev/null | head -n 1)" = '
      '\'dolt version ${AiTeamPins.dolt}\' ]\n'
      'for tool in tmux jq lsof git; do command -v "\$tool" >/dev/null; done\n'
      '[ -x $agentBin/opencode ]\n'
      'echo ${AiTeamPins.gascity}\n';

  /// Downloads (resumable, SHA-256 checked) and installs the three programs
  /// for the device's CPU. [downloading] labels each download with its index
  /// (`{index}` and `{total}` are filled in here) and [preparing] the rest,
  /// in the app's language.
  ///
  /// Only installs: the team itself (its store, a project, the running
  /// supervisor) is made later, per project, by BuiltinTeam.
  static String installScript({
    required String downloading,
    required String preparing,
    String baseUrlOverride = AiTeamPins.baseUrlOverride,
  }) {
    final buffer = StringBuffer()
      ..writeln('set -eu')
      ..writeln('case "\$(uname -m)" in');
    for (final (pattern, downloads) in [
      ('aarch64|arm64', AiTeamPins.arm64),
      ('x86_64|amd64', AiTeamPins.x64),
    ]) {
      buffer.writeln('  $pattern)');
      for (final download in downloads) {
        final url = baseUrlOverride.isEmpty
            ? download.url
            : '$baseUrlOverride${download.fileName}';
        final tool = download.tool;
        buffer
          ..writeln('    ${tool}_url=${_quote(url)}')
          ..writeln('    ${tool}_sha=${download.sha256}')
          ..writeln('    ${tool}_member=${_quote(download.member)}');
      }
      buffer.writeln('    ;;');
    }
    buffer
      ..writeln(
        '  *) echo "[oc] AI Team needs a 64-bit phone (this one reports '
        '\$(uname -m))" >&2; exit 64 ;;',
      )
      ..writeln('esac')
      ..writeln('oc_apt_install $ubuntuPackages')
      ..writeln('mkdir -p $cache');
    const tools = ['gc', 'bd', 'dolt'];
    for (var i = 0; i < tools.length; i++) {
      final tool = tools[i];
      final label = downloading
          .replaceAll('{index}', '${i + 1}')
          .replaceAll('{total}', '${tools.length}');
      buffer
        ..writeln('oc_stage ${_quote(label)}')
        ..writeln(
          'oc_download "\$${tool}_url" $cache/$tool.tar.gz "\$${tool}_sha"',
        );
    }
    buffer
      ..writeln('oc_stage ${_quote(preparing)}')
      ..write(unpackScript)
      ..writeln('rm -rf $cache')
      ..writeln('oc_version ${AiTeamPins.gascity}');
    return buffer.toString();
  }

  /// The Ubuntu packages the team needs besides git: lsof (`gc` refuses to
  /// start a city without it), procps (`ps` and `pkill`, which its scripts
  /// use), tmux and jq (the Gas City pack).
  static const ubuntuPackages = 'tmux jq lsof procps';

  /// The part of [installScript] after the downloads, shared with the
  /// Termux runtime (lib/termux/team_scripts.dart), which downloads on the
  /// Termux side and unpacks inside its own Ubuntu: unpacks the three
  /// verified archives from [cache] into [home] (replacing an older one
  /// only once all three unpacked), writes the agents' `opencode`, links
  /// the programs into /usr/local/bin, sets the team's identity and runs
  /// each program once. Expects `gc_member`, `bd_member` and `dolt_member`
  /// (each program's path inside its archive) to be set; leaves [cache] for
  /// the caller to delete.
  static String get unpackScript => _unpackScript(appFallback: true);

  /// [unpackScript] with plain `tar`, for the Termux runtime: it has no
  /// setup prelude and no app to answer `::oc unpack`.
  static String get unpackScriptPlainTar => _unpackScript(appFallback: false);

  static String _unpackScript({required bool appFallback}) {
    const tools = ['gc', 'bd', 'dolt'];
    final buffer = StringBuffer()
      ..writeln('rm -rf $home.new')
      ..writeln('mkdir -p $home.new/bin');
    for (final tool in tools) {
      final strip = '"\$(printf %s "\$${tool}_member" | tr -cd / | wc -c)"';
      buffer.writeln(
        appFallback
            ? 'oc_untar $cache/$tool.tar.gz $home.new/bin $strip '
                  '"\$${tool}_member"'
            : 'tar -xzf $cache/$tool.tar.gz -C $home.new/bin '
                  '--strip-components=$strip "\$${tool}_member"',
      );
    }
    buffer
      ..writeln('mkdir -p $home.new/agent-bin')
      ..writeln("cat > $home.new/agent-bin/opencode <<'OC_EOF'")
      ..write(agentWrapperScript)
      ..writeln('OC_EOF')
      ..writeln('chmod 755 $home.new/bin/* $home.new/agent-bin/opencode')
      ..writeln('rm -rf $home')
      ..writeln('mv $home.new $home')
      ..writeln(
        'for tool in gc bd dolt; do ln -sf $home/bin/\$tool '
        '/usr/local/bin/\$tool; done',
      )
      ..write(_identity)
      ..write(_runsHere);
    return buffer.toString();
  }

  /// Runs each program once. A program Android's system-call filter kills
  /// exits with 159 (128 + SIGSYS); saying so beats a team that dies later.
  static const _runsHere = r'''for tool in gc bd dolt; do
  case $tool in bd) arg=--version ;; *) arg=version ;; esac
  rc=0
  "$tool" "$arg" >/dev/null 2>&1 || rc=$?
  if [ "$rc" = 159 ]; then
    echo "[oc] Android stopped $tool: this phone blocks a system call it needs (SIGSYS)." >&2
    exit 70
  fi
  if [ "$rc" != 0 ]; then
    echo "[oc] $tool does not run here (exit $rc)" >&2
    exit 70
  fi
done
''';

  /// Who the team's commits are by, unless the person set it already; the
  /// beads role; Dolt's online version check off (it would slow every
  /// start and fail offline). `core.createObject rename`: under proot, git's
  /// default of hard-linking new objects into place loses them when it
  /// pushes to a repository on the phone ("unpack should have generated
  /// …"), and the team pushes every change to one.
  static const _identity = r'''git config --global user.name >/dev/null 2>&1 ||
  git config --global user.name 'OpenCode Mobile'
git config --global user.email >/dev/null 2>&1 ||
  git config --global user.email 'aiteam@opencode-mobile.local'
git config --global beads.role maintainer
git config --global core.createObject rename
dolt config --global --add user.name 'OpenCode Mobile' >/dev/null 2>&1 || true
dolt config --global --add user.email 'aiteam@opencode-mobile.local' >/dev/null 2>&1 || true
dolt config --global --add versioncheck.disabled true >/dev/null 2>&1 || true
dolt config --global --add metrics.disabled true >/dev/null 2>&1 || true
BD_DISABLE_METRICS=1 bd metrics off >/dev/null 2>&1 || true
''';

  /// Deletes the programs and the team's own state (its store and settings).
  /// Projects and their history stay. The caller stops the team first.
  static const removeScript =
      '''set -eu
rm -f /usr/local/bin/gc /usr/local/bin/bd /usr/local/bin/dolt
rm -rf $home $cache /root/aiteam /root/.gc
''';

  /// What [removeScript] would delete, in kilobytes (disk usage, links
  /// not followed). Missing paths count nothing.
  static const sizeScript =
      '''set -u
du -sk $home $cache /root/aiteam /root/.gc 2>/dev/null | awk '{s+=\$1} END {print s+0}'
''';

  static String _quote(String value) => "'${value.replaceAll("'", "'\"'\"'")}'";
}
