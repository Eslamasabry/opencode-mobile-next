// FactsBook on this phone (F6): the controller's states and steps against a
// fake built-in Linux host, and the page in plain words. The pairing code and
// the passphrase never reach a failure, a log line or the screen.
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:opencode_mobile/state/factsbook/factsbook_store.dart';
import 'package:opencode_mobile/ui/screens/factsbook/capture_screen.dart';
import 'package:opencode_mobile/ui/screens/factsbook/connect_screen.dart';
import 'package:opencode_mobile/ui/screens/factsbook/memory_section.dart';
import 'package:opencode_mobile/builtin/factsbook_node_host.dart';
import 'package:opencode_mobile/domain/factsbook/factsbook_types.dart';
import 'package:opencode_mobile/domain/factsbook/fake_factsbook_gateway.dart';
import 'package:opencode_mobile/state/factsbook/factsbook_phone_node.dart';
import 'package:opencode_mobile/state/factsbook/factsbook_session.dart';
import 'package:opencode_mobile/ui/screens/factsbook/phone_node_screen.dart';

import 'factsbook_test_support.dart';

const _secret = 'SECRETCODE0123456789abcdef';

class FakeNodeHost implements FactsBookNodeHost {
  FakeNodeHost({this.supported = true, NodeHostFacts? facts})
    : facts = facts ?? const NodeHostFacts();

  @override
  bool supported;
  @override
  String get address => '127.0.0.1:8787';
  NodeHostFacts facts;
  final calls = <String>[];
  NodeHostException? installFails;
  NodeHostException? vaultFails;
  NodeHostException? startFails;
  NodeHostException? pairFails;
  String logText = 'node started';
  var running = false;

  NodeHostFacts _facts() => NodeHostFacts(
    linuxReady: facts.linuxReady,
    binaryInstalled: facts.binaryInstalled,
    dataExists: facts.dataExists,
    serviceRunning: running,
    binaryAvailable: facts.binaryAvailable,
  );

  @override
  Future<NodeHostFacts> inspect() async => _facts();

  @override
  Future<void> installBinary() async {
    calls.add('install');
    if (installFails != null) throw installFails!;
    facts = NodeHostFacts(
      linuxReady: true,
      binaryInstalled: true,
      dataExists: facts.dataExists,
      binaryAvailable: true,
    );
  }

  @override
  Future<void> createVault() async {
    calls.add('vault');
    if (vaultFails != null) throw vaultFails!;
    facts = NodeHostFacts(
      linuxReady: true,
      binaryInstalled: true,
      dataExists: true,
      binaryAvailable: true,
    );
  }

  @override
  Future<void> startNode() async {
    calls.add('start');
    if (startFails != null) throw startFails!;
    running = true;
  }

  @override
  Future<void> stopNode() async {
    calls.add('stop');
    running = false;
  }

  @override
  Future<NodePairing> pairingCode({required String ownerKeyHex}) async {
    calls.add('pair-code');
    if (pairFails != null) throw pairFails!;
    return const NodePairing(code: _secret, ownerDevice: 'dev_1');
  }

  @override
  Future<String> log() async => logText;

  @override
  Future<void> removeData() async {
    calls.add('remove');
    running = false;
    facts = const NodeHostFacts(
      linuxReady: true,
      binaryInstalled: true,
      binaryAvailable: true,
    );
  }
}

NodeHostFacts get _ready =>
    const NodeHostFacts(linuxReady: true, binaryAvailable: true);

