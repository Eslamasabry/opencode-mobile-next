import 'dart:ffi' show Abi;

import '../../l10n/app_localizations.dart';
import '../../platform/platform_capabilities.dart';
import '../../termux/bridge.dart' show TermuxBridge, TermuxRuntime;
import '../../termux/opencode_ubuntu_setup.dart';
import '../../voice/model_manifest.dart';
import '../../state/download_size.dart';
import '../builtin_linux.dart';
import 'aiteam_scripts.dart';
import 'setup_contract.dart';
import 'voice_component.dart';

/// Every component the phone setup can install, in dependency order
/// (docs/design/phone-setup-v2-2026-09-24.md, "Components").
///
/// Adding a tool means adding one entry here: a check script and an install
/// script. The engine, the Kotlin job runner and the screens stay as they
/// are.
///
/// [params] are per component id; `opencode` reads `runtime`
/// (`opencode1`, the default, or `opencode2`) and `version` (the pinned one
/// unless a caller asks for another on purpose).
///
/// The estimates start from the emulator (setup.json keeps each component's
/// start and end; 2026-09-24: Linux base 8.5 s, essentials 29 s, Python 17 s,
/// Node 5 s, OpenCode 26 s, start 7 s on a fast network) and are raised for a
/// phone, where proot makes apt and npm several times slower. They weigh the
/// bar and the ETA against each other, and the ETA rescales them by the
/// pace it measures, so being off by a factor only shows in the first 10 s.
///
/// [host] is where the job runs. On Termux an older build installed Node
/// from Ubuntu's own packages; that Node already runs OpenCode there, so
/// the Termux check accepts it rather than replacing it under a working
/// server (an existing Termux install is recognised as done).
List<SetupComponent> setupComponents(
  AppLocalizations l10n, {
  Map<String, Map<String, String>> params = const {},
  SetupHostKind host = SetupHostKind.builtin,
}) {
  final openCode = params[SetupComponentIds.openCode] ?? const {};
  final runtime = TermuxRuntime.parse(openCode['runtime']);
  final version = openCode['version'];
  return [
    SetupComponent(
      id: SetupComponentIds.linux,
      title: l10n.phoneSetupLinuxTitle,
      shortTitle: l10n.phoneSetupLinuxTitle,
      why: l10n.phoneSetupLinuxWhy,
      required: true,
      native: true,
      estimatedSeconds: 15,
      downloadBytes: 30 * _mb,
      checkScript: SetupScripts.linuxCheck,
      installScript: '',
    ),
    SetupComponent(
      id: SetupComponentIds.essentials,
      title: l10n.phoneSetupEssentialsTitle,
      shortTitle: l10n.phoneSetupEssentialsShort,
      why: l10n.phoneSetupEssentialsWhy,
      dependsOn: const [SetupComponentIds.linux],
      required: true,
      estimatedSeconds: 60,
      downloadBytes: 45 * _mb,
      checkScript: SetupScripts.essentialsCheck,
      presenceScript:
          'command -v git >/dev/null 2>&1 || '
          'command -v curl >/dev/null 2>&1 || command -v ssh >/dev/null 2>&1',
      installScript: SetupScripts.essentialsInstall,
    ),
    SetupComponent(
      id: SetupComponentIds.python,
      title: 'Python',
      shortTitle: 'Python',
      dependsOn: const [SetupComponentIds.essentials],
      defaultOn: true,
      estimatedSeconds: 40,
      downloadBytes: 25 * _mb,
      checkScript: SetupScripts.pythonCheck,
      installScript: SetupScripts.pythonInstall,
      removeScript: SetupScripts.pythonRemove,
      presenceScript: SetupScripts.pythonPresence,
      sizeScript: SetupScripts.pythonSize,
    ),
    SetupComponent(
      id: SetupComponentIds.node,
      title: 'Node.js',
      shortTitle: 'Node.js',
      why: l10n.phoneSetupNodeWhy,
      // curl comes with the essentials; Ubuntu Base has none.
      dependsOn: const [SetupComponentIds.essentials],
      required: true,
      estimatedSeconds: 20,
      downloadBytes: 58 * _mb,
      checkScript: host == SetupHostKind.termux
          ? SetupScripts.termuxNodeCheck
          : SetupScripts.nodeCheck,
      presenceScript:
          '[ -e /opt/node ] || [ -L /opt/node ] || '
          'command -v node >/dev/null 2>&1',
      installScript: SetupScripts.nodeInstall,
    ),
    SetupComponent(
      id: SetupComponentIds.openCode,
      title: 'OpenCode',
      shortTitle: 'OpenCode',
      why: l10n.phoneSetupOpenCodeWhy,
      dependsOn: const [SetupComponentIds.node],
      required: true,
      estimatedSeconds: 100,
      downloadBytes: 50 * _mb,
      // In the app's own Ubuntu: the pinned native program, never npm (its
      // wrapper and install scripts failed on the owner's phone, build
      // 2062). Termux keeps the shared text its own manager also runs.
      checkScript: host == SetupHostKind.builtin
          ? SetupScripts.openCodeNativeCheck(runtime, version: version)
          : SetupScripts.openCodeCheck(runtime, version: version),
      presenceScript:
          'command -v opencode >/dev/null 2>&1 || '
          'command -v opencode2 >/dev/null 2>&1 || '
          '[ -e /opt/opencode ] || [ -e /opt/opencode2 ] || '
          '[ -e /usr/local/lib/node_modules/opencode-ai ] || '
          '[ -e /usr/local/lib/node_modules/opencode ]',
      installScript: host == SetupHostKind.builtin
          ? SetupScripts.openCodeNativeInstall(runtime, version: version)
          : SetupScripts.openCodeInstall(runtime, version: version),
    ),
    // Several agents sharing the work on one project. Opt-in and install
    // only: a team belongs to a project, which the first setup does not
    // have yet, so it is turned on later, per project (BuiltinTeam).
    SetupComponent(
      id: SetupComponentIds.aiTeam,
      title: l10n.aiteamComponentTitle,
      shortTitle: l10n.aiteamComponentTitle,
      // Its agents are OpenCode, and it needs Git and curl.
      dependsOn: const [
        SetupComponentIds.essentials,
        SetupComponentIds.openCode,
      ],
      // Mostly the three downloads; the ETA's pace scaling corrects it.
      estimatedSeconds: 150,
      downloadBytes: AiTeamPins.deviceDownloadBytes,
      // apt dependencies add an unpinned amount to these pinned archives.
      downloadSize: aiTeamSetupDownloadSize(),
      checkScript: AiTeamScripts.checkScript,
      installScript: AiTeamScripts.installScript(
        downloading: l10n.aiteamComponentStageDownloading('{index}', '{total}'),
        preparing: l10n.aiteamComponentStagePreparing,
      ),
      removeScript: AiTeamScripts.removeScript,
      sizeScript: AiTeamScripts.sizeScript,
      presenceScript:
          '[ -e /opt/aiteam ] || [ -L /opt/aiteam ] || '
          '[ -e /root/aiteam ] || [ -e /root/.gc ] || '
          '[ -e /var/cache/oc-setup/aiteam ] || '
          '[ -L /usr/local/bin/gc ] || [ -e /usr/local/bin/gc ] || '
          '[ -L /usr/local/bin/bd ] || [ -e /usr/local/bin/bd ] || '
          '[ -L /usr/local/bin/dolt ] || [ -e /usr/local/bin/dolt ]',
    ),
    SetupComponent(
      id: SetupComponentIds.start,
      title: l10n.phoneSetupStartTitle,
      shortTitle: l10n.phoneSetupStartTitle,
      why: l10n.phoneSetupStartWhy,
      dependsOn: const [SetupComponentIds.openCode],
      required: true,
      jobStep: true,
      estimatedSeconds: 15,
      checkScript: '',
      installScript: '',
    ),
    // Voice typing's speech model: installed by the app into its own
    // storage, not inside Linux (SetupComponent.app), so nothing here
    // depends on Linux. Last, after the start: a working agent does not
    // wait for it, and a failed download leaves OpenCode running. Its size
    // is the pack this phone would use, once the device has been asked.
    if (platformCapabilities.supportsVoice)
      SetupComponent(
        id: SetupComponentIds.voice,
        title: l10n.voiceComponentTitle,
        shortTitle: l10n.voiceComponentTitle,
        summary: l10n.voiceComponentSummary,
        // Mostly the download; the ETA's pace scaling corrects it.
        estimatedSeconds: 45,
        downloadBytes:
            VoiceSetupComponent.instance.lastOfferBytes ??
            voiceModelPack('base').downloadBytes,
        checkScript: '',
        installScript: '',
        app: VoiceSetupComponent.instance,
      ),
  ];
}

