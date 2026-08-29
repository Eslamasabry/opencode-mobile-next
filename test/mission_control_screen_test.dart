import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/api/sse.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/screens/mission_control_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

class _Repository implements ProductRepository {
  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

class _Controller extends ConnectionController {
  _Controller(super.store);

  int prepareCalls = 0;
  int refreshCalls = 0;
  final preparedRepositories = <ServerOperationsGateway?>[];

  @override
  Future<ServerOperationsGateway?> prepareActionRepository() async {
    prepareCalls += 1;
    preparedRepositories.add(repository);
    return repository;
  }

  @override
  Future<void> refreshSessions() async {
    refreshCalls += 1;
  }
}

Session _session(
  String id, {
  String? title,
  String? parentID,
  int updated = 0,
}) => Session(
  id: id,
  title: title,
  parentID: parentID,
  directory: '/work/oc_app',
  time: SessionTime(created: updated - 10, updated: updated),
);

Future<_Controller> _controller({bool seed = true}) async {
  SharedPreferences.setMockInitialValues({});
  final preferences = await SharedPreferences.getInstance();
  final controller = _Controller(ProfileStore(prefs: preferences))
    ..repository = _Repository()
    ..status = StreamStatus.connected;
  if (seed) {
    final now = DateTime.now().millisecondsSinceEpoch;
    controller.sessionsById = {
      'ses_run': _session('ses_run', title: 'Build feature', updated: now),
      'ses_child': _session('ses_child', parentID: 'ses_run', updated: now),
      'ses_idle': _session(
        'ses_idle',
        title: 'Yesterday cleanup',
        updated: now - 60000,
      ),
    };
    controller.busySessions = {'ses_run'};
    controller.permissions = {
      'perm-1': PermissionRequest(
        id: 'perm-1',
        sessionID: 'ses_run',
        permission: 'edit',
        patterns: const ['lib/main.dart'],
      ),
    };
    controller.questions = {
      'q-1': const PendingQuestion(
        id: 'q-1',
        sessionID: 'ses_idle',
        prompts: [
          QuestionPrompt(
            title: 'Direction',
            question: 'Proceed?',
            multiple: false,
            custom: true,
            choices: [],
          ),
        ],
      ),
    };
  }
  return controller;
}

Widget _app(Widget home, {Map<String, WidgetBuilder> routes = const {}}) =>
    MaterialApp(home: home, routes: routes);

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('sections render running, attention, and recent truth', (
    tester,
  ) async {
    final controller = await _controller();
    addTearDown(controller.dispose);
    await tester.pumpWidget(
      _app(MissionControlScreen(controller: controller)),
    );

    expect(find.text('Needs attention'.toUpperCase()), findsOneWidget);
    expect(find.text('Permission · edit'), findsOneWidget);
    expect(find.text('Question · Direction'), findsOneWidget);
    expect(find.byKey(const ValueKey('mission-running-ses_run')), findsOneWidget);
    expect(find.byKey(const ValueKey('mission-recent-ses_idle')), findsOneWidget);
    // The running root shows its cached subagent count.
    expect(
      find.descendant(
        of: find.byKey(const ValueKey('mission-running-ses_run')),
        matching: find.text('1'),
      ),
      findsOneWidget,
    );
    // Children never appear as their own rows.
    expect(find.byKey(const ValueKey('mission-recent-ses_child')), findsNothing);
    expect(find.byKey(const ValueKey('mission-all-sessions')), findsOneWidget);
  });

  testWidgets('attention and running rows open the exact chat', (
    tester,
  ) async {
    final controller = await _controller();
    addTearDown(controller.dispose);
    await tester.pumpWidget(
      _app(
        MissionControlScreen(controller: controller),
        routes: {
          '/chat/ses_run': (_) =>
              Scaffold(appBar: AppBar(), body: const Text('run chat')),
          '/chat/ses_idle': (_) =>
              Scaffold(appBar: AppBar(), body: const Text('idle chat')),
        },
      ),
    );

    // Bounded pumps throughout: Mission Control's live spinner never
    // settles while it is the visible route.
    await tester.tap(find.text('Permission · edit'));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 600));
    expect(find.text('run chat'), findsOneWidget);

    await tester.pageBack();
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 600));
    await tester.tap(find.byKey(const ValueKey('mission-recent-ses_idle')));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 600));
    expect(find.text('idle chat'), findsOneWidget);
  });

  testWidgets('refresh reconciles the wake transport before querying', (
    tester,
  ) async {
    final controller = await _controller();
    addTearDown(controller.dispose);
    await tester.pumpWidget(
      _app(MissionControlScreen(controller: controller)),
    );

    // Android wake replaced the repository; the refresh must resolve the
    // replacement before the session query runs.
    final replacement = _Repository();
    controller.repository = replacement;
    controller.notifyListeners();
    await tester.pump();

    // Bounded pumps: the Running row's live spinner never settles.
    await tester.tap(find.byTooltip('Refresh'));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 50));

    expect(controller.prepareCalls, 1);
    expect(controller.refreshCalls, 1);
    expect(controller.preparedRepositories.single, same(replacement));
  });

  testWidgets('an empty fleet invites action', (tester) async {
    final controller = await _controller(seed: false);
    addTearDown(controller.dispose);
    await tester.pumpWidget(
      _app(MissionControlScreen(controller: controller)),
    );

    expect(find.text('Nothing in flight'), findsOneWidget);
    expect(find.text('All sessions'), findsOneWidget);
  });

  testWidgets('mission control fits a 320dp phone at 2x text', (tester) async {
    await tester.binding.setSurfaceSize(const Size(320, 640));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    final controller = await _controller();
    addTearDown(controller.dispose);
    await tester.pumpWidget(
      MaterialApp(
        builder: (context, child) => MediaQuery(
          data: MediaQuery.of(
            context,
          ).copyWith(textScaler: const TextScaler.linear(2)),
          child: child!,
        ),
        home: MissionControlScreen(controller: controller),
      ),
    );

    expect(find.byKey(const ValueKey('mission-running-ses_run')), findsOneWidget);
    expect(tester.takeException(), isNull);
  });
}
