// Remove agent (FA6, docs/design/BA10-contract.md): the agent's sheet offers
// "Remove <Agent>" as a quiet destructive action only where the source can
// remove it, asks first, shows the progress in place, then what was freed or
// that it was already removed, and says each failure in one of three fixed
// sentences. A signed-in agent at its plan limit still opens its sheet.
// Behaviour only; the source is a fake that plays the controller's part.
import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/agent_auth_probe.dart';
import 'package:opencode_mobile/domain/agent_catalog.dart';
import 'package:opencode_mobile/domain/phone_agent_host.dart';
import 'package:opencode_mobile/domain/server_gateway.dart'
    show ProductException;
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/screens/agents/agents_section.dart';
import 'package:opencode_mobile/ui/screens/agents/agents_text.dart'
    show agentFreedSize;

import '../tool/capture/fixtures.dart' show loadCaptureFonts;
import 'support/agents_fakes.dart';
import 'support/chats_fakes.dart';

const _signedIn = AgentAuthProbeResult(state: AgentAuthProbeState.signedIn);

String _n(String name) => KitBidi.auto(name);

/// Codex (and any other removable agent) signed in and ready, removable.
FakeRemovableAgentsSource _agents({
  List<String> ids = const ['codex'],
  Set<String>? removable,
}) {
  final agents = FakeRemovableAgentsSource(
    rows: [for (final id in ids) agentRowFor(id, FakeAgentStage.ready)],
  );
  for (final id in ids) {
    agents.check(id, _signedIn);
  }
  agents.removable.addAll(removable ?? ids.where((id) => id != 'claude'));
  return agents;
}

Future<FakeChatsHost> _pump(
  WidgetTester tester,
  FakePhoneAgentsSource agents, {
  Locale locale = const Locale('en'),
}) async {
  tester.view.physicalSize = const Size(412, 915);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  final host = FakeChatsHost(
    FakeChatFeedSource(
      items: const [],
      projects: [project('alpha')],
      lastUsed: '/root/projects/alpha',
    ),
  )..phoneAgents = agents;
  await tester.pumpWidget(
    chatsApp(host, ListView(children: const [AgentsSection()]), locale: locale),
  );
  await tester.pumpAndSettle();
  return host;
}

Future<void> _open(WidgetTester tester, String id) async {
  await tester.tap(find.byKey(ValueKey('agents-row-$id')));
  await tester.pumpAndSettle();
}

Finder get _remove => find.byKey(const ValueKey('agents-remove'));
Finder get _confirm => find.byKey(const ValueKey('agents-remove-confirm'));
Finder get _removing => find.byKey(const ValueKey('agents-removing'));
Finder get _words => find.byKey(const ValueKey('agents-removed-words'));

Future<void> _askAndConfirm(WidgetTester tester) async {
  await tester.tap(_remove);
  await tester.pumpAndSettle();
  await tester.tap(_confirm);
}

