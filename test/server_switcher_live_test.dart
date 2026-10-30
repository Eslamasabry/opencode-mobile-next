// The server switcher says each saved server's state from what the app
// really holds for it (vision slice 2026-10-20,
// docs/qa/vision-server-switcher-2026-10-20/README.md):
//
// - a server the Conversations list keeps open beside the one in use (its
//   own live connection) shows the permission it waits on, with the exact
//   command, and Allow once answers it on that server's own connection;
// - one that list cannot reach says "Isn't answering" with Try again;
// - a question with options opens its conversation (owner decision 1);
// - a server only the attention monitor watches says "Needs you" with Open,
//   never Allow once (the monitor keeps no command and no way to reply), and
//   when its last read failed, when it checks again (no Try again: the
//   monitor has no "now").
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/chat_feed.dart';
import 'package:opencode_mobile/domain/models.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profile_monitor.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/widgets/server_switcher_sheet.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../tool/capture/fixtures.dart'
    show CaptureApi, CaptureController, SeededProfileStore;

final _laptop = ServerProfile(
  id: 'laptop',
  name: 'Laptop',
  baseUrl: 'http://192.168.1.20:4096',
);
final _studio = ServerProfile(
  id: 'studio',
  name: 'Studio Mac',
  baseUrl: 'https://studio.example.test:4096',
);
final _homePc = ServerProfile(
  id: 'homepc',
  name: 'Home PC',
  baseUrl: 'http://192.168.1.30:4096',
);

class _Api extends CaptureApi {
  _Api({this.asks = true});
  final bool asks;

  @override
  ServerCapabilities get capabilities =>
      ServerCapabilities(permissionRequests: asks);
}

class _Side extends CaptureController {
  _Side(super.store, this._profile);
  final ServerProfile _profile;

  @override
  ServerProfile? get profile => _profile;
}

class _Monitor extends ProfileMonitor {
  _Monitor(ProfileStore store, this.fixed)
    : super(
        store: store,
        createGateway: (_) => throw UnimplementedError(),
        isReadable: (_) => true,
        networkWifi: () async => null,
        alert: (_, _, _, _) async => false,
        dismiss: (_) async => false,
      );
  final Map<String, ProfileAttentionSnapshot> fixed;

  @override
  ProfileAttentionSnapshot snapshotFor(String id) =>
      fixed[id] ??
      ProfileAttentionSnapshot(
        profileID: id,
        status: ProfileMonitorStatus.disabled,
      );
}

class _Main extends CaptureController {
  _Main(super.store, {this.side, this.watched = const {}});

  final _Side? side;
  final Map<String, ProfileAttentionSnapshot> watched;
  final retried = <String>[];
  _Monitor? _monitor;

  final row = ChatFeedItem(
    sessionID: 'ses_studio',
    title: 'Set up the landing page',
    directory: '/Users/me/site',
    projectName: 'site',
    isGit: true,
    status: ChatStatus.needsYou,
    lastActivity: DateTime(2026, 10, 20, 9),
    sourceId: 'profile:studio',
  );

  @override
  ProfileMonitor get profileMonitor => _monitor ??= _Monitor(store, watched);

  @override
  bool isProfileReadable(String id) => true;

  @override
  ChatFeedSnapshot chatFeed([ChatFeedFilter filter = ChatFeedFilter.all]) =>
      side == null ? super.chatFeed(filter) : ChatFeedSnapshot(items: [row]);

  @override
  ConnectionController? connectionForRow(ChatFeedItem item) =>
      item.sourceId == 'profile:studio' ? side : super.connectionForRow(item);

  @override
  List<ChatListSource> get chatListSources => [
    if (side != null)
      const ChatListSource(
        id: 'homepc',
        name: 'Home PC',
        kind: ChatListSourceKind.openCode,
        shown: true,
        onThisPhone: false,
        unreachable: true,
      ),
  ];

  @override
  Future<void> retryChatListSource(String id) async => retried.add(id);
}

PermissionRequest _npmInstall() => PermissionRequest(
  id: 'per_npm',
  sessionID: 'ses_studio',
  permission: 'bash',
  patterns: const ['npm install'],
  metadata: const {'command': 'npm install'},
);

Future<SharedPreferences> _prefs() async {
  SharedPreferences.setMockInitialValues({});
  return SharedPreferences.getInstance();
}

