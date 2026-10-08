import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/builtin/agents/agent_removal_scripts.dart';
import 'package:opencode_mobile/domain/agent_catalog.dart';

const _ids = ['codex', 'gemini', 'qwen', 'goose', 'omp-acp', 'fx'];

/// Isolated authored-script fixture: rewrite only fixed guest paths and proc
/// in test input. Production exposes no prefix, UID or proc override.
class _Guest {
  _Guest(this.temp);
  final Directory temp;
  final agent = AgentCatalog.builtIn.byId('codex')!;
  String get home => '${temp.path}/home';
  String get bin => '$home/.local/bin';
  String get parent => '$home/.local/share/oc-agents';
  String get base => '$parent/codex';
  String get version => agent.recipe!.version;
  String get launch => '$base/$version/launch';
  String get link => '$bin/codex';
  String get lock => '$parent/.lock-codex';
  String get proc => '${temp.path}/proc';

  Future<void> prepare() async {
    await Directory('$base/$version/payload').create(recursive: true);
    await Directory(bin).create(recursive: true);
    await Directory(proc).create();
    await File(launch).writeAsString('authored launcher');
    await File('$base/$version/payload/codex').writeAsString('payload');
    await Link(link).create(launch);
    for (final path in [
      '$home/.oc-profiles/owner/claude/account',
      '$home/.oc-profiles/owner/codex/history',
      '$parent/claude/payload',
      '$home/.local/node/bin/node',
      '$home/.local/share/paseo/daemon',
    ]) {
      await File(path).create(recursive: true);
      await File(path).writeAsString('keep');
    }
  }

  Future<ProcessResult> run({
    AgentDescriptor? descriptor,
    String? uid,
    String? environmentHome,
  }) async {
    var script = AgentRemovalScripts.remove(descriptor ?? agent)
        .replaceAll('/home/oc', home)
        .replaceAll("pathlib.Path('/proc')", "pathlib.Path('$proc')");
    if (uid != null) script = script.replaceAll('os.getuid()', uid);
    return Process.run(
      'sh',
      ['-c', script],
      environment: {'HOME': environmentHome ?? home, 'PATH': '/usr/bin:/bin'},
      workingDirectory: temp.path,
    ).timeout(const Duration(seconds: 10));
  }

  Future<void> expectKept() async {
    for (final path in [
      '$home/.oc-profiles/owner/claude/account',
      '$home/.oc-profiles/owner/codex/history',
      '$parent/claude/payload',
      '$home/.local/node/bin/node',
      '$home/.local/share/paseo/daemon',
    ]) {
      expect(await File(path).readAsString(), 'keep', reason: path);
    }
  }
}

void _receipt(ProcessResult result, int code) {
  expect(result.exitCode, code);
  expect(
    result.stdout,
    '::oc-check-begin agent-codex\n::oc-check-end agent-codex $code\n',
  );
  expect(result.stderr, isEmpty);
}

