// The Inbox (vision slice, 2026-10-20; docs/qa/vision-inbox-2026-10-20/
// README.md): everything that waits on the person, across connections, as
// one list cleared top to bottom. Permission asks answer on the row with the
// exact command and the chat's held Undo window; questions open their
// conversation; a watched server's request only opens; nothing waiting is one
// calm line with what finished and was not opened yet.
import 'package:clock/clock.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/chat_feed.dart';
import 'package:opencode_mobile/domain/models.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profile_monitor.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/screens/chat/permission_sheet.dart'
    show requestFirstSeen;
import 'package:opencode_mobile/ui/screens/inbox/inbox_items.dart';
import 'package:opencode_mobile/ui/screens/inbox/inbox_screen.dart';

import '../tool/capture/fixtures.dart';
import 'support/setup_capture_preferences.dart';

final _laptop = ServerProfile(
  id: 'laptop',
  name: 'Laptop',
  baseUrl: 'http://192.168.1.20:4096',
);
final _homePc = ServerProfile(
  id: 'homepc',
  name: 'Home PC',
  baseUrl: 'http://192.168.1.30:4096',
);

class _Api extends CaptureApi {
  _Api() {
    final now = DateTime.now().millisecondsSinceEpoch;
    Session at(String id, String title, int minutesAgo) => Session(
      id: id,
      title: title,
      directory: projectDirectory,
      time: SessionTime(
        created: now - (minutesAgo + 30) * 60000,
        updated: now - minutesAgo * 60000,
      ),
    );
    sessionsById = {
      'ses_old': at('ses_old', 'Fix the old bug', 50),
      'ses_new': at('ses_new', 'Add the new page', 5),
      'ses_ask': at('ses_ask', 'Pick a branch', 20),
      'ses_done': at('ses_done', 'Speed up CI', 40),
    };
    busy = {};
  }

  @override
  ServerCapabilities get capabilities =>
      const ServerCapabilities(permissionRequests: true);
}

class _Controller extends CaptureController {
  _Controller(super.store, {this.watched = false, this.finished = const {}});

  final bool watched;
  final Set<String> finished;
  _Monitor? _monitor;

  @override
  ProfileMonitor get profileMonitor =>
      _monitor ??= _Monitor(store, watched: watched);

  @override
  bool isProfileReadable(String id) => true;

  @override
  bool finishedUnseen(String sessionID) => finished.contains(sessionID);
}

class _Monitor extends ProfileMonitor {
  _Monitor(ProfileStore store, {required this.watched})
    : super(
        store: store,
        createGateway: (_) => throw UnimplementedError(),
        isReadable: (_) => true,
        networkWifi: () async => null,
        alert: (_, _, _, _) async => false,
        dismiss: (_) async => false,
      );

  final bool watched;

  @override
  ProfileAttentionSnapshot snapshotFor(String id) => watched && id == 'homepc'
      ? ProfileAttentionSnapshot(
          profileID: id,
          status: ProfileMonitorStatus.current,
          complete: true,
          checkedAt: DateTime.now(),
          requests: const [
            MonitoredRequest(
              id: 'per_docs',
              sessionID: 'ses_docs',
              kind: MonitoredRequestKind.permission,
              title: 'Update the docs',
            ),
          ],
        )
      : ProfileAttentionSnapshot(
          profileID: id,
          status: ProfileMonitorStatus.disabled,
        );
}

PermissionRequest _bash(String id, String sessionID, String command) =>
    PermissionRequest(
      id: id,
      sessionID: sessionID,
      permission: 'bash',
      patterns: [command],
      metadata: {'command': command},
    );

Future<_Controller> _controller({
  bool watched = false,
  bool waiting = true,
  Set<String> finished = const {},
}) async {
  final prefs = await setupCapturePreferences();
  final store = SeededProfileStore(
    prefs: prefs,
    seeded: [_laptop, if (watched) _homePc],
  );
  final api = _Api();
  final controller = _Controller(store, watched: watched, finished: finished)
    ..api = api
    ..repository = CaptureRepository()
    ..status = StreamStatus.connected
    ..directory = projectDirectory
    ..sessionsById = Map.of(api.sessionsById)
    ..busySessions = {};
  if (waiting) {
    controller.permissions = {
      'per_new': _bash('per_new', 'ses_new', 'npm run build'),
      'per_old': _bash('per_old', 'ses_old', 'npm install'),
    };
    controller.questions = {
      'que_1': PendingQuestion(
        id: 'que_1',
        sessionID: 'ses_ask',
        prompts: const [
          QuestionPrompt(
            title: 'Branch',
            question: 'Which branch should I use?',
            multiple: false,
            custom: true,
            choices: [QuestionChoice(label: 'main', description: '')],
          ),
        ],
      ),
    };
  }
  return controller;
}

Future<void> _pump(WidgetTester tester, ConnectionController controller) async {
  const secure = MethodChannel('plugins.it_nomads.com/flutter_secure_storage');
  tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
    secure,
    (call) async => call.method == 'readAll' ? <String, String>{} : null,
  );
  addTearDown(
    () => tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
      secure,
      null,
    ),
  );
  tester.view.physicalSize = const Size(412, 1400);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  chatScreenFor = (id) => Text('chat $id');
  await tester.pumpWidget(
    captureApp(
      home: InboxScreen(controller: controller),
      boundaryKey: GlobalKey(),
      controller: controller,
    ),
  );
  await tester.pump();
  await tester.pump(const Duration(milliseconds: 300));
}

