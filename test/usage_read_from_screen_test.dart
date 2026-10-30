// Usage › "Read usage from {connection}": plain words, no plumbing; In-app
// Ubuntu is set up for real; a computer is turned on, read once at once, and
// the screen shows what was found or why not.
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/builtin/quota_collector_host.dart';
import 'package:opencode_mobile/domain/usage_subscriptions.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/computer_usage_source.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/state/usage_subscriptions_controller.dart';
import 'package:opencode_mobile/ui/screens/usage/usage_read_from_screen.dart';

import 'support/computer_usage_fakes.dart';
import 'support/usage_ui_harness.dart';

final _en = lookupAppLocalizations(const Locale('en'));

class _FakeCollector extends InAppQuotaCollector {
  _FakeCollector({required super.prefs, this.failure}) : super(supported: true);
  final QuotaSetupFailure? failure;
  final steps = <QuotaSetupStep>[];
  int setUps = 0;

  @override
  Future<void> setUp(
    ServerProfile profile, {
    void Function(QuotaSetupStep step)? onStep,
  }) async {
    setUps++;
    for (final step in [QuotaSetupStep.checking, QuotaSetupStep.starting]) {
      steps.add(step);
      onStep?.call(step);
    }
    if (failure != null) throw QuotaSetupException(failure!);
  }
}

ComputerUsageSource _computer(
  UsageHarness h,
  List<FakeStep> steps, {
  List<FakeComputerPort>? ports,
}) => ComputerUsageSource(
  prefs: h.usage.prefs,
  profiles: () => h.connection.store.profiles,
  isReadable: (_) => true,
  isInApp: (_) => false,
  createPort: (_) {
    final port = FakeComputerPort(steps);
    ports?.add(port);
    return port;
  },
  loadSource: () async => 'SCRIPT',
  timeout: const Duration(seconds: 2),
);

UsageSourceResult _signedInOn(String id) => UsageSourceResult(
  signIns: [UsageSignIn(providerId: 'codex', connectionId: id)],
  authoritativeSignIns: {id},
);

