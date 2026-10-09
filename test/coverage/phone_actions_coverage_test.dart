// Action gate for phone setup, installs and the agents on this phone: every
// call that CHANGES something (the native setup runner, the Termux manager,
// the setup engine, the agent host) and every control on the setup and agent
// screens has a decision in test/fixtures/coverage/phone_actions_ledger.json:
//
//   reachable: <screen> > <control>   a tap path here checks the control
//                                     exists and does the thing, or `proof:`
//                                     names an existing test that does (the
//                                     ratchet checks it is still there)
//   not offered: <reason>             why no control calls it
//
// A dangerous act (remove an agent, sign out) must ask first and name its
// target: the tap paths below check the question says the agent's name.
import 'dart:convert';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/agent_auth_probe.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/screens/agents/agents_section.dart';

import '../../tool/capture/fixtures.dart' show loadCaptureFonts;
import '../goldens/kit/kit_gallery.dart' show loadKitGalleryFonts;
import '../support/agents_fakes.dart';
import '../support/chats_fakes.dart';
import 'servers_support.dart' show frames;

Map<String, String> _load(String name) =>
    (jsonDecode(File('test/fixtures/coverage/$name').readAsStringSync()) as Map)
        .cast<String, String>();

final _ledger = _load('phone_actions_ledger.json');
final _wire =
    ((jsonDecode(
                  File(
                    'test/fixtures/coverage/phone_actions_samples.json',
                  ).readAsStringSync(),
                )
                as Map)['wire']
            as List)
        .cast<String>();

/// Every control the setup and agent screens offer, by the name the ledger uses.
const _app = [
  'app agent.cancel-check',
  'app agent.cancel-install',
  'app agent.cancel-sign-in-button',
  'app agent.check',
  'app agent.check-all',
  'app agent.choose',
  'app agent.close-app',
  'app agent.install',
  'app agent.model',
  'app agent.new-chat-replace',
  'app agent.remove',
  'app agent.remove-claude',
  'app agent.resume',
  'app agent.sign-in',
  'app agent.sign-in-again',
  'app agent.sign-out',
  'app agent.sign-out-unqualified',
  'app agent.switch-builtin',
  'app agent.terminal-again',
  'app agent.terminal-copy-code',
  'app agent.terminal-leave',
  'app agent.terminal-open-page',
  'app setup.add-tools',
  'app setup.cancel',
  'app setup.continue',
  'app setup.customize',
  'app setup.open-ready',
  'app setup.report-failure',
  'app setup.run',
  'app setup.storage-settings',
  'app setup.termux-allow',
  'app setup.termux-get',
  'app setup.termux-update',
  'app setup.uninstall-linux',
  'app setup.voice-remove',
  'app storage.allow-files',
  'app storage.restart-open',
];

final _paths = <String, Future<void> Function(WidgetTester)>{};

void path(List<String> keys, Future<void> Function(WidgetTester tester) body) {
  for (final key in keys) {
    _paths[key] = body;
  }
  testWidgets('action · ${keys.first}', (tester) async {
    tester.view.physicalSize = const Size(412, 1400);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    await body(tester);
    await tester.pumpWidget(const SizedBox.shrink());
  });
}

const _signedIn = AgentAuthProbeResult(state: AgentAuthProbeState.signedIn);

/// Settings > Agents over a source that plays the controller's part.
Future<void> openAgents(
  WidgetTester tester,
  FakePhoneAgentsSource agents,
) async {
  final host = FakeChatsHost(
    FakeChatFeedSource(
      items: const [],
      projects: [project('alpha')],
      lastUsed: '/root/projects/alpha',
    ),
  )..phoneAgents = agents;
  await tester.pumpWidget(
    chatsApp(host, ListView(children: const [AgentsSection()])),
  );
  await frames(tester, 12);
}