/// Only explicit supported Android architectures select trusted archive pins.
/// This is a full-install payload lower bound, not remaining bytes after cache
/// reuse. The estimated extra apt packages are deliberately excluded.
DownloadSize aiTeamSetupDownloadSize({Abi? abi}) {
  final downloads = switch (abi ?? Abi.current()) {
    Abi.androidArm64 => AiTeamPins.arm64,
    Abi.androidX64 => AiTeamPins.x64,
    _ => null,
  };
  return downloads == null
      ? const DownloadSize.unknown()
      : DownloadSize.lowerBound(AiTeamPins.bytesFor(downloads));
}

const _mb = 1000 * 1000;

abstract final class SetupComponentIds {
  static const linux = 'linux';
  static const essentials = 'essentials';
  static const python = 'python';
  static const node = 'node';
  static const openCode = 'opencode';
  static const aiTeam = 'aiteam';

  /// Voice typing's speech model, installed by the app itself.
  static const voice = 'voice';

  /// Not installed: starting the server and connecting to it, the last step
  /// of every job (see [SetupComponent.jobStep]).
  static const start = 'start';
}

/// One pinned OpenCode download for one CPU: where it is, its SHA-256, and
/// the path of the `opencode` program inside the archive.
typedef OpenCodeAsset = ({String url, String sha256, String member});