double _y(WidgetTester tester, String text) =>
    tester.getTopLeft(find.textContaining(text)).dy;

void main() {
  setUp(() => KitLiveBackground.paintWithoutLoops = true);
  tearDown(() => KitLiveBackground.paintWithoutLoops = false);

  testWidgets('every waiting thing, oldest first, with its place and command', (
    tester,
  ) async {
    final controller = await _controller();
    addTearDown(controller.dispose);
    await _pump(tester, controller);
    expect(find.text('Inbox'), findsOneWidget);
    expect(find.text('3 waiting'), findsOneWidget);
    expect(find.textContaining('Fix the old bug'), findsOneWidget);
    expect(find.text('npm install'), findsOneWidget);
    expect(find.text('npm run build'), findsOneWidget);
    expect(find.text(KitBidi.auto('Which branch should I use?')), findsOne);
    // Oldest conversation first: the old bug, the branch question, the page.
    expect(
      _y(tester, 'Fix the old bug'),
      lessThan(_y(tester, 'Pick a branch')),
    );
    expect(
      _y(tester, 'Pick a branch'),
      lessThan(_y(tester, 'Add the new page')),
    );
    // Permissions answer here; the question only opens.
    expect(find.text('Allow once'), findsNWidgets(2));
    expect(find.text('Reject'), findsNWidgets(2));
    expect(find.text('Open'), findsOneWidget);
    expect(inboxCount(controller), 3);
  });

  testWidgets('each permission row says how long it has waited here', (
    tester,
  ) async {
    final controller = await _controller();
    addTearDown(controller.dispose);
    // First seen on this phone 4 minutes ago (the chat card's own clock).
    withClock(
      Clock.fixed(clock.now().subtract(const Duration(minutes: 4, seconds: 5))),
      () => requestFirstSeen(controller, 'per_old'),
    );
    await _pump(tester, controller);
    expect(find.textContaining('waiting\u00A04\u00A0min'), findsOneWidget);
    // The other ask was first seen as the Inbox opened.
    expect(
      find.textContaining('waiting\u00A0less\u00A0than\u00A0a\u00A0minute'),
      findsOneWidget,
    );
  });

  testWidgets('Allow once is held with Undo, then sent; Undo keeps it', (
    tester,
  ) async {
    final controller = await _controller();
    addTearDown(controller.dispose);
    await _pump(tester, controller);
    await tester.tap(find.byKey(const Key('permission-card-allow')).first);
    await tester.pump(const Duration(milliseconds: 300));
    expect(find.text('Undo'), findsOneWidget);
    expect(find.text('2 waiting'), findsOneWidget);
    await tester.tap(find.text('Undo'));
    await tester.pump(const Duration(seconds: 4));
    expect(controller.answered, isEmpty);

    await tester.tap(find.byKey(const Key('permission-card-reject')).first);
    await tester.pump(const Duration(seconds: 4));
    expect(controller.answered, [(id: 'per_old', reply: 'reject')]);
    await tester.pump(const Duration(milliseconds: 400));
    expect(find.text('npm install'), findsNothing);
    expect(find.text('npm run build'), findsOneWidget);
  });

  testWidgets('a watched server only opens: no command, no Allow once', (
    tester,
  ) async {
    final controller = await _controller(watched: true, waiting: false);
    addTearDown(controller.dispose);
    await _pump(tester, controller);
    expect(find.text('Asks for your permission'), findsOneWidget);
    expect(find.textContaining('Home PC'), findsOneWidget);
    expect(find.text('Allow once'), findsNothing);
    expect(find.text('Open'), findsOneWidget);
    expect(inboxCount(controller), 1);
  });

  testWidgets('nothing waiting: one calm line and what finished unopened', (
    tester,
  ) async {
    final controller = await _controller(
      waiting: false,
      finished: {'ses_done'},
    );
    addTearDown(controller.dispose);
    await _pump(tester, controller);
    expect(find.text('Nothing waits for you'), findsOneWidget);
    expect(find.text('Finished, not opened yet'), findsOneWidget);
    expect(find.text(KitBidi.auto('Speed up CI')), findsOneWidget);
    expect(find.text('Open'), findsNothing);
    expect(inboxCount(controller), 0);
  });

  testWidgets('nothing waiting and nothing finished: only the calm line', (
    tester,
  ) async {
    final controller = await _controller(waiting: false);
    addTearDown(controller.dispose);
    await _pump(tester, controller);
    expect(find.text('Nothing waits for you'), findsOneWidget);
    expect(find.text('Finished, not opened yet'), findsNothing);
  });

  testWidgets('a waiting row is never shown as finished', (tester) async {
    final controller = await _controller(finished: {'ses_old'});
    addTearDown(controller.dispose);
    expect(
      inboxFinished(controller).map((item) => item.sessionID),
      isNot(contains('ses_old')),
    );
    expect(
      inboxEntries(controller).whereType<InboxFeedEntry>().first.item.status,
      ChatStatus.needsYou,
    );
  });
}