FakeRemovableAgentsSource readyCodex() {
  final agents = FakeRemovableAgentsSource(
    rows: [agentRowFor('codex', FakeAgentStage.ready)],
  )..removable.add('codex');
  agents.signOutCapable.add('codex');
  agents.check('codex', _signedIn);
  return agents;
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(() async {
    await loadKitGalleryFonts();
    await loadCaptureFonts();
  });

  test('every mutating call and control has a decision', () {
    final wanted = {..._wire, ..._app};
    expect(
      wanted.difference(_ledger.keys.toSet()),
      isEmpty,
      reason: 'calls and controls with no entry in phone_actions_ledger.json',
    );
    expect(
      _ledger.keys.toSet().difference(wanted),
      isEmpty,
      reason: 'ledger entries for calls or controls that no longer exist',
    );
    for (final entry in _ledger.entries) {
      final ok =
          entry.value.startsWith('reachable: ') ||
          (entry.value.startsWith('not offered: ') && entry.value.length > 30);
      expect(ok, isTrue, reason: '${entry.key}: reachable / not offered');
    }
  });

  test('a proof names a test that still exists', () {
    for (final entry in _ledger.entries) {
      final at = entry.value.indexOf('; proof: ');
      if (at < 0) continue;
      final parts = entry.value
          .substring(at + '; proof: '.length)
          .split(' :: ');
      expect(parts, hasLength(2), reason: entry.key);
      final file = File(parts[0]);
      expect(file.existsSync(), isTrue, reason: '${entry.key}: ${parts[0]}');
      expect(
        file.readAsStringSync(),
        contains(parts[1]),
        reason: '${entry.key}: no test called "${parts[1]}" in ${parts[0]}',
      );
    }
  });

  test('every reachable control without a proof has a tap path here', () {
    final tapped = {
      for (final entry in _ledger.entries)
        if (entry.value.startsWith('reachable: ') &&
            !entry.value.contains('; proof: '))
          entry.key,
    };
    expect(
      tapped.difference(_paths.keys.toSet()),
      isEmpty,
      reason: '"reachable" with no tap path and no proof',
    );
    expect(
      _paths.keys.toSet().difference(tapped),
      isEmpty,
      reason: 'tap paths the ledger does not call "reachable"',
    );
  });

  path(['app agent.install', 'agents installAgent'], (tester) async {
    final agents = FakePhoneAgentsSource(
      rows: [agentRowFor('codex', FakeAgentStage.notInstalled)],
    );
    await openAgents(tester, agents);
    await tester.tap(find.byKey(const ValueKey('agents-fix-codex')));
    await frames(tester, 10);
    await tester.tap(find.byKey(const ValueKey('agents-install')));
    await frames(tester, 10);
    expect(agents.calls, contains('install:codex'));
  });

  path(['app agent.cancel-install', 'agents cancelAgentInstall'], (
    tester,
  ) async {
    final agents = FakePhoneAgentsSource(
      rows: [agentRowFor('codex', FakeAgentStage.notInstalled)],
    );
    await openAgents(tester, agents);
    await tester.tap(find.byKey(const ValueKey('agents-fix-codex')));
    await frames(tester, 10);
    await tester.tap(find.byKey(const ValueKey('agents-install')));
    await frames(tester, 10);
    await tester.tap(find.byKey(const ValueKey('agents-cancel-setup')));
    await frames(tester, 10);
    expect(agents.calls, contains('cancel-install'));
  });

  path(['app agent.sign-out', 'agents signOutAgent'], (tester) async {
    final agents = readyCodex();
    await openAgents(tester, agents);
    await tester.tap(find.byKey(const ValueKey('agents-row-codex')));
    await frames(tester, 10);
    await tester.tap(find.byKey(const ValueKey('agents-sign-out')));
    await frames(tester, 10);
    // The question names the agent before anything happens.
    expect(find.text('Sign out of ${KitBidi.auto('Codex')}?'), findsOneWidget);
    expect(agents.calls, isNot(contains('sign-out:codex')));
    await tester.tap(find.byKey(const ValueKey('agents-sign-out-confirm')));
    await frames(tester, 12);
    expect(agents.calls, contains('sign-out:codex'));
  });

  path(['app agent.remove', 'agents removeAgent'], (tester) async {
    final agents = readyCodex();
    await openAgents(tester, agents);
    await tester.tap(find.byKey(const ValueKey('agents-row-codex')));
    await frames(tester, 10);
    await tester.tap(find.byKey(const ValueKey('agents-remove')));
    await frames(tester, 10);
    // The question names the agent and says what stays, before anything goes.
    expect(find.text('Remove ${KitBidi.auto('Codex')}?'), findsOneWidget);
    expect(
      find.textContaining('accounts and conversations stay'),
      findsOneWidget,
    );
    expect(agents.calls, isNot(contains('remove:codex')));
    await tester.tap(find.byKey(const ValueKey('agents-remove-confirm')));
    await frames(tester, 12);
    expect(agents.calls, contains('remove:codex'));
  });
}