/// The OpenCode programs the app's own Ubuntu installs: native builds,
/// downloaded whole and checked against these SHA-256s before anything is
/// unpacked or run. No npm: its wrapper package and install scripts are what
/// failed on the owner's ARM64 phone (build 2062, npm 11.19 `allowScripts`).
///
/// - OpenCode 1: the release archives of
///   https://github.com/anomalyco/opencode/releases/tag/v1.18.32, the same
///   pins as scripts/host/ubuntu-opencode.sh (GitHub's asset digests, read
///   and matched against the files 2026-09-28). x64 is the baseline build,
///   which also runs on CPUs without AVX2.
/// - OpenCode 2 (`@opencode/cli` 2.0.10) has no GitHub release. Its official
///   per-platform npm packages hold only the program and a package.json,
///   with no install script; their tarballs' SHA-512 matched the registry's
///   published `integrity` on 2026-09-28, and these are those files'
///   SHA-256s.
///
/// Moving to a newer OpenCode means new pins here, next to
/// [TermuxRuntime.pinnedVersion].
abstract final class OpenCodePins {
  static const v1Arm64 = (
    url:
        'https://github.com/anomalyco/opencode/releases/download/v1.18.32/'
        'opencode-linux-arm64.tar.gz',
    sha256: '568461b7d4d8c19865c97e9a1102e613049c6039d01fe772154de873c1865840',
    member: 'opencode',
  );
  static const v1X64 = (
    url:
        'https://github.com/anomalyco/opencode/releases/download/v1.18.32/'
        'opencode-linux-x64-baseline.tar.gz',
    sha256: '763af386ef88a8cab18df00fcf055690e5a55e31a7088beabe02307142a6adce',
    member: 'opencode',
  );
  static const v2Arm64 = (
    url:
        'https://registry.npmjs.org/@opencode/cli-linux-arm64/-/'
        'cli-linux-arm64-2.0.10.tgz',
    sha256: 'cf5416676240455dc5a98500237af296cb1a00efbd2cca302ad22c92ea080ebc',
    member: 'package/bin/opencode',
  );
  static const v2X64 = (
    url:
        'https://registry.npmjs.org/@opencode/cli-linux-x64-baseline/-/'
        'cli-linux-x64-baseline-2.0.10.tgz',
    sha256: '700c4d0fcc209e42f61c10f9773331d1d1f7eff35670971ee316b38641a1a76c',
    member: 'package/bin/opencode',
  );

  /// The pinned downloads of [runtime], by CPU.
  static ({OpenCodeAsset arm64, OpenCodeAsset x64}) of(TermuxRuntime runtime) =>
      runtime == TermuxRuntime.openCode2
      ? (arm64: v2Arm64, x64: v2X64)
      : (arm64: v1Arm64, x64: v1X64);

  /// Where the program lives in Ubuntu; `/usr/local/bin/<command>` links it.
  static String dir(TermuxRuntime runtime) =>
      runtime == TermuxRuntime.openCode2 ? '/opt/opencode2' : '/opt/opencode';