void main() {
  setUpAll(loadCaptureFonts);

  group('Remove is offered only where the source can remove the agent', () {
    testWidgets('a removable agent offers Remove, named after the agent', (
      tester,
    ) async {
      await _pump(tester, _agents());
      await _open(tester, 'codex');
      expect(_remove, findsOneWidget);
      expect(find.text('Remove ${_n('Codex')}'), findsOneWidget);
      expect(tester.widget<KitButton>(_remove).destructive, isTrue);
    });

    testWidgets('it sits at the bottom, after Sign out', (tester) async {
      final agents = _agents(ids: ['fx'])..signOutCapable.add('fx');
      await _pump(tester, agents);
      await _open(tester, 'fx');
      final out = tester.getTopLeft(
        find.byKey(const ValueKey('agents-sign-out')),
      );
      expect(tester.getTopLeft(_remove).dy, greaterThan(out.dy));
    });

    testWidgets('an agent the source cannot remove has no Remove', (
      tester,
    ) async {
      await _pump(tester, _agents(removable: {}));
      await _open(tester, 'codex');
      expect(_remove, findsNothing);
    });

    testWidgets('Claude Code never offers Remove', (tester) async {
      // Even a source that claims it can.
      final agents = _agents(ids: ['claude'], removable: {'claude'})
        ..signOutCapable.add('claude');
      await _pump(tester, agents);
      await _open(tester, 'claude');
      expect(find.byKey(const ValueKey('agents-sign-out')), findsOneWidget);
      expect(_remove, findsNothing);
    });

    testWidgets('a source without removal shows no Remove', (tester) async {
      final agents = FakeAccountAgentsSource(
        rows: [agentRowFor('codex', FakeAgentStage.ready)],
      )..check('codex', _signedIn);
      await _pump(tester, agents);
      await _open(tester, 'codex');
      expect(_remove, findsNothing);
    });

    testWidgets('a failed install can be removed from its install step', (
      tester,
    ) async {
      final agents = FakeRemovableAgentsSource(
        rows: [agentRowFor('codex', FakeAgentStage.notInstalled)],
      )..removable.add('codex');
      agents.progress = const AgentSetupProgress(
        agentId: 'codex',
        phase: AgentSetupPhase.interrupted,
      );
      await _pump(tester, agents);
      await tester.tap(find.byKey(const ValueKey('agents-fix-codex')));
      await tester.pumpAndSettle();
      expect(find.byKey(const ValueKey('agents-install')), findsOneWidget);
      expect(_remove, findsOneWidget);
    });

    testWidgets('a not-installed agent has none', (tester) async {
      final agents = FakeRemovableAgentsSource(
        rows: [agentRowFor('codex', FakeAgentStage.notInstalled)],
      )..removable.add('codex');
      await _pump(tester, agents);
      await tester.tap(find.byKey(const ValueKey('agents-fix-codex')));
      await tester.pumpAndSettle();
      expect(_remove, findsNothing);
    });
  });

  group('the question', () {
    testWidgets('names the agent and says accounts and conversations stay', (
      tester,
    ) async {
      final agents = _agents();
      await _pump(tester, agents);
      await _open(tester, 'codex');
      await tester.tap(_remove);
      await tester.pumpAndSettle();
      expect(find.text('Remove ${_n('Codex')}?'), findsOneWidget);
      expect(
        find.text(
          'This removes the installed agent from this phone. Your accounts '
          'and conversations stay, and you can install it again.',
        ),
        findsOneWidget,
      );
      expect(find.text('Cancel'), findsOneWidget);
      // The button names the act and the agent (two words or more, COPY-9).
      expect(
        find.descendant(
          of: _confirm,
          matching: find.text('Remove ${_n('Codex')}'),
        ),
        findsOneWidget,
      );
      final confirm = tester.widget<KitButton>(_confirm);
      final cancel = tester.widget<KitButton>(
        find.byKey(const ValueKey('kit-confirm-cancel')),
      );
      expect(confirm.destructive, isTrue);
      expect(cancel.destructive, isFalse);
      expect(agents.calls.where((c) => c.startsWith('remove')), isEmpty);
    });

    testWidgets('Cancel removes nothing', (tester) async {
      final agents = _agents();
      await _pump(tester, agents);
      await _open(tester, 'codex');
      await tester.tap(_remove);
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const ValueKey('kit-confirm-cancel')));
      await tester.pumpAndSettle();
      expect(agents.calls.where((c) => c.startsWith('remove')), isEmpty);
      expect(_remove, findsOneWidget);
    });
  });

  group('while it runs', () {
    testWidgets('the sheet says Removing, in place, with nothing else to do '
        'for that agent', (tester) async {
      final agents = _agents()..removeGate = Completer<void>();
      await _pump(tester, agents);
      await _open(tester, 'codex');
      await _askAndConfirm(tester);
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 500));
      expect(agents.calls, contains('remove:codex'));
      expect(_removing, findsOneWidget);
      expect(find.text('Removing ${_n('Codex')}…'), findsWidgets);
      expect(_remove, findsNothing);
      expect(find.byKey(const ValueKey('agents-sign-in-again')), findsNothing);
      expect(find.byKey(const ValueKey('agents-install')), findsNothing);
      agents.removeGate!.complete();
      await tester.pumpAndSettle();
    });

    testWidgets('the Settings row says Removing and offers no act', (
      tester,
    ) async {
      final agents = _agents(ids: ['codex', 'gemini'])
        ..removeGate = Completer<void>();
      // Gemini is not installed: it keeps its Install chip.
      agents.rows = [
        agentRowFor('codex', FakeAgentStage.ready),
        agentRowFor('gemini', FakeAgentStage.notInstalled),
      ];
      await _pump(tester, agents);
      await _open(tester, 'codex');
      await _askAndConfirm(tester);
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 500));
      final row = find.byKey(const ValueKey('agents-row-codex'));
      expect(
        find.descendant(
          of: row,
          matching: find.text('Removing ${_n('Codex')}…'),
        ),
        findsOneWidget,
      );
      expect(find.byKey(const ValueKey('agents-fix-codex')), findsNothing);
      expect(find.byKey(const ValueKey('agents-fix-gemini')), findsOneWidget);
      agents.removeGate!.complete();
      await tester.pumpAndSettle();
    });

    testWidgets('another agent cannot be installed meanwhile, and says why', (
      tester,
    ) async {
      final agents = FakeRemovableAgentsSource(
        rows: [
          agentRowFor('codex', FakeAgentStage.ready),
          agentRowFor('gemini', FakeAgentStage.notInstalled),
        ],
      )..removing = 'codex';
      await _pump(tester, agents);
      await tester.tap(find.byKey(const ValueKey('agents-fix-gemini')));
      await tester.pumpAndSettle();
      expect(
        tester
            .widget<KitButton>(find.byKey(const ValueKey('agents-install')))
            .onPressed,
        isNull,
      );
      expect(
        find.text('This agent is in use. Finish its work and try again.'),
        findsOneWidget,
      );
      expect(agents.calls.where((c) => c.startsWith('install')), isEmpty);
    });

    testWidgets('a row being removed cannot be opened', (tester) async {
      final agents = _agents();
      await _pump(tester, agents);
      // Another place started the removal; the row is not tappable now.
      agents.change(() => agents.removing = 'codex');
      await tester.pumpAndSettle();
      expect(
        find.descendant(
          of: find.byKey(const ValueKey('agents-row-codex')),
          matching: find.text('Removing ${_n('Codex')}…'),
        ),
        findsOneWidget,
      );
      await tester.tap(find.byKey(const ValueKey('agents-row-codex')));
      await tester.pumpAndSettle();
      expect(find.byKey(const ValueKey('agents-sheet')), findsNothing);
    });
  });

  group('when it ends', () {
    testWidgets('it says what was freed, then the row is back to Install', (
      tester,
    ) async {
      final agents = _agents();
      await _pump(tester, agents);
      await _open(tester, 'codex');
      await _askAndConfirm(tester);
      await tester.pumpAndSettle();
      expect(
        find.text('${_n('Codex')} removed. Freed ${agentFreedSize(98000000)}.'),
        findsOneWidget,
      );
      expect(agentFreedSize(98000000), KitBidi.ltr('98 MB'));
      await tester.tap(find.byKey(const ValueKey('agents-removed-done')));
      await tester.pumpAndSettle();
      expect(find.byKey(const ValueKey('agents-sheet')), findsNothing);
      final chip = find.byKey(const ValueKey('agents-fix-codex'));
      expect(chip, findsOneWidget);
      expect(
        find.descendant(of: chip, matching: find.text('Install')),
        findsOneWidget,
      );
      expect(find.textContaining('Not installed'), findsOneWidget);
    });

    testWidgets('the freed size keeps its order in Arabic', (tester) async {
      final agents = _agents();
      await _pump(tester, agents, locale: const Locale('ar'));
      await _open(tester, 'codex');
      await _askAndConfirm(tester);
      await tester.pumpAndSettle();
      final text = tester.widget<KitNotice>(_words).message;
      expect(text, contains(KitBidi.ltr('98 MB')));
    });

    testWidgets('small and large sizes are formatted and isolated', (
      tester,
    ) async {
      expect(agentFreedSize(412000), KitBidi.ltr('412 kB'));
      expect(agentFreedSize(1500000000), KitBidi.ltr('1.5 GB'));
      expect(agentFreedSize(512), KitBidi.ltr('512 B'));
    });

    testWidgets('an agent that was already gone says so', (tester) async {
      final agents = _agents()
        ..nextRemoval = const AgentRemovalResult(
          agentId: 'codex',
          freedBytes: 0,
          alreadyAbsent: true,
        );
      await _pump(tester, agents);
      await _open(tester, 'codex');
      await _askAndConfirm(tester);
      await tester.pumpAndSettle();
      expect(find.text('${_n('Codex')} is already removed.'), findsOneWidget);
      expect(find.textContaining('Freed'), findsNothing);
    });
  });

  group('failures are one of three fixed sentences', () {
    const unsupported = "This agent can't be removed here.";
    const busy = 'This agent is in use. Finish its work and try again.';
    const unconfirmed =
        "Couldn't confirm this agent was removed. Check this phone and try "
        'again.';

    for (final (name, error, words) in [
      ('unsupported', const ProductException(unsupported), unsupported),
      ('busy', const ProductException(busy), busy),
      ('unconfirmed', const ProductException(unconfirmed), unconfirmed),
      // Anything else, with paths or native text in it, is the third one.
      (
        'a raw error',
        StateError('/home/oc/.local/share/oc-agents/codex failed: EACCES'),
        unconfirmed,
      ),
      (
        'a host failure',
        const AgentHostException(AgentHostFailure.unavailable),
        unconfirmed,
      ),
    ]) {
      testWidgets(name, (tester) async {
        final agents = _agents()..removeError = error;
        await _pump(tester, agents);
        await _open(tester, 'codex');
        await _askAndConfirm(tester);
        await tester.pumpAndSettle();
        expect(find.text(words), findsOneWidget);
        // The sheet is back where it was, and Remove can be tried again.
        expect(_remove, findsOneWidget);
        expect(_removing, findsNothing);
        expect(find.textContaining('EACCES'), findsNothing);
        expect(find.textContaining('oc-agents'), findsNothing);
        expect(find.textContaining('StateError'), findsNothing);
        expect(
          find.byKey(const ValueKey('agents-error-details')),
          findsNothing,
        );
      });
    }

    testWidgets('a retry after a failure removes it', (tester) async {
      final agents = _agents()
        ..removeError = const ProductException(unconfirmed);
      await _pump(tester, agents);
      await _open(tester, 'codex');
      await _askAndConfirm(tester);
      await tester.pumpAndSettle();
      agents.removeError = null;
      await _askAndConfirm(tester);
      await tester.pumpAndSettle();
      expect(find.textContaining('removed. Freed'), findsOneWidget);
      expect(find.text(unconfirmed), findsNothing);
    });

    testWidgets('the sentences read in Arabic too', (tester) async {
      final agents = _agents()..removeError = const ProductException(busy);
      await _pump(tester, agents, locale: const Locale('ar'));
      await _open(tester, 'codex');
      await _askAndConfirm(tester);
      await tester.pumpAndSettle();
      expect(
        find.text('هذا الوكيل قيد الاستخدام. أنهِ عمله ثم حاول مرة أخرى.'),
        findsOneWidget,
      );
      expect(find.text(busy), findsNothing);
    });
  });

  group('a signed-in agent at its plan limit still opens its sheet', () {
    testWidgets('Codex at its limit reaches Remove', (tester) async {
      final agents = _agents();
      agents.rows = [agentRowFor('codex', FakeAgentStage.limitReached)];
      await _pump(tester, agents);
      expect(find.text('Plan limit reached'), findsOneWidget);
      await _open(tester, 'codex');
      expect(find.byKey(const ValueKey('agents-sheet')), findsOneWidget);
      expect(
        find.text('${_n('Codex')} plan limit reached · try again later'),
        findsOneWidget,
      );
      // The limit is not a reason to offer to use it.
      expect(find.byKey(const ValueKey('agents-sign-in-done')), findsNothing);
      expect(_remove, findsOneWidget);
    });

    testWidgets('Claude Code at its limit reaches Sign out, not Remove', (
      tester,
    ) async {
      final agents = _agents(ids: ['claude'])..signOutCapable.add('claude');
      agents.rows = [agentRowFor('claude', FakeAgentStage.limitReached)];
      await _pump(tester, agents);
      await _open(tester, 'claude');
      expect(find.byKey(const ValueKey('agents-sheet')), findsOneWidget);
      expect(find.byKey(const ValueKey('agents-sign-out')), findsOneWidget);
      expect(_remove, findsNothing);
    });

    testWidgets('the row shows it can be opened', (tester) async {
      final agents = _agents();
      agents.rows = [agentRowFor('codex', FakeAgentStage.limitReached)];
      await _pump(tester, agents);
      expect(
        find.descendant(
          of: find.byKey(const ValueKey('agents-row-codex')),
          matching: find.byType(KitChevron),
        ),
        findsOneWidget,
      );
    });
  });

  group('the check step', () {
    testWidgets('a failed phone check can still remove the agent', (
      tester,
    ) async {
      final agents =
          FakeRemovableAgentsSource(
              rows: [agentRowFor('codex', FakeAgentStage.needsCheck)],
            )
            ..removable.add('codex')
            ..nextCheck = AgentPhoneCheckResult(
              agentId: 'codex',
              architecture: AgentArchitecture.arm64,
              passed: false,
              completed: const [AgentPhoneCheckStep.install],
              failure: AgentHostFailure.version,
            );
      await _pump(tester, agents);
      await tester.tap(find.byKey(const ValueKey('agents-fix-codex')));
      await tester.pumpAndSettle();
      expect(find.byKey(const ValueKey('agents-check-again')), findsOneWidget);
      expect(_remove, findsOneWidget);
    });
  });
}
