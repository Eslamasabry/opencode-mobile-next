import 'dart:async';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/builtin/builtin_linux.dart';
import 'package:opencode_mobile/builtin/setup/aiteam_scripts.dart';
import 'package:opencode_mobile/builtin/setup/component_removal.dart';
import 'package:opencode_mobile/builtin/setup/components.dart';
import 'package:opencode_mobile/builtin/setup/setup_contract.dart';
import 'package:opencode_mobile/builtin/team/builtin_team.dart';
import 'package:opencode_mobile/l10n/app_localizations_en.dart';
import 'package:opencode_mobile/termux/bridge.dart';
import 'package:opencode_mobile/termux/team_runtime.dart';

SetupComponent _component(
  String id, {
  List<String> dependsOn = const [],
  bool required = false,
  bool removable = true,
  bool jobStep = false,
  bool hasProbe = true,
}) => SetupComponent(
  id: id,
  title: id,
  shortTitle: id,
  checkScript: 'version:$id',
  presenceScript: hasProbe ? 'presence:$id' : null,
  installScript: 'install:$id',
  removeScript: removable ? 'remove:$id' : null,
  dependsOn: dependsOn,
  required: required,
  jobStep: jobStep,
);

class _Linux extends BuiltinLinux {
  final calls = <String>[];
  final probes = <String, int>{};
  final removals = <String, String>{};
  final throwingProbes = <String>{};
  bool installed = true;
  bool statusFails = false;
  bool setupFails = false;
  String? setup;
  int removalExit = 0;
  String? removalError;
  bool leaveInstalled = false;
  Completer<void>? removalGate;
  final removalStarted = Completer<void>();

  void register(List<SetupComponent> registry) {
    for (final component in registry) {
      final probe = component.presenceScript;
      if (probe == null) continue;
      probes[probe] = 0;
      final removal = component.removeScript;
      if (removal != null) removals[removal] = probe;
    }
  }

  @override
  Future<BuiltinLinuxStatus> status() async {
    if (statusFails) throw StateError('Inventory unavailable');
    return BuiltinLinuxStatus(
      installed: installed,
      phase: installed ? BuiltinLinuxPhase.ready : BuiltinLinuxPhase.idle,
    );
  }

  @override
  Future<String?> setupStatus() async {
    if (setupFails) throw StateError('Setup status unavailable');
    return setup;
  }

  @override
  Future<BuiltinLinuxRunResult> run(
    String script, {
    Duration timeout = const Duration(minutes: 2),
    String? input,
  }) async {
    calls.add(script);
    if (throwingProbes.contains(script)) {
      throw StateError('Presence unavailable');
    }
    if (probes.containsKey(script)) {
      return BuiltinLinuxRunResult(exitCode: probes[script]!, output: '');
    }
    final probe = removals[script];
    if (probe != null) {
      if (removalError != null) throw StateError(removalError!);
      if (!removalStarted.isCompleted) removalStarted.complete();
      await removalGate?.future;
      if (removalExit == 0 && !leaveInstalled) probes[probe] = 1;
      return BuiltinLinuxRunResult(exitCode: removalExit, output: '');
    }
    throw StateError('Unexpected script');
  }
}

class _Team extends BuiltinTeam {
  _Team(this.fakeLinux, this.probe) : super(linux: fakeLinux);

  final _Linux fakeLinux;
  final String? probe;
  int removeCalls = 0;

  @override
  Future<void> remove() async {
    removeCalls++;
    if (probe != null) fakeLinux.probes[probe!] = 1;
  }
}

/// Termux's team manager, answering `remove` as told.
class _TermuxRuntime extends TermuxTeamRuntime {
  _TermuxRuntime(this.onRemove);

  final Future<TeamRuntimeStatus> Function() onRemove;
  int removes = 0;

  @override
  Future<TeamRuntimeStatus> remove() {
    removes++;
    return onRemove();
  }
}

Matcher _blocked(ComponentRemovalBlock code) => throwsA(
  isA<ComponentRemovalException>().having((error) => error.code, 'code', code),
);

