import 'dart:io';

import 'package:crypto/crypto.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/builtin/agents/agent_scripts.dart';
import 'package:opencode_mobile/domain/agent_catalog.dart';

AgentDescriptor _agent(
  String hash, {
  String version = '1.2.3',
  AgentArtifactFormat format = AgentArtifactFormat.executable,
  String? member,
  String id = 'fixture',
}) => AgentDescriptor(
  id: id,
  name: 'Fixture',
  iconKey: 'fixture',
  route: AgentRoute.paseoNative,
  providerId: 'fixture',
  signInMethod: AgentSignInMethod.none,
  limitation: 'Test runtime',
  resumeReason: 'No resume proof',
  recipe: AgentInstallRecipe(
    version: version,
    executable: id,
    artifacts: {
      AgentArchitecture.x64: AgentArtifact(
        url: Uri.parse('https://example.invalid/pinned'),
        sha256: hash,
        format: format,
        archiveMember: member,
      ),
    },
  ),
);
String _quote(String text) => "'${text.replaceAll("'", "'\\''")}'";

/// An isolated fake guest: rewrite the fixed home only in test input, never
/// expose a production install-prefix override. No curl/network or root paths.
class _Guest {
  _Guest(this.root);
  final Directory root;
  String get home => '${root.path}/home';
  String get bin => '$home/.local/bin';
  String get payload => '${root.path}/artifact';
  String get ran => '${root.path}/executed';
  Future<void> prepare({
    String version = '1.2.3',
    String architecture = 'x86_64',
    String uid = '1000',
  }) async {
    await Directory(bin).create(recursive: true);
    await File(
      '$bin/id',
    ).writeAsString('#!/bin/sh\nprintf "%s\\n" ${_quote(uid)}\n');
    await File(
      '$bin/uname',
    ).writeAsString('#!/bin/sh\nprintf "%s\\n" ${_quote(architecture)}\n');
    await Process.run('chmod', ['700', '$bin/id', '$bin/uname']);
    await File(payload).writeAsString(
      '#!/bin/sh\nprintf x >> ${_quote(ran)}\nprintf "%s\\n" ${_quote(version)}\n',
    );
  }

  Future<String> get hash async =>
      sha256.convert(await File(payload).readAsBytes()).toString();
  Future<ProcessResult> run(String script, {String? downloader}) => Process.run(
    'bash',
    [
      '-c',
      '''
${downloader ?? 'oc_download() { cp ${_quote(payload)} "\$2"; }'}
oc_stage() { :; }
oc_version() { printf '::oc version %s\\n' "\$1"; }
${script.replaceAll('/home/oc', home).replaceAll('/usr/local/bin', '$home/usr-local-bin')}
''',
    ],
    environment: {'HOME': home, 'PATH': '$bin:/usr/bin:/bin'},
    workingDirectory: root.path,
  );
}