  static String command(TermuxRuntime runtime) =>
      runtime == TermuxRuntime.openCode2 ? 'opencode2' : 'opencode';
}

/// The reasons [SetupScripts.openCodeNativeInstall] ends a failed run with
/// (its last log line, which becomes the component's error), so
/// [describeSetupFailure] can say in plain words what went wrong.
abstract final class OpenCodeInstallFailure {
  static const noProgram =
      "[oc] OpenCode's download did not contain its program";
  static const wontRun = "[oc] OpenCode's program does not run on this phone";
  static const wrongVersion = "[oc] OpenCode's program reports another version";
  static const noStart = '[oc] OpenCode was installed but did not start';
  static const unpinned =
      '[oc] This app can only install its own pinned OpenCode';
  static const cpu = "[oc] OpenCode has no build for this phone's processor";
}

/// The scripts behind [setupComponents]. Checks exit 0 only when the piece is
/// there and works, and print its version on their last line. Installs are
/// idempotent and report with the prelude's helpers (setup_scripts.dart).
abstract final class SetupScripts {
  /// Run through proot only once the Kotlin side says Ubuntu is installed;
  /// before that there is nothing to run it in.
  static const linuxCheck =
      '[ -s /etc/os-release ] && . /etc/os-release && echo "\${VERSION%% *}"';

  static const essentialsCheck = '''set -e
command -v curl >/dev/null
command -v git >/dev/null
command -v ssh >/dev/null
[ -s /etc/ssl/certs/ca-certificates.crt ]
git --version | cut -d ' ' -f 3
''';

  static const essentialsInstall = '''set -eu
oc_apt_install curl ca-certificates git openssh-client
oc_version "\$(git --version | cut -d ' ' -f 3)"
''';

  static const pythonCheck = '''set -e
python3 -c 'import ensurepip, venv' >/dev/null
python3 -m pip --version >/dev/null
python3 --version | cut -d ' ' -f 2
''';

  static const pythonInstall = '''set -eu
oc_apt_install python3 python3-venv python3-pip
oc_version "\$(python3 --version | cut -d ' ' -f 2)"
''';

  static const pythonRemove = '''set -eu
export DEBIAN_FRONTEND=noninteractive
apt-get remove -y python3-venv python3-pip
apt-get autoremove -y
''';

  /// What [pythonRemove] would delete, in kilobytes: the packages apt
  /// plans to remove with it (a dry run), by their installed size.
  static const pythonSize = r'''set -eu
packages=$(apt-get -s --auto-remove remove python3-venv python3-pip 2>/dev/null | awk '/^Remv /{print $2}')
[ -n "$packages" ] || { echo 0; exit 0; }
dpkg-query -W -f='${Installed-Size}\n' $packages 2>/dev/null | awk '{s+=$1} END {print s+0}'
''';

  // Python itself belongs to Ubuntu and survives pythonRemove. Inventory
  // describes the removable pip/venv component, including partial installs.
  static const pythonPresence = r'''set -eu
[ -r /var/lib/dpkg/status ] || exit 2
packages=$(dpkg-query -W -f='${Package} ${Status}\n') || exit 2
printf '%s\n' "$packages" | grep -Eq '^python3-(pip|venv) install ok installed$'
''';

  static String get _nodeVersion =>
      TermuxBridge.localAgentsPins['node_version']!;

  /// Only the pinned Node counts: an older one from Ubuntu's own packages or
  /// an earlier app would leave OpenCode on a Node it was not tested with.
  static String get nodeCheck =>
      '''set -e
[ "\$(node --version)" = '$_nodeVersion' ]
command -v npm >/dev/null
[ "\$(npm config get prefix)" = /usr/local ]
node --version | sed 's/^v//'
''';

  /// The Termux host: the pinned Node, or the Node an older build put in
  /// Termux's Ubuntu from its own packages, as long as it and npm run.
  static const termuxNodeCheck = '''set -e
node --version >/dev/null
command -v npm >/dev/null
node --version | sed 's/^v//'
''';