void main() {
  late _Linux linux;
  late _Team team;
  late ComponentRemovalService service;

  void configure(List<SetupComponent> registry) {
    linux = _Linux()..register(registry);
    final teamEntries = registry.where((entry) => entry.id == 'aiteam');
    team = _Team(
      linux,
      teamEntries.isEmpty ? null : teamEntries.single.presenceScript,
    );
    service = ComponentRemovalService(
      linux: linux,
      registry: registry,
      team: team,
    );
  }

  test('Python removal executes its registered removeScript', () async {
    final registry = setupComponents(AppLocalizationsEn());
    configure(registry);
    final python = registry.singleWhere((entry) => entry.id == 'python');
    final before = setupToolsChanged.value;

    await service.remove('python');

    expect(
      linux.calls.where((script) => script == python.removeScript),
      hasLength(1),
    );
    expect(linux.probes[python.presenceScript], 1);
    expect(team.removeCalls, 0);
    expect(service.busy.value, isFalse);
    expect(setupToolsChanged.value, before + 1);
  });

  test('AI Team removal uses its lifecycle API', () async {
    final registry = setupComponents(AppLocalizationsEn());
    configure(registry);
    final component = registry.singleWhere((entry) => entry.id == 'aiteam');
    final before = setupToolsChanged.value;

    await service.remove('aiteam');

    expect(team.removeCalls, 1);
    expect(linux.calls, isNot(contains(component.removeScript)));
    expect(linux.probes[component.presenceScript], 1);
    expect(setupToolsChanged.value, before + 1);
  });

  test('AI Team refuses a substituted unrecognized removeScript', () async {
    configure([_component('aiteam')]);

    await expectLater(
      service.remove('aiteam'),
      _blocked(ComponentRemovalBlock.unsupported),
    );

    expect(team.removeCalls, 0);
    expect(linux.calls, isNot(contains('remove:aiteam')));
  });

  test('Registered presence scripts parse as POSIX shell', () async {
    final registry = setupComponents(AppLocalizationsEn());
    for (final component in registry) {
      final script = component.presenceScript;
      if (script == null) continue;
      final result = await Process.run('sh', ['-n', '-c', script]);
      expect(result.exitCode, 0, reason: component.id);
    }
  });

  test(
    'Node removal reports OpenCode and transitive AI Team dependents',
    () async {
      configure(setupComponents(AppLocalizationsEn()));
      final entries = await service.inventory();
      final node = entries.singleWhere((entry) => entry.component.id == 'node');

      expect(node.presence, ComponentPresence.installed);
      expect(node.blockingDependents, containsAll(['opencode', 'aiteam']));
      expect(node.canRemove, isFalse);
      expect(
        entries.map((entry) => entry.component.id),
        isNot(contains('start')),
      );
      final before = setupToolsChanged.value;
      await expectLater(
        service.remove('node'),
        _blocked(ComponentRemovalBlock.dependentInstalled),
      );
      expect(linux.calls.where(linux.removals.containsKey), isEmpty);
      expect(setupToolsChanged.value, before);
    },
  );

  test(
    'Inventory uses presence probes rather than pinned version checks',
    () async {
      configure([_component('tool')]);

      final entry = (await service.inventory()).single;

      expect(entry.presence, ComponentPresence.installed);
      expect(entry.canRemove, isTrue);
      expect(linux.calls, isNot(contains('version:tool')));
    },
  );

  test('A required component cannot be removed without dependents', () async {
    configure([_component('base', required: true)]);
    await expectLater(
      service.remove('base'),
      _blocked(ComponentRemovalBlock.requiredComponent),
    );
    expect(linux.calls.where(linux.removals.containsKey), isEmpty);
  });

  test('A component without removeScript is unavailable for removal', () async {
    configure([_component('tool', removable: false)]);
    await expectLater(
      service.remove('tool'),
      _blocked(ComponentRemovalBlock.unsupported),
    );
  });

  test('An absent component cannot run its removeScript', () async {
    configure([_component('tool')]);
    linux.probes['presence:tool'] = 1;
    expect(
      (await service.inventory()).single.presence,
      ComponentPresence.absent,
    );
    await expectLater(
      service.remove('tool'),
      _blocked(ComponentRemovalBlock.notInstalled),
    );
    expect(linux.calls, isNot(contains('remove:tool')));
  });

  for (final code in [-1, 2, 127]) {
    test('Presence exit $code is unknown and fails closed', () async {
      configure([_component('tool')]);
      linux.probes['presence:tool'] = code;
      expect(
        (await service.inventory()).single.presence,
        ComponentPresence.unknown,
      );
      await expectLater(
        service.remove('tool'),
        _blocked(ComponentRemovalBlock.inventoryUnavailable),
      );
      expect(linux.calls, isNot(contains('remove:tool')));
    });
  }

  test(
    'A missing presence probe does not treat a healthy version as proof',
    () async {
      configure([_component('tool', hasProbe: false)]);
      expect(
        (await service.inventory()).single.presence,
        ComponentPresence.unknown,
      );
      await expectLater(
        service.remove('tool'),
        _blocked(ComponentRemovalBlock.inventoryUnavailable),
      );
      expect(linux.calls, isEmpty);
    },
  );

  test('A failed native inventory never runs a removeScript', () async {
    configure([_component('tool')]);
    linux.statusFails = true;
    await expectLater(
      service.remove('tool'),
      _blocked(ComponentRemovalBlock.inventoryUnavailable),
    );
    expect(linux.calls.where(linux.removals.containsKey), isEmpty);
    expect(service.busy.value, isFalse);
  });

  test('A failed presence probe never runs a removeScript', () async {
    configure([_component('tool')]);
    linux.throwingProbes.add('presence:tool');
    await expectLater(
      service.remove('tool'),
      _blocked(ComponentRemovalBlock.inventoryUnavailable),
    );
    expect(linux.calls, isNot(contains('remove:tool')));
  });

  test('An unknown dependent blocks removal with its identity', () async {
    configure([
      _component('base'),
      _component('child', dependsOn: ['base']),
    ]);
    linux.probes['presence:child'] = 2;
    final base = (await service.inventory()).first;
    expect(base.blockingDependents, contains('child'));
    await expectLater(
      service.remove('base'),
      _blocked(ComponentRemovalBlock.dependencyUnknown),
    );
    expect(linux.calls, isNot(contains('remove:base')));
  });

  test(
    'Removal refreshes dependencies after the confirmation inventory',
    () async {
      configure([
        _component('base'),
        _component('child', dependsOn: ['base']),
      ]);
      linux.probes['presence:child'] = 1;
      expect((await service.inventory()).first.canRemove, isTrue);

      linux.probes['presence:child'] = 0;

      await expectLater(
        service.remove('base'),
        _blocked(ComponentRemovalBlock.dependentInstalled),
      );
      expect(linux.calls, isNot(contains('remove:base')));
    },
  );

  test('A running setup blocks component removal', () async {
    configure([_component('tool')]);
    linux.setup = '{"state":"running"}';
    final before = setupToolsChanged.value;
    await expectLater(
      service.remove('tool'),
      _blocked(ComponentRemovalBlock.setupRunning),
    );
    expect(linux.calls, isNot(contains('remove:tool')));
    expect(service.busy.value, isFalse);
    expect(setupToolsChanged.value, before);
  });

  test('Unavailable setup status fails closed', () async {
    configure([_component('tool')]);
    linux.setupFails = true;
    await expectLater(
      service.remove('tool'),
      _blocked(ComponentRemovalBlock.inventoryUnavailable),
    );
    expect(linux.calls, isNot(contains('remove:tool')));
  });

  for (final record in ['invalid', '{}', '{"state":"unrecognized"}']) {
    test('Unrecognized setup record $record fails closed', () async {
      configure([_component('tool')]);
      linux.setup = record;
      await expectLater(
        service.remove('tool'),
        _blocked(ComponentRemovalBlock.inventoryUnavailable),
      );
      expect(linux.calls, isNot(contains('remove:tool')));
    });
  }

  test(
    'An absent native runtime marks components absent without scripts',
    () async {
      configure(setupComponents(AppLocalizationsEn()));
      linux.installed = false;

      final entries = await service.inventory();

      expect(entries, isNotEmpty);
      expect(
        entries.every((entry) => entry.presence == ComponentPresence.absent),
        isTrue,
      );
      await expectLater(
        service.remove('python'),
        _blocked(ComponentRemovalBlock.notInstalled),
      );
      expect(linux.calls, isEmpty);
    },
  );

  test('An unknown component id cannot become a shell command', () async {
    configure([_component('tool')]);
    await expectLater(
      service.remove('unknown; touch /tmp/not-a-command'),
      _blocked(ComponentRemovalBlock.unsupported),
    );
    expect(linux.calls.where(linux.removals.containsKey), isEmpty);
    expect(
      linux.calls.every((script) => !script.contains('not-a-command')),
      isTrue,
    );
  });

  test(
    'Concurrent removal is blocked and busy resets after completion',
    () async {
      configure([_component('tool')]);
      linux.removalGate = Completer<void>();
      final busy = <bool>[];
      service.busy.addListener(() => busy.add(service.busy.value));
      final before = setupToolsChanged.value;
      final first = service.remove('tool');
      await linux.removalStarted.future;

      expect(service.busy.value, isTrue);
      await expectLater(
        service.remove('tool'),
        _blocked(ComponentRemovalBlock.busy),
      );
      expect(
        setupToolsChanged.value,
        before,
        reason: 'removal is still running',
      );
      linux.removalGate!.complete();
      await first;

      expect(service.busy.value, isFalse);
      expect(busy, [true, false]);
      expect(setupToolsChanged.value, before + 1);
      expect(
        linux.calls.where((script) => script == 'remove:tool'),
        hasLength(1),
      );
    },
  );

  test('A nonzero removeScript result is failure and resets busy', () async {
    configure([_component('tool')]);
    linux.removalExit = 1;
    final before = setupToolsChanged.value;
    await expectLater(
      service.remove('tool'),
      _blocked(ComponentRemovalBlock.failed),
    );
    expect(linux.probes['presence:tool'], 0);
    expect(service.busy.value, isFalse);
    expect(setupToolsChanged.value, before + 1);
  });

  test('Thrown script errors expose only a stable failure reason', () async {
    configure([_component('tool')]);
    linux.removalError = 'Authorization: Bearer fake-component-removal-token';
    final before = setupToolsChanged.value;

    await expectLater(
      service.remove('tool'),
      throwsA(
        isA<ComponentRemovalException>()
            .having((error) => error.code, 'code', ComponentRemovalBlock.failed)
            .having(
              (error) => error.toString(),
              'safe description',
              allOf(
                isNot(contains('fake-component-removal-token')),
                isNot(contains('Authorization')),
              ),
            ),
      ),
    );
    expect(service.busy.value, isFalse);
    expect(setupToolsChanged.value, before + 1);
  });

  test('A successful script with surviving files is still failure', () async {
    configure([_component('tool')]);
    linux.leaveInstalled = true;
    await expectLater(
      service.remove('tool'),
      _blocked(ComponentRemovalBlock.failed),
    );
    expect(linux.probes['presence:tool'], 0);
    expect(service.busy.value, isFalse);
  });

  test('Partial removal invalidates inventory after releasing busy', () async {
    final registry = setupComponents(AppLocalizationsEn());
    configure(registry);
    final component = registry.singleWhere((entry) => entry.id == 'aiteam');
    service = ComponentRemovalService(
      linux: linux,
      registry: registry,
      removeTeam: () async {
        linux.probes[component.presenceScript!] = 1;
        throw StateError('Removal failed after deleting files');
      },
    );
    final busyWhenChanged = <bool>[];
    void onChanged() => busyWhenChanged.add(service.busy.value);
    setupToolsChanged.addListener(onChanged);
    addTearDown(() => setupToolsChanged.removeListener(onChanged));

    await expectLater(
      service.remove('aiteam'),
      _blocked(ComponentRemovalBlock.failed),
    );

    expect(busyWhenChanged, [false]);
    final entry = (await service.inventory()).singleWhere(
      (entry) => entry.component.id == 'aiteam',
    );
    expect(entry.presence, ComponentPresence.absent);
  });

  group('P1.4: sizes and other hosts', () {
    SetupComponent sized(String id, String? size) => SetupComponent(
      id: id,
      title: id,
      shortTitle: id,
      checkScript: 'version:$id',
      presenceScript: 'presence:$id',
      installScript: 'install:$id',
      removeScript: 'remove:$id',
      sizeScript: size,
    );

    test(
      'freedBytes: a number is bytes; nothing or zero is no figure',
      () async {
        final registry = [sized('python', 'size:python'), sized('bare', null)];
        final fake = _SizedLinux({'size:python': '12\n25600\n'});
        final removal = ComponentRemovalService(
          linux: fake,
          registry: registry,
        );
        expect(await removal.freedBytes('python'), 25600 * 1024);
        expect(await removal.freedBytes('bare'), isNull);
        expect(await removal.freedBytes('missing'), isNull);
        fake.outputs['size:python'] = '0\n';
        expect(await removal.freedBytes('python'), isNull);
        fake.exit = 1;
        fake.outputs['size:python'] = '25600\n';
        expect(await removal.freedBytes('python'), isNull);
      },
    );

    test('Registered size scripts parse as POSIX shell', () async {
      for (final component in setupComponents(AppLocalizationsEn())) {
        final script = component.sizeScript;
        if (script == null) continue;
        final result = await Process.run('sh', ['-n', '-c', script]);
        expect(result.exitCode, 0, reason: component.id);
      }
    });

    test('a host with its own team remover uses it, not BuiltinTeam', () async {
      final registry = setupComponents(AppLocalizationsEn());
      final fake = _Linux()..register(registry);
      final component = registry.singleWhere((entry) => entry.id == 'aiteam');
      var removed = 0;
      final removal = ComponentRemovalService(
        linux: fake,
        registry: registry,
        removeTeam: () async {
          removed++;
          fake.probes[component.presenceScript!] = 1;
        },
      );
      await removal.remove('aiteam');
      expect(removed, 1);
    });

    test('without a team remover AI Team is not offered', () async {
      final registry = setupComponents(AppLocalizationsEn());
      final fake = _Linux()..register(registry);
      final removal = ComponentRemovalService(linux: fake, registry: registry);
      final entry = (await removal.inventory()).singleWhere(
        (entry) => entry.component.id == 'aiteam',
      );
      expect(entry.block, ComponentRemovalBlock.unsupported);
    });

    test('Termux: the manager removes the team, then the component script '
        'runs in the same Ubuntu', () async {
      final fake = _SizedLinux({});
      final runtime = _TermuxRuntime(
        () async => const TeamRuntimeStatus(phase: TeamRuntimePhase.idle),
      );
      await ComponentRemovalService.termuxTeamRemover(
        runtime: runtime,
        host: fake,
      )();
      expect(runtime.removes, 1);
      expect(fake.ran, [AiTeamScripts.removeScript]);
    });

    test('Termux: a failed manager step stops before any file goes, and '
        'says only that it failed', () async {
      final fake = _SizedLinux({});
      final failed = _TermuxRuntime(
        () async => const TeamRuntimeStatus(phase: TeamRuntimePhase.failed),
      );
      await expectLater(
        ComponentRemovalService.termuxTeamRemover(
          runtime: failed,
          host: fake,
        )(),
        _blocked(ComponentRemovalBlock.failed),
      );
      final broken = _TermuxRuntime(
        () async => throw const TermuxBridgeException(
          'token=synthetic-secret',
          code: 'aiteam_dispatch_failed',
        ),
      );
      await expectLater(
        ComponentRemovalService.termuxTeamRemover(
          runtime: broken,
          host: fake,
        )(),
        _blocked(ComponentRemovalBlock.failed),
      );
      expect(fake.ran, isEmpty);
    });
  });
}

/// Answers each script with a fixed output.
class _SizedLinux extends BuiltinLinux {
  _SizedLinux(this.outputs);

  final Map<String, String> outputs;
  final ran = <String>[];
  int exit = 0;

  @override
  Future<BuiltinLinuxRunResult> run(
    String script, {
    Duration timeout = const Duration(minutes: 2),
    String? input,
  }) async {
    ran.add(script);
    return BuiltinLinuxRunResult(exitCode: exit, output: outputs[script] ?? '');
  }
}
