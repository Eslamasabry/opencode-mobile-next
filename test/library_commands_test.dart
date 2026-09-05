import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/api/sse.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/screens/library_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

class _CommandsRepository implements ProductRepository {
  @override
  Future<List<CommandInfo>> listCommands() async => [
    const CommandInfo(name: 'review', subtask: false),
  ];

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

class _CommandsApi extends OpenCodeApi {
  _CommandsApi() : super(baseUrl: 'http://localhost');

  int creates = 0;
  Completer<Session>? creation;
  Completer<void>? submission;
  Object? failure;
  final calls =
      <({String session, String args, ModelRef? model, String? variant})>[];

  @override
  Future<Session> createSession() async {
    creates++;
    return creation == null ? Session(id: 'new-chat') : await creation!.future;
  }

  @override
  Future<void> slashCommand(
    String sessionID,
    String command,
    String args, {
    ModelRef? model,
    String? variant,
  }) async {
    calls.add((session: sessionID, args: args, model: model, variant: variant));
    if (failure != null) throw failure!;
    await submission?.future;
  }
}

Future<ConnectionController> _controller(_CommandsApi api) async {
  SharedPreferences.setMockInitialValues({});
  return ConnectionController(
      ProfileStore(prefs: await SharedPreferences.getInstance()),
    )
    ..api = api
    ..repository = _CommandsRepository()
    ..status = StreamStatus.connected;
}

Future<void> _open(WidgetTester tester, ConnectionController controller) async {
  await tester.pumpWidget(
    MaterialApp(
      home: CommandsScreen(controller: controller),
      onGenerateRoute: (settings) => MaterialPageRoute<void>(
        builder: (_) => Scaffold(body: Text(settings.name!)),
      ),
    ),
  );
  await tester.pumpAndSettle();
  await tester.tap(find.text('/review'));
  await tester.pumpAndSettle();
}

final _run = find.byKey(const ValueKey('command-submit'));
final _arguments = find.byKey(const ValueKey('command-arguments'));

void main() {
  testWidgets(
    'new workspace creates a chat and runs without duplicate submission',
    (tester) async {
      final api = _CommandsApi()..submission = Completer<void>();
      final controller = await _controller(api);
      addTearDown(controller.dispose);
      await _open(tester, controller);
      expect(find.text('New chat'), findsOneWidget);
      expect(api.creates, 0);
      await tester.enterText(_arguments, '  pending changes  ');
      await tester.tap(_run);
      await tester.pump();
      await tester.tap(_run);
      await tester.pump();
      expect(api.creates, 1);
      expect(api.calls.single.args, 'pending changes');
      expect(find.byType(AlertDialog), findsOneWidget);
      api.submission!.complete();
      await tester.pumpAndSettle();
      expect(find.text('/chat/new-chat'), findsOneWidget);
    },
  );

  testWidgets(
    'failed command retains arguments and retries the same new chat',
    (tester) async {
      final api = _CommandsApi()..failure = const ProductException('Try again');
      final controller = await _controller(api);
      addTearDown(controller.dispose);
      await _open(tester, controller);
      await tester.enterText(_arguments, 'my arguments');
      await tester.tap(_run);
      await tester.pumpAndSettle();
      expect(find.text('Try again'), findsOneWidget);
      expect(
        tester.widget<TextField>(_arguments).controller!.text,
        'my arguments',
      );
      api.failure = null;
      await tester.tap(_run);
      await tester.pumpAndSettle();
      expect(api.creates, 1);
      expect(api.calls.map((call) => call.session), ['new-chat', 'new-chat']);
      expect(find.text('/chat/new-chat'), findsOneWidget);
    },
  );

  testWidgets('existing chat uses its own model and variant', (tester) async {
    final api = _CommandsApi();
    final controller = await _controller(api);
    addTearDown(controller.dispose);
    controller.sessionsById['existing'] = Session(
      id: 'existing',
      title: 'My chat',
    );
    controller.selectedModel = ModelRef(
      providerID: 'default',
      modelID: 'default',
    );
    controller.sessionModels['existing'] = SessionModelChoice(
      model: ModelRef(providerID: 'provider', modelID: 'chosen'),
      variant: 'high',
    );
    await _open(tester, controller);
    await tester.tap(_run);
    await tester.pumpAndSettle();
    expect(api.creates, 0);
    expect(api.calls.single.session, 'existing');
    expect(api.calls.single.model!.modelID, 'chosen');
    expect(api.calls.single.variant, 'high');
  });

  testWidgets('workspace switch during creation prevents command dispatch', (
    tester,
  ) async {
    final api = _CommandsApi()..creation = Completer<Session>();
    final controller = await _controller(api);
    addTearDown(controller.dispose);
    await _open(tester, controller);
    await tester.tap(_run);
    await tester.pump();
    controller.locationRevision++;
    api.creation!.complete(Session(id: 'old-workspace-chat'));
    await tester.pumpAndSettle();
    expect(api.calls, isEmpty);
    expect(find.textContaining('server or workspace changed'), findsOneWidget);
    await tester.tap(_run);
    await tester.pumpAndSettle();
    expect(api.creates, 1);
    expect(api.calls, isEmpty);
  });
}
