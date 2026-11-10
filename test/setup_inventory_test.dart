import 'dart:async';
import 'dart:convert';

import 'package:flutter/widgets.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/builtin/builtin_linux.dart';
import 'package:opencode_mobile/builtin/setup/setup_contract.dart';
import 'package:opencode_mobile/builtin/setup/setup_engine.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';

class _InventoryLinux extends BuiltinLinux {
  bool installed = true;
  Set<String> tools = {'python', 'aiteam'};
  final scripts = <String>[];
  Completer<void>? holdFirstProbe;
  BuiltinLinuxException? failure;
  BuiltinLinuxRunResult? result;
  Map<String, Object?>? job;

  @override
  Future<BuiltinLinuxStatus> status() async => BuiltinLinuxStatus(
    installed: installed,
    phase: installed ? BuiltinLinuxPhase.ready : BuiltinLinuxPhase.idle,
  );

  @override
  Future<BuiltinLinuxRunResult> run(
    String script, {
    Duration timeout = const Duration(minutes: 2),
    String? input,
  }) async {
    scripts.add(script);
    // A probe reports the inventory it observed before an install/removal,
    // even if its channel reply arrives after the change notification.
    final observed = Set<String>.of(tools);
    if (scripts.length == 1) await holdFirstProbe?.future;
    if (failure case final error?) throw error;
    if (result case final response?) return response;
    final output = StringBuffer();
    for (final match in RegExp(
      r"::oc-check-begin %s\\n' '([a-z0-9]+)'",
    ).allMatches(script)) {
      final id = match.group(1)!;
      output
        ..writeln('::oc-check-begin $id')
        ..writeln(observed.contains(id) ? '1.0' : 'command not found')
        ..writeln('::oc-check-end $id ${observed.contains(id) ? 0 : 1}');
    }
    return BuiltinLinuxRunResult(exitCode: 0, output: output.toString());
  }

  @override
  Future<String?> setupStatus() async => job == null ? null : jsonEncode(job);
}

const _components = [
  SetupComponent(
    id: 'python',
    title: 'Python',
    shortTitle: 'Python',
    checkScript: 'python3 --version',
    installScript: ':',
  ),
  SetupComponent(
    id: 'aiteam',
    title: 'AI Team',
    shortTitle: 'AI Team',
    checkScript: 'test -f /opt/ai-team/installed',
    installScript: ':',
  ),
  SetupComponent(
    id: 'opencode',
    title: 'OpenCode',
    shortTitle: 'OpenCode',
    checkScript: 'opencode --version',
    installScript: ':',
    required: true,
  ),
];

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  late _InventoryLinux linux;
  late ChannelSetupEngine engine;

  setUp(() {
    linux = _InventoryLinux();
    engine = ChannelSetupEngine(
      linux: linux,
      strings: () => lookupAppLocalizations(const Locale('en')),
      components: (_, _) => _components,
      pollInterval: const Duration(hours: 1),
    );
  });

  tearDown(() => engine.dispose());

  test('concurrent inventory consumers share one Linux check', () async {
    linux.holdFirstProbe = Completer<void>();
    final phone = engine.installedOptional();
    final team = engine.installedOptional();
    final addTools = engine.installedOptional();
    await Future<void>.delayed(Duration.zero);
    expect(linux.scripts, hasLength(1));
    linux.holdFirstProbe!.complete();
    final inventories = await Future.wait([phone, team, addTools]);
    for (final inventory in inventories) {
      expect(inventory, {'python', 'aiteam'});
    }
  });

  test('a later inventory read checks the phone again', () async {
    expect(await engine.installedOptional(), {'python', 'aiteam'});
    linux.tools = {'python'};
    expect(await engine.installedOptional(), {'python'});
    expect(linux.scripts, hasLength(2));
  });

  test('a busy Linux probe is unknown, not an empty inventory', () async {
    linux.failure = const BuiltinLinuxException(
      'Another phone task is running. Wait a moment and try again.',
    );
    await expectLater(
      engine.installedOptional(),
      throwsA(isA<BuiltinLinuxException>()),
    );
    linux.failure = null;
    expect(await engine.installedOptional(), {'python', 'aiteam'});
    expect(linux.scripts, hasLength(2));
  });

  test('a failed refresh cannot report prior tools as removed', () async {
    expect(await engine.installedOptional(), {'python', 'aiteam'});
    setupToolsChanged.value++;
    linux.failure = const BuiltinLinuxException('The check could not finish.');
    await expectLater(
      engine.installedOptional(),
      throwsA(isA<BuiltinLinuxException>()),
    );
    linux.failure = null;
    expect(await engine.installedOptional(), {'python', 'aiteam'});
  });

  for (final result in [
    const BuiltinLinuxRunResult(exitCode: -1, output: ''),
    const BuiltinLinuxRunResult(
      exitCode: 0,
      output: '::oc-check-begin python\n1.0\n::oc-check-end python 0\n',
    ),
  ]) {
    test(
      'an incomplete check (${result.exitCode}) is unknown, not absence',
      () async {
        linux.result = result;
        await expectLater(
          engine.installedOptional(),
          throwsA(isA<BuiltinLinuxException>()),
        );
        linux.result = null;
        expect(await engine.installedOptional(), {'python', 'aiteam'});
      },
    );
  }

  test('a missing Linux rootfs is an authoritative empty inventory', () async {
    linux.installed = false;
    expect(await engine.installedOptional(), isEmpty);
    expect(linux.scripts, isEmpty);
  });

  test('a tools change discards an earlier in-flight inventory', () async {
    linux.tools = {'python'};
    linux.holdFirstProbe = Completer<void>();
    final beforeInstall = engine.installedOptional();
    await Future<void>.delayed(Duration.zero);
    expect(linux.scripts, hasLength(1));
    linux.tools = {'python', 'aiteam'};
    setupToolsChanged.value++;
    final afterInstall = engine.installedOptional();
    linux.holdFirstProbe!.complete();
    expect(await beforeInstall, {'python', 'aiteam'});
    expect(await afterInstall, {'python', 'aiteam'});
    expect(linux.scripts, hasLength(2));
  });

  test('removal notification refreshes every inventory consumer', () async {
    expect(await engine.installedOptional(), {'python', 'aiteam'});
    linux.tools = {'python'};
    setupToolsChanged.value++;
    final inventories = await Future.wait([
      engine.installedOptional(),
      engine.installedOptional(),
      engine.installedOptional(),
    ]);
    for (final inventory in inventories) {
      expect(inventory, {'python'});
    }
    expect(linux.scripts, hasLength(2));
  });

  test('a runtime switch job does not erase Python or AI Team', () async {
    expect(await engine.installedOptional(), {'python', 'aiteam'});
    linux.job = {
      'jobId': 'switch-to-older',
      'state': 'running',
      'current': 'opencode',
      'order': ['opencode'],
      'components': {
        'opencode': {'state': 'running'},
      },
    };
    await engine.restore();
    expect(engine.progress.value.state, SetupState.running);
    expect(engine.progress.value.components.map((c) => c.id), ['opencode']);
    expect(await engine.installedOptional(), {'python', 'aiteam'});
    linux.job!['state'] = 'done';
    linux.job!['components'] = {
      'opencode': {'state': 'done'},
    };
    await engine.restore();
    expect(await engine.installedOptional(), {'python', 'aiteam'});
  });
}
