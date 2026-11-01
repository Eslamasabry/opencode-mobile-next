// The setup backup's rules and scripts (lib/builtin/linux_backup/
// linux_backup_plan.dart). The backup and restore bodies run for real under
// /bin/sh (dash on the build server) in a temporary folder, with stand-ins
// for apt, dpkg, npm and pipx: what goes in, what never goes in (sign-ins,
// tokens, private SSH keys, downloaded folders), and what a restore does
// with what is already there.

@TestOn('linux')
library;

import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/builtin/linux_backup/linux_backup_plan.dart';

/// A fake guest: home, projects, services, a stage and a restore folder.
class _Guest {
  _Guest(this.dir);
  final Directory dir;
  String get home => '${dir.path}/home';
  String get projects => '${dir.path}/home/projects';
  String get bin => '${dir.path}/bin';
  LinuxBackupPaths get paths => LinuxBackupPaths(
    home: home,
    projects: projects,
    stage: '${dir.path}/stage',
    restore: '${dir.path}/restore',
    services: '${dir.path}/svc',
  );

  void write(String path, String text) {
    final file = File('${dir.path}/$path');
    file.parent.createSync(recursive: true);
    file.writeAsStringSync(text);
  }

  bool has(String path) =>
      FileSystemEntity.typeSync('${dir.path}/$path', followLinks: false) !=
      FileSystemEntityType.notFound;

  String read(String path) => File('${dir.path}/$path').readAsStringSync();

  /// Stand-ins on PATH: what apt, dpkg, npm and pipx would say.
  void stubs() {
    void tool(String name, String body) {
      write('bin/$name', '#!/bin/sh\n$body\n');
      Process.runSync('chmod', ['755', '$bin/$name']);
    }

    tool('apt-mark', 'printf "git\\nripgrep\\ngone-pkg\\n"');
    // tar as it is under proot on a phone (seen on the emulator,
    // 2026-11-01): it cannot open a named archive itself, only stdin or
    // stdout ("-").
    tool('tar', r'''
prev=""
for a in "$@"; do
  case "$prev" in
    -czf|-xzf|-tzf|-cf|-xf|-tf|-f)
      if [ "$a" != "-" ]; then
        echo "tar (child): $a: Cannot open: Function not implemented" >&2
        exit 2
      fi ;;
  esac
  prev="$a"
done
exec /usr/bin/tar "$@"''');
    tool('npm', r'''
case "$1" in
  ls) [ "${4:-}" = "--json" ] || exit 1
      printf '{"dependencies":{"npm":{},"typescript":{}}}' ;;
  install) printf '%s\n' "$*" >> "$OC_LOG" ;;
esac''');
    tool('pipx', r'''
case "$1" in
  list) printf 'black 24.1\n' ;;
  install) printf 'pipx %s\n' "$2" >> "$OC_LOG" ;;
esac''');
    tool('dpkg', r'[ "$2" = git ]');
    tool('apt-cache', r'[ "$2" != gone-pkg ]');
    tool('node', r'''
# Prints the dependency names of the JSON file (enough for the test).
grep -o '"[a-z]*":{}' "$3" | cut -d'"' -f2 | grep -vx npm''');
  }

  /// Runs [body] the way the job does (POSIX sh, with the helpers it uses).
  ProcessResult run(String body) => Process.runSync(
    '/bin/sh',
    [
      '-c',
      'oc_stage() { printf "::oc stage %s\\n" "\$*"; }\n'
          'oc_apt_install() { printf "apt %s\\n" "\$*" >> "\$OC_LOG"; }\n'
          '$body',
    ],
    environment: {
      'PATH': '$bin:${Platform.environment['PATH']}',
      'OC_LOG': '${dir.path}/calls.log',
    },
  );

  List<String> tarList(String path) =>
      (Process.runSync('tar', ['-tzf', path]).stdout as String)
          .split('\n')
          .where((l) => l.isNotEmpty)
          .toList();
}