Future<void> _open(
  WidgetTester tester,
  UsageHarness h,
  String connectionId, {
  InAppQuotaCollector? collector,
  ComputerUsageSource? computer,
}) async {
  usagePhoneView(tester);
  await h.usage.refresh();
  await tester.pumpWidget(
    usageApp(
      Builder(
        builder: (context) => Scaffold(
          body: Center(
            child: ElevatedButton(
              onPressed: () => Navigator.of(context).push(
                MaterialPageRoute<void>(
                  builder: (_) => UsageReadFromScreen(
                    controller: h.connection,
                    usage: h.usage,
                    connectionId: connectionId,
                    providerId: 'codex',
                    collector: collector,
                    computer: computer,
                  ),
                ),
              ),
              child: const Text('Go'),
            ),
          ),
        ),
      ),
    ),
  );
  await tester.tap(find.text('Go'));
  await tester.pumpAndSettle();
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  usageTestWidgets('a computer: explains the check in plain words, shows what '
      'runs, and offers Turn on', (tester) async {
    final h = await usageHarness(
      connections: const [usageInApp, usagePc],
      result: _signedInOn('pc'),
    );
    await _open(tester, h, 'pc', computer: _computer(h, const []));
    expect(find.text(_en.usageReadFrom('PC')), findsOneWidget);
    expect(find.text(_en.usageReadFromSignedIn('Codex', 'PC')), findsOneWidget);
    expect(find.text(_en.usageComputerExplain('PC')), findsOneWidget);
    expect(find.text(_en.usageComputerTurnOn), findsOneWidget);
    expect(find.text('Coming soon'), findsNothing);
    // What runs: the command line, not the whole script; where it reads.
    await tester.tap(find.text(_en.usageComputerWhatRuns));
    await tester.pumpAndSettle();
    expect(find.text('node --input-type=module -e <check>'), findsOneWidget);
    expect(find.text(_en.usageComputerReadsValue('PC')), findsOneWidget);
    expect(find.textContaining('SCRIPT'), findsNothing);
    for (final word in [
      'collector',
      'consent',
      'operator',
      'route',
      'loopback',
    ]) {
      expect(find.textContaining(word), findsNothing, reason: word);
      expect(find.textContaining(word.toUpperCase()), findsNothing);
    }
    await tester.tap(usageKey('usage-read-from-not-now'));
    await tester.pumpAndSettle();
    expect(usageKey('usage-read-from'), findsNothing);
  });

  usageTestWidgets('Turn on stores the switch, reads once at once and shows '
      'the plans found', (tester) async {
    final h = await usageHarness(
      connections: const [usageInApp, usagePc],
      result: _signedInOn('pc'),
    );
    final ports = <FakeComputerPort>[];
    final computer = _computer(h, [
      FakeStep(output: ['${computerLine(computerCodexOk)}\r\n']),
    ], ports: ports);
    await _open(tester, h, 'pc', computer: computer);
    final before = h.source.calls;
    await tester.tap(usageKey('usage-read-from-set-up'));
    await tester.pumpAndSettle();
    expect(h.usage.prefs.getBool('oc.usageComputerReader.pc'), isTrue);
    expect(ports, hasLength(1));
    expect(ports.single.removed, ['pty_1']);
    expect(find.text(_en.usageComputerFound('Codex', 'PC')), findsOneWidget);
    expect(find.text(_en.usageComputerDone), findsOneWidget);
    expect(find.text(_en.usageComputerTurnOff), findsOneWidget);
    expect(h.source.calls, greaterThan(before));
    // Still here: the result is the point of the screen.
    expect(usageKey('usage-read-from'), findsOneWidget);
    await tester.tap(usageKey('usage-read-from-done'));
    await tester.pumpAndSettle();
    expect(usageKey('usage-read-from'), findsNothing);
  });

  usageTestWidgets('Turn on finding no plan says so', (tester) async {
    final h = await usageHarness(
      connections: const [usageInApp, usagePc],
      result: _signedInOn('pc'),
    );
    final computer = _computer(h, [
      FakeStep(output: ['${computerLine(computerAnswer())}\r\n']),
    ]);
    await _open(tester, h, 'pc', computer: computer);
    await tester.tap(usageKey('usage-read-from-set-up'));
    await tester.pumpAndSettle();
    expect(find.text(_en.usageComputerFoundNone('PC')), findsOneWidget);
  });

  usageTestWidgets('Turn off removes the switch and offers Turn on again', (
    tester,
  ) async {
    final h = await usageHarness(
      connections: const [usageInApp, usagePc],
      result: _signedInOn('pc'),
    );
    await h.usage.prefs.setBool('oc.usageComputerReader.pc', true);
    final computer = _computer(h, const []);
    await _open(tester, h, 'pc', computer: computer);
    expect(find.text(_en.usageComputerReadNow), findsOneWidget);
    expect(find.text(_en.usageComputerOnNote('PC')), findsOneWidget);
    await tester.tap(usageKey('usage-read-from-turn-off'));
    await tester.pumpAndSettle();
    expect(h.usage.prefs.containsKey('oc.usageComputerReader.pc'), isFalse);
    expect(find.text(_en.usageComputerTurnOn), findsOneWidget);
    expect(find.text(_en.usageComputerTurnOff), findsNothing);
  });

  usageTestWidgets('each failure is plain words and the screen stays', (
    tester,
  ) async {
    final cases = <(List<FakeStep>, String Function(String))>[
      (
        [
          const FakeStep(connectError: true),
          const FakeStep(output: ['node: command not found\r\n']),
        ],
        _en.usageComputerNeedsNode,
      ),
      (
        [
          FakeStep(
            output: [
              '${computerLine(computerAnswer(error: 'nodeTooOld'))}\r\n',
            ],
          ),
        ],
        _en.usageComputerNeedsNode,
      ),
      (
        [const FakeStep(startError: ComputerUsageProblem.unreachable)],
        _en.usageComputerOffline,
      ),
      (
        [const FakeStep(startError: ComputerUsageProblem.serverTooOld)],
        _en.usageComputerServerTooOld,
      ),
      (
        [
          const FakeStep(output: ['hello\r\n']),
        ],
        _en.usageComputerNoAnswer,
      ),
    ];
    for (final (steps, words) in cases) {
      final h = await usageHarness(
        connections: const [usageInApp, usagePc],
        result: _signedInOn('pc'),
      );
      await _open(tester, h, 'pc', computer: _computer(h, steps));
      await tester.tap(usageKey('usage-read-from-set-up'));
      await tester.pumpAndSettle();
      expect(usageKey('usage-read-from-failure'), findsOneWidget);
      expect(find.text(words('PC')), findsOneWidget);
      expect(usageKey('usage-read-from'), findsOneWidget);
      for (final word in ['ENOENT', 'ECONN', 'Exception', 'pty_', 'bash']) {
        expect(find.textContaining(word), findsNothing, reason: word);
      }
      await tester.pumpWidget(const SizedBox.shrink());
    }
  });

  usageTestWidgets('a server too old for this is not left switched on', (
    tester,
  ) async {
    final h = await usageHarness(
      connections: const [usageInApp, usagePc],
      result: _signedInOn('pc'),
    );
    await _open(
      tester,
      h,
      'pc',
      computer: _computer(h, const [
        FakeStep(startError: ComputerUsageProblem.serverTooOld),
      ]),
    );
    await tester.tap(usageKey('usage-read-from-set-up'));
    await tester.pumpAndSettle();
    expect(h.usage.prefs.containsKey('oc.usageComputerReader.pc'), isFalse);
  });

  usageTestWidgets('U23: lists only readable plans, once each, by proper '
      'name', (tester) async {
    final h = await usageHarness(
      connections: const [usageInApp, usagePc],
      result: UsageSourceResult(
        signIns: [
          for (final id in [
            'codex',
            'glm',
            'minimax',
            'claude',
            'copilot',
            'kimi',
          ])
            UsageSignIn(
              providerId: id,
              connectionId: 'pc',
              displayName: id == 'copilot' ? 'GitHub Copilot' : id,
            ),
        ],
        authoritativeSignIns: const {'pc'},
      ),
    );
    await _open(tester, h, 'pc');
    expect(
      find.text(_en.usageReadFromSignedIn('Codex, GLM, MiniMax', 'PC')),
      findsOneWidget,
    );
    expect(find.textContaining('copilot'), findsNothing);
    expect(find.textContaining('Claude'), findsNothing);
  });

  usageTestWidgets('In-app Ubuntu: Set up runs the helper set-up, reads '
      'again and closes', (tester) async {
    final h = await usageHarness(
      connections: const [usageInApp, usagePc],
      result: _signedInOn('inapp'),
    );
    final collector = _FakeCollector(prefs: h.usage.prefs);
    await _open(tester, h, 'inapp', collector: collector);
    expect(find.text(_en.usageReadFromBodyInApp), findsOneWidget);
    expect(find.text('Coming soon'), findsNothing);
    final before = h.source.calls;
    await tester.tap(usageKey('usage-read-from-set-up'));
    await tester.pumpAndSettle();
    expect(collector.setUps, 1);
    expect(h.source.calls, greaterThan(before));
    expect(h.source.force, isTrue);
    expect(usageKey('usage-read-from'), findsNothing);
  });

  usageTestWidgets('In-app Ubuntu failing to set up says why in words and '
      'stays', (tester) async {
    final h = await usageHarness(
      connections: const [usageInApp, usagePc],
      result: _signedInOn('inapp'),
    );
    final collector = _FakeCollector(
      prefs: h.usage.prefs,
      failure: QuotaSetupFailure.noNode,
    );
    await _open(tester, h, 'inapp', collector: collector);
    await tester.tap(usageKey('usage-read-from-set-up'));
    await tester.pumpAndSettle();
    expect(usageKey('usage-read-from-failure'), findsOneWidget);
    expect(
      find.text(_en.quotaInAppFailNoNode('On this phone')),
      findsOneWidget,
    );
    expect(usageKey('usage-read-from'), findsOneWidget);
  });

  usageTestWidgets('a connection that is gone says so', (tester) async {
    final h = await usageHarness();
    await _open(tester, h, 'gone');
    expect(usageKey('usage-read-from-gone'), findsOneWidget);
  });
}
