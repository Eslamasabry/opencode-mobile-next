import '../state/profiles.dart';

/// The commands a person runs on their computer, written once. The connect
/// screen shows the one that starts the chosen agent and the setup guide
/// lists the rest; both must print the same text or a copied command and a
/// read one drift apart.
///
/// Sources: `docs/paseo-connection.md`, `docs/codex-connection.md`.
abstract final class SetupCommands {
  /// Starts OpenCode 2 and prints a pairing code.
  static const pair = 'opencode2 pair';

  /// Starts OpenCode 2 on loopback with the password it generates.
  static const serve = 'opencode2 serve --hostname 127.0.0.1 --port 4096';

  /// The Paseo daemon, kept off the relay. This app never uses the relay.
  static const paseoStart = 'paseo start --no-relay';

  static const paseoStartPrivateNetwork =
      'bash -c \'IFS= read -rsp "Choose a daemon password > " PASEO_PASSWORD &&\n'
      '  printf "\\n" && test -n "\$PASEO_PASSWORD" &&\n'
      '  export PASEO_PASSWORD &&\n'
      '  exec paseo start --no-relay --listen "\$(tailscale ip -4):6767"\'';

  static const codexToken =
      'umask 077\n'
      "python3 -c 'import secrets; "
      'print(secrets.token_urlsafe(32), end="")\' '
      '> /absolute/path/codex-capability-token\n'
      'chmod 600 /absolute/path/codex-capability-token';

  /// One line, so it reads and copies as one command: a block of `\`
  /// continuations shows a prompt on every line and is cut at the edge.
  static const codexStart =
      'codex app-server --listen ws://127.0.0.1:4141 '
      '--ws-auth capability-token '
      '--ws-token-file /absolute/path/codex-capability-token';

  static const codexUsb = 'adb reverse tcp:4141 tcp:4141';

  /// The one command that starts [backend] on the computer.
  static String startFor(ServerBackend backend) => switch (backend) {
    ServerBackend.openCode => pair,
    ServerBackend.paseo => paseoStart,
    ServerBackend.codex => codexStart,
  };
}

/// One host-side file this app tells people to download: where it lives in
/// the repository and the SHA-256 of its bytes at [HostScripts.commit].
final class HostScript {
  const HostScript({
    required this.path,
    required this.sha256,
    required this.saveAs,
  });

  /// Path inside the repository, as `git` names it.
  final String path;

  /// Lower-case hex SHA-256 of the file at [HostScripts.commit].
  final String sha256;

  /// The file name the downloaded copy is saved under.
  final String saveAs;

  /// The raw download, pinned to [HostScripts.commit]: a commit, unlike a
  /// branch or a tag, can never be moved to other bytes.
  String get url =>
      'https://raw.githubusercontent.com/${HostScripts.repository}/'
      '${HostScripts.commit}/$path';

  /// Download to a side file, check it against [sha256], and only then
  /// give it its real name. A failed download or a changed file stops the
  /// chain before anything runs, and never leaves an unchecked copy under
  /// [saveAs] for the later commands to run. Each line ends in `&&`, so a
  /// terminal that is pasted all of it still stops at the first failure.
  String get verifiedDownload =>
      'curl -fsSLo $saveAs.part \\\n'
      '  $url &&\n'
      "echo '$sha256  $saveAs.part' | sha256sum -c - &&\n"
      'mv $saveAs.part $saveAs';
}

/// The host-side scripts, pinned and checksummed in one place. The app
/// never tells anyone to pipe a download into a shell: every command here
/// downloads [HostScript.url] from one published commit and checks its
/// SHA-256 before it runs.
///
/// `test/host_script_pin_test.dart` hashes the files in this checkout and at
/// [commit]; a script edit that does not move the pin and the checksum here
/// (after the commit holding it is published), or record the edit as a
/// pending pin update in that test's `_pendingPinUpdate`, fails that test.
/// The guides in `docs/ubuntu-host.md` and `docs/ai-team-host.md` show the
/// same commands and are checked by the same test.
///
abstract final class HostScripts {
  static const repository = 'Eslamasabry/opencode-mobile-next';

  /// The commit of release [release] (tag `v1.0.44+50`), published on
  /// GitHub.
  static const commit = 'c62f159ae3c1741cb4ec0ef92b4941c0ddfc0a18';

  /// The app release [commit] belongs to, as the page names it.
  static const release = '1.0.44';

  /// The AI Team front: lets allowlisted Tailscale identities reach a Gas
  /// City supervisor (docs/ai-team-host.md §5).
  static const front = HostScript(
    path: 'tool/host/cp_front/front.py',
    sha256: 'b672254944c3d77cb18f85b2337773e330f82d29ed64690d57e4fdc26235cd47',
    saveAs: 'opencode-mobile-front.py',
  );

  static const _guideBase = 'https://github.com/$repository/blob/master/docs';

  /// The living guides. They are read, never run, so they follow `master`;
  /// the commands in them carry the same pin and checksum as the app.
  static const ubuntuGuideUrl = '$_guideBase/ubuntu-host.md';
  static const teamGuideUrl = '$_guideBase/ai-team-host.md';

  /// First-time install on the host, listening on [port].
  static String install(int port) =>
      'npm install -g @opencode/cli@2.1.1 &&\n'
      'opencode2 serve --hostname 127.0.0.1 --port $port';

  /// Gas City's three tools are on PATH (docs/ai-team-host.md §1).
  static const teamCheckTools = 'command -v gc bd dolt';

  /// Create the city from the guide's city file and add the project (§3).
  static const teamCreateCity =
      'gc init --file ./city.toml --name myteam --no-start city &&\n'
      'cd city &&\n'
      'gc rig add ~/code/myproject --name myproject &&\n'
      'gc import install';

  /// Start the supervisor and ask it whether the city answers (§4).
  static const teamStart =
      'gc start &&\n'
      'curl -s http://127.0.0.1:8372/v0/city/myteam/health';

  /// Download the front, check it, and run it on the Tailscale address
  /// (§5). The login after `--allow` is the person's own.
  static String get teamFront =>
      '${front.verifiedDownload} &&\n'
      'python3 ${front.saveAs} --supervisor http://127.0.0.1:8372 \\\n'
      '  --bind "\$(tailscale ip -4)" --port 8373 \\\n'
      '  --allow you@example.com';
}