void main() {
  late _Guest g;

  setUp(() async {
    g = _Guest(await Directory.systemTemp.createTemp('oc-backup-'));
    g.stubs();
    // Settings files, some kept, some never.
    g.write('home/.bashrc', 'alias ll="ls -l"\n');
    g.write('home/.gitconfig', '[user]\n  name = Me\n');
    g.write('home/.ssh/authorized_keys', 'ssh-ed25519 AAAA laptop\n');
    g.write('home/.ssh/config', 'Host *\n');
    g.write('home/.ssh/id_ed25519', 'PRIVATE KEY\n');
    g.write('home/.ssh/id_ed25519.pub', 'ssh-ed25519 AAAA me\n');
    // A key under a name of its own: the id_* rule, not the known names.
    g.write('home/.ssh/id_work', 'PRIVATE KEY\n');
    g.write('home/.config/nvim/init.lua', '-- mine\n');
    g.write('home/.config/nvim/token.pem', 'secret\n');
    g.write('home/.config/gh/hosts.yml', 'oauth_token: x\n');
    g.write('home/.local/share/opencode/auth.json', '{"key":"sk-x"}\n');
    // Projects, with things that never go and things that can be downloaded.
    g.write('home/projects/app/src/main.py', 'print(1)\n');
    g.write('home/projects/app/.env', 'API_KEY=x\n');
    g.write('home/projects/app/node_modules/x/index.js', 'x\n');
    g.write('home/projects/app/.git/HEAD', 'ref: refs/heads/main\n');
    g.write(
      'home/projects/app/.git/config',
      '[remote "origin"]\n  url = https://me:ghp_token@github.com/me/app\n',
    );
    g.write('home/projects/site/index.html', '<p>hi</p>\n');
    g.write('home/projects/site/.git/config', '[core]\n  bare = false\n');
    // Services.
    g.write('svc/defs/web.svc', 'version=1\nname=web\ncommand=x\n');
    g.write('svc/want/web', 'on\n');
  });

  tearDown(() => g.dir.delete(recursive: true));

  group('rules', () {
    test('no kept settings file is ever a credential', () {
      for (final f in linuxBackupHomeFiles) {
        expect(f, isNot(startsWith('.ssh/id_')));
        expect(f, isNot(contains('opencode')));
        expect(f, isNot(contains('.claude')));
        expect(f, isNot(contains('gh')));
      }
      expect(linuxBackupNeverPatterns, containsAll(['.ssh/id_*', 'auth.json']));
    });

    test('manifest round trip; newer or broken ones are refused', () {
      final m = LinuxBackupManifest(
        kind: LinuxBackupKind.setupAndProjects,
        created: DateTime.utc(2026, 11, 1, 9),
        appVersion: '2.1.0',
        abi: 'arm64-v8a',
        entries: const ['home.tar.gz', 'projects.tar.gz', '../evil'],
      );
      final back = LinuxBackupManifest.decode(m.encode())!;
      expect(back.kind, LinuxBackupKind.setupAndProjects);
      expect(back.created, DateTime.utc(2026, 11, 1, 9));
      expect(back.entries, ['home.tar.gz', 'projects.tar.gz']);
      expect(back.hasProjects, isTrue);
      expect(LinuxBackupManifest.decode('{"format":2,"kind":"setup"}'), isNull);
      expect(LinuxBackupManifest.decode('not json'), isNull);
      expect(LinuxBackupManifest.decode('{"format":1,"kind":"x"}'), isNull);
    });

    test('file names and job lines', () {
      expect(
        linuxBackupFileName(LinuxBackupKind.setup, DateTime(2026, 11, 1)),
        'opencode-linux-setup-2026-11-01.zip',
      );
      expect(
        linuxBackupFileName(
          LinuxBackupKind.setupAndProjects,
          DateTime(2026, 1, 9),
        ),
        'opencode-linux-setup-projects-2026-01-09.zip',
      );
      final s = LinuxRestoreSummary.parse(
        'x\n::oc skipped gone-pkg\n::oc restored projects 2\n'
        '::oc restored services 1\n',
      );
      expect(s.counts, {'projects': 2, 'services': 1});
      expect(s.skipped, ['gone-pkg']);
      expect(
        parseBackupSize('::oc backup-size 5\n::oc backup-size 1234\n'),
        1234,
      );
      expect(parseBackupSize('nothing'), isNull);
    });

    test('both job scripts carry their frame', () {
      final backup = linuxBackupJobScript(LinuxBackupKind.setup);
      expect(backup, contains('::oc job backup'));
      expect(backup, contains('linux-job.lock'));
      expect(linuxRestoreJobScript(), contains('::oc job restore'));
    });
  });

  test('a setup and projects backup holds what it should, and no secret', () {
    final r = g.run(
      linuxBackupBody(LinuxBackupKind.setupAndProjects, paths: g.paths),
    );
    expect(r.exitCode, 0, reason: '${r.stdout}\n${r.stderr}');
    expect(r.stdout, contains('::oc backup-size '));
    expect(g.read('stage/apt-manual.txt'), contains('ripgrep'));
    expect(g.read('stage/npm-global.json'), contains('typescript'));
    expect(g.read('stage/pipx.txt'), contains('black'));
    final home = g.tarList('${g.dir.path}/stage/home.tar.gz');
    expect(
      home,
      containsAll([
        '.bashrc',
        '.gitconfig',
        '.ssh/authorized_keys',
        '.ssh/config',
        '.config/nvim/init.lua',
      ]),
    );
    for (final never in [
      '.ssh/id_ed25519',
      '.ssh/id_ed25519.pub',
      '.ssh/id_work',
      '.config/nvim/token.pem',
      '.config/gh/hosts.yml',
      '.local/share/opencode/auth.json',
    ]) {
      expect(home, isNot(contains(never)), reason: never);
    }
    final projects = g.tarList('${g.dir.path}/stage/projects.tar.gz');
    expect(projects, contains('./app/src/main.py'));
    expect(projects, contains('./app/.git/HEAD'));
    expect(projects, contains('./site/.git/config'));
    for (final never in [
      './app/.env',
      './app/.git/config',
      './app/node_modules/x/index.js',
    ]) {
      expect(projects, isNot(contains(never)), reason: never);
    }
    expect(
      g.tarList('${g.dir.path}/stage/services.tar.gz'),
      containsAll(['defs/web.svc', 'want/web']),
    );
    expect(g.has('stage/excludes'), isFalse);
  });

  test('a setup backup leaves projects out', () {
    final r = g.run(linuxBackupBody(LinuxBackupKind.setup, paths: g.paths));
    expect(r.exitCode, 0, reason: '${r.stderr}');
    expect(g.has('stage/projects.tar.gz'), isFalse);
    expect(g.has('stage/home.tar.gz'), isTrue);
  });

  test('a restore puts back, keeps what is there, and says what came back', () {
    expect(
      g
          .run(
            linuxBackupBody(LinuxBackupKind.setupAndProjects, paths: g.paths),
          )
          .exitCode,
      0,
    );
    // The backup moves to the restore folder, as the app unpacks it there.
    Directory('${g.dir.path}/stage').renameSync('${g.dir.path}/restore');
    g.write('restore/manifest.json', '{}');
    // Since the backup: a changed setting, a removed service, a lost project.
    g.write('home/.bashrc', 'changed\n');
    File('${g.dir.path}/svc/defs/web.svc').deleteSync();
    Directory('${g.dir.path}/home/projects/site').deleteSync(recursive: true);
    final r = g.run(linuxRestoreBody(paths: g.paths));
    expect(r.exitCode, 0, reason: '${r.stdout}\n${r.stderr}');
    final out = r.stdout as String;
    // git is installed already; gone-pkg is gone from Ubuntu.
    expect(g.read('calls.log'), contains('apt ripgrep'));
    expect(g.read('calls.log'), isNot(contains('apt git')));
    expect(out, contains('::oc skipped gone-pkg'));
    expect(out, contains('::oc restored packages 1'));
    expect(g.read('calls.log'), contains('install -g typescript'));
    expect(g.read('calls.log'), contains('pipx black'));
    // The setting is back, the person's change kept beside it.
    expect(g.read('home/.bashrc'), 'alias ll="ls -l"\n');
    expect(g.read('home/.bashrc.before-restore'), 'changed\n');
    expect(g.has('svc/defs/web.svc'), isTrue);
    // The lost project is back under its name; the one still there comes
    // back beside it, never over it.
    expect(g.read('home/projects/site/index.html'), '<p>hi</p>\n');
    expect(g.has('home/projects/app (restored)/src/main.py'), isTrue);
    expect(g.has('home/projects/app/src/main.py'), isTrue);
    expect(out, contains('::oc restored projects 2'));
    expect(out, contains('::oc restored services 1'));
    expect(g.has('restore'), isFalse, reason: 'the unpacked backup is removed');
  });

  test('a restore without a manifest does nothing', () {
    Directory('${g.dir.path}/restore').createSync();
    final r = g.run(linuxRestoreBody(paths: g.paths));
    expect(r.exitCode, 80);
  });
}