void main() {
  late Directory temp;
  late _Guest guest;
  setUp(() async {
    temp = await Directory.systemTemp.createTemp('oc-agent-removal-');
    guest = _Guest(temp);
    await guest.prepare();
  });
  tearDown(() async {
    await temp.delete(recursive: true);
  });

  test(
    'all six authored recipes are valid shell; Claude is excluded',
    () async {
      for (final id in _ids) {
        final script = AgentRemovalScripts.remove(
          AgentCatalog.builtIn.byId(id)!,
        );
        final file = await File('${temp.path}/remove.sh').writeAsString(script);
        expect(
          (await Process.run('sh', ['-n', file.path])).exitCode,
          0,
          reason: id,
        );
        expect(script, isNot(contains('kill(')));
        expect(script, isNot(contains('signOut')));
      }
      expect(
        () => AgentRemovalScripts.remove(AgentCatalog.builtIn.byId('claude')!),
        throwsArgumentError,
      );
      expect(
        () =>
            AgentRemovalScripts.remove(AgentCatalog.builtIn.byId('opencode')!),
        throwsArgumentError,
      );
    },
  );

  test(
    'removes payload and partial staging, preserves accounts and shared tools',
    () async {
      await Directory(
        '${guest.base}/${guest.version}.new/payload',
      ).create(recursive: true);
      await File(
        '${guest.base}/${guest.version}.new/payload/partial',
      ).writeAsString('download');
      await Directory('${guest.base}/${guest.version}.old.123').create();
      await Link('${guest.link}.new.123').create(guest.launch);
      _receipt(await guest.run(), 0);
      expect(await Directory(guest.base).exists(), isFalse);
      expect(await Link(guest.link).exists(), isFalse);
      expect(await Link('${guest.link}.new.123').exists(), isFalse);
      expect(await Directory(guest.lock).exists(), isFalse);
      await guest.expectKept();
      _receipt(await guest.run(), 0);
    },
  );

  test(
    'absent payload is idempotent and leaves shared directories alone',
    () async {
      await Directory(guest.base).delete(recursive: true);
      await Link(guest.link).delete();
      _receipt(await guest.run(), 0);
      expect(await Directory(guest.lock).exists(), isFalse);
      await guest.expectKept();
    },
  );

  test('existing install lock is busy and never removed', () async {
    await Directory(guest.lock).create();
    await File('${guest.lock}/foreign-owner').writeAsString('keep');
    _receipt(await guest.run(), 16);
    expect(await File('${guest.lock}/foreign-owner').readAsString(), 'keep');
    expect(await File(guest.launch).exists(), isTrue);
  });

  test('absent payload never clears a foreign install lock', () async {
    await Directory(guest.base).delete(recursive: true);
    await Link(guest.link).delete();
    await Directory(guest.lock).create();
    _receipt(await guest.run(), 16);
    expect(await Directory(guest.lock).exists(), isTrue);
  });

  test('absent payload with a surviving target PID remains busy', () async {
    await Directory(guest.base).delete(recursive: true);
    await Link(guest.link).delete();
    await Directory('${guest.proc}/123').create();
    await File(
      '${guest.proc}/123/cmdline',
    ).writeAsString('${guest.base}/${guest.version}/payload/codex\u0000');
    _receipt(await guest.run(), 16);
    expect(await File('${guest.proc}/123/cmdline').exists(), isTrue);
    expect(await Directory(guest.lock).exists(), isFalse);
  });

  test('unrelated executable link refuses before any mutation', () async {
    await Link(guest.link).delete();
    await Link(guest.link).create('${guest.home}/.local/node/bin/node');
    _receipt(await guest.run(), 17);
    expect(
      await Link(guest.link).target(),
      '${guest.home}/.local/node/bin/node',
    );
    expect(await File(guest.launch).exists(), isTrue);
    expect(await Directory(guest.lock).exists(), isFalse);
  });

  test('regular executable refuses before any mutation', () async {
    await Link(guest.link).delete();
    await File(guest.link).writeAsString('foreign executable');
    _receipt(await guest.run(), 17);
    expect(await File(guest.link).readAsString(), 'foreign executable');
    expect(await File(guest.launch).exists(), isTrue);
  });

  test(
    'staging symlink is unlinked without following its account target',
    () async {
      await Directory('${guest.base}/${guest.version}.new').create();
      await Link(
        '${guest.base}/${guest.version}.new/escaped',
      ).create('${guest.home}/.oc-profiles/owner');
      _receipt(await guest.run(), 0);
      expect(await Directory(guest.base).exists(), isFalse);
      expect(await Link(guest.link).exists(), isFalse);
      await guest.expectKept();
    },
  );

  test(
    'payload links are removed without following internal or external targets',
    () async {
      final payload = '${guest.base}/${guest.version}/payload';
      await Link('$payload/internal').create('codex');
      await Link(
        '$payload/external',
      ).create('${guest.home}/.oc-profiles/owner');
      final outside = await File(
        '${temp.path}/outside',
      ).writeAsString('keep outside');
      await Link('$payload/external-file').create(outside.path);
      _receipt(await guest.run(), 0);
      expect(await Directory(guest.base).exists(), isFalse);
      expect(await outside.readAsString(), 'keep outside');
      await guest.expectKept();
    },
  );

  test('symlinked target root refuses before any mutation', () async {
    await Directory(guest.base).delete(recursive: true);
    await Link(guest.base).create('${guest.home}/.oc-profiles/owner');
    _receipt(await guest.run(), 17);
    expect(await Link(guest.base).target(), '${guest.home}/.oc-profiles/owner');
    await guest.expectKept();
  });

  test('symlinked bin ancestor refuses before any mutation', () async {
    await Directory(guest.bin).rename('${guest.bin}-saved');
    await Link(guest.bin).create('${guest.bin}-saved');
    _receipt(await guest.run(), 17);
    expect(await File(guest.launch).exists(), isTrue);
  });

  test('foreign staging launcher refuses all cleanup', () async {
    await Link(
      '${guest.link}.new.123',
    ).create('${guest.home}/.local/node/bin/node');
    _receipt(await guest.run(), 17);
    expect(await Link(guest.link).exists(), isTrue);
    expect(await File(guest.launch).exists(), isTrue);
  });

  test('non-PID staging launcher refuses all cleanup', () async {
    await Link('${guest.link}.new.foreign').create(guest.launch);
    _receipt(await guest.run(), 17);
    expect(await Link(guest.link).exists(), isTrue);
    expect(await File(guest.launch).exists(), isTrue);
  });

  test(
    'exact target PID is busy without signalling or deleting anything',
    () async {
      await Directory('${guest.proc}/123').create();
      await File('${guest.proc}/123/cmdline').writeAsBytes([
        ...'/usr/bin/node'.codeUnits,
        0,
        ...'${guest.base}/${guest.version}/payload/codex'.codeUnits,
        0,
      ]);
      _receipt(await guest.run(), 16);
      expect(await File(guest.launch).exists(), isTrue);
      expect(await File('${guest.proc}/123/cmdline').exists(), isTrue);
      expect(await Directory(guest.lock).exists(), isFalse);
    },
  );

  test('host-rootfs executable identity also blocks removal', () async {
    await Directory('${guest.proc}/124').create();
    await File('${guest.proc}/124/cmdline').writeAsString('codex\u0000');
    await Link(
      '${guest.proc}/124/exe',
    ).create('/data/rootfs${guest.base}/${guest.version}/payload/codex');
    _receipt(await guest.run(), 16);
    expect(await File(guest.launch).exists(), isTrue);
  });

  test('other agent processes never block or get removed', () async {
    await Directory('${guest.proc}/125').create();
    await File(
      '${guest.proc}/125/cmdline',
    ).writeAsString('${guest.parent}/claude/payload\u0000');
    _receipt(await guest.run(), 0);
    expect(await File('${guest.proc}/125/cmdline').exists(), isTrue);
    await guest.expectKept();
  });

  test('wrong UID or HOME refuses before any mutation', () async {
    _receipt(await guest.run(uid: '0'), 17);
    _receipt(await guest.run(environmentHome: '/root'), 17);
    expect(await File(guest.launch).exists(), isTrue);
  });

  test(
    'unsafe catalog version and executable mapping cannot become shell input',
    () {
      AgentDescriptor descriptor(String executable, String version) =>
          AgentDescriptor(
            id: 'codex',
            name: 'Codex',
            iconKey: 'codex',
            route: AgentRoute.paseoNative,
            providerId: 'codex',
            signInMethod: AgentSignInMethod.none,
            resumeReason: 'Unverified',
            recipe: AgentInstallRecipe(
              version: version,
              executable: executable,
              artifacts: guest.agent.recipe!.artifacts,
            ),
          );
      expect(
        () => AgentRemovalScripts.remove(descriptor('claude', guest.version)),
        throwsArgumentError,
      );
      expect(
        () => AgentRemovalScripts.remove(
          descriptor('codex', "v'; touch /tmp/escaped"),
        ),
        throwsArgumentError,
      );
      expect(
        () => AgentRemovalScripts.remove(descriptor('codex', '../owner')),
        throwsArgumentError,
      );
    },
  );
}
