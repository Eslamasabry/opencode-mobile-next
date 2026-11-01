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
import 'package:opencode_mobile/paseo/transport.dart' show PaseoFailure;
import 'package:opencode_mobile/state/profiles.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'paseo_acp_pilot_test.dart' show FakePaseoSocket;
import 'paseo_gateway_test.dart' show agentJson;
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
  String? invalidExecutable;
  Object? payloadPresent;
  Object? foundVersion;
  var refuseDelete = false;
  var setupOwner = 'phone';
  var authOutput = '{"state":"signedIn"}';
  var authBridgePresent = true;
  var previousAvailable = false;
  final authCalls = <Map>[];
  // Android's private auth bridge runs one check per profile; a second one
  // meanwhile is refused as hostUnavailable (PhoneAgentAuthProbe.execute).
  var authInFlight = 0;
  var authPeak = 0;
  Completer<void>? authRelease;
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
    invalidExecutable = null;
    payloadPresent = null;
    foundVersion = null;
    refuseDelete = false;
    setupOwner = 'phone';
    authOutput = '{"state":"signedIn"}';
    authBridgePresent = true;
    previousAvailable = false;
    authCalls.clear();
    authInFlight = 0;
    authPeak = 0;
    authRelease = null;
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
        case 'run':
          return {'exitCode': previousAvailable ? 0 : 1, 'output': ''};
        case 'agentAuthProbe':
          if (!authBridgePresent) throw MissingPluginException();
          authCalls.add(call.arguments as Map);
          if (authInFlight > 0) {
            return {'state': 'error', 'error': 'hostUnavailable'};
          }
          authInFlight++;
          if (authInFlight > authPeak) authPeak = authInFlight;
          try {
            await authRelease?.future;
            return jsonDecode(authOutput);
          } finally {
            authInFlight--;
          }
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
            'helperVersion': PaseoPhoneScripts.version,
            'lastExitCode': 137,
            'lastExitAtMs': 1700000001000,
            'lastStopRequested': false,
            'exitReason': 'memory_or_phantom_kill',
          };
        case 'agentHostPrepareUpdate':
          return {'ready': true};
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
          final valid =
              versionValid &&
              (call.arguments as Map)['executable'] != invalidExecutable;
          return {
            'installed': valid,
            'payloadPresent': payloadPresent,
            'foundVersion': foundVersion,
            'version': valid ? (call.arguments as Map)['version'] : null,
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
      socketFactory: (_, _) async => _idleSocket(),
    );
  });
  tearDown(() async {
    await host.dispose();
    final messenger =
        TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
    messenger.setMockMethodCallHandler(_storage, null);
    messenger.setMockMethodCallHandler(_native, null);
  });

  test('a refused helper start names the phone\'s code under Details, '
      'never its message', () async {
    final messenger =
        TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
    var code = 'agent_host_exited';
    messenger.setMockMethodCallHandler(_native, (call) async {
      if (call.method != 'startAgentHost') return null;
      throw PlatformException(code: code, message: 'private native text');
    });
    final exited = await host.start().then<Object?>(
      (_) => null,
      onError: (Object error) => error,
    );
    expect(exited, isA<AgentHostException>());
    expect(
      (exited! as AgentHostException).reason,
      AgentHostFailure.unavailable,
    );
    // Several native refusals share "unavailable"; only the code tells them
    // apart once logcat is silent.
    expect('$exited', 'AgentHostException(unavailable: agent_host_exited)');
    expect('$exited', isNot(contains('private native text')));
    code = 'Not a code: /data/private';
    final unsafe = await host.start().then<Object?>(
      (_) => null,
      onError: (Object error) => error,
    );
    expect('$unsafe', 'AgentHostException(unavailable)');
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
    'BA16 failed version retains only strict native payload inventory',
    () async {
      versionValid = false;
      payloadPresent = true;
      final runtime = await host.inspect('fx');
      expect(runtime.installed, isFalse);
      expect(runtime.payloadPresent, isTrue);
      for (final malformed in [null, false, 'true', 1, <String>[]]) {
        payloadPresent = malformed;
        expect((await host.inspect('fx')).payloadPresent, isFalse);
      }
    },
  );

  test(
    'an older Claude Code that runs is installed, not "Not installed"',
    () async {
      // The pin moved on after this copy was installed (or it came from the
      // setup component): the program runs, only the version differs.
      versionValid = false;
      foundVersion = '2.1.283';
      expect((await host.inspect('claude')).installed, isTrue);
      foundVersion = null;
      expect((await host.inspect('claude')).installed, isFalse);
      foundVersion = 42;
      expect((await host.inspect('claude')).installed, isFalse);
    },
  );

  test(
    'BA1 probe uses the stable private home and returns only narrow account facts',
    () async {
      authOutput = '{"state":"signedOut"}';
      final out = await host.probeSignIn('claude');
      expect(out.state, AgentAuthProbeState.signedOut);
      expect(await host.helperRunning(), isFalse);
      running = true;
      expect(await host.helperRunning(), isTrue);
      final call = authCalls.single;
      expect(call['profileId'], 'phone');
      expect(call['agentId'], 'claude');
      expect(call['action'], 'probe');
      expect(call['timeoutSeconds'], 65);
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
    'status checks asked at once take turns and never meet Android refusing a second',
    () async {
      authRelease = Completer<void>();
      // The agent list, the sign-in step and the phone check ask together.
      final list = host.probeSignIn('claude');
      final step = host.probeSignIn('claude');
      final other = host.probeSignIn('fx');
      await pumpEventQueue();
      authRelease!.complete();
      final results = await Future.wait([list, step, other]);
      expect(
        results.map((result) => result.state),
        everyElement(AgentAuthProbeState.signedIn),
      );
      expect(authPeak, 1);
      // Two asks for the same agent shared one check.
      expect(authCalls.map((call) => call['agentId']), ['claude', 'fx']);
      // After it ends, a new ask runs a fresh check.
      await host.probeSignIn('claude');
      expect(authCalls, hasLength(3));
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
  test('the native host launches Paseo 0.11.2 without removed launch flags', () {
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
    expect(PaseoPhoneScripts.version, '0.11.2');
  });

  test(
    'old Paseo hello explains helper update instead of agent silence',
    () async {
      await host.dispose();
      host = BuiltinPhoneAgents(
        profileId: 'phone',
        prefs: prefs,
        loadLock: () async => lock,
        engineFactory: (_, _) => engine,
        socketFactory: (_, _) async => _OldPaseoSocket(),
      );
      final result = await host.selfTest('claude');
      expect(result.passed, false);
      expect(result.failure?.name, 'helperVersion');
    },
  );

  test(
    'signed-out Claude asks for sign in and retains installation proof',
    () async {
      authOutput = '{"state":"signedOut"}';
      final result = await host.selfTest('claude');
      expect(result.failure, AgentHostFailure.needsSignIn);
      expect(result.completed, isNot(contains(AgentPhoneCheckStep.hello)));
      expect((await host.inspect('claude')).architectureQualified, true);
      expect(result.previousVersion, isNull);
    },
  );

  test('auth timeout is distinct from missing sign in', () async {
    authOutput = '{"state":"error","error":"timedOut"}';
    final result = await host.selfTest('claude');
    expect(result.failure, AgentHostFailure.timeout);
    expect((await host.inspect('claude')).architectureQualified, true);
  });

  test(
    'unchanged installed pair keeps proof and changed pin explains check',
    () async {
      expect((await host.selfTest('claude')).passed, true);
      expect((await host.inspect('claude')).checkRequiredReason, isNull);
      final gates =
          jsonDecode(prefs.getString('${phoneAgentGatePrefix}phone')!) as Map;
      final fingerprint =
          jsonDecode(gates['claude']['fingerprint'] as String) as List;
      fingerprint[1] = '2.1.283';
      gates['claude']['fingerprint'] = jsonEncode(fingerprint);
      await prefs.setString('${phoneAgentGatePrefix}phone', jsonEncode(gates));
      // The older tested version is still installed: it keeps running work
      // while the update to the pin is owed.
      final older = await host.inspect('claude');
      expect(older.behindPin, true);
      expect(older.architectureQualified, true);
      // With nothing to run on, the person is asked for the check.
      versionValid = false;
      final runtime = await host.inspect('claude');
      versionValid = true;
      expect(runtime.architectureQualified, false);
      expect(runtime.checkRequiredReason, 'agentVersion');
    },
  );

  test(
    'busy helper refuses installation before replacing shared payloads',
    () async {
      running = true;
      await host.dispose();
      host = BuiltinPhoneAgents(
        profileId: 'phone',
        prefs: prefs,
        loadLock: () async => lock,
        engineFactory: (_, _) => engine,
        socketFactory: (_, _) async =>
            FakePaseoSocket()
              ..handlers['fetch_agents_request'] = (_) => (
                'fetch_agents_response',
                {
                  'entries': [
                    {
                      'agent': {'status': 'running', 'pendingPermissions': []},
                    },
                  ],
                  'pageInfo': {'hasMore': false},
                },
              ),
      );
      final result = await host.selfTest('claude');
      expect(result.failure, AgentHostFailure.busy);
      expect(engine.runs, isEmpty);
      expect(calls, isNot(contains('stopAgentHost')));
    },
  );

  // Field report (4363): the Paseo helper exited with 137 around the automatic
  // agent update. A phone check used to restart a healthy helper at the pinned
  // version whenever the native side had no version recorded for it (it was
  // started without one), SIGTERM then SIGKILL, dropping live conversations.
  test('a new agent version does not restart a helper that already runs the '
      'pinned Paseo', () async {
    running = true;
    final result = await host.selfTest('claude');
    expect(result.passed, true, reason: '${result.failure}');
    expect(calls, isNot(contains('startAgentHost')));
    expect(calls, isNot(contains('stopAgentHost')));
    expect(engine.runs, isNotEmpty, reason: 'the agent itself is installed');
  });

  test(
    'idle parent with running provider child prevents helper update',
    () async {
      running = true;
      await host.dispose();
      host = BuiltinPhoneAgents(
        profileId: 'phone',
        prefs: prefs,
        loadLock: () async => lock,
        engineFactory: (_, _) => engine,
        socketFactory: (_, _) async {
          final socket = FakePaseoSocket();
          socket.handlers['fetch_agents_request'] = (_) => (
            'fetch_agents_response',
            {
              'entries': [
                {'agent': agentJson('parent')},
              ],
              'pageInfo': {'hasMore': false},
            },
          );
          socket.handlers['agent.provider_subagents.list.request'] = (_) => (
            'agent.provider_subagents.list.response',
            {
              'parentAgentId': 'parent',
              'subagents': [
                {'id': 'child', 'parentAgentId': 'parent', 'status': 'running'},
              ],
              'error': null,
            },
          );
          return socket;
        },
      );
      final result = await host.selfTest('claude');
      expect(result.failure, AgentHostFailure.busy);
      expect(engine.runs, isEmpty);
    },
  );

  test(
    'verified retained rollback is guarded and survives later checks',
    () async {
      previousAvailable = true;
      final result = await host.usePreviousVersion('claude');
      expect(result.passed, true);
      expect(engine.runs.single, {'agent-paseo'});
      expect(
        calls.indexOf('agentHostPrepareUpdate'),
        lessThan(calls.indexOf('stopAgentHost')),
      );
      expect((await host.inspect('claude')).architectureQualified, true);
      expect((await host.selfTest('claude')).passed, true);
      expect(engine.runs.last, {'agent-paseo'});
    },
  );

  test(
    'changed retained rollback payload invalidates persisted proof',
    () async {
      previousAvailable = true;
      expect((await host.usePreviousVersion('claude')).passed, true);
      previousAvailable = false;
      final installs = engine.runs.length;
      final result = await host.selfTest('claude');
      expect(result.failure, AgentHostFailure.version);
      expect(engine.runs.length, installs);
      expect((await host.inspect('claude')).architectureQualified, false);
    },
  );

  test(
    'absent previous version never changes the active installation',
    () async {
      final result = await host.usePreviousVersion('claude');
      expect(result.failure, AgentHostFailure.version);
      expect(calls, isNot(contains('stopAgentHost')));
      expect(engine.runs, isEmpty);
    },
  );

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
      invalidExecutable = 'fx';
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
  test('Paseo config launches Kilo and Cline as custom ACP providers', () {
    final providers =
        (jsonDecode(host.configuration()) as Map)['agents']['providers'] as Map;
    expect(providers['kilo'], {
      'extends': 'acp',
      'label': 'Kilo',
      'command': ['/home/oc/.local/bin/kilo', 'acp'],
    });
    expect(providers['cline'], {
      'extends': 'acp',
      'label': 'Cline',
      'command': ['/home/oc/.local/bin/cline', '--acp'],
    });
    for (final id in providers.keys) {
      expect(id, matches(RegExp(r'^[a-z][a-z0-9-]*$')));
    }
  });
  for (final sample in [
    (id: 'codex', expectedBytes: 508219890),
    (id: 'goose', expectedBytes: 468715890),
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

  // The owner's phone (APK 4360): starting agent work while the automatic
  // update ran was refused with "Agent setup is in progress ... (phone check
  // running)". The person's work wins now.
  group('automatic update yields to the person\'s work', () {
    late _HoldEngine hold;
    Future<void> reopen() async {
      await host.dispose();
      hold = _HoldEngine();
      host = BuiltinPhoneAgents(
        profileId: 'phone',
        prefs: prefs,
        loadLock: () async => lock,
        engineFactory: (_, _) => hold,
        socketFactory: (_, _) async => _idleSocket(),
      );
    }

    Future<Object?> startWork() async {
      final gateway = host.newGatewaySync('/root/projects/app');
      try {
        await gateway.promptAsync('ses_one', text: 'hello');
        return null;
      } catch (error) {
        return error;
      }
    }

    test('while it downloads, work sets it aside and runs; the update is '
        'marked to retry, not failed', () async {
      await reopen();
      final update = host.autoUpdate('claude');
      await hold.started.future;
      expect(host.autoUpdateCanYield, isTrue);
      final error = await startWork();
      expect('$error', isNot(contains('refused new agent work')));
      expect('$error', isNot(contains('Agent setup is in progress')));
      expect(hold.cancels, 1, reason: 'the download was set aside');
      final result = await update;
      expect(result.passed, isFalse);
      expect(host.lastAutoUpdateYielded, isTrue);
      expect(host.autoUpdateCanYield, isFalse);
    });

    test('with every byte in, or nothing known, the update is not set aside '
        '(it may be switching versions)', () async {
      await reopen();
      final update = host.autoUpdate('claude');
      await hold.started.future;
      expect(host.autoUpdateCanYield, isTrue);
      hold.emit(
        const SetupProgress(
          state: SetupState.running,
          current: 'agent-claude',
          overall: 0.9,
          components: [
            ComponentProgress(
              id: 'agent-claude',
              state: ComponentState.running,
              bytesDone: 100,
              bytesTotal: 100,
            ),
          ],
        ),
      );
      expect(host.autoUpdateCanYield, isFalse);
      hold.emit(
        const SetupProgress(
          state: SetupState.running,
          current: 'agent-claude',
          overall: 0.95,
          components: [
            ComponentProgress(
              id: 'agent-claude',
              state: ComponentState.running,
            ),
          ],
        ),
      );
      expect(host.autoUpdateCanYield, isFalse);
      await hold.cancel();
      await update;
    });

    test('once it switches versions, work waits for it instead of being '
        'refused', () async {
      await reopen();
      engine = hold;
      hold.holdOpen = false;
      hold.afterRun = const SetupProgress(
        state: SetupState.done,
        components: [],
        overall: 1,
      );
      versionEntered = Completer<void>();
      versionRelease = Completer<void>();
      final update = host.autoUpdate('claude');
      await versionEntered!.future;
      expect(host.autoUpdateCanYield, isFalse);
      var workDone = false;
      final work = startWork().then((error) {
        workDone = true;
        return error;
      });
      await Future<void>.delayed(const Duration(milliseconds: 400));
      expect(workDone, isFalse, reason: 'it waits, it is not refused');
      versionRelease!.complete();
      expect((await update).passed, isTrue);
      final error = await work;
      expect('$error', isNot(contains('refused new agent work')));
      versionEntered = null;
      versionRelease = null;
    });

    test('a person\'s own check still blocks work, with the same plain '
        'reason kept for Details', () async {
      await reopen();
      hold.holdOpen = false;
      hold.afterRun = const SetupProgress(
        state: SetupState.done,
        components: [],
        overall: 1,
      );
      versionEntered = Completer<void>();
      versionRelease = Completer<void>();
      final check = host.selfTest('claude');
      await versionEntered!.future;
      final error = await startWork();
      expect(error, isA<PaseoFailure>());
      expect((error! as PaseoFailure).reason, contains('phone check running'));
      versionRelease!.complete();
      await check;
      versionEntered = null;
      versionRelease = null;
    });

    test('an agent behind the pin that is still installed keeps running and '
        'is not asked for a check; with nothing to run on it is', () async {
      Future<void> oldGate() async {
        final agent = AgentCatalog.builtIn.byId('claude')!;
        await prefs.setString(
          '${phoneAgentGatePrefix}phone',
          jsonEncode({
            'claude': {
              'fingerprint': jsonEncode([
                'claude',
                '2.1.294',
                agent.artifactFor(AgentArchitecture.arm64)!.sha256,
                PaseoPhoneScripts.version,
                PaseoPhoneScripts.packageLockSha256,
                PaseoPhoneScripts.nodeVersion,
              ]),
              'architecture': 'arm64',
              'activeVersion': '2.1.294',
            },
          }),
        );
      }

      await oldGate();
      final behind = await host.inspect('claude');
      expect(behind.behindPin, isTrue);
      expect(behind.architectureQualified, isTrue);
      expect(behind.checkRequiredReason, isNull);
      versionValid = false;
      final gone = await host.inspect('claude');
      expect(gone.behindPin, isFalse);
      expect(gone.architectureQualified, isFalse);
      expect(gone.checkRequiredReason, 'agentVersion');
      versionValid = true;
    });

    test(
      'an automatic update keeps the old proof until the new version '
      'passes, so a set-aside one leaves the old version qualified',
      () async {
        await reopen();
        final before = {
          'claude': {
            'fingerprint': jsonEncode([
              'claude',
              '2.1.294',
              'x',
              'p',
              'l',
              'n',
            ]),
            'architecture': 'arm64',
            'activeVersion': '2.1.294',
          },
        };
        await prefs.setString(
          '${phoneAgentGatePrefix}phone',
          jsonEncode(before),
        );
        final update = host.autoUpdate('claude');
        await hold.started.future;
        await startWork();
        await update;
        expect(
          jsonDecode(prefs.getString('${phoneAgentGatePrefix}phone')!),
          before,
        );
      },
    );
  });
}

/// An install that is still downloading until it is cancelled or released.
class _HoldEngine extends FakeSetupEngine {
  final started = Completer<void>();
  bool holdOpen = true;
  final _release = Completer<void>();

  @override
  Future<void> run(
    Set<String> ids, {
    Map<String, Map<String, String>> params = const {},
  }) async {
    runs.add(ids);
    runParams.add(params);
    if (!holdOpen) {
      if (afterRun != null) emit(afterRun!);
      return;
    }
    emit(
      SetupProgress(
        state: SetupState.running,
        current: ids.first,
        overall: 0.2,
        components: [
          ComponentProgress(
            id: ids.first,
            state: ComponentState.running,
            bytesDone: 40,
            bytesTotal: 100,
          ),
        ],
      ),
    );
    if (!started.isCompleted) started.complete();
    await _release.future;
  }

  @override
  Future<void> cancel() async {
    cancels++;
    emit(
      const SetupProgress(
        state: SetupState.cancelled,
        components: [],
        overall: 0,
      ),
    );
    if (!_release.isCompleted) _release.complete();
  }
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

class _OldPaseoSocket extends FakePaseoSocket {
  @override
  void send(String message) {
    final envelope = jsonDecode(message) as Map<String, dynamic>;
    if (envelope['type'] == 'hello') {
      push('status', {'status': 'server_info', 'version': '0.9.2'});
      return;
    }
    super.send(message);
  }
}

FakePaseoSocket _idleSocket() => FakePaseoSocket()
  ..handlers['fetch_agents_request'] = (_) => (
    'fetch_agents_response',
    {
      'entries': [],
      'pageInfo': {'hasMore': false},
    },
  );
