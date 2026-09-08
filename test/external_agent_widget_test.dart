import 'dart:async';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/external_agent.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/external_agents.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/screens/external_agents_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../tool/capture/fixtures.dart' show loadCaptureFonts, capturePng;
import 'external_agent_state_test.dart'
    show FakeExternalGateway, agentCard, waitingTask, completeTask;

class _RefusingDraftStore extends ExternalAgentStore {
  bool refuse = true;
  _RefusingDraftStore(super.prefs, super.secure);
  @override
  Future<void> saveDraft(String id, String localId, String text) {
    if (refuse) {
      return Future.error(
        const ExternalAgentException(ExternalAgentIssue.storage),
      );
    }
    return super.saveDraft(id, localId, text);
  }
}

class _HeldDraftStore extends ExternalAgentStore {
  final gates = [Completer<void>(), Completer<void>()];
  int writes = 0;
  Future<void> _tail = Future.value();
  _HeldDraftStore(super.prefs, super.secure);
  @override
  Future<void> saveDraft(String id, String localId, String text) {
    final at = writes++;
    final gate = at < gates.length ? gates[at].future : Future<void>.value();
    final result = _tail.then((_) async {
      await gate;
      await super.saveDraft(id, localId, text);
    });
    _tail = result;
    return result;
  }
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  late ExternalAgentStore store;
  late FakeExternalGateway gateway;
  setUp(() async {
    final secrets = <String, String>{};
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
          const MethodChannel('plugins.it_nomads.com/flutter_secure_storage'),
          (call) async {
            final args = Map<String, dynamic>.from(call.arguments as Map);
            switch (call.method) {
              case 'write':
                secrets[args['key'] as String] = args['value'] as String;
                return null;
              case 'read':
                return secrets[args['key']];
              case 'delete':
                secrets.remove(args['key']);
                return null;
              default:
                return null;
            }
          },
        );
    SharedPreferences.setMockInitialValues({});
    store = ExternalAgentStore(
      await SharedPreferences.getInstance(),
      const FlutterSecureStorage(),
    );
    gateway = FakeExternalGateway();
  });
  tearDown(() => store.dispose());
  Widget app(
    Widget child, {
    bool dark = false,
    double scale = 1,
    GlobalKey? boundary,
  }) => MaterialApp(
    theme: dark ? AppTheme.dark() : AppTheme.light(),
    localizationsDelegates: AppLocalizations.localizationsDelegates,
    supportedLocales: AppLocalizations.supportedLocales,
    builder: (context, child) => MediaQuery(
      data: MediaQuery.of(
        context,
      ).copyWith(textScaler: TextScaler.linear(scale)),
      child: RepaintBoundary(key: boundary, child: child!),
    ),
    home: child,
  );
  Future<void> tap(WidgetTester tester, String text) async {
    final matches = find.text(text);
    if (matches.evaluate().isEmpty) {
      await tester.scrollUntilVisible(
        matches,
        250,
        scrollable: find.byType(Scrollable).first,
      );
    }
    final target = matches.last;
    await tester.ensureVisible(target);
    await tester.pumpAndSettle();
    await tester.ensureVisible(target);
    await tester.tap(target);
    await tester.pumpAndSettle();
  }

  testWidgets(
    'inspect save explicit task input result reopen and delete journey',
    (tester) async {
      await tester.pumpWidget(
        app(ExternalAgentsScreen(store: store, gatewayFactory: () => gateway)),
      );
      await tap(tester, 'Add agent');
      await tester.enterText(
        find.byType(TextField).first,
        'https://agent.example',
      );
      await tap(tester, 'Inspect Agent Card');
      expect(find.text('Advertised skills'), findsOneWidget);
      final bearer = find.widgetWithText(TextField, 'Agent bearer credential');
      await tester.scrollUntilVisible(
        bearer,
        250,
        scrollable: find.byType(Scrollable).first,
      );
      await tester.enterText(bearer, 'fixture-token');
      await tap(tester, 'Save agent');
      expect(store.profiles.length, 1);
      await tap(tester, 'Color agent');
      await tap(tester, 'New task');
      await tester.enterText(find.byType(TextField), 'Choose a color');
      await tap(tester, 'Review task');
      expect(gateway.sends, 0);
      final edited =
          'Choose a color. ${'Keep the full design requirement. ' * 20}';
      await tester.enterText(find.byType(TextField), edited);
      await tester.pumpAndSettle();
      await tester.pageBack();
      await tester.pumpAndSettle();
      await tap(tester, 'Choose a color');
      expect(
        tester.widget<TextField>(find.byType(TextField)).controller!.text,
        edited,
      );
      expect(store.tasks(store.profiles.single.id).single.draft, edited);
      expect(gateway.sends, 0);
      await tap(tester, 'Send to agent');
      expect(gateway.sends, 1);
      expect(store.tasks(store.profiles.single.id).single.draft, isEmpty);
      expect(find.text('Your input is needed'), findsOneWidget);
      await tester.enterText(find.byType(TextField), 'Blue');
      gateway.next = completeTask;
      await tap(tester, 'Reply to this task');
      expect(gateway.continuation?.id, waitingTask.id);
      expect(find.text('Blue result'), findsOneWidget);
      await tester.pageBack();
      await tester.pumpAndSettle();
      await tap(tester, 'Choose a color');
      expect(gateway.queries, greaterThanOrEqualTo(1));
      expect(gateway.sends, 2);
      await tap(tester, 'Forget saved task');
      await tap(tester, 'Delete local data');
      expect(store.tasks(store.profiles.single.id), isEmpty);
      await tap(tester, 'Delete agent');
      await tap(tester, 'Delete local data');
      expect(store.profiles, isEmpty);
      await tester.pumpWidget(const SizedBox());
      await tester.pumpAndSettle();
      expect(tester.takeException(), isNull);
    },
  );
  testWidgets(
    'uncertain delivery has no enabled send and restart does not resend',
    (tester) async {
      final profile = await store.add(agentCard, 'fixture-token');
      final record = ExternalTaskRecord(
        localId: 'uncertain',
        title: 'Choose a color',
        created: DateTime(2026, 9, 8),
        uncertain: true,
      );
      await store.saveTask(profile.id, record);
      await tester.pumpWidget(
        app(
          ExternalTaskScreen(
            store: store,
            profile: profile,
            record: record,
            gateway: gateway,
          ),
        ),
      );
      await tester.pumpAndSettle();
      expect(find.text('Delivery unconfirmed'), findsOneWidget);
      expect(find.text('Send to agent'), findsNothing);
      expect(gateway.sends, 0);
      expect(gateway.queries, 0);
      await tester.pumpWidget(const SizedBox());
    },
  );
  testWidgets('cancel keeps active state when agent does not confirm', (
    tester,
  ) async {
    final profile = await store.add(agentCard, 'fixture-token');
    final record = ExternalTaskRecord(
      localId: 'task',
      title: 'Choose a color',
      created: DateTime(2026, 9, 8),
      task: waitingTask,
    );
    await store.saveTask(profile.id, record);
    gateway.cancelResult = waitingTask;
    await tester.pumpWidget(
      app(
        ExternalTaskScreen(
          store: store,
          profile: profile,
          record: record,
          gateway: gateway,
        ),
      ),
    );
    await tester.pumpAndSettle();
    await tap(tester, 'Cancel task');
    await tap(tester, 'Request cancellation');
    expect(gateway.cancels, 1);
    expect(
      find.textContaining('Cancellation is not confirmed'),
      findsOneWidget,
    );
    expect(find.text('Canceled'), findsNothing);
    await tester.pumpWidget(const SizedBox());
  });
  testWidgets(
    'failed draft save keeps review text and blocks Back and Send until retry',
    (tester) async {
      final refusing = _RefusingDraftStore(store.prefs, store.secure);
      store.dispose();
      store = refusing;
      final profile = await store.add(agentCard, 'fixture-token');
      final record = ExternalTaskRecord(
        localId: 'draft',
        title: 'Review task',
        created: DateTime(2026, 9, 8),
        draft: 'Original draft',
      );
      await store.saveTask(profile.id, record);
      await tester.pumpWidget(
        app(
          ExternalAgentDetailScreen(
            store: store,
            profile: profile,
            gatewayFactory: () => gateway,
          ),
        ),
      );
      await tap(tester, 'Review task');
      await tester.enterText(find.byType(TextField), 'Edited draft to keep');
      await tester.pumpAndSettle();
      expect(
        find.textContaining('Draft changes could not be saved'),
        findsOneWidget,
      );
      await tester.pageBack();
      await tester.pumpAndSettle();
      expect(find.byType(ExternalTaskScreen), findsOneWidget);
      await tester.scrollUntilVisible(
        find.text('Send to agent'),
        200,
        scrollable: find.byType(Scrollable).first,
      );
      final send = tester.widget<FilledButton>(
        find.ancestor(
          of: find.text('Send to agent'),
          matching: find.byWidgetPredicate((widget) => widget is FilledButton),
        ),
      );
      expect(send.onPressed, isNull);
      expect(gateway.sends, 0);
      expect(
        tester.widget<TextField>(find.byType(TextField)).controller!.text,
        'Edited draft to keep',
      );
      refusing.refuse = false;
      await tap(tester, 'Retry saving draft');
      await tester.pageBack();
      await tester.pumpAndSettle();
      expect(store.tasks(profile.id).single.draft, 'Edited draft to keep');
      await tester.pumpWidget(const SizedBox());
    },
  );
  testWidgets(
    'Back and Send wait for latest A after held B then A draft writes',
    (tester) async {
      final held = _HeldDraftStore(store.prefs, store.secure);
      store.dispose();
      store = held;
      final profile = await store.add(agentCard, 'fixture-token');
      final record = ExternalTaskRecord(
        localId: 'draft',
        title: 'Review task',
        created: DateTime(2026, 9, 8),
        draft: 'A',
      );
      await store.saveTask(profile.id, record);
      await tester.pumpWidget(
        app(
          ExternalAgentDetailScreen(
            store: store,
            profile: profile,
            gatewayFactory: () => gateway,
          ),
        ),
      );
      await tap(tester, 'Review task');
      await tester.enterText(find.byType(TextField), 'B');
      await tester.pump();
      await tester.enterText(find.byType(TextField), 'A');
      await tester.pump();
      expect(held.writes, 2);
      await tester.scrollUntilVisible(
        find.text('Send to agent'),
        200,
        scrollable: find.byType(Scrollable).first,
      );
      final send = tester.widget<FilledButton>(
        find.ancestor(
          of: find.text('Send to agent'),
          matching: find.byWidgetPredicate((widget) => widget is FilledButton),
        ),
      );
      expect(send.onPressed, isNull);
      await tester.pageBack();
      await tester.pumpAndSettle();
      expect(find.byType(ExternalTaskScreen), findsOneWidget);
      held.gates[0].complete();
      await tester.pumpAndSettle();
      expect(store.tasks(profile.id).single.draft, 'B');
      expect(find.byType(ExternalTaskScreen), findsOneWidget);
      expect(
        tester.widget<TextField>(find.byType(TextField)).controller!.text,
        'A',
      );
      held.gates[1].complete();
      await tester.pumpAndSettle();
      expect(find.byType(ExternalTaskScreen), findsNothing);
      final reconstructed = ExternalAgentStore(store.prefs, store.secure);
      expect(reconstructed.tasks(profile.id).single.draft, 'A');
      reconstructed.dispose();
      expect(gateway.sends, 0);
      await tester.pumpWidget(const SizedBox());
    },
  );
  for (final state in ['input', 'result', 'uncertain', 'error', 'draft']) {
    for (final mode in ['light', 'dark', 'large']) {
      testWidgets('capture external task $state $mode', (tester) async {
        await tester.binding.setSurfaceSize(const Size(390, 844));
        addTearDown(() => tester.binding.setSurfaceSize(null));
        final captureDir = Platform.environment['A2A_CAPTURE_DIR'];
        if (captureDir != null) await loadCaptureFonts();
        final boundary = GlobalKey();
        final profile = await store.add(agentCard, 'fixture-token');
        final task = state == 'result'
            ? completeTask
            : state == 'uncertain' || state == 'draft'
            ? null
            : waitingTask;
        final record = ExternalTaskRecord(
          localId: 'capture',
          title: 'Choose a color for the welcome screen',
          created: DateTime(2026, 9, 8),
          task: task,
          uncertain: state == 'uncertain',
          draft: state == 'draft'
              ? 'Use a calm blue accent and keep the welcome screen readable in sunlight.'
              : '',
        );
        await store.saveTask(profile.id, record);
        gateway.next = task ?? waitingTask;
        if (state == 'error') {
          gateway.queryAction = () async => throw const ExternalAgentException(
            ExternalAgentIssue.unavailable,
          );
        }
        await tester.pumpWidget(
          app(
            ExternalTaskScreen(
              store: store,
              profile: profile,
              record: record,
              gateway: gateway,
            ),
            dark: mode == 'dark',
            scale: mode == 'large' ? 1.8 : 1,
            boundary: boundary,
          ),
        );
        await tester.pumpAndSettle();
        expect(tester.takeException(), isNull);
        if (captureDir != null) {
          final bytes = await capturePng(tester, boundary);
          await tester.runAsync(() async {
            await Directory(captureDir).create(recursive: true);
            await File('$captureDir/$state-$mode.png').writeAsBytes(bytes);
          });
          if (state == 'input' && mode == 'large') {
            await tester.scrollUntilVisible(
              find.text('Reply to this task'),
              250,
              scrollable: find.byType(Scrollable).first,
            );
            await tester.pumpAndSettle();
            expect(tester.takeException(), isNull);
            final controls = await capturePng(tester, boundary);
            await tester.runAsync(
              () => File(
                '$captureDir/input-large-controls.png',
              ).writeAsBytes(controls),
            );
          }
        }
        await tester.pumpWidget(const SizedBox());
        await tester.pumpAndSettle();
      });
    }
  }
  testWidgets('capture inspected agent and saved tasks', (tester) async {
    await tester.binding.setSurfaceSize(const Size(390, 844));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    final captureDir = Platform.environment['A2A_CAPTURE_DIR'];
    if (captureDir != null) {
      await loadCaptureFonts();
    }
    final boundary = GlobalKey();
    final profile = await store.add(agentCard, 'fixture-token');
    await store.saveTask(
      profile.id,
      ExternalTaskRecord(
        localId: 'saved',
        title: 'Welcome screen color',
        created: DateTime(2026, 9, 8),
        task: completeTask,
      ),
    );
    await tester.pumpWidget(
      app(
        ExternalAgentDetailScreen(
          store: store,
          profile: profile,
          gatewayFactory: () => gateway,
        ),
        boundary: boundary,
      ),
    );
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);
    if (captureDir != null) {
      final bytes = await capturePng(tester, boundary);
      await tester.runAsync(
        () => File('$captureDir/inspected-agent.png').writeAsBytes(bytes),
      );
    }
    await tester.pumpWidget(const SizedBox());
  });
}