void main() {
  late Directory temp;
  late _Guest guest;
  setUp(() async {
    temp = await Directory.systemTemp.createTemp('oc-agent-scripts-');
    guest = _Guest(temp);
    await guest.prepare();
  });
  tearDown(() async {
    await temp.delete(recursive: true);
  });

  test('the Terminal can run the agent by name, as the agent user', () async {
    final agent = _agent(await guest.hash, id: 'claude');
    final result = await guest.run(AgentPhoneScripts.install(agent));
    expect(result.exitCode, 0, reason: '${result.stderr}');
    final launcher = File('${guest.home}/usr-local-bin/claude');
    final text = launcher.readAsStringSync();
    expect(text, startsWith('#!/bin/sh\n# oc-terminal-launcher\n'));
    expect(text, contains('su -l oc'));
    expect(text, contains('cd "\$0"'));
    expect(text, contains('${guest.home}/.local/bin/claude'));
    expect(launcher.statSync().mode & 0x49, 0x49, reason: 'executable');
    expect((await Process.run('sh', ['-n', launcher.path])).exitCode, 0);
    // Installing again keeps working and a file we did not write is left be.
    await launcher.writeAsString('#!/bin/sh\necho mine\n');
    final again = await guest.run(AgentPhoneScripts.install(agent));
    expect(again.exitCode, 0, reason: '${again.stderr}');
    expect(launcher.readAsStringSync(), contains('echo mine'));
  });

  test(
    'interrupted agent download survives extraction cleanup and resumes',
    () async {
      final agent = _agent(await guest.hash, id: 'claude');
      final size = await File(guest.payload).length();
      final first = await guest.run(
        AgentPhoneScripts.install(agent),
        downloader:
            'oc_download() { head -c 12 ${_quote(guest.payload)} > "\$2"; return 18; }',
      );
      expect(first.exitCode, 18);
      final cache = Directory(
        '${guest.home}/.local/share/oc-agents/claude/.downloads',
      );
      final artifact = cache.listSync().whereType<File>().single;
      expect(artifact.lengthSync(), 12);
      final resumed = await guest.run(
        AgentPhoneScripts.install(agent),
        downloader:
            'oc_download() { [ "\$(stat -c %s "\$2")" = 12 ] || return 99; tail -c +13 ${_quote(guest.payload)} >> "\$2"; }',
      );
      expect(resumed.exitCode, 0, reason: '${resumed.stderr}');
      expect(
        File(
          '${guest.home}/.local/share/oc-agents/claude/1.2.3/payload/claude',
        ).lengthSync(),
        size,
      );
      expect(artifact.existsSync(), isFalse);
    },
  );

  test(
    'check rejects changed executable even when its version and marker match',
    () async {
      final agent = _agent(await guest.hash, id: 'claude');
      expect((await guest.run(AgentPhoneScripts.install(agent))).exitCode, 0);
      final payload = File(
        '${guest.home}/.local/share/oc-agents/claude/1.2.3/payload/claude',
      );
      await payload.writeAsString(
        '${await payload.readAsString()}\n# altered\n',
      );
      expect(
        (await guest.run(AgentPhoneScripts.check(agent))).exitCode,
        isNot(0),
      );
    },
  );

  test(
    'rollback checks retained official payload and rebuilds its launcher',
    () async {
      await guest.prepare(version: '2.1.283');
      final oldHash = await guest.hash;
      final old = _agent(oldHash, id: 'claude', version: '2.1.283');
      expect((await guest.run(AgentPhoneScripts.install(old))).exitCode, 0);
      await guest.prepare(version: '2.1.294');
      final current = _agent(
        await guest.hash,
        id: 'claude',
        version: '2.1.294',
      );
      expect((await guest.run(AgentPhoneScripts.install(current))).exitCode, 0);
      String fixture(String script) => script.replaceAll(
        AgentPhoneScripts
            .previousClaudeRecipe
            .artifacts[AgentArchitecture.x64]!
            .sha256,
        oldHash,
      );
      final prior = '${guest.home}/.local/share/oc-agents/claude/2.1.283';
      await File('$prior/launch').writeAsString('#!/bin/sh\nexit 99\n');
      final available = await guest.run(
        fixture(AgentPhoneScripts.previousVersion(current)),
      );
      expect(available.exitCode, 0, reason: '${available.stderr}');
      expect(available.stdout, '2.1.283\n');
      final restored = await guest.run(
        fixture(AgentPhoneScripts.usePreviousVersion(current, '2.1.283')),
      );
      expect(restored.exitCode, 0, reason: '${restored.stderr}');
      expect((await guest.run(AgentPhoneScripts.check(old))).exitCode, 0);
      expect(
        Directory(
          '${guest.home}/.local/share/oc-agents/claude/2.1.294',
        ).existsSync(),
        isTrue,
      );
      await File(
        '$prior/payload/claude',
      ).writeAsString('#!/bin/sh\necho 2.1.283\n');
      expect(
        (await guest.run(
          fixture(AgentPhoneScripts.previousVersion(current)),
        )).exitCode,
        isNot(0),
      );
      expect(
        (await guest.run(
          fixture(AgentPhoneScripts.usePreviousVersion(current, '2.1.283')),
        )).exitCode,
        isNot(0),
      );
    },
  );

  test(
    'agent cache and rollback refuse symlinked parent directories',
    () async {
      final agent = _agent(await guest.hash, id: 'claude');
      final base = '${guest.home}/.local/share/oc-agents/claude';
      await Directory(base).create(recursive: true);
      final outside = Directory('${temp.path}/outside')..createSync();
      final sentinel = File('${outside.path}/keep')
        ..writeAsStringSync('private');
      await Link('$base/.downloads').create(outside.path);
      expect(
        (await guest.run(AgentPhoneScripts.install(agent))).exitCode,
        isNot(0),
      );
      expect(await sentinel.readAsString(), 'private');
      await Link('$base/.downloads').delete();
      await Directory(base).delete();
      await Link(base).create(outside.path);
      expect(
        (await guest.run(AgentPhoneScripts.previousVersion(agent))).exitCode,
        isNot(0),
      );
      expect(
        (await guest.run(
          AgentPhoneScripts.usePreviousVersion(agent, '2.1.283'),
        )).exitCode,
        isNot(0),
      );
      expect(await sentinel.readAsString(), 'private');
    },
  );

  test(
    'Claude check restores interrupted code before probing its link',
    () async {
      final agent = _agent(await guest.hash, id: 'claude');
      expect((await guest.run(AgentPhoneScripts.install(agent))).exitCode, 0);
      final finalPath = '${guest.home}/.local/share/oc-agents/claude/1.2.3';
      await Directory(finalPath).rename('$finalPath.oc-good');
      await Directory(finalPath).create();
      await File('$finalPath/launch').writeAsString('#!/bin/sh\nexit 2\n');
      await File('$finalPath.oc-pending').writeAsString('existing\n');
      final result = await guest.run(AgentPhoneScripts.check(agent));
      expect(result.exitCode, 0, reason: '${result.stderr}');
      expect(await File('$finalPath.oc-pending').exists(), isFalse);
      expect(await File('$finalPath/payload/claude').exists(), isTrue);
    },
  );

  test(
    'Claude check refuses another installer lock without changing code',
    () async {
      final agent = _agent(await guest.hash, id: 'claude');
      expect((await guest.run(AgentPhoneScripts.install(agent))).exitCode, 0);
      final lock = Directory(
        '${guest.home}/.local/share/oc-agents/.lock-claude',
      );
      await lock.create();
      final sentinel = File('${lock.path}/owner')..writeAsStringSync('fixture');
      final result = await guest.run(AgentPhoneScripts.check(agent));
      expect(result.exitCode, isNot(0));
      expect(
        result.stderr,
        contains('Another Claude install may still be running'),
      );
      expect(await sentinel.readAsString(), 'fixture');
    },
  );

  test(
    'failed Claude active probe restores the prior pinned executable',
    () async {
      final original = await File(guest.payload).readAsString();
      final agent = _agent(await guest.hash, id: 'claude');
      expect((await guest.run(AgentPhoneScripts.install(agent))).exitCode, 0);
      await File(guest.payload).writeAsString(
        '#!/bin/sh\ncase "\$0" in *".new/"*) printf "1.2.3\\n";; *) exit 2;; esac\n',
      );
      final update = _agent(await guest.hash, id: 'claude');
      final result = await guest.run(AgentPhoneScripts.install(update));
      expect(result.exitCode, isNot(0));
      expect(
        result.stderr,
        contains('Claude could not finish updating. Run setup again.'),
      );
      final finalPath = '${guest.home}/.local/share/oc-agents/claude/1.2.3';
      expect(await File('$finalPath/payload/claude').readAsString(), original);
      expect((await guest.run(AgentPhoneScripts.check(agent))).exitCode, 0);
    },
  );

  test(
    'every catalog recipe generates valid shell without executing downloads',
    () async {
      for (final agent in AgentCatalog.builtIn.agents) {
        final file = File('${temp.path}/script.sh');
        await file.writeAsString(AgentPhoneScripts.install(agent));
        expect(
          (await Process.run('bash', ['-n', file.path])).exitCode,
          0,
          reason: agent.id,
        );
        await file.writeAsString(AgentPhoneScripts.check(agent));
        expect(
          (await Process.run('bash', ['-n', file.path])).exitCode,
          0,
          reason: agent.id,
        );
      }
    },
  );

  test(
    'oc bootstrap accepts PRoot identity mapping without requiring real chown',
    () async {
      final result = await guest.run(
        r'''
id() { case "$*" in '-u') printf '0\n';; '-u oc'|'-g oc') printf '1000\n';; 'oc') return 0;; *) return 1;; esac; }
getent() { case "$*" in 'group oc') printf 'oc:x:1000:\n';; 'passwd oc') printf 'oc:x:1000:1000::/home/oc:/bin/bash\n';; *) return 1;; esac; }
install() { return 0; }
stat() { printf '0\n'; }
''' +
            AgentPhoneScripts.bootstrapUser,
      );
      expect(result.exitCode, 0);
    },
  );

  test(
    'a checksum mismatch never executes the candidate or activates it',
    () async {
      final result = await guest.run(
        AgentPhoneScripts.install(_agent('0' * 64)),
      );
      expect(result.exitCode, isNot(0));
      expect(await File(guest.ran).exists(), isFalse);
      expect(await Link('${guest.bin}/fixture').exists(), isFalse);
    },
  );

  test(
    'version mismatch never activates and suppresses raw CLI output',
    () async {
      await guest.prepare(version: 'synthetic-secret 1.2.30');
      final result = await guest.run(
        AgentPhoneScripts.install(_agent(await guest.hash)),
      );
      expect(result.exitCode, isNot(0));
      expect(
        await File(guest.ran).exists(),
        isTrue,
      ); // Pin passed before the probe.
      expect(await Link('${guest.bin}/fixture').exists(), isFalse);
      expect(
        '${result.stdout}${result.stderr}',
        isNot(contains('synthetic-secret')),
      );
    },
  );

  test(
    'valid executable installs atomically, checks, and repeats idempotently',
    () async {
      final agent = _agent(await guest.hash);
      expect((await guest.run(AgentPhoneScripts.install(agent))).exitCode, 0);
      final active = Link('${guest.bin}/fixture');
      expect(
        await active.target(),
        '${guest.home}/.local/share/oc-agents/fixture/1.2.3/launch',
      );
      expect((await guest.run(AgentPhoneScripts.check(agent))).exitCode, 0);
      expect((await guest.run(AgentPhoneScripts.install(agent))).exitCode, 0);
      expect(
        await Directory(
          '${guest.home}/.local/share/oc-agents/fixture/1.2.3.new',
        ).exists(),
        isFalse,
      );
      expect(
        await Directory(
          '${guest.home}/.local/share/oc-agents/.lock-fixture',
        ).exists(),
        isFalse,
      );
    },
  );

  test(
    'root execution and unknown architecture fail before download',
    () async {
      await guest.prepare(uid: '0');
      expect(
        (await guest.run(
          AgentPhoneScripts.install(_agent(await guest.hash)),
        )).exitCode,
        isNot(0),
      );
      expect(await File(guest.ran).exists(), isFalse);
      await guest.prepare(architecture: 'armv7l');
      expect(
        (await guest.run(
          AgentPhoneScripts.install(_agent(await guest.hash)),
        )).exitCode,
        isNot(0),
      );
      expect(await File(guest.ran).exists(), isFalse);
    },
  );

  test(
    'concurrent installer lock does not damage held installation files',
    () async {
      await Directory(
        '${guest.home}/.local/share/oc-agents/.lock-fixture',
      ).create(recursive: true);
      final held = File('${guest.home}/.local/share/oc-agents/held');
      await held.writeAsString('keep');
      expect(
        (await guest.run(
          AgentPhoneScripts.install(_agent(await guest.hash)),
        )).exitCode,
        isNot(0),
      );
      expect(await held.readAsString(), 'keep');
      expect(await File(guest.ran).exists(), isFalse);
    },
  );

  Future<void> archive(String python) async {
    final result = await Process.run('python3', [
      '-c',
      python,
      guest.payload,
      guest.ran,
    ]);
    expect(result.exitCode, 0);
  }

  test(
    'tar path traversal and archive links are refused before extraction execution',
    () async {
      for (final mode in ['traversal', 'link']) {
        await archive('''import io, sys, tarfile
with tarfile.open(sys.argv[1], 'w:gz') as t:
 m=tarfile.TarInfo('../escape' if '$mode' == 'traversal' else 'fixture')
 if '$mode' == 'link':
  m.type=tarfile.SYMTYPE; m.linkname=sys.argv[2]; t.addfile(m)
 else:
  data=b'malicious'; m.size=len(data); t.addfile(m, io.BytesIO(data))
''');
        final result = await guest.run(
          AgentPhoneScripts.install(
            _agent(
              await guest.hash,
              format: AgentArtifactFormat.tarGz,
              member: 'fixture',
            ),
          ),
        );
        expect(result.exitCode, isNot(0));
        expect(await File(guest.ran).exists(), isFalse);
        expect(
          await File(
            '${guest.home}/.local/share/oc-agents/fixture/escape',
          ).exists(),
          isFalse,
        );
      }
    },
  );

  test(
    'bundled archive retains sibling files and validates entry version',
    () async {
      await archive(r'''import io, sys, tarfile
with tarfile.open(sys.argv[1], 'w:bz2') as t:
 for name, data in [('bundle/data', b'1.2.3'), ('bundle/fixture', b'#!/bin/sh\ncat "$(dirname "$0")/data"\n')]:
  m=tarfile.TarInfo(name); m.size=len(data); m.mode=0o700; t.addfile(m, io.BytesIO(data))
''');
      final agent = _agent(
        await guest.hash,
        format: AgentArtifactFormat.tarBz2,
        member: 'bundle/fixture',
      );
      expect((await guest.run(AgentPhoneScripts.install(agent))).exitCode, 0);
      expect(
        await File(
          '${guest.home}/.local/share/oc-agents/fixture/1.2.3/payload/bundle/data',
        ).readAsString(),
        '1.2.3',
      );
    },
  );

  test(
    'npm bundle wrapper keeps siblings under the pinned private Node',
    () async {
      final node = File('${guest.home}/.local/node/bin/node');
      await node.parent.create(recursive: true);
      await node.writeAsString(r'''#!/bin/sh
if [ "$1" = --version ]; then echo v24.22.2; else exec /bin/sh "$@"; fi
''');
      await Process.run('chmod', ['700', node.path]);
      await archive(r'''import io, sys, tarfile
with tarfile.open(sys.argv[1], 'w:gz') as t:
 for name, data in [('package/bundle/data', b'1.2.3'), ('package/bundle/entry.js', b'#!/bin/sh\ncat "$(dirname "$0")/data"\n')]:
  m=tarfile.TarInfo(name); m.size=len(data); m.mode=0o700; t.addfile(m, io.BytesIO(data))
''');
      final agent = _agent(
        await guest.hash,
        format: AgentArtifactFormat.npmTarGz,
        member: 'package/bundle/entry.js',
      );
      expect((await guest.run(AgentPhoneScripts.install(agent))).exitCode, 0);
      expect((await guest.run(AgentPhoneScripts.check(agent))).exitCode, 0);
      final launch = await File(
        '${guest.home}/.local/share/oc-agents/fixture/1.2.3/launch',
      ).readAsString();
      expect(launch, contains('${guest.home}/.local/node/bin/node'));
    },
  );

  test(
    'check refuses an altered pin marker before executing the CLI',
    () async {
      final agent = _agent(await guest.hash);
      expect((await guest.run(AgentPhoneScripts.install(agent))).exitCode, 0);
      await File(guest.ran).delete();
      await File(
        '${guest.home}/.local/share/oc-agents/fixture/1.2.3/.oc-pin',
      ).writeAsString('not-the-pin');
      expect(
        (await guest.run(AgentPhoneScripts.check(agent))).exitCode,
        isNot(0),
      );
      expect(await File(guest.ran).exists(), isFalse);
    },
  );

  test(
    'shell metacharacters in recipe version or archive member fail generation',
    () {
      expect(
        () => AgentPhoneScripts.install(_agent('0' * 64, version: r'$(id)')),
        throwsArgumentError,
      );
      expect(
        () => AgentPhoneScripts.install(
          _agent(
            '0' * 64,
            format: AgentArtifactFormat.tarGz,
            member: r'package/$(id)',
          ),
        ),
        throwsArgumentError,
      );
    },
  );

  test('zip symlink payload is refused', () async {
    await archive('''import stat, sys, zipfile
with zipfile.ZipFile(sys.argv[1], 'w') as z:
 m=zipfile.ZipInfo('fixture'); m.create_system=3; m.external_attr=(stat.S_IFLNK | 0o777) << 16; z.writestr(m, sys.argv[2])
''');
    expect(
      (await guest.run(
        AgentPhoneScripts.install(
          _agent(
            await guest.hash,
            format: AgentArtifactFormat.zip,
            member: 'fixture',
          ),
        ),
      )).exitCode,
      isNot(0),
    );
    expect(await File(guest.ran).exists(), isFalse);
  });
}
