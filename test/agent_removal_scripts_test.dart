import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/builtin/agents/agent_removal_scripts.dart';
import 'package:opencode_mobile/domain/agent_catalog.dart';

const _ids = ['codex', 'gemini', 'qwen', 'goose', 'omp-acp', 'fx'];

/// The guest's files are owned by uid 1000; the files this test creates are
/// owned by whoever runs it (1001 on a GitHub runner). Point the script's
/// ownership checks at the real owner and leave every other check as authored.
final _hostUid = Process.runSync('id', ['-u']).stdout.toString().trim();

String _asHostOwner(String script) => script
    .replaceAll('entry.st_uid != 1000', 'entry.st_uid != $_hostUid')
    .replaceAll(
      'entry.st_uid not in (0, 1000)',
      'entry.st_uid not in (0, $_hostUid)',
    );

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
  String get receiptId => 'a' * 32;
  String get receiptPath => '$home/.local/share/oc-agent-removal-$receiptId';
  int? freedBytes;
  bool? alreadyAbsent;

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
    bool drainReceipt = true,
  }) async {
    var script =
        AgentRemovalScripts.remove(descriptor ?? agent, receiptId: receiptId)
            .replaceAll('/home/oc', home)
            .replaceAll("pathlib.Path('/proc')", "pathlib.Path('$proc')");
    script = _asHostOwner(script).replaceAll('os.getuid()', uid ?? '1000');
    final result = await Process.run(
      'sh',
      ['-c', script],
      environment: {'HOME': environmentHome ?? home, 'PATH': '/usr/bin:/bin'},
      workingDirectory: temp.path,
    ).timeout(const Duration(seconds: 10));
    if (result.exitCode == 0 && drainReceipt) {
      final read = await readReceipt();
      expect(read.exitCode, 0);
      expect(read.stderr, isEmpty);
      final values = (read.stdout as String).trim().split(' ');
      expect(values, hasLength(2));
      freedBytes = int.parse(values[0]);
      alreadyAbsent = values[1] == '1';
      expect(await File(receiptPath).exists(), isFalse);
    }
    return result;
  }

  Future<ProcessResult> readReceipt({String uid = '0'}) => Process.run(
    'sh',
    [
      '-c',
      _asHostOwner(
        AgentRemovalScripts.readReceipt(receiptId),
      ).replaceAll('/home/oc', home).replaceAll('os.getuid()', uid),
    ],
    environment: {'HOME': '/root', 'PATH': '/usr/bin:/bin'},
    workingDirectory: temp.path,
  ).timeout(const Duration(seconds: 10));

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

  Future<int> allocated(List<String> paths) async {
    final measured = await Process.run('du', ['-s', '-B1', ...paths]);
    expect(measured.exitCode, 0);
    return (measured.stdout as String)
        .trim()
        .split('\n')
        .fold<int>(
          0,
          (sum, line) => sum + int.parse(line.split(RegExp(r'\s+')).first),
        );
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
          receiptId: guest.receiptId,
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
        () => AgentRemovalScripts.remove(
          AgentCatalog.builtIn.byId('claude')!,
          receiptId: guest.receiptId,
        ),
        throwsArgumentError,
      );
      expect(
        () => AgentRemovalScripts.remove(
          AgentCatalog.builtIn.byId('opencode')!,
          receiptId: guest.receiptId,
        ),
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
    await File('${guest.lock}/foreign-owner').writeAsString('keep');
    _receipt(await guest.run(), 16);
    expect(await Directory(guest.lock).exists(), isTrue);
  });

  test(
    'empty abandoned install lock is removed with partial staging',
    () async {
      await Directory(guest.lock).create();
      await Directory('${guest.base}/${guest.version}.new').create();
      await File(
        '${guest.base}/${guest.version}.new/partial',
      ).writeAsString('partial');
      final expected = await guest.allocated([
        guest.base,
        guest.link,
        guest.lock,
      ]);
      _receipt(await guest.run(), 0);
      expect(guest.freedBytes, expected);
      expect(guest.alreadyAbsent, isFalse);
      expect(await Directory(guest.base).exists(), isFalse);
      expect(await Directory(guest.lock).exists(), isFalse);
      await guest.expectKept();
    },
  );

  test(
    'absent empty abandoned lock returns its allocated bytes and not absent',
    () async {
      await Directory(guest.base).delete(recursive: true);
      await Link(guest.link).delete();
      await Directory(guest.lock).create();
      final expected = await guest.allocated([guest.lock]);
      _receipt(await guest.run(), 0);
      expect(guest.freedBytes, expected);
      expect(guest.alreadyAbsent, isFalse);
      expect(await Directory(guest.lock).exists(), isFalse);
      _receipt(await guest.run(), 0);
      expect(guest.freedBytes, 0);
      expect(guest.alreadyAbsent, isTrue);
    },
  );

  test('live authored bash installer preserves empty lock and target', () async {
    await Directory(guest.lock).create();
    await Directory('${guest.proc}/126').create();
    await File('${guest.proc}/126/cmdline').writeAsString(
      '/bin/bash\u0000-c\u0000set -eu\noc_lock=${guest.lock}\nmkdir "\$oc_lock"\n\u0000',
    );
    _receipt(await guest.run(), 16);
    expect(await File(guest.launch).exists(), isTrue);
    expect(await Directory(guest.lock).exists(), isTrue);
    expect(await File(guest.receiptPath).exists(), isFalse);
  });

  test(
    'unsafe lock links and non-directories refuse before deleting target',
    () async {
      await Link(guest.lock).create('${guest.home}/.oc-profiles/owner');
      _receipt(await guest.run(), 17);
      await Link(guest.lock).delete();
      await File(guest.lock).writeAsString('foreign lock');
      _receipt(await guest.run(), 17);
      expect(await File(guest.launch).exists(), isTrue);
      await guest.expectKept();
    },
  );

  test(
    'allocated bytes deduplicate payload hardlinks and exclude retained inode',
    () async {
      final payload = '${guest.base}/${guest.version}/payload';
      final outside = await File(
        '${temp.path}/retained',
      ).writeAsBytes(List.filled(65536, 7));
      expect(
        (await Process.run('ln', [
          '$payload/codex',
          '$payload/internal-copy',
        ])).exitCode,
        0,
      );
      expect(
        (await Process.run('ln', [
          outside.path,
          '$payload/retained-link',
        ])).exitCode,
        0,
      );
      final retainedBytes = await guest.allocated([outside.path]);
      final expected =
          await guest.allocated([guest.base, guest.link]) - retainedBytes;
      _receipt(await guest.run(), 0);
      expect(guest.freedBytes, expected);
      expect(guest.alreadyAbsent, isFalse);
      expect(await outside.length(), 65536);
      await guest.expectKept();
    },
  );

  test(
    'nonce receipt is private, numeric only, and reader deletes it',
    () async {
      final expected = await guest.allocated([guest.base, guest.link]);
      _receipt(await guest.run(drainReceipt: false), 0);
      final file = File(guest.receiptPath);
      final body = await file.readAsString();
      expect(body, '{"bytes":$expected,"absent":0}');
      expect(
        (await Process.run('stat', ['-c', '%a', file.path])).stdout,
        '600\n',
      );
      final read = await guest.readReceipt();
      expect(read.exitCode, 0);
      expect(read.stdout, '$expected 0\n');
      expect(read.stderr, isEmpty);
      expect(await file.exists(), isFalse);
      final repeat = await guest.readReceipt();
      expect(repeat.exitCode, 18);
      expect(repeat.stdout, isEmpty);
    },
  );

  test(
    'existing nonce file prevents removal rather than overwriting a receipt',
    () async {
      await File(guest.receiptPath).writeAsString('keep existing');
      _receipt(await guest.run(), 17);
      expect(await File(guest.receiptPath).readAsString(), 'keep existing');
      expect(await File(guest.launch).exists(), isTrue);
    },
  );

  test(
    'reader rejects and cleans malformed bounded private receipts silently',
    () async {
      for (final value in [
        '',
        'x' * 97,
        '{"bytes":true,"absent":0}',
        '{"bytes":-1,"absent":0}',
        '{"bytes":9223372036854775808,"absent":0}',
        '{"bytes":1,"absent":1}',
        '{"bytes":0,"absent":2}',
        '{"bytes":0,"bytes":1,"absent":0}',
        '{"bytes":0,"absent":0,"extra":"synthetic-private"}',
      ]) {
        await File(guest.receiptPath).writeAsString(value);
        await Process.run('chmod', ['600', guest.receiptPath]);
        final read = await guest.readReceipt();
        expect(read.exitCode, 18);
        expect(read.stdout, isEmpty);
        expect(read.stderr, isEmpty);
        expect(await File(guest.receiptPath).exists(), isFalse);
      }
    },
  );

  test(
    'reader never follows receipt links or deletes their retained target',
    () async {
      final outside = await File(
        '${temp.path}/retained-receipt',
      ).writeAsString('{"bytes":0,"absent":1}');
      await Link(guest.receiptPath).create(outside.path);
      final read = await guest.readReceipt();
      expect(read.exitCode, 18);
      expect(read.stdout, isEmpty);
      expect(await outside.readAsString(), '{"bytes":0,"absent":1}');
      expect(await Link(guest.receiptPath).exists(), isTrue);
    },
  );

  test(
    'reader requires root view and bounded signed integer projection',
    () async {
      await File(
        guest.receiptPath,
      ).writeAsString('{"bytes":9223372036854775807,"absent":0}');
      await Process.run('chmod', ['600', guest.receiptPath]);
      final denied = await guest.readReceipt(uid: '1000');
      expect(denied.exitCode, 18);
      expect(denied.stdout, isEmpty);
      expect(await File(guest.receiptPath).exists(), isTrue);
      final read = await guest.readReceipt();
      expect(read.exitCode, 0);
      expect(read.stdout, '9223372036854775807 0\n');
      expect(await File(guest.receiptPath).exists(), isFalse);
    },
  );

  test('unsafe nonce values are rejected before becoming authored shell', () {
    for (final id in [
      'a' * 31,
      'A' * 32,
      '../escape',
      "'; touch /tmp/escaped",
    ]) {
      expect(
        () => AgentRemovalScripts.remove(guest.agent, receiptId: id),
        throwsArgumentError,
      );
      expect(() => AgentRemovalScripts.readReceipt(id), throwsArgumentError);
    }
  });

  test(
    'prior pinned-version authored launcher is removed within the same target',
    () async {
      final old = '${guest.base}/0.159.0/launch';
      await File(old).create(recursive: true);
      await File(old).writeAsString('older authored launcher');
      await Link(guest.link).delete();
      await Link(guest.link).create(old);
      await Link('${guest.link}.new.123').create(old);
      _receipt(await guest.run(), 0);
      expect(await Directory(guest.base).exists(), isFalse);
      expect(await Link(guest.link).exists(), isFalse);
      expect(await Link('${guest.link}.new.123').exists(), isFalse);
      await guest.expectKept();
    },
  );

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
        () => AgentRemovalScripts.remove(
          descriptor('claude', guest.version),
          receiptId: guest.receiptId,
        ),
        throwsArgumentError,
      );
      expect(
        () => AgentRemovalScripts.remove(
          descriptor('codex', "v'; touch /tmp/escaped"),
          receiptId: guest.receiptId,
        ),
        throwsArgumentError,
      );
      expect(
        () => AgentRemovalScripts.remove(
          descriptor('codex', '../owner'),
          receiptId: guest.receiptId,
        ),
        throwsArgumentError,
      );
    },
  );
}
