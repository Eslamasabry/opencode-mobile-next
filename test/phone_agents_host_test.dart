import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/builtin/agents/agent_components.dart';
import 'package:opencode_mobile/builtin/agents/paseo_scripts.dart';
import 'package:opencode_mobile/builtin/agents/phone_agents_host.dart';
import 'package:opencode_mobile/builtin/builtin_linux.dart';
import 'package:opencode_mobile/builtin/setup/setup_contract.dart';
import 'package:opencode_mobile/builtin/setup/preflight.dart';
import 'package:opencode_mobile/builtin/setup/setup_engine.dart'
    show ChannelSetupEngine, expandSelection;
import 'package:opencode_mobile/builtin/setup/claude_scripts.dart';
import 'package:opencode_mobile/domain/agent_catalog.dart';
import 'package:opencode_mobile/domain/agent_auth_probe.dart';
import 'package:opencode_mobile/domain/phone_agent_host.dart';
import 'package:opencode_mobile/l10n/app_localizations_en.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'paseo_acp_pilot_test.dart' show FakePaseoSocket;
import 'support/fake_setup_engine.dart';

const _native = MethodChannel(
  'io.github.eslamasabry.opencode_mobile/builtin_linux',
);
const _storage = MethodChannel('plugins.it_nomads.com/flutter_secure_storage');
void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  late SharedPreferences prefs;
  late BuiltinPhoneAgents host;
  late FakeSetupEngine engine;
  final secrets = <String, String>{};
  final calls = <String>[];
  var abi = 'arm64-v8a';
  var running = false;
  var versionValid = true;
  var refuseDelete = false;
  var setupOwner = 'phone';
  var authOutput = '{"state":"signedOut"}';
  var authBridgePresent = true;
  final authCalls = <Map>[];
  Completer<void>? versionEntered;
  Completer<void>? versionRelease;
  Completer<void>? architectureEntered;
  Completer<void>? architectureRelease;
  final lock = File(PaseoPhoneScripts.packageLockAsset).readAsStringSync();
  setUp(() async {
    SharedPreferences.setMockInitialValues({});
    prefs = await SharedPreferences.getInstance();
    secrets.clear();
    calls.clear();
    abi = 'arm64-v8a';
    running = false;
    versionValid = true;
    refuseDelete = false;
    setupOwner = 'phone';
    authOutput = '{"state":"signedOut"}';
    authBridgePresent = true;
    authCalls.clear();
    versionEntered = null;
    versionRelease = null;
    architectureEntered = null;
    architectureRelease = null;
    final messenger =
        TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
    messenger.setMockMethodCallHandler(_storage, (call) async {
      final args = call.arguments as Map? ?? {};
      switch (call.method) {
        case 'read':
          return secrets[args['key']];
        case 'write':
          secrets[args['key'] as String] = args['value'] as String;
          return null;
        case 'delete':
          secrets.remove(args['key']);
          return null;
        case 'readAll':
          return Map<String, String>.from(secrets);
        case 'containsKey':
          return secrets.containsKey(args['key']);
      }
      return null;
    });
    messenger.setMockMethodCallHandler(_native, (call) async {
      calls.add(call.method);
      switch (call.method) {
        case 'agentAuthProbe':
          if (!authBridgePresent) throw MissingPluginException();
          authCalls.add(call.arguments as Map);
          return jsonDecode(authOutput);
        case 'setupStatus':
          return jsonEncode({
            'params': {
              'phoneAgentOwner': {'profileId': setupOwner, 'agentId': 'claude'},
            },
          });
        case 'agentHostStatus':
          if (architectureRelease != null) {
            architectureEntered!.complete();
            await architectureRelease!.future;
          }
          return {
            'abi': abi,
            'running': running,
            'lastExitCode': 137,
            'lastExitAtMs': 1700000001000,
            'lastStopRequested': false,
            'exitReason': 'memory_or_phantom_kill',
          };
        case 'startAgentHost':
          final args = call.arguments as Map;
          expect(args['port'], 4099);
          expect((args['password'] as String).length, 64);
          final config = jsonDecode(args['config'] as String) as Map;
          expect(config['daemon']['listen'], '127.0.0.1:4099');
          expect(config['daemon']['relay']['enabled'], false);
          expect(config['daemon']['mcp']['injectIntoAgents'], false);
          // Voice/dictation would download local speech models at start.
          expect(config['features']['dictation']['enabled'], false);
          expect(config['features']['voiceMode']['enabled'], false);
          expect(config['features']['webUi']['enabled'], false);
          // Paseo's rotating supervisor log refuses /dev/null and exits.
          expect(config['log']['file']['path'], 'daemon.log');
          expect(config['log']['file']['level'], 'fatal');
          running = true;
          return {'running': true, 'abi': abi};
        case 'agentHostWorkspace':
          return {'shared': true};
        case 'stopAgentHost':
          running = false;
          return {'running': false, 'abi': abi};
        case 'agentHostVersion':
          if (versionRelease != null) {
            versionEntered!.complete();
            await versionRelease!.future;
          }
          return {
            'installed': versionValid,
            'version': versionValid ? (call.arguments as Map)['version'] : null,
          };
        case 'deleteAgentHost':
          if (refuseDelete) {
            throw PlatformException(
              code: 'refused',
              message: 'private native failure',
            );
          }
          running = false;
          return null;
      }
      return null;
    });
    engine = FakeSetupEngine()
      ..afterRun = const SetupProgress(
        state: SetupState.done,
        components: [],
        overall: 1,
      );
    host = BuiltinPhoneAgents(
      profileId: 'phone',
      prefs: prefs,
      loadLock: () async => lock,
      engineFactory: (_, _) => engine,
      socketFactory: (_, _) async => FakePaseoSocket(),
    );
  });
  tearDown(() async {
    await host.dispose();
    final messenger =
        TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
    messenger.setMockMethodCallHandler(_storage, null);
    messenger.setMockMethodCallHandler(_native, null);
  });

  test('BD13 helper status reads only persisted narrow native facts', () async {
    final status = await host.helperStatus();
    expect(status.running, isFalse);
    expect(status.lastExitCode, 137);
    expect(
      status.lastExitAt,
      DateTime.fromMillisecondsSinceEpoch(1700000001000, isUtc: true),
    );
    expect(status.notice, contains('Android may have'));
    running = true;
    final restarted = await host.helperStatus();
    expect(restarted.lastExitCode, 137);
    expect(restarted.notice, isNull);
  });

  test(
    'BA1 probe uses the stable private home and returns only narrow account facts',
    () async {
      final out = await host.probeSignIn('claude');
      expect(out.state, AgentAuthProbeState.signedOut);
      expect(await host.helperRunning(), isFalse);
      running = true;
      expect(await host.helperRunning(), isTrue);
      final call = authCalls.single;
      expect(call['profileId'], 'phone');
      expect(call['agentId'], 'claude');
      expect(call['action'], 'probe');
      expect(call['timeoutSeconds'], 10);
      expect(call['script'], contains("HOME='/home/oc/.oc-profiles/phone'"));
      expect(
        call['script'],
        contains("CLAUDE_CONFIG_DIR='/home/oc/.oc-profiles/phone/claude'"),
      );
      authOutput =
          '{"state":"signedIn","accountDisplayName":"Example account"}';
      expect(
        (await host.probeSignIn('claude')).accountDisplayName,
        'Example account',
      );
      authOutput = '{"loggedIn":true,"unexpected":"raw CLI text"}';
      expect(
        (await host.probeSignIn('claude')).state,
        AgentAuthProbeState.error,
      );
      authOutput = '{"state":"signedOut"}';
      expect(
        (await host.signOut('claude')).state,
        AgentAuthProbeState.signedOut,
      );
      expect(authCalls.last['script'], contains('logout'));
    },
  );

  test(
    'BA1 missing private bridge stays unavailable and never uses setup output',
    () async {
      authBridgePresent = false;
      expect(
        (await host.probeSignIn('fx')).error,
        AgentAuthProbeError.probeUnsupported,
      );
      expect(host.supportsSignOut('fx'), isFalse);
      expect(calls, isNot(contains('run')));
    },
  );

  test(
    'shared engine installs as oc with Python and conditional host Node',
    () {
      expect(
        AgentCatalog.builtIn.byId('claude')!.recipe!.version,
        ClaudeScripts.version,
      );
      expect(
        AgentCatalog.builtIn
            .byId('claude')!
            .artifactFor(AgentArchitecture.arm64)!
            .sha256,
        ClaudeScripts.arm64Sha256,
      );
      final components = phoneAgentComponents(
        AppLocalizationsEn(),
        AgentCatalog.builtIn.byId('claude')!,
        lock,
      );
      final selected = expandSelection(components, {'agent-claude'});
      expect(
        selected.map((c) => c.id),
        containsAll([
          'linux',
          'essentials',
          'python',
          'agent-user',
          'agent-node',
          'agent-paseo',
          'agent-claude',
        ]),
      );
      expect(selected.any((c) => c.id == 'opencode' || c.id == 'start'), false);
      for (final id in ['agent-node', 'agent-paseo', 'agent-claude']) {
        expect(selected.singleWhere((c) => c.id == id).agentUser, true);
      }
      expect(
        selected.singleWhere((c) => c.id == 'agent-user').agentUser,
        false,
      );
    },
  );
  test('the native host launches Paseo 0.9.2 without removed launch flags', () {
    // Emulator 2026-10-04: `paseo start --foreground --listen ...` exits at
    // once with "--listen was removed", so the check's hello never connected.
    final source = File(
      'android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile/PhoneAgentHost.kt',
    ).readAsStringSync();
    final launch = RegExp(
      r'exec /home/oc/\.local/bin/paseo [^\n]*',
    ).firstMatch(source)![0]!;
    expect(launch, contains(r'''paseo daemon run --home "${'$'}HOME/paseo"'''));
    for (final removed in [
      '--foreground',
      '--listen',
      '--port',
      '--relay',
      '--no-relay',
      '--no-web-ui',
      '--web-ui',
      '--no-mcp',
      '--no-inject-mcp',
      '--hostnames',
    ]) {
      expect(launch.split(' '), isNot(contains(removed)), reason: removed);
    }
    expect(source, contains(r'export PASEO_LISTEN=127.0.0.1:$port'));
    expect(PaseoPhoneScripts.version, '0.9.2');
  });

  test(
    'ARM64 gate requires install version daemon and exact Paseo hello',
    () async {
      final result = await host.selfTest('claude');
      expect(result.arm64Qualified, true);
      expect(result.completed, AgentPhoneCheckStep.values);
      expect(engine.runs.single, {'agent-claude'});
      expect(secrets.keys, ['${phoneAgentHostSecretPrefix}phone']);
      expect(prefs.getString('${phoneAgentGatePrefix}phone'), isNotNull);
      expect((await host.inspect('claude')).capabilities.resumeVerified, false);
      expect((await host.inspect('claude')).architectureQualified, true);
    },
  );
  test(
    'x64 pass cannot qualify ARM64 and changed architecture invalidates gate',
    () async {
      abi = 'x86_64';
      final result = await host.selfTest('claude');
      expect(result.passed, true);
      expect(result.arm64Qualified, false);
      abi = 'arm64-v8a';
      expect((await host.inspect('claude')).architectureQualified, false);
    },
  );
  test(
    'legacy fx check still qualifies after owner migration and never after failed retry',
    () async {
      abi = 'x86_64';
      expect((await host.selfTest('claude')).passed, true);
      final ownerGates = prefs.getString('${phoneAgentGatePrefix}phone')!;
      expect((await host.selfTest('fx')).passed, true);
      final allGates =
          jsonDecode(prefs.getString('${phoneAgentGatePrefix}phone')!) as Map;
      await prefs.setString('${phoneAgentGatePrefix}phone', ownerGates);
      await prefs.setString(
        '${phoneAgentGatePrefix}old',
        jsonEncode({'fx': allGates['fx']}),
      );
      await prefs.setString('oc.phoneAgentOwner.phone', 'phone');
      await prefs.setString('oc.phoneAgentOwner.old', 'phone');
      await prefs.setString(
        'oc.profiles',
        jsonEncode([
          ServerProfile(
            id: 'phone',
            name: 'One',
            baseUrl: 'http://127.0.0.1:4097',
          ).toJson(),
          ServerProfile(
            id: 'old',
            name: 'Two',
            baseUrl: 'http://127.0.0.1:4097',
            flavor: ServerFlavor.v2,
          ).toJson(),
        ]),
      );
      final store = ProfileStore(prefs: prefs);
      await store.load();
      expect((await host.inspect('claude')).architectureQualified, true);
      expect((await host.inspect('fx')).architectureQualified, true);
      // Qualification still depends on the current installation and ABI.
      abi = 'arm64-v8a';
      expect((await host.inspect('fx')).architectureQualified, false);
      abi = 'x86_64';
      versionValid = false;
      expect((await host.selfTest('fx')).passed, false);
      await store.load();
      expect((await host.inspect('fx')).architectureQualified, false);
      expect((await host.inspect('claude')).architectureQualified, true);
    },
  );

  test(
    'failed retry clears prior proof and never starts daemon after wrong version',
    () async {
      expect((await host.selfTest('claude')).passed, true);
      versionValid = false;
      calls.clear();
      final result = await host.selfTest('claude');
      expect(result.failure, AgentHostFailure.version);
      expect(calls.contains('startAgentHost'), false);
      expect((await host.inspect('claude')).architectureQualified, false);
    },
  );
  test(
    'stopped self-test cannot start a daemon after a late version check',
    () async {
      versionEntered = Completer<void>();
      versionRelease = Completer<void>();
      final checking = host.selfTest('claude');
      await versionEntered!.future;
      await host.stop();
      versionRelease!.complete();
      final result = await checking;
      expect(result.passed, false);
      expect(result.failure, AgentHostFailure.stale);
      expect(calls.contains('startAgentHost'), false);
      expect(prefs.getString('${phoneAgentGatePrefix}phone'), '{}');
    },
  );
  test('restore and cancel cannot operate another profile setup job', () async {
    await host.install('claude');
    setupOwner = 'other-phone';
    await host.restoreInstall();
    await host.cancelInstall();
    expect(engine.restores, 0);
    expect(engine.cancels, 0);
  });
  test('cancel after handoff still stops an exactly owned setup job', () async {
    await host.install('claude');
    await host.cancelInstall();
    expect(engine.cancels, 1);
  });
  test('cancel during local checks never hands an install to native', () async {
    await host.dispose();
    final linux = _HeldAgentChecks();
    host = BuiltinPhoneAgents(
      profileId: 'phone',
      prefs: prefs,
      loadLock: () async => lock,
      engineFactory: (_, _) => ChannelSetupEngine(
        linux: linux,
        strings: AppLocalizationsEn.new,
        components: (_, _) => const [
          SetupComponent(
            id: 'agent-claude',
            title: 'Claude Code',
            shortTitle: 'Claude Code',
            agentUser: true,
            checkScript: 'false',
            installScript: 'true',
          ),
        ],
      ),
    );
    setupOwner = 'other-phone';
    final installing = host.install('claude');
    await linux.entered.future;
    await host.cancelInstall();
    linux.release.complete();
    await installing;
    expect(host.setupProgress.phase, AgentSetupPhase.interrupted);
    expect(linux.starts, 0);
    expect(linux.cancels, 0);
  });
  test('cancel retires an install awaiting architecture', () async {
    architectureEntered = Completer<void>();
    architectureRelease = Completer<void>();
    final installing = host.install('claude');
    await architectureEntered!.future;
    await host.cancelInstall();
    architectureRelease!.complete();
    await expectLater(
      installing,
      throwsA(
        isA<AgentHostException>().having(
          (error) => error.reason,
          'reason',
          AgentHostFailure.stale,
        ),
      ),
    );
    expect(engine.runs, isEmpty);
    expect(prefs.getString('${phoneAgentInstallPrefix}phone'), isNull);
    architectureRelease = null;
    await host.install('claude');
    expect(engine.runs, [
      {'agent-claude'},
    ]);
  });
  test('cancel retires an install awaiting preparation', () async {
    await host.dispose();
    final entered = Completer<void>();
    final release = Completer<void>();
    host = BuiltinPhoneAgents(
      profileId: 'phone',
      prefs: prefs,
      loadLock: () async {
        entered.complete();
        await release.future;
        return lock;
      },
      engineFactory: (_, _) => engine,
    );
    final installing = host.install('claude');
    await entered.future;
    await host.cancelInstall();
    release.complete();
    await expectLater(
      installing,
      throwsA(
        isA<AgentHostException>().having(
          (error) => error.reason,
          'reason',
          AgentHostFailure.stale,
        ),
      ),
    );
    expect(engine.runs, isEmpty);
    expect(prefs.getString('${phoneAgentInstallPrefix}phone'), isNull);
  });
  for (final sample in [
    (id: 'codex', expectedBytes: 465514826),
    (id: 'goose', expectedBytes: 460456104),
    (id: 'omp-acp', expectedBytes: 628181952),
    (id: 'fx', expectedBytes: 300000000),
  ]) {
    test(
      'native storage guard includes ${sample.id} catalog install peak',
      () async {
        await host.dispose();
        final captured = <Map<String, Object?>>[];
        Map<String, Object?>? job;
        TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
            .setMockMethodCallHandler(_native, (call) async {
              switch (call.method) {
                case 'agentHostStatus':
                  return {'abi': 'x86_64', 'running': false};
                case 'status':
                  return {'installed': true, 'phase': 'ready'};
                case 'setupStatus':
                  return job == null ? null : jsonEncode(job);
                case 'run':
                  return {'exitCode': 0, 'output': ''};
                case 'startSetup':
                  final args = call.arguments as Map;
                  captured.addAll([
                    for (final spec in args['components'] as List)
                      Map<String, Object?>.from(spec as Map),
                  ]);
                  job = {
                    'jobId': args['jobId'],
                    'state': 'done',
                    'order': [for (final spec in captured) spec['id']],
                    'components': {
                      for (final spec in captured)
                        spec['id'] as String: {'state': 'done'},
                    },
                  };
                  return null;
              }
              return null;
            });
        host = BuiltinPhoneAgents(
          profileId: 'phone',
          prefs: prefs,
          loadLock: () async => lock,
        );
        await host.install(sample.id);
        final target = captured.singleWhere(
          (spec) => spec['id'] == 'agent-${sample.id}',
        );
        expect(
          (target['data'] as Map)['requiredFreeBytes'],
          '${sample.expectedBytes}',
        );
        final common = phoneAgentComponents(
          AppLocalizationsEn(),
          AgentCatalog.builtIn.byId(sample.id)!,
          lock,
        ).where((component) => component.id != 'agent-${sample.id}');
        for (final component in common) {
          final spec = captured.singleWhere(
            (spec) => spec['id'] == component.id,
          );
          expect(
            (spec['data'] as Map)['requiredFreeBytes'],
            '${requiredSetupFreeBytes(component.downloadBytes ?? 0)}',
            reason:
                'Shared dependency ${component.id} keeps its own storage policy',
          );
        }
        expect(host.setupProgress.phase, AgentSetupPhase.done);
      },
    );
  }
  test(
    'running installation requires continue instead of a second install',
    () async {
      engine.afterRun = const SetupProgress(
        state: SetupState.running,
        components: [],
        overall: .1,
      );
      await host.install('claude');
      await expectLater(
        host.install('claude'),
        throwsA(
          isA<AgentHostException>().having(
            (error) => error.reason,
            'reason',
            AgentHostFailure.busy,
          ),
        ),
      );
      expect(engine.runs, hasLength(1));
    },
  );
  test('background loss exposes resume and reuses the Keystore slot', () async {
    await host.selfTest('claude');
    final previous = secrets['${phoneAgentHostSecretPrefix}phone'];
    running = false;
    expect((await host.inspect('claude')).stoppedInBackground, true);
    await host.start();
    expect(secrets['${phoneAgentHostSecretPrefix}phone'] == previous, true);
    expect((await host.inspect('claude')).hostAvailable, true);
  });
  test(
    'restore resumes same persisted component selection with safe progress',
    () async {
      await host.install('claude');
      await host.restoreInstall();
      expect(engine.restores, 1);
      engine.emit(
        const SetupProgress(
          state: SetupState.failed,
          components: [],
          overall: .3,
          error: 'private failure',
          logTail: 'private transcript',
        ),
      );
      expect(host.setupProgress.phase, AgentSetupPhase.failed);
      expect(host.setupProgress.fraction, .3);
      expect(host.setupProgress.toString().contains('private'), false);
    },
  );
  test(
    'profile removal drains native account home before deleting password',
    () async {
      await host.selfTest('claude');
      final store = ProfileStore(prefs: prefs);
      await store.upsert(
        ServerProfile(
          id: 'phone',
          name: 'Phone',
          baseUrl: 'http://127.0.0.1:4097',
        ),
      );
      calls.clear();
      await store.remove('phone');
      expect(calls, contains('deleteAgentHost'));
      expect(secrets.containsKey('${phoneAgentHostSecretPrefix}phone'), false);
      expect(prefs.containsKey('${phoneAgentGatePrefix}phone'), false);
      expect(prefs.containsKey('${phoneAgentInstallPrefix}phone'), false);
    },
  );
  test(
    'failed native drain preserves profile and secure slot for retry',
    () async {
      await host.selfTest('claude');
      final store = ProfileStore(prefs: prefs);
      await store.upsert(
        ServerProfile(
          id: 'phone',
          name: 'Phone',
          baseUrl: 'http://127.0.0.1:4097',
        ),
      );
      refuseDelete = true;
      await expectLater(
        store.remove('phone'),
        throwsA(isA<AgentHostException>()),
      );
      expect(store.profiles.single.id, 'phone');
      expect(secrets.containsKey('${phoneAgentHostSecretPrefix}phone'), true);
      expect(prefs.containsKey('${phoneAgentInstallPrefix}phone'), true);
    },
  );
  test(
    'saved-sign-in reset includes orphan agent slots and preserves unrelated secrets',
    () async {
      secrets['${phoneAgentHostSecretPrefix}orphan'] = List.filled(
        64,
        'a',
      ).join();
      secrets['unrelated'] = 'fixture';
      await ProfileStore(prefs: prefs).resetSavedSignIns();
      expect(calls, contains('deleteAgentHost'));
      expect(secrets.keys, ['unrelated']);
    },
  );
}

class _HeldAgentChecks extends BuiltinLinux {
  final entered = Completer<void>();
  final release = Completer<void>();
  var starts = 0;
  var cancels = 0;

  @override
  Future<String?> setupStatus() async => null;

  @override
  Future<BuiltinLinuxStatus> status() async => const BuiltinLinuxStatus(
    installed: true,
    phase: BuiltinLinuxPhase.ready,
    serverRunning: false,
  );

  @override
  Future<BuiltinLinuxRunResult> runAgentSetupCheck(
    String script, {
    Duration timeout = const Duration(minutes: 2),
  }) async {
    entered.complete();
    await release.future;
    return const BuiltinLinuxRunResult(exitCode: 0, output: '');
  }

  @override
  Future<void> startSetup({
    required String jobId,
    required List<Map<String, Object?>> components,
    required Map<String, Map<String, String>> params,
    required Map<String, String> texts,
  }) async {
    starts++;
    throw StateError('Unexpected native handoff');
  }

  @override
  Future<void> cancelSetup() async => cancels++;
}