void main() {
  late FactsBookSession session;
  final paired = <(String, String, String?)>[];
  FactsBookPhoneNode make(FakeNodeHost host, {bool pairs = true}) =>
      FactsBookPhoneNode(
        session,
        host: host,
        probe: (_) async => host.running,
        probeAttempts: 2,
        probeDelay: Duration.zero,
        pairWith: ({required address, required code, ownerDeviceId}) async {
          paired.add((address, code, ownerDeviceId));
          if (!pairs) throw const FactsBookException('forbidden_scope');
        },
      );

  setUp(() {
    session = FactsBookSession.fixed(FakeFactsBookGateway.sample());
    paired.clear();
  });

  group('states', () {
    test('a runner without the built-in Linux is unsupported', () async {
      final node = make(FakeNodeHost(supported: false));
      await node.refresh();
      expect(node.state, PhoneNodeState.unsupported);
    });

    test('the Linux not set up, no build, ready to install', () async {
      var node = make(FakeNodeHost());
      await node.refresh();
      expect(node.state, PhoneNodeState.linuxNotSetUp);
      node = make(FakeNodeHost(facts: const NodeHostFacts(linuxReady: true)));
      await node.refresh();
      expect(node.state, PhoneNodeState.noBuild);
      node = make(FakeNodeHost(facts: _ready));
      await node.refresh();
      expect(node.state, PhoneNodeState.notInstalled);
    });

    test(
      'a vault that exists is stopped; a running service is running',
      () async {
        final host = FakeNodeHost(
          facts: const NodeHostFacts(
            linuxReady: true,
            binaryInstalled: true,
            dataExists: true,
          ),
        );
        final node = make(host);
        await node.refresh();
        expect(node.state, PhoneNodeState.stopped);
        host.running = true;
        await node.refresh();
        expect(node.state, PhoneNodeState.running);
      },
    );
  });

  group('run memory on this phone', () {
    test(
      'installs, makes the vault, starts, then pairs with the owner key',
      () async {
        final host = FakeNodeHost(facts: _ready);
        final node = make(host);
        final steps = <PhoneNodeStep>[];
        node.addListener(() {
          if (node.state == PhoneNodeState.installing &&
              (steps.isEmpty || steps.last != node.step)) {
            steps.add(node.step);
          }
        });
        await node.runHere();
        expect(host.calls, ['install', 'vault', 'start', 'pair-code']);
        expect(steps, [
          PhoneNodeStep.copying,
          PhoneNodeStep.preparing,
          PhoneNodeStep.starting,
          PhoneNodeStep.pairing,
        ]);
        expect(paired.single, ('127.0.0.1:8787', _secret, 'dev_1'));
        expect(node.state, PhoneNodeState.running);
        expect(node.failure, isNull);
      },
    );

    test('the program already installed is not installed again', () async {
      final host = FakeNodeHost(
        facts: const NodeHostFacts(
          linuxReady: true,
          binaryInstalled: true,
          binaryAvailable: true,
        ),
      );
      await make(host).runHere();
      expect(host.calls, ['vault', 'start', 'pair-code']);
    });

    test('no build is said as no build, not as a failure', () async {
      final host = FakeNodeHost(facts: const NodeHostFacts(linuxReady: true))
        ..installFails = const NodeHostException(NodeHostFailure.noBinary);
      final node = make(host);
      await node.runHere();
      expect(node.state, PhoneNodeState.noBuild);
    });

    test(
      'a build that will not run fails as install, with the reason apart',
      () async {
        final host = FakeNodeHost(facts: _ready)
          ..installFails = const NodeHostException(
            NodeHostFailure.badBinary,
            'The node build did not run (sha256 abc).',
          );
        final node = make(host);
        await node.runHere();
        expect(node.state, PhoneNodeState.failed);
        expect(node.problem, PhoneNodeProblem.install);
        expect(node.failure, contains('sha256'));
        expect(host.calls, ['install']);
      },
    );

    test('a node that never answers fails as start with its log', () async {
      final host = FakeNodeHost(facts: _ready)..logText = 'port in use';
      final node = FactsBookPhoneNode(
        session,
        host: host,
        probe: (_) async => false,
        probeAttempts: 2,
        probeDelay: Duration.zero,
        pairWith: ({required address, required code, ownerDeviceId}) async {},
      );
      await node.runHere();
      expect(node.problem, PhoneNodeProblem.start);
      expect(node.failure, 'port in use');
      expect(host.calls, isNot(contains('pair-code')));
    });

    test(
      'a pairing that fails never keeps the code, and Start tries only pairing',
      () async {
        final host = FakeNodeHost(facts: _ready);
        final node = make(host, pairs: false);
        await node.runHere();
        expect(node.problem, PhoneNodeProblem.pair);
        expect(node.failure, isNot(contains(_secret)));
        expect(node.failure, contains('forbidden_scope'));
        host.calls.clear();
        await node.start();
        // The node is already running: it is not started or installed again.
        expect(host.calls, ['pair-code']);
      },
    );

    test('a second run while one is going does nothing', () async {
      final host = FakeNodeHost(facts: _ready);
      final node = make(host);
      final first = node.runHere();
      await node.runHere();
      await first;
      expect(host.calls.where((c) => c == 'install'), hasLength(1));
    });
  });

  group('stop, restart, remove', () {
    FakeNodeHost running() => FakeNodeHost(
      facts: const NodeHostFacts(
        linuxReady: true,
        binaryInstalled: true,
        dataExists: true,
        binaryAvailable: true,
      ),
    )..running = true;

    test('stop leaves the data and the node stopped', () async {
      final host = running();
      final node = make(host);
      await node.refresh();
      await node.stop();
      expect(host.calls, ['stop']);
      expect(node.state, PhoneNodeState.stopped);
    });

    test('restart stops, starts and keeps the pairing', () async {
      final host = running();
      final node = make(host);
      await node.restart();
      expect(host.calls.take(2), ['stop', 'start']);
      expect(node.state, PhoneNodeState.running);
    });

    test('remove deletes the data and returns to ready to install', () async {
      final host = running();
      final node = make(host);
      await node.refresh();
      await node.remove();
      expect(host.calls, ['remove']);
      expect(node.state, PhoneNodeState.notInstalled);
      expect(node.facts.dataExists, isFalse);
    });
  });

  group('what the node printed', () {
    test('the pairing line is found among other output', () {
      final p = NodePairing.parse(
        'warming up\n{"v":1,"code":"abc","owner_device":"dev_9","scopes":[]}\n',
      );
      expect(p?.code, 'abc');
      expect(p?.ownerDevice, 'dev_9');
      expect(NodePairing.parse('{"code":1}\nnot json'), isNull);
      expect(NodePairing.parse(''), isNull);
    });

    test('the inspect line', () {
      expect(BuiltinLinuxNodeHost.parseInspect('x\nfb b=1 d=0\n'), (
        binaryInstalled: true,
        dataExists: false,
      ));
      expect(BuiltinLinuxNodeHost.parseInspect('nothing'), (
        binaryInstalled: false,
        dataExists: false,
      ));
    });
  });

  group('when the app opens', () {
    final link = FactsBookLink(
      baseUrl: Uri.parse('http://127.0.0.1:8787'),
      clientId: 'cli_1',
      vault: 'vlt_1',
      rootFingerprint: 'a' * 64,
      node: 'dev_1',
      scopes: const ['read'],
    );
    FakeNodeHost stopped() => FakeNodeHost(
      facts: const NodeHostFacts(
        linuxReady: true,
        binaryInstalled: true,
        dataExists: true,
        binaryAvailable: true,
      ),
    );
    Future<FactsBookPhoneNode> open(
      FakeNodeHost host, {
      bool paired = true,
      Map<String, Object> prefs = const {},
    }) async {
      SharedPreferences.setMockInitialValues(prefs);
      final store = await SharedPreferences.getInstance();
      final node = FactsBookPhoneNode(
        paired
            ? FactsBookSession.fixed(FakeFactsBookGateway.sample(), link: link)
            : FactsBookSession.unpaired(),
        host: host,
        probe: (_) async => host.running,
        probeAttempts: 2,
        probeDelay: Duration.zero,
        pairWith: ({required address, required code, ownerDeviceId}) async {},
        prefs: store,
      );
      await node.ensureRunning();
      return node;
    }

    test('right after an update the Linux is still starting: the app waits '
        'for it, then starts the memory', () async {
      final host = _StartingLinuxHost(readyAfter: 3);
      SharedPreferences.setMockInitialValues(const {});
      final node = FactsBookPhoneNode(
        FactsBookSession.fixed(FakeFactsBookGateway.sample(), link: link),
        host: host,
        probe: (_) async => host.running,
        probeAttempts: 2,
        probeDelay: Duration.zero,
        linuxWaitDelay: Duration.zero,
        pairWith: ({required address, required code, ownerDeviceId}) async {},
        prefs: await SharedPreferences.getInstance(),
      );
      await node.ensureRunning();
      expect(host.looks, greaterThan(3));
      expect(host.calls, contains('start'));
      expect(node.state, PhoneNodeState.running);
    });

    test('a first look that fails while the Linux starts is retried, then '
        'the memory starts', () async {
      final host = _FailingLookHost(failFor: 2);
      SharedPreferences.setMockInitialValues(const {});
      final node = FactsBookPhoneNode(
        FactsBookSession.fixed(FakeFactsBookGateway.sample(), link: link),
        host: host,
        probe: (_) async => host.running,
        probeAttempts: 2,
        probeDelay: Duration.zero,
        linuxWaitDelay: Duration.zero,
        pairWith: ({required address, required code, ownerDeviceId}) async {},
        prefs: await SharedPreferences.getInstance(),
      );
      await node.ensureRunning();
      expect(host.calls, contains('start'));
      expect(node.state, PhoneNodeState.running);
    });

    test('a start that trips over a busy Linux is tried again', () async {
      final host = _FlakyStartHost(failFor: 1);
      SharedPreferences.setMockInitialValues(const {});
      final node = FactsBookPhoneNode(
        FactsBookSession.fixed(FakeFactsBookGateway.sample(), link: link),
        host: host,
        probe: (_) async => host.running,
        probeAttempts: 2,
        probeDelay: Duration.zero,
        linuxWaitDelay: Duration.zero,
        pairWith: ({required address, required code, ownerDeviceId}) async {},
        prefs: await SharedPreferences.getInstance(),
      );
      await node.ensureRunning();
      expect(host.calls, contains('start'));
      expect(node.state, PhoneNodeState.running);
    });

    test('the memory counts as starting for the whole of ensureRunning, '
        'including its waits for the Linux', () async {
      final host = _StartingLinuxHost(readyAfter: 3);
      SharedPreferences.setMockInitialValues(const {});
      final node = FactsBookPhoneNode(
        FactsBookSession.fixed(FakeFactsBookGateway.sample(), link: link),
        host: host,
        probe: (_) async => host.running,
        probeAttempts: 2,
        probeDelay: Duration.zero,
        linuxWaitDelay: Duration.zero,
        pairWith: ({required address, required code, ownerDeviceId}) async {},
        prefs: await SharedPreferences.getInstance(),
      );
      final seen = <bool>[];
      node.addListener(() => seen.add(node.busy));
      expect(node.busy, isFalse);
      final done = node.ensureRunning();
      expect(node.busy, isTrue);
      await Future<void>.delayed(Duration.zero);
      expect(node.busy, isTrue);
      await done;
      expect(node.busy, isFalse);
      expect(seen.first, isTrue);
      expect(seen.last, isFalse);
    });

    test('a stopped, paired memory is started by the app', () async {
      final host = stopped();
      final node = await open(host);
      expect(host.calls, contains('start'));
      expect(node.state, PhoneNodeState.running);
    });

    test('a memory the person stopped stays stopped', () async {
      final host = stopped();
      final node = await open(
        host,
        prefs: {FactsBookPhoneNode.personStoppedKey: true},
      );
      expect(host.calls, isEmpty);
      expect(node.state, PhoneNodeState.stopped);
    });

    test('Stop is remembered and Start forgets it', () async {
      final host = stopped()..running = true;
      final node = await open(host);
      expect(node.personStopped, isFalse);
      await node.stop();
      expect(node.personStopped, isTrue);
      host.calls.clear();
      await node.ensureRunning();
      expect(host.calls, isEmpty);
      await node.start();
      expect(node.personStopped, isFalse);
    });

    test('a memory this app is not paired with is left alone', () async {
      final host = stopped();
      await open(host, paired: false);
      expect(host.calls, isEmpty);
    });
  });

  group('the Memory row on the Chats home', () {
    final link = FactsBookLink(
      baseUrl: Uri.parse('http://127.0.0.1:8787'),
      clientId: 'cli_1',
      vault: 'vlt_1',
      rootFingerprint: 'a' * 64,
      node: 'dev_1',
      scopes: const ['read'],
    );

    factsTest('a stopped memory is started once the pairing has been read, '
        'even when that finishes after the first frame', (tester) async {
      final host = FakeNodeHost(
        facts: const NodeHostFacts(
          linuxReady: true,
          binaryInstalled: true,
          dataExists: true,
          binaryAvailable: true,
        ),
      );
      SharedPreferences.setMockInitialValues(const {});
      final prefs = await SharedPreferences.getInstance();
      final late = _LateSession(FakeFactsBookGateway.sample(), link: link);
      final node = FactsBookPhoneNode(
        late,
        host: host,
        probe: (_) async => host.running,
        probeAttempts: 2,
        probeDelay: Duration.zero,
        linuxWaitDelay: Duration.zero,
        pairWith: ({required address, required code, ownerDeviceId}) async {},
        prefs: prefs,
      );
      await pumpFacts(
        tester,
        const FactsBookMemorySection(),
        session: late,
        overrides: [factsBookPhoneNodeProvider.overrideWithValue(node)],
      );
      expect(host.calls, isNot(contains('start')));
      late.finishLoading();
      await settle(tester);
      expect(host.calls, contains('start'));
    });

    factsTest('stopped says so in words, with Start, not "did not answer"', (
      tester,
    ) async {
      final host = FakeNodeHost(
        facts: const NodeHostFacts(
          linuxReady: true,
          binaryInstalled: true,
          dataExists: true,
          binaryAvailable: true,
        ),
      );
      SharedPreferences.setMockInitialValues({
        FactsBookPhoneNode.personStoppedKey: true,
      });
      final prefs = await SharedPreferences.getInstance();
      final gateway = FakeFactsBookGateway.sample()..reachable = false;
      final linked = FactsBookSession.fixed(gateway, link: link);
      final node = FactsBookPhoneNode(
        linked,
        host: host,
        probe: (_) async => host.running,
        probeAttempts: 2,
        probeDelay: Duration.zero,
        pairWith: ({required address, required code, ownerDeviceId}) async {},
        prefs: prefs,
      );
      await pumpFacts(
        tester,
        const FactsBookMemorySection(),
        session: linked,
        overrides: [factsBookPhoneNodeProvider.overrideWithValue(node)],
      );
      expect(find.text('Memory is stopped'), findsOneWidget);
      expect(find.text('FactsBook did not answer'), findsNothing);
      gateway.reachable = true;
      await tester.tap(find.byKey(const ValueKey('factsbook-memory-start')));
      await settle(tester);
      expect(host.calls, contains('start'));
      expect(find.text('Memory is stopped'), findsNothing);
    });

    Future<
      ({FactsBookPhoneNode node, FactsBookSession session, FakeNodeHost host})
    >
    runningNode(FakeFactsBookGateway gateway) async {
      final host = FakeNodeHost(
        facts: const NodeHostFacts(
          linuxReady: true,
          binaryInstalled: true,
          dataExists: true,
          binaryAvailable: true,
        ),
      )..running = true;
      SharedPreferences.setMockInitialValues(const {});
      final prefs = await SharedPreferences.getInstance();
      final linked = FactsBookSession.fixed(gateway, link: link);
      final node = FactsBookPhoneNode(
        linked,
        host: host,
        probe: (_) async => host.running,
        probeAttempts: 2,
        probeDelay: Duration.zero,
        linuxWaitDelay: Duration.zero,
        pairWith: ({required address, required code, ownerDeviceId}) async {},
        prefs: prefs,
      );
      return (node: node, session: linked, host: host);
    }

    factsTest('a first read that cannot reach a memory still coming up shows '
        'the starting row, then the content once it answers', (tester) async {
      final gateway = FakeFactsBookGateway.sample()..reachable = false;
      final r = await runningNode(gateway);
      await pumpFacts(
        tester,
        const FactsBookMemorySection(
          readRetryDelay: Duration(seconds: 10),
          readRetryAttempts: 3,
        ),
        session: r.session,
        overrides: [factsBookPhoneNodeProvider.overrideWithValue(r.node)],
      );
      expect(find.text('Starting memory…'), findsOneWidget);
      expect(find.text('Memory did not answer'), findsNothing);
      gateway.reachable = true;
      await tester.pump(const Duration(seconds: 10));
      await settle(tester);
      expect(find.text('Starting memory…'), findsNothing);
      expect(find.text('Memory did not answer'), findsNothing);
      expect(find.text('Open About you'), findsOneWidget);
    });

    factsTest('a memory that never answers shows the error after the '
        'retries', (tester) async {
      final gateway = FakeFactsBookGateway.sample()..reachable = false;
      final r = await runningNode(gateway);
      await pumpFacts(
        tester,
        const FactsBookMemorySection(
          readRetryDelay: Duration(seconds: 10),
          readRetryAttempts: 2,
        ),
        session: r.session,
        overrides: [factsBookPhoneNodeProvider.overrideWithValue(r.node)],
      );
      expect(find.text('Starting memory…'), findsOneWidget);
      for (var i = 0; i < 2; i++) {
        await tester.pump(const Duration(seconds: 10));
        await settle(tester);
      }
      expect(find.text('Starting memory…'), findsNothing);
      expect(find.text('Memory did not answer'), findsOneWidget);
    });

    factsTest('a real error such as unauthorized shows at once, not the '
        'starting row', (tester) async {
      final gateway = _UnauthorizedGateway();
      final r = await runningNode(gateway);
      await pumpFacts(
        tester,
        const FactsBookMemorySection(
          readRetryDelay: Duration(seconds: 10),
          readRetryAttempts: 3,
        ),
        session: r.session,
        overrides: [factsBookPhoneNodeProvider.overrideWithValue(r.node)],
      );
      expect(find.text('Starting memory…'), findsNothing);
      expect(
        find.byKey(const ValueKey('factsbook-memory-error')),
        findsOneWidget,
      );
    });

    factsTest('pressing Stop makes the row say stopped at once', (
      tester,
    ) async {
      final host = FakeNodeHost(
        facts: const NodeHostFacts(
          linuxReady: true,
          binaryInstalled: true,
          dataExists: true,
          binaryAvailable: true,
        ),
      )..running = true;
      final gateway = FakeFactsBookGateway.sample();
      final linked = FactsBookSession.fixed(gateway, link: link);
      final node = FactsBookPhoneNode(
        linked,
        host: host,
        probe: (_) async => host.running,
        probeAttempts: 2,
        probeDelay: Duration.zero,
        pairWith: ({required address, required code, ownerDeviceId}) async {},
      );
      await pumpFacts(
        tester,
        const FactsBookMemorySection(),
        session: linked,
        overrides: [factsBookPhoneNodeProvider.overrideWithValue(node)],
      );
      expect(find.text('Memory is stopped'), findsNothing);
      // The node goes away with the stop, as a real one does.
      gateway.reachable = false;
      await node.stop();
      await settle(tester);
      await settle(tester);
      expect(find.text('Memory is stopped'), findsOneWidget);
      expect(find.text('Memory did not answer'), findsNothing);
    });

    factsTest('first run opens Memory on this phone, then Add something', (
      tester,
    ) async {
      final host = FakeNodeHost(facts: _ready);
      final linked = FactsBookSession.unpaired();
      final node = FactsBookPhoneNode(
        linked,
        host: host,
        probe: (_) async => host.running,
        probeAttempts: 2,
        probeDelay: Duration.zero,
        pairWith: ({required address, required code, ownerDeviceId}) async {
          linked.link = link;
          linked.gateway = FakeFactsBookGateway.sample();
        },
      );
      await pumpFacts(
        tester,
        const FactsBookMemorySection(),
        session: linked,
        nodeHost: host,
        overrides: [factsBookPhoneNodeProvider.overrideWithValue(node)],
      );
      await tester.tap(find.text('Set up Memory'));
      await settle(tester);
      expect(find.byType(FactsBookPhoneNodeScreen), findsOneWidget);
      expect(find.byType(FactsBookConnectScreen), findsNothing);
      expect(find.text('What is this?'), findsOneWidget);
      await tester.tap(find.byKey(const ValueKey('factsbook-node-run')));
      for (var i = 0; i < 8; i++) {
        await tester.pump(const Duration(milliseconds: 120));
      }
      expect(find.byType(FactsBookCaptureScreen), findsOneWidget);
    });

    factsTest('the explanation reads naturally and no build says what to do', (
      tester,
    ) async {
      final node = make(
        FakeNodeHost(facts: const NodeHostFacts(linuxReady: true)),
      );
      await pumpFacts(
        tester,
        const FactsBookPhoneNodeScreen(),
        overrides: [factsBookPhoneNodeProvider.overrideWithValue(node)],
      );
      await settle(tester);
      expect(find.text('No memory build yet'), findsOneWidget);
      expect(find.textContaining('Update the app'), findsOneWidget);
      final what = tester.widget<Text>(
        find.descendant(
          of: find.byKey(const ValueKey('factsbook-node-what')),
          matching: find.byType(Text),
        ),
      );
      expect(what.data, isNot(contains('runs in On this phone')));
      expect(what.data, contains('runs on this phone'));
    });

    factsTest('the manual form is behind Connect to my own server', (
      tester,
    ) async {
      final host = FakeNodeHost(facts: _ready);
      final node = make(host);
      await pumpFacts(
        tester,
        const FactsBookPhoneNodeScreen(),
        overrides: [factsBookPhoneNodeProvider.overrideWithValue(node)],
      );
      expect(find.text('Connect to my own server'), findsNothing);
      await tester.tap(find.byKey(const ValueKey('factsbook-node-details')));
      await settle(tester);
      await tester.tap(find.byKey(const ValueKey('factsbook-node-own-server')));
      await settle(tester);
      expect(find.byType(FactsBookConnectScreen), findsOneWidget);
    });
  });

  group('the page', () {
    Future<void> openMore(WidgetTester tester) async {
      final toggle = find.byKey(const ValueKey('factsbook-node-details'));
      await tester.ensureVisible(toggle);
      await tester.tap(toggle);
      await settle(tester);
    }

    Future<FactsBookPhoneNode> pump(
      WidgetTester tester,
      FakeNodeHost host, {
      Locale locale = const Locale('en'),
    }) async {
      final node = make(host);
      await pumpFacts(
        tester,
        const FactsBookPhoneNodeScreen(),
        session: session,
        locale: locale,
        overrides: [factsBookPhoneNodeProvider.overrideWithValue(node)],
      );
      return node;
    }

    factsTest(
      'ready to install offers one button; running offers stop, restart, remove',
      (tester) async {
        final host = FakeNodeHost(facts: _ready);
        await pump(tester, host);
        expect(find.text('Memory not installed yet'), findsOneWidget);
        await tester.tap(find.byKey(const ValueKey('factsbook-node-run')));
        for (var i = 0; i < 6; i++) {
          await tester.pump(const Duration(milliseconds: 120));
        }
        expect(find.text('Memory is running here'), findsOneWidget);
        expect(find.text('Stop memory'), findsOneWidget);
        expect(find.text('Restart memory'), findsOneWidget);
        // Remove is under Details, not a button on the page.
        expect(find.text('Remove memory from this phone'), findsNothing);
        await openMore(tester);
        expect(find.text('Remove memory from this phone'), findsOneWidget);
        expect(find.text('Only inside this phone'), findsOneWidget);
        expect(find.textContaining(_secret), findsNothing);
      },
    );

    factsTest('remove names what is lost and asks first', (tester) async {
      final host = FakeNodeHost(
        facts: const NodeHostFacts(
          linuxReady: true,
          binaryInstalled: true,
          dataExists: true,
          binaryAvailable: true,
        ),
      )..running = true;
      await pump(tester, host);
      await openMore(tester);
      await tester.tap(find.byKey(const ValueKey('factsbook-node-remove')));
      await settle(tester);
      expect(find.text('Remove memory from phone?'), findsOneWidget);
      expect(
        find.text('Every note, fact and reminder Memory holds here'),
        findsOneWidget,
      );
      expect(host.calls, isEmpty);
      await tester.tap(find.text('Remove memory from this phone').last);
      for (var i = 0; i < 6; i++) {
        await tester.pump(const Duration(milliseconds: 120));
      }
      expect(host.calls, ['remove']);
      expect(find.text('Memory removed from this phone.'), findsOneWidget);
    });

    factsTest('a failure is plain words with the reason under Details', (
      tester,
    ) async {
      final host = FakeNodeHost(facts: _ready)
        ..installFails = const NodeHostException(
          NodeHostFailure.badBinary,
          'checksum mismatch',
        );
      await pump(tester, host);
      await tester.tap(find.byKey(const ValueKey('factsbook-node-run')));
      for (var i = 0; i < 6; i++) {
        await tester.pump(const Duration(milliseconds: 120));
      }
      expect(
        find.text(
          'The program could not be installed here. Try again; Details says why.',
        ),
        findsOneWidget,
      );
      expect(find.byKey(const ValueKey('factsbook-node-details')), findsOne);
      expect(find.byKey(const ValueKey('factsbook-node-run')), findsOneWidget);
    });

    factsTest('without the built-in Linux set up it says to set it up', (
      tester,
    ) async {
      await pump(tester, FakeNodeHost());
      expect(find.text('Set up phone first'), findsOneWidget);
      expect(find.byKey(const ValueKey('factsbook-node-setup')), findsOne);
    });

    factsTest('on a device without it, it says so', (tester) async {
      await pump(tester, FakeNodeHost(supported: false));
      expect(find.text('Not available here'), findsOneWidget);
    });

    factsTest('with no build to install, it says so', (tester) async {
      await pump(
        tester,
        FakeNodeHost(facts: const NodeHostFacts(linuxReady: true)),
      );
      expect(find.text('No memory build yet'), findsOneWidget);
      expect(find.byKey(const ValueKey('factsbook-node-run')), findsNothing);
    });

    factsTest('Arabic reads right to left', (tester) async {
      await pump(
        tester,
        FakeNodeHost(facts: _ready),
        locale: const Locale('ar'),
      );
      expect(find.text('الذاكرة ليست على هذا الهاتف بعد'), findsOneWidget);
      expect(
        Directionality.of(
          tester.element(find.text('الذاكرة ليست على هذا الهاتف بعد')),
        ),
        TextDirection.rtl,
      );
    });
  });
}