Future<_Side> _studioSide({bool asks = true}) async {
  final side =
      _Side(
          SeededProfileStore(prefs: await _prefs(), seeded: [_studio]),
          _studio,
        )
        ..api = _Api(asks: asks)
        ..status = StreamStatus.connected
        ..sessionsById = {
          'ses_studio': Session(id: 'ses_studio', directory: '/Users/me/site'),
        }
        ..busySessions = {};
  return side;
}

Future<_Main> _main({
  _Side? side,
  Map<String, ProfileAttentionSnapshot> watched = const {},
}) async {
  final main =
      _Main(
          SeededProfileStore(
            prefs: await _prefs(),
            seeded: [_laptop, _studio, _homePc],
          ),
          side: side,
          watched: watched,
        )
        ..api = _Api()
        ..status = StreamStatus.connected
        ..busySessions = {};
  return main;
}

Future<ValueNotifier<ServerSwitcherChoice?>> _open(
  WidgetTester tester,
  ConnectionController controller,
) async {
  final choice = ValueNotifier<ServerSwitcherChoice?>(null);
  await tester.pumpWidget(
    MaterialApp(
      theme: AppTheme.dark(),
      localizationsDelegates: AppLocalizations.localizationsDelegates,
      supportedLocales: AppLocalizations.supportedLocales,
      home: Scaffold(
        body: Builder(
          builder: (context) => Center(
            child: TextButton(
              onPressed: () async =>
                  choice.value = await showServerSwitcher(context, controller),
              child: const Text('open'),
            ),
          ),
        ),
      ),
    ),
  );
  await tester.tap(find.text('open'));
  await tester.pumpAndSettle();
  return choice;
}

String _line(WidgetTester tester, String id) =>
    (tester
                .widget<RichText>(
                  find
                      .descendant(
                        of: find.byKey(
                          ValueKey('server-switcher-profile-$id-status'),
                        ),
                        matching: find.byType(RichText),
                      )
                      .first,
                )
                .text
            as TextSpan)
        .toPlainText()
        .replaceAll('\u00a0', ' ')
        // The command is isolated left to right (KitBidi).
        .replaceAll(RegExp('[\u2066-\u2069]'), '');

