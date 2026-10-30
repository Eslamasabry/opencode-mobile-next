// Continue with… (docs/design/agent-handoff-contract.md): the sheet's states
// over real handoff controllers and fake gateways. Nothing exists on the
// target until "Start and continue"; a model of the same server is a switch;
// a result that is not confirmed never offers a second start.
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/agent_handoff.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/agent_handoff_links.dart';
import 'package:opencode_mobile/state/continue_with.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/widgets/continue_with_sheet.dart';
import 'package:opencode_mobile/ui/widgets/session_menu.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'support/continue_with_fakes.dart';

Future<ValueNotifier<ContinueWithResult?>> _open(
  WidgetTester tester,
  FakeContinueWithHost host, {
  bool opened = true,
}) async {
  final result = ValueNotifier<ContinueWithResult?>(null);
  addTearDown(result.dispose);
  tester.view.physicalSize = const Size(412, 1800);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  await tester.pumpWidget(
    MaterialApp(
      theme: AppTheme.dark(),
      localizationsDelegates: AppLocalizations.localizationsDelegates,
      supportedLocales: AppLocalizations.supportedLocales,
      home: Builder(
        builder: (context) => Scaffold(
          body: Center(
            child: TextButton(
              key: const Key('open'),
              onPressed: () async {
                result.value = await showContinueWithSheet(context, host: host);
              },
              child: const Text('open'),
            ),
          ),
        ),
      ),
    ),
  );
  if (opened) {
    await tester.tap(find.byKey(const Key('open')));
    await tester.pumpAndSettle();
  }
  return result;
}