  /// Node from its official pinned download instead of Ubuntu's `npm`
  /// package, which drags in hundreds of packages (ten minutes under proot on
  /// the emulator). The download sits outside /tmp so a force-stopped run
  /// resumes it, and is deleted once unpacked. npm's global folder is
  /// /usr/local, so `opencode` lands on the PATH the server starts with.
  static String get nodeInstall {
    final pins = TermuxBridge.localAgentsPins;
    final plain = _nodeVersion.replaceFirst('v', '');
    return '''set -eu
case "\$(uname -m)" in
  aarch64|arm64) node_arch=arm64; node_sha=${pins['node_sha256_arm64']} ;;
  x86_64|amd64) node_arch=x64; node_sha=${pins['node_sha256_x64']} ;;
  *) echo "[oc] Unsupported CPU: \$(uname -m)" >&2; exit 64 ;;
esac
node_version=$_nodeVersion
node_file=/var/cache/oc-setup/node-\$node_version-linux-\$node_arch.tar.gz
oc_stage 'Downloading Node.js $plain'
oc_download "${pins['node_base_url']}/\$node_version/node-\$node_version-linux-\$node_arch.tar.gz" \\
  "\$node_file" "\$node_sha"
oc_stage 'Unpacking Node.js'
rm -rf /opt/node.new
mkdir -p /opt/node.new
tar -xzf "\$node_file" -C /opt/node.new --strip-components=1
rm -rf /opt/node
mv /opt/node.new /opt/node
for tool in node npm npx; do ln -sf /opt/node/bin/\$tool /usr/local/bin/\$tool; done
npm config set prefix /usr/local
rm -f "\$node_file"
oc_version "\$(node --version | sed 's/^v//')"
''';
  }

  /// Passes only for [runtime] at the requested version, so "Update
  /// OpenCode" (a new version) and "Switch to OpenCode 2" (the other runtime)
  /// both find something to do, while a plain continue skips it.
  static String openCodeCheck(TermuxRuntime runtime, {String? version}) {
    final selected = _version(runtime, version);
    return 'set -e\n'
        'installed=\$(${BuiltinLinux.versionScript(runtime)})\n'
        'installed=\${installed##* v}\n'
        '[ "\$installed" = \'$selected\' ]\n'
        'echo "\$installed"\n';
  }

  /// The shared text the Termux manager also runs
  /// ([openCodeUbuntuSetupScript]); it finds Node, Git and curl in place and
  /// goes straight to `npm install -g --foreground-scripts` (npm crashes
  /// under proot without it). OpenCode 1 then refreshes its model catalog;
  /// a failure there does not fail the install, the server fetches it later.
  static String openCodeInstall(TermuxRuntime runtime, {String? version}) {
    final selected = _version(runtime, version);
    final binary = runtime == TermuxRuntime.openCode2
        ? 'opencode2'
        : 'opencode';
    final refresh = runtime == TermuxRuntime.openCode1
        ? "oc_stage 'Getting the model list'\n"
              'opencode models --refresh >/dev/null 2>&1 || '
              "echo '[oc] The model list could not be refreshed now; "
              "OpenCode will fetch it later.'\n"
        : '';
    return 'set -eu\n'
        "oc_stage 'Installing OpenCode $selected'\n"
        "export OC_REQUESTED_VERSION='$selected'\n"
        "export OC_RUNTIME='${runtime.wireName}'\n"
        "bash -s <<'OC_PROOT_SETUP'\n"
        '${openCodeUbuntuSetupScript}OC_PROOT_SETUP\n'
        '$refresh'
        'installed=\$($binary --version)\n'
        'oc_version "\${installed##* v}"\n';
  }

  /// Passes only when [runtime]'s command is the pinned native program this
  /// app installed (not npm's wrapper from an older build) at the requested
  /// version. A phone whose npm install failed, or that has the wrapper,
  /// fails this check, so Continue replaces OpenCode and nothing else: the
  /// Linux base, Git, Python and Node pass their own checks.
  ///
  /// [root] prefixes every path; it exists for the script tests only.
  static String openCodeNativeCheck(
    TermuxRuntime runtime, {
    String? version,
    String root = '',
  }) {
    final selected = _version(runtime, version);
    final command = OpenCodePins.command(runtime);
    final program = '$root${OpenCodePins.dir(runtime)}/bin/$command';
    return 'set -e\n'
        'oc_bin=\$(command -v $command)\n'
        '[ "\$(readlink -f "\$oc_bin")" = \'$program\' ]\n'
        'installed=\$("\$oc_bin" --version | tail -n 1)\n'
        'installed=\${installed##* v}\n'
        '[ "\$installed" = \'$selected\' ]\n'
        'echo "\$installed"\n';
  }