void main() {
  testWidgets('a side connection\'s permission is answered on its own row, '
      'with the exact command, on that server\'s connection', (tester) async {
    final side = await _studioSide();
    side.permissions = {'per_npm': _npmInstall()};
    final main = await _main(side: side);
    addTearDown(() {
      side.dispose();
      main.dispose();
    });
    await _open(tester, main);

    final studio = find.byKey(const ValueKey('server-switcher-profile-studio'));
    final homePc = find.byKey(const ValueKey('server-switcher-profile-homepc'));
    // One compact row: the exact command on the state line, Allow once at
    // the end; no question card and no Reject in the sheet.
    expect(_line(tester, 'studio'), 'Needs you · npm install');
    expect(find.text('Run this command?'), findsNothing);
    expect(find.text('Reject'), findsNothing);
    expect(tester.getSize(studio).height, tester.getSize(homePc).height);
    // The waiting server sorts before the one that isn't answering.
    expect(
      tester.getTopLeft(studio).dy,
      lessThan(
        tester
            .getTopLeft(
              find.byKey(const ValueKey('server-switcher-profile-homepc')),
            )
            .dy,
      ),
    );

    await tester.tap(
      find.byKey(const ValueKey('server-switcher-allow-per_npm')),
    );
    await tester.pump();
    // Held for its Undo window, then sent on Studio Mac's own connection.
    expect(_line(tester, 'studio'), 'Allowed · npm install');
    expect(find.byKey(const ValueKey('server-switcher-undo')), findsOneWidget);
    await tester.pump(const Duration(seconds: 4));
    await tester.pumpAndSettle();
    expect(side.answered, [(id: 'per_npm', reply: 'once')]);
    expect(main.answered, isEmpty);
  });

  testWidgets('a command too long for the row, or two requests, open the '
      'conversation instead of Allow once', (tester) async {
    final side = await _studioSide();
    side.permissions = {
      'per_npm': PermissionRequest(
        id: 'per_npm',
        sessionID: 'ses_studio',
        permission: 'bash',
        metadata: const {
          'command':
              'npm run build && npx playwright test --project=chromium '
              '--reporter=line',
        },
      ),
    };
    final main = await _main(side: side);
    addTearDown(() {
      side.dispose();
      main.dispose();
    });
    final choice = await _open(tester, main);
    expect(
      find.byKey(const ValueKey('server-switcher-allow-per_npm')),
      findsNothing,
    );
    await tester.tap(find.byKey(const ValueKey('server-switcher-open-studio')));
    await tester.pumpAndSettle();
    expect(choice.value, isA<ServerSwitcherOpenChat>());

    side.permissions = {
      'per_npm': _npmInstall(),
      'per_two': PermissionRequest(
        id: 'per_two',
        sessionID: 'ses_studio',
        permission: 'bash',
        metadata: const {'command': 'npm test'},
      ),
    };
    await _open(tester, main);
    expect(_line(tester, 'studio'), '2 need you · npm install');
    expect(
      find.byKey(const ValueKey('server-switcher-allow-per_npm')),
      findsNothing,
    );
    expect(
      find.byKey(const ValueKey('server-switcher-open-studio')),
      findsOneWidget,
    );
  });

  testWidgets('a server that never asks shows no question', (tester) async {
    final side = await _studioSide(asks: false);
    side.permissions = {'per_npm': _npmInstall()};
    final main = await _main(side: side);
    addTearDown(() {
      side.dispose();
      main.dispose();
    });
    await _open(tester, main);
    expect(
      find.byKey(const ValueKey('server-switcher-allow-per_npm')),
      findsNothing,
    );
  });

  testWidgets('a side connection that isn\'t answering offers Try again', (
    tester,
  ) async {
    final side = await _studioSide();
    final main = await _main(side: side);
    addTearDown(() {
      side.dispose();
      main.dispose();
    });
    await _open(tester, main);
    expect(_line(tester, 'homepc'), "Isn't answering");
    await tester.tap(
      find.byKey(const ValueKey('server-switcher-retry-homepc')),
    );
    await tester.pump();
    expect(main.retried, ['homepc']);
  });

  testWidgets('a question with options opens its conversation', (tester) async {
    final side = await _studioSide();
    side.questions = {
      'que_1': const PendingQuestion(
        id: 'que_1',
        sessionID: 'ses_studio',
        prompts: [
          QuestionPrompt(
            title: 'Branch',
            question: 'Which branch should I deploy?',
            multiple: false,
            custom: true,
            choices: [
              QuestionChoice(label: 'main', description: ''),
              QuestionChoice(label: 'develop', description: ''),
            ],
          ),
        ],
      ),
    };
    final main = await _main(side: side);
    addTearDown(() {
      side.dispose();
      main.dispose();
    });
    final choice = await _open(tester, main);
    expect(
      find.byKey(const ValueKey('server-switcher-allow-per_npm')),
      findsNothing,
    );
    await tester.tap(find.byKey(const ValueKey('server-switcher-open-studio')));
    await tester.pumpAndSettle();
    final open = choice.value;
    expect(open, isA<ServerSwitcherOpenChat>());
    expect((open! as ServerSwitcherOpenChat).item.sessionID, 'ses_studio');
  });

  testWidgets('a watched server says Needs you with Open, never Allow once, '
      'and when it checks again', (tester) async {
    final now = DateTime.now();
    final main = await _main(
      watched: {
        'studio': ProfileAttentionSnapshot(
          profileID: 'studio',
          status: ProfileMonitorStatus.current,
          complete: true,
          checkedAt: now,
          runningCount: 0,
          requests: const [
            MonitoredRequest(
              id: 'per_1',
              sessionID: 'ses_studio',
              kind: MonitoredRequestKind.permission,
            ),
          ],
        ),
        'homepc': ProfileAttentionSnapshot(
          profileID: 'homepc',
          status: ProfileMonitorStatus.unavailable,
          nextCheckAt: now.add(const Duration(minutes: 3, seconds: 30)),
        ),
      },
    );
    addTearDown(main.dispose);
    final choice = await _open(tester, main);
    expect(_line(tester, 'studio'), 'Needs you');
    expect(
      find.byKey(const ValueKey('server-switcher-allow-per_npm')),
      findsNothing,
    );
    expect(_line(tester, 'homepc'), "Isn't answering · checks again in 4 min");
    expect(
      find.byKey(const ValueKey('server-switcher-retry-homepc')),
      findsNothing,
    );
    await tester.tap(find.byKey(const ValueKey('server-switcher-open-studio')));
    await tester.pumpAndSettle();
    final open = choice.value;
    expect(open, isA<ServerSwitcherOpenServers>());
  });
}
