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
}) => AgentDescriptor(
  id: 'fixture',
  name: 'Fixture',
  iconKey: 'fixture',
  route: AgentRoute.paseoNative,
  providerId: 'fixture',
  signInMethod: AgentSignInMethod.none,
  limitation: 'Test runtime',
  resumeReason: 'No resume proof',
  recipe: AgentInstallRecipe(
    version: version,
    executable: 'fixture',
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
  Future<ProcessResult> run(String script) => Process.run(
    'bash',
    [
      '-c',
      '''
oc_download() { cp ${_quote(payload)} "\$2"; }
oc_stage() { :; }
oc_version() { printf '::oc version %s\\n' "\$1"; }
${script.replaceAll('/home/oc', home)}
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
if [ "$1" = --version ]; then echo v24.21.0; else exec /bin/sh "$@"; fi
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