  /// Installs [runtime]'s pinned native program in the app's Ubuntu:
  ///
  /// 1. picks the archive for this CPU and downloads it with `oc_download`
  ///    (resumable, into /var/cache/oc-setup so a force-stopped run picks it
  ///    up again); a file that does not match its SHA-256 is deleted and the
  ///    run stops before anything is unpacked;
  /// 2. unpacks only the program, next to the current install;
  /// 3. proves it: `--version` from the program itself, then a short
  ///    `serve` in a throwaway home until its health address answers;
  /// 4. only then swaps it in, links `/usr/local/bin/<command>` to it and
  ///    deletes what npm installed before (a running server keeps its file
  ///    until the start step restarts it).
  ///
  /// A failure ends with one of [OpenCodeInstallFailure]'s lines, after the
  /// program's own output, so the log ends with the reason. OpenCode 1 then
  /// refreshes its model list, bounded; a failure there does not fail the
  /// install (the server fetches it later).
  ///
  /// [assets], [root] and [probeSeconds] exist for the script tests only.
  static String openCodeNativeInstall(
    TermuxRuntime runtime, {
    String? version,
    ({OpenCodeAsset arm64, OpenCodeAsset x64})? assets,
    String root = '',
    int probeSeconds = 120,
  }) {
    final selected = _version(runtime, version);
    if (selected != runtime.pinnedVersion) {
      return "echo '${OpenCodeInstallFailure.unpinned} "
          "(${runtime.pinnedVersion}, not $selected)' >&2\n"
          'exit 64\n';
    }
    final pins = assets ?? OpenCodePins.of(runtime);
    final command = OpenCodePins.command(runtime);
    final dir = '$root${OpenCodePins.dir(runtime)}';
    final health = runtime == TermuxRuntime.openCode2
        ? '/api/info /api/health'
        : '/global/health';
    final modules = '$root/usr/local/lib/node_modules';
    final leftovers = runtime == TermuxRuntime.openCode2
        ? '"$root/opt/oc2" "$modules/@opencode-ai/cli" '
              '"$modules/@opencode-ai/"cli-linux-*'
        : '"$modules/opencode-ai" "$modules/"opencode-linux-*';
    final refresh = runtime == TermuxRuntime.openCode1
        ? "oc_stage 'Getting the model list'\n"
              'timeout 180 "\$oc_dir/bin/$command" models --refresh '
              '>/dev/null 2>&1 || '
              "echo '[oc] The model list could not be refreshed now; "
              "OpenCode will fetch it later.'\n"
        : '';
    return '''set -eu
oc_fail() { echo "\$*" >&2; exit 1; }
case "\$(uname -m)" in
  aarch64|arm64) oc_url='${pins.arm64.url}'; oc_sha=${pins.arm64.sha256}; oc_member='${pins.arm64.member}' ;;
  x86_64|amd64) oc_url='${pins.x64.url}'; oc_sha=${pins.x64.sha256}; oc_member='${pins.x64.member}' ;;
  *) echo "${OpenCodeInstallFailure.cpu} (\$(uname -m))" >&2; exit 64 ;;
esac
oc_dir='$dir'
oc_new="\$oc_dir.new"
oc_file="$root/var/cache/oc-setup/opencode-$selected-\$oc_sha.archive"
oc_stage 'Downloading OpenCode $selected'
# Checked against the pinned SHA-256 before anything is unpacked; a file
# that does not match is deleted and the run stops here.
oc_download "\$oc_url" "\$oc_file" "\$oc_sha"
oc_stage 'Unpacking OpenCode'
rm -rf "\$oc_new"
mkdir -p "\$oc_new/bin"
if ! tar -xzf "\$oc_file" -C "\$oc_new" "\$oc_member" ||
  [ ! -f "\$oc_new/\$oc_member" ] || [ -L "\$oc_new/\$oc_member" ]; then
  rm -rf "\$oc_new" "\$oc_file"
  oc_fail "${OpenCodeInstallFailure.noProgram}"
fi
mv -f "\$oc_new/\$oc_member" "\$oc_new/bin/$command.tmp"
mv -f "\$oc_new/bin/$command.tmp" "\$oc_new/bin/$command"
case "\$oc_member" in */*) rm -rf "\$oc_new/\${oc_member%%/*}" ;; esac
chmod 755 "\$oc_new/bin/$command"
oc_program="\$oc_new/bin/$command"
if ! oc_out=\$("\$oc_program" --version 2>&1); then
  printf '%s\\n' "\$oc_out" | tail -n 20
  rm -rf "\$oc_new"
  oc_fail "${OpenCodeInstallFailure.wontRun}"
fi
oc_got=\$(printf '%s\\n' "\$oc_out" | tail -n 1)
oc_got=\${oc_got##* v}
if [ "\$oc_got" != '$selected' ]; then
  printf '%s\\n' "\$oc_out" | tail -n 5
  rm -rf "\$oc_new"
  oc_fail "${OpenCodeInstallFailure.wrongVersion} (\$oc_got, not $selected)"
fi
oc_stage 'Checking that OpenCode starts'
oc_probe=\$(mktemp -d "\${TMPDIR:-/tmp}/oc-probe.XXXXXX")
# A busy port is not a broken OpenCode: try up to three ports.
oc_started=
for oc_try in 1 2 3; do
oc_port=\$((30000 + (\$\$ + oc_try * 7919 + \$(od -An -N2 -tu2 /dev/urandom | tr -d ' ')) % 20000))
oc_pw=\$(od -An -N12 -tx1 /dev/urandom | tr -d ' \\n')
(
  cd "\$oc_probe"
  unset OPENCODE_CONFIG OPENCODE_CONFIG_CONTENT OPENCODE_DB
  export HOME="\$oc_probe" XDG_DATA_HOME="\$oc_probe/data" XDG_CACHE_HOME="\$oc_probe/cache"
  export XDG_STATE_HOME="\$oc_probe/state" XDG_CONFIG_HOME="\$oc_probe/config"
  export OPENCODE_CONFIG_DIR="\$oc_probe/config/opencode" OPENCODE_DISABLE_MODELS_FETCH=1
  export OPENCODE_SERVER_USERNAME=opencode OPENCODE_SERVER_PASSWORD="\$oc_pw"
  exec "\$oc_program" serve --hostname 127.0.0.1 --port "\$oc_port"
) > "\$oc_probe/log" 2>&1 </dev/null &
oc_pid=\$!
oc_alive() {
  kill -0 "\$oc_pid" 2>/dev/null || return 1
  case "\$(sed 's/^.*) //' "/proc/\$oc_pid/stat" 2>/dev/null)" in Z*|X*) return 1 ;; esac
}
oc_started=
oc_i=0
while [ "\$oc_i" -lt $probeSeconds ]; do
  for oc_path in $health; do
    oc_code=\$(curl -s --max-time 3 -o /dev/null -w '%{http_code}' \\
      -u "opencode:\$oc_pw" "http://127.0.0.1:\$oc_port\$oc_path" 2>/dev/null) || true
    case "\$oc_code" in 200|401) oc_started=1 ;; esac
  done
  [ -z "\$oc_started" ] || break
  oc_alive || break
  sleep "\${OC_PROBE_POLL:-1}"
  oc_i=\$((oc_i + 1))
done
kill "\$oc_pid" 2>/dev/null || true
oc_i=0
while [ "\$oc_i" -lt 5 ] && oc_alive; do sleep "\${OC_PROBE_POLL:-1}"; oc_i=\$((oc_i + 1)); done
kill -9 "\$oc_pid" 2>/dev/null || true
wait "\$oc_pid" 2>/dev/null || true
[ -z "\$oc_started" ] || break
grep -qiE 'address already in use|EADDRINUSE' "\$oc_probe/log" 2>/dev/null || break
done
if [ -z "\$oc_started" ]; then
  echo "[oc] What OpenCode said:"
  tail -n 20 "\$oc_probe/log" | sed 's/^/  /'
  rm -rf "\$oc_probe" "\$oc_new"
  oc_fail "${OpenCodeInstallFailure.noStart}"
fi
rm -rf "\$oc_probe"
rm -rf "\$oc_dir"
mv "\$oc_new" "\$oc_dir"
mkdir -p "$root/usr/local/bin"
ln -sfn "\$oc_dir/bin/$command" "$root/usr/local/bin/$command"
# What npm installed before, wrapper and all.
rm -rf $leftovers
rm -f "\$oc_file"
$refresh'''
        'oc_version "\$oc_got"\n';
  }

  static final _versionPattern = RegExp(r'^[A-Za-z0-9._+-]+$');

  static String _version(TermuxRuntime runtime, String? version) {
    final selected = version ?? runtime.pinnedVersion;
    if (!_versionPattern.hasMatch(selected)) {
      throw ArgumentError.value(version, 'version', 'Invalid package version.');
    }
    return selected;
  }
}