/// A built-in Linux whose first [failFor] looks fail outright (it answers
/// "status" but cannot run a command yet), then has a stopped memory.
class _FailingLookHost extends _StartingLinuxHost {
  _FailingLookHost({required this.failFor}) : super(readyAfter: 0);

  final int failFor;

  @override
  Future<NodeHostFacts> inspect() async {
    if (looks < failFor) {
      looks++;
      throw const NodeHostException(NodeHostFailure.start);
    }
    return super.inspect();
  }
}

/// A built-in Linux whose first look succeeds but whose next [failFor]
/// looks fail (the look inside the start step, right after an update).
class _FlakyStartHost extends _StartingLinuxHost {
  _FlakyStartHost({required this.failFor}) : super(readyAfter: 0);

  final int failFor;
  var _failed = 0;

  @override
  Future<NodeHostFacts> inspect() async {
    if (looks >= 1 && _failed < failFor) {
      looks++;
      _failed++;
      throw const NodeHostException(NodeHostFailure.start);
    }
    return super.inspect();
  }
}

/// A built-in Linux that is still starting for the first [readyAfter] looks,
/// then has a set-up, stopped memory (the app just updated).
class _StartingLinuxHost extends FakeNodeHost {
  _StartingLinuxHost({required this.readyAfter})
    : super(
        facts: const NodeHostFacts(
          linuxReady: true,
          binaryInstalled: true,
          dataExists: true,
          binaryAvailable: true,
        ),
      );

  final int readyAfter;
  var looks = 0;

  @override
  Future<NodeHostFacts> inspect() async {
    looks++;
    if (looks <= readyAfter) return const NodeHostFacts();
    return super.inspect();
  }
}

/// A paired session whose pairing is read only after the first frame, as
/// on a real phone (secure storage answers late).
class _LateSession extends FactsBookSession {
  _LateSession(super.gateway, {super.link}) : super.fixed();

  var _done = false;

  @override
  bool get loaded => _done;

  void finishLoading() {
    _done = true;
    notifyListeners();
  }
}

/// A node that answers every read with a real refusal, not a lost link.
class _UnauthorizedGateway extends FakeFactsBookGateway {
  _UnauthorizedGateway();

  @override
  Future<NodeStatus> status() async =>
      throw const FactsBookException('unauthorized', status: 401);
}