Finder _text(String text) => find.text(text);

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  late SharedPreferences prefs;
  setUp(() async => prefs = await FakeContinueWithHost.prefsWithProfiles());

  testWidgets('lists models of this server and agents with their reason', (
    tester,
  ) async {
    final host = FakeContinueWithHost(
      prefs,
      agentBlock: ContinueWithBlock.needsSignIn,
    );
    final result = await _open(tester, host);
    expect(_text('Continue this work'), findsOneWidget);
    expect(_text('Repair parser · project'), findsOneWidget);
    expect(_text('Models on this server'), findsOneWidget);
    expect(_text('GPT-6 Terra'), findsOneWidget);
    expect(_text('Agents on this phone'), findsOneWidget);
    expect(_text('Sign in to use Claude Code.'), findsOneWidget);
    // A missing sign-in uses the existing sign-in flow: the sheet closes with
    // the agent to sign in, it never opens another sheet over itself.
    await tester.tap(
      find.byKey(const ValueKey('continue-with-sign-in-claude')),
    );
    await tester.pumpAndSettle();
    expect(result.value?.signInAgentId, 'claude');
    expect(host.target.mutations, isEmpty);
  });

  testWidgets('an agent that is not ready yet can be read again', (
    tester,
  ) async {
    final host = FakeContinueWithHost(
      prefs,
      agentBlock: ContinueWithBlock.notReady,
    );
    await _open(tester, host);
    expect(_text("Claude Code isn't ready on this phone yet."), findsOneWidget);
    expect(host.loads, 1);
    await tester.tap(_text('Try again'));
    await tester.pumpAndSettle();
    expect(host.loads, 2);
    expect(host.target.mutations, isEmpty);
  });

  testWidgets('a model of this server keeps the conversation and switches it', (
    tester,
  ) async {
    final host = FakeContinueWithHost(prefs);
    final result = await _open(tester, host);
    await tester.tap(_text('GPT-6 Terra'));
    await tester.pumpAndSettle();
    expect(_text('Your conversation stays here.'), findsOneWidget);
    expect(_text('Conversation record'), findsNothing);
    expect(_text('Use this model'), findsOneWidget);
    // No record, no conversation, no link: nothing is prepared for a switch.
    expect(host.source.mutations, isEmpty);
    expect(host.target.mutations, isEmpty);
    await tester.tap(_text('Use this model'));
    await tester.pumpAndSettle();
    expect(host.switched, ['GPT-6 Terra']);
    expect(find.byKey(const ValueKey('continue-with-sheet')), findsNothing);
    expect(result.value, isNull);
    expect(prefs.containsKey('oc.agentHandoffs.p'), isFalse);
  });

  testWidgets('an agent prepares a reviewable record, then starts', (
    tester,
  ) async {
    final host = FakeContinueWithHost(prefs);
    final result = await _open(tester, host);
    await tester.tap(_text('Opus 5.5'));
    await tester.pumpAndSettle();
    expect(
      _text('Start a new conversation with a record of this one.'),
      findsOneWidget,
    );
    expect(_text('This conversation stays available.'), findsOneWidget);
    expect(_text('Conversation record'), findsOneWidget);
    expect(
      find.textContaining('Saved as .handoff/review-record/record.md'),
      findsOneWidget,
    );
    // Preparing is a read: nothing is written until Start and continue.
    expect(host.target.mutations, isEmpty);

    // The preview unfolds on request.
    expect(find.byKey(const ValueKey('handoff-record-preview')), findsNothing);
    await tester.tap(find.byKey(const ValueKey('handoff-record-toggle')));
    await tester.pumpAndSettle();
    expect(
      find.byKey(const ValueKey('handoff-record-preview')),
      findsOneWidget,
    );

    // The first message starts as the draft and is masked as it is edited.
    final field = find.byKey(const ValueKey('continue-with-message'));
    expect(field, findsOneWidget);
    await tester.enterText(
      field,
      'Continue from .handoff/review-record/record.md. api_key=synthetic-sensitive-value',
    );
    await tester.pumpAndSettle();
    expect(find.textContaining('synthetic-sensitive-value'), findsNothing);

    await tester.tap(find.byKey(const ValueKey('continue-with-start')));
    await tester.pumpAndSettle();
    expect(host.target.mutations, ['write', 'create', 'prompt']);
    expect(host.target.sentText, contains('.handoff/review-record/record.md'));
    expect(host.target.sentText, isNot(contains('synthetic-sensitive-value')));
    expect(host.adopted, ['draft-new']);
    expect(result.value?.sessionId, 'draft-new');
    expect(
      AgentHandoffLinks.forPreferences(prefs).forSession('p', 'durable-source'),
      hasLength(1),
    );
  });

  testWidgets('Cancel closes without any side effect', (tester) async {
    final host = FakeContinueWithHost(prefs);
    final result = await _open(tester, host);
    await tester.tap(_text('Opus 5.5'));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('continue-with-cancel')));
    await tester.pumpAndSettle();
    expect(find.byKey(const ValueKey('continue-with-sheet')), findsNothing);
    expect(result.value, isNull);
    expect(host.target.mutations, isEmpty);
    expect(host.adopted, isEmpty);
  });

  testWidgets('a conversation that cannot be read says so in plain words', (
    tester,
  ) async {
    final host = FakeContinueWithHost(prefs)
      ..source.sessionError = StateError('synthetic-private-server-detail');
    await _open(tester, host);
    await tester.tap(_text('Opus 5.5'));
    await tester.pumpAndSettle();
    expect(
      _text("Couldn't read the conversation. Try preparing it again."),
      findsOneWidget,
    );
    expect(find.textContaining('synthetic-private'), findsNothing);
    expect(
      find.byKey(const ValueKey('continue-with-prepare-again')),
      findsOneWidget,
    );
    expect(host.target.mutations, isEmpty);
  });

  testWidgets('an unconfirmed delivery never offers another start', (
    tester,
  ) async {
    final host = FakeContinueWithHost(prefs);
    host.target.promptError = StateError('synthetic-private-server-detail');
    await _open(tester, host);
    await tester.tap(_text('Opus 5.5'));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('continue-with-start')));
    await tester.pumpAndSettle();
    expect(
      _text(
        "The result isn't confirmed. Check the target conversations before trying again.",
      ),
      findsOneWidget,
    );
    expect(find.textContaining('synthetic-private'), findsNothing);
    expect(host.target.mutations, ['write', 'create', 'prompt']);
    // The list is back, but the agent row cannot start a second attempt.
    await tester.tap(find.byKey(const ValueKey('continue-with-change')));
    await tester.pumpAndSettle();
    await tester.tap(_text('Opus 5.5'));
    await tester.pumpAndSettle();
    expect(host.target.mutations, ['write', 'create', 'prompt']);
  });

  testWidgets('a link that could not be saved is retried without resending', (
    tester,
  ) async {
    final host = FakeContinueWithHost(prefs);
    final result = await _open(tester, host);
    await tester.tap(_text('Opus 5.5'));
    await tester.pumpAndSettle();
    final disk = RefusingLinksDisk.current!;
    disk.refuseLinks = true;
    await tester.tap(find.byKey(const ValueKey('continue-with-start')));
    await tester.pumpAndSettle();
    expect(
      _text("The conversation started, but its link couldn't be saved."),
      findsOneWidget,
    );
    expect(find.byKey(const ValueKey('continue-with-open')), findsOneWidget);
    disk.refuseLinks = false;
    await tester.tap(find.byKey(const ValueKey('continue-with-save-link')));
    await tester.pumpAndSettle();
    expect(result.value?.sessionId, 'draft-new');
    // One conversation, one prompt: the retry only wrote the link.
    expect(host.target.mutations, ['write', 'create', 'prompt']);
  });

  testWidgets('a list that cannot load offers Try again', (tester) async {
    final host = FakeContinueWithHost(prefs, loadError: StateError('x'));
    await _open(tester, host);
    expect(_text("Couldn't load the agents. Try again."), findsOneWidget);
    expect(find.byKey(const ValueKey('continue-with-reload')), findsOneWidget);
  });

  test('the conversation menu offers Continue with…', () {
    final items = sessionMenuItems(
      lookupAppLocalizations(const Locale('en')),
      SessionMenuOffer.of(
        const ServerCapabilities(),
        shared: false,
        savedServer: true,
      ),
      onSelected: (_) {},
    );
    expect(
      items.where((i) => i.key == const ValueKey('session-menu-continue-with')),
      hasLength(1),
    );
    expect(items.map((i) => i.label), contains('Continue with…'));
  });

  testWidgets('the link line opens the other conversation', (tester) async {
    var opened = 0;
    await tester.pumpWidget(
      MaterialApp(
        theme: AppTheme.dark(),
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: Scaffold(
          body: KitContinuedLine(
            text: 'Continued in Claude Code',
            onOpen: () => opened++,
          ),
        ),
      ),
    );
    await tester.tap(find.byKey(const ValueKey('continued-line')));
    expect(opened, 1);
  });

  testWidgets('a deleted conversation reads as gone and cannot be opened', (
    tester,
  ) async {
    var opened = 0;
    await tester.pumpWidget(
      MaterialApp(
        theme: AppTheme.dark(),
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: Scaffold(
          body: KitContinuedLine(
            text: 'Continued in Claude Code',
            gone: true,
            onOpen: () => opened++,
          ),
        ),
      ),
    );
    expect(_text('This conversation is no longer available.'), findsOneWidget);
    await tester.tap(find.byKey(const ValueKey('continued-line')));
    expect(opened, 0);
  });

  test(
    'link lines name the agent on the source and the title on the new one',
    () async {
      final host = FakeContinueWithHost(prefs);
      final controller = host.controllerFor(
        (await host.load()).agents.single,
        isCurrent: () => true,
        onChanged: () {},
      );
      await controller.prepare(lastState: host.lastState);
      await controller.send(controller.draft!.prompt);
      final links = AgentHandoffLinks.forPreferences(prefs);
      final source = continueWithLinkLines(
        links,
        profileId: 'p',
        sessionId: 'durable-source',
        origin: HandoffOrigin.server,
      );
      expect(source.single.outgoing, isTrue);
      expect(source.single.agent, 'Claude Code');
      final target = continueWithLinkLines(
        links,
        profileId: 'p',
        sessionId: 'durable-draft-new',
        origin: HandoffOrigin.phoneAgent,
      );
      expect(target.single.outgoing, isFalse);
      expect(target.single.title, 'Repair parser');
    },
  );
}
