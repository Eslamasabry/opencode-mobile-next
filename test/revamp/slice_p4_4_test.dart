import 'package:opencode_mobile/domain/server_gateway.dart';
// slice-P4.4 One connection status. One controller-owned connection status
// feeds the one status line every KitScreen draws; the chat's own line, the
// team pages' lines and the app's lines (an update ready, a share waiting)
// go into that same slot, so two lines never show at once. The page the app
// opens on while it reconnects (root-connecting) *is* the connection status:
// it escalates on the controller's clock and the line above it never repeats
// it. The line's Details tells the same diagnosis as that page.
//
// Also: the chat perf pass (a streamed reply's fragments merged in linear
// time, same text) and audit2 A4 (a worker's acknowledgement never clears
// words typed after the send).

import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/models.dart';
import '../support/fake_server_gateway.dart';
import 'package:opencode_mobile/domain/connection_status.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart';
import 'package:opencode_mobile/ui/widgets/app_connection_status.dart';
import 'package:opencode_mobile/ui/widgets/connection_failure.dart';
import 'package:opencode_mobile/ui/widgets/connection_status_banner.dart';
import 'package:opencode_mobile/ui/widgets/saved_server_connection_card.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../support/complete_message_history.dart';

Finder _key(String value) => find.byKey(ValueKey(value));

final _laptop = ServerProfile(
  id: 'laptop',
  name: 'Laptop',
  baseUrl: 'http://192.168.1.20:4096',
);

/// A controller whose connection snapshot a test sets.
class _Controller extends ConnectionController {
  _Controller(super.store);
  ConnectionStatusSnapshot snapshot = const ConnectionStatusSnapshot(
    phase: ConnectionStatusPhase.connected,
    profileId: 'laptop',
    serverName: 'Laptop',
  );
  ServerProfile? profileOverride;

  @override
  ConnectionStatusSnapshot get connectionStatus => snapshot;

  @override
  ServerProfile? get profile => profileOverride ?? super.profile;

  void show(ConnectionStatusPhase phase) {
    snapshot = ConnectionStatusSnapshot(
      phase: phase,
      profileId: 'laptop',
      serverName: 'Laptop',
    );
    notifyListeners();
  }
}

class _Api extends FakeServerGateway with CompleteMessageHistory {
  _Api(this.value) : super(baseUrl: 'http://localhost');
  Session value;

  @override
  Future<List<MessageWithParts>> messages(String id) async => [
    MessageWithParts(
      info: MessageInfo(
        id: 'm1',
        sessionID: id,
        role: 'user',
        time: MsgTime(created: 1, completed: 1),
      ),
      parts: [Part(id: 'p1', messageID: 'm1', type: 'text', text: 'Hello')],
    ),
    MessageWithParts(
      info: MessageInfo(
        id: 'm2',
        sessionID: id,
        role: 'assistant',
        parentID: 'm1',
        finish: 'stop',
        time: MsgTime(created: 2, completed: 3),
      ),
      parts: [Part(id: 'p2', messageID: 'm2', type: 'text', text: 'Hi.')],
    ),
  ];
  @override
  Future<Session> session(String id) async => value;
  @override
  Future<List<Session>> sessions() async => [value];
  @override
  Future<Map<String, String>> sessionStatuses() async => const {};
  @override
  Future<List<Todo>> todos(String id) async => const [];
  @override
  Future<List<FileNode>> listFiles([String path = '']) async => const [];
}

const _update = KitStatus(
  kind: KitStatusKind.update,
  id: 'update:app',
  key: ValueKey('test-update-ready'),
  icon: Icons.download,
  message: 'An update is ready',
);

Future<_Controller> _controller(Session session) async {
  SharedPreferences.setMockInitialValues({});
  final c = _Controller(
    ProfileStore(prefs: await SharedPreferences.getInstance()),
  );
  final api = _Api(session);
  c
    ..api = api
    ..status = StreamStatus.connected;
  c.sessionsById[session.id] = session;
  addTearDown(c.dispose);
  return c;
}

/// The app's shape: conditions above the navigator (main.dart's
/// AppConnectionStatusScope feeds connection + app lines this way), the
/// page below it.
Widget _app(
  _Controller c,
  Widget home, {
  List<KitStatus> app = const [],
  GlobalKey<NavigatorState>? navigator,
}) => ProviderScope(
  overrides: [connProvider.overrideWithValue(c)],
  child: MaterialApp(
    navigatorKey: navigator,
    theme: AppTheme.dark(),
    localizationsDelegates: AppLocalizations.localizationsDelegates,
    supportedLocales: AppLocalizations.supportedLocales,
    builder: (context, child) => ListenableBuilder(
      listenable: c,
      builder: (context, _) => AppConditionsScope(
        conditions: [
          connectionKitStatus(
            context,
            c,
            actionContext: () => navigator?.currentState?.overlay?.context,
          ),
          ...app,
        ],
        child: child!,
      ),
    ),
    home: home,
  ),
);

void _phone(WidgetTester tester) {
  tester.view.physicalSize = const Size(412, 915);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.resetPhysicalSize);
  addTearDown(tester.view.resetDevicePixelRatio);
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  group('one line per window', () {
    testWidgets('the chat\'s own line and an update-ready app line: one '
        'line, the chat\'s; a lost connection outranks both', (tester) async {
      _phone(tester);
      final c = await _controller(Session(id: 's1', reverted: true));
      await tester.pumpWidget(
        _app(c, const ChatScreen(sessionID: 's1'), app: const [_update]),
      );
      await tester.pumpAndSettle();

      // The chat's line (its conversation was undone) wins over the update.
      expect(find.byType(KitStatusLine), findsOneWidget);
      expect(_key('chat-status-undone'), findsOneWidget);
      expect(_key('test-update-ready'), findsNothing);

      // The server stops answering: one line, the connection's.
      c.show(ConnectionStatusPhase.notAnswering);
      await tester.pumpAndSettle();
      expect(find.byType(KitStatusLine), findsOneWidget);
      expect(_key('connection-status-banner'), findsOneWidget);
      expect(find.text("Laptop isn't answering"), findsOneWidget);
      expect(_key('chat-status-undone'), findsNothing);

      // Back: the chat's line again, never two.
      c.show(ConnectionStatusPhase.connected);
      await tester.pumpAndSettle();
      expect(find.byType(KitStatusLine), findsOneWidget);
      expect(_key('chat-status-undone'), findsOneWidget);
      expect(tester.takeException(), isNull);
    });

    testWidgets('a chat with nothing of its own shows the update line', (
      tester,
    ) async {
      _phone(tester);
      final c = await _controller(Session(id: 's1'));
      await tester.pumpWidget(
        _app(c, const ChatScreen(sessionID: 's1'), app: const [_update]),
      );
      await tester.pumpAndSettle();
      expect(find.byType(KitStatusLine), findsOneWidget);
      expect(_key('test-update-ready'), findsOneWidget);
      expect(tester.takeException(), isNull);
    });
  });

  group('root-connecting is the connection status', () {
    Widget card({required bool notAnswering, String? error}) =>
        SavedServerConnectionCard(
          profileName: 'Laptop',
          baseUrl: _laptop.baseUrl,
          error: error,
          attempts: 1,
          notAnswering: notAnswering,
          onChangeServer: () {},
          onRetry: () {},
        );

    testWidgets('the card keeps no clock of its own: it escalates only when '
        'the controller\'s wait ran out', (tester) async {
      _phone(tester);
      final c = await _controller(Session(id: 's1'));
      await tester.pumpWidget(
        _app(c, KitScreen(body: card(notAnswering: false))),
      );
      await tester.pump();
      await tester.pump(const Duration(seconds: 30));
      expect(_key('saved-server-connecting'), findsOneWidget);
      expect(_key('saved-server-not-answering'), findsNothing);

      await tester.pumpWidget(
        _app(c, KitScreen(body: card(notAnswering: true))),
      );
      await tester.pump();
      expect(_key('saved-server-not-answering'), findsOneWidget);
      expect(find.text("Laptop isn't answering"), findsOneWidget);
      expect(_key('saved-server-restart'), findsNothing);
      expect(_key('saved-server-not-answering-retry'), findsOneWidget);
      expect(_key('saved-server-choose-another'), findsOneWidget);
    });

    testWidgets('the line above the card never repeats the connection; a '
        'lower app line (a share waiting) shows instead', (tester) async {
      _phone(tester);
      final c = await _controller(Session(id: 's1'));
      c.show(ConnectionStatusPhase.notAnswering);
      const share = KitStatus(
        kind: KitStatusKind.work,
        id: 'app:share-waiting',
        key: ValueKey('test-share-waiting'),
        icon: Icons.share,
        message: 'The shared text waits for Laptop',
      );
      await tester.pumpWidget(
        _app(
          c,
          KitScreen(
            bodySays: const {KitStatusKind.connection},
            body: card(notAnswering: true),
          ),
          app: const [share],
        ),
      );
      // The not-answering drawing breathes: no settle.
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 600));
      expect(_key('connection-status-banner'), findsNothing);
      expect(_key('test-share-waiting'), findsOneWidget);
      expect(find.byType(KitStatusLine), findsOneWidget);
      // Said once: by the page.
      expect(find.text("Laptop isn't answering"), findsOneWidget);

      // Any other page still shows the connection line.
      await tester.pumpWidget(
        _app(c, const KitScreen(body: SizedBox()), app: const [share]),
      );
      await tester.pumpAndSettle();
      expect(_key('connection-status-banner'), findsOneWidget);
      expect(_key('test-share-waiting'), findsNothing);
    });
  });

  testWidgets('the line\'s Details tells root-connecting\'s diagnosis: its '
      'title, explanation and checks; the raw error only in the fold', (
    tester,
  ) async {
    _phone(tester);
    final c = await _controller(Session(id: 's1'));
    c
      ..profileOverride = _laptop
      ..lastError = 'Health check failed: connection refused'
      ..show(ConnectionStatusPhase.notAnswering);
    final navigator = GlobalKey<NavigatorState>();
    await tester.pumpWidget(
      _app(
        c,
        Builder(
          builder: (context) => KitScreen(
            body: KitButton(
              role: KitButtonRole.primary,
              label: 'open',
              onPressed: () => showConnectionDetailsSheet(context, c),
            ),
          ),
        ),
        navigator: navigator,
      ),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.text('open'));
    await tester.pumpAndSettle();
    final l10n = AppLocalizations.of(
      tester.element(_key('connection-banner-details-sheet')),
    );
    final failure = ConnectionFailure.diagnose(
      l10n: l10n,
      error: 'Health check failed: connection refused',
      baseUrl: _laptop.baseUrl,
    );
    expect(find.text(failure.title), findsWidgets);
    expect(
      tester.widget<KitText>(_key('connection-details-explanation')).text,
      failure.explanation,
    );
    for (final check in failure.checks) {
      expect(find.text('• $check'), findsOneWidget);
    }
    expect(find.byType(KitDetailsFold), findsOneWidget);
    // The raw error waits folded: the plain diagnosis is what is read
    // first, and one tap opens the technical text.
    expect(
      find.textContaining('Health check failed: connection refused'),
      findsNothing,
    );
    await tester.tap(_key('kit-details-toggle'));
    await tester.pumpAndSettle();
    expect(
      find.textContaining('Health check failed: connection refused'),
      findsOneWidget,
    );
    expect(tester.takeException(), isNull);
  });

  group(
    'perf: a streamed reply\'s fragments merge the same, in linear time',
    () {
      /// The merge as it was (quadratic: it read the whole buffer back per
      /// fragment), kept as the parity oracle.
      String reference(List<String> texts) {
        final buffer = StringBuffer();
        for (final text in texts) {
          if (text.trim().isEmpty) continue;
          if (buffer.isNotEmpty &&
              !buffer.toString().endsWith('\n') &&
              !text.startsWith('\n')) {
            buffer.write('\n\n');
          }
          buffer.write(text);
        }
        return buffer.toString();
      }

      Part merged(List<String> texts) => debugMergeTextParts([
        for (final (i, text) in texts.indexed)
          Part(id: 'p$i', messageID: 'm', type: 'text', text: text),
      ]);

      for (final (name, texts) in [
        ('plain fragments', ['One.', 'Two.', 'Three.']),
        ('whitespace-only fragments are skipped', ['One.', '  ', '\n', 'Two.']),
        ('a fragment ending in a newline', ['One.\n', 'Two.', 'Three.\n']),
        ('a fragment starting with a newline', ['One.', '\nTwo.', '\n\nThree']),
        ('CRLF edges', ['One.\r\n', 'Two.\r', '\r\nThree']),
        ('leading whitespace-only', ['   ', 'One.', 'Two.']),
        (
          'code fence split across fragments',
          ['```dart\nfinal a', ' = 1;\n', '```'],
        ),
      ]) {
        test(name, () {
          final part = merged(texts);
          expect(part.text, reference(texts));
          expect(part.id, 'p0', reason: 'the first part\'s identity');
        });
      }

      test('5,000 fragments: identical text', () {
        final texts = [for (var i = 0; i < 5000; i++) 'Part $i.'];
        expect(merged(texts).text, reference(texts));
      });

      test('a single part is returned as is', () {
        final part = Part(id: 'only', messageID: 'm', type: 'text', text: 'x');
        expect(identical(debugMergeTextParts([part]), part), isTrue);
      });
    },
  );

  group('audit2 A4: a worker\'s acknowledgement keeps newer words', () {
    Future<(Completer<bool> Function(), List<String>)> pump(
      WidgetTester tester,
      _Controller c,
    ) async {
      final sent = <String>[];
      var pending = Completer<bool>();
      await tester.pumpWidget(
        _app(
          c,
          ChatScreen(
            sessionID: 's1',
            watch: ChatWatch(
              banner: () => ('Watching furiosa', AppStatusTone.neutral),
              hint: 'Message furiosa…',
              draftId: 'furiosa',
              onSend: (text) {
                sent.add(text);
                return pending.future;
              },
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();
      return (
        () {
          final current = pending;
          pending = Completer<bool>();
          return current;
        },
        sent,
      );
    }

    String field(WidgetTester tester) => tester
        .widget<TextField>(
          find.descendant(
            of: _key('chat-watching-message-field'),
            matching: find.byType(TextField),
          ),
        )
        .controller!
        .text;

    Future<_Controller> watched() async {
      final c = await _controller(Session(id: 's1'));
      c.profileOverride = _laptop;
      return c;
    }

    testWidgets('words typed while the host acknowledges stay, and so does '
        'their saved draft', (tester) async {
      _phone(tester);
      final c = await watched();
      final (take, sent) = await pump(tester, c);
      await tester.enterText(_key('chat-watching-message-field'), 'First');
      await tester.pump();
      await tester.tap(_key('chat-watching-message-send'));
      await tester.pump();
      expect(sent, ['First']);
      // The person types on before the host answers.
      await tester.enterText(
        _key('chat-watching-message-field'),
        'Second thought',
      );
      await tester.pump();
      take().complete(true);
      await tester.pumpAndSettle();
      expect(field(tester), 'Second thought');
      await tester.pump(const Duration(seconds: 1));
      final prefs = await SharedPreferences.getInstance();
      expect(
        prefs.getString('oc.draft.team-message.furiosa.laptop'),
        'Second thought',
      );
      expect(tester.takeException(), isNull);
    });

    testWidgets('editing away and back is still an edit', (tester) async {
      _phone(tester);
      final c = await watched();
      final (take, _) = await pump(tester, c);
      await tester.enterText(_key('chat-watching-message-field'), 'Same');
      await tester.pump();
      await tester.tap(_key('chat-watching-message-send'));
      await tester.pump();
      await tester.enterText(_key('chat-watching-message-field'), 'Sam');
      await tester.pump();
      await tester.enterText(_key('chat-watching-message-field'), 'Same');
      await tester.pump();
      take().complete(true);
      await tester.pumpAndSettle();
      expect(field(tester), 'Same');
    });

    testWidgets('an untouched send clears the field and its draft; a refused '
        'one keeps the words', (tester) async {
      _phone(tester);
      final c = await watched();
      final (take, _) = await pump(tester, c);
      await tester.enterText(_key('chat-watching-message-field'), 'Go on');
      await tester.pump();
      await tester.tap(_key('chat-watching-message-send'));
      await tester.pump();
      take().complete(false);
      await tester.pumpAndSettle();
      expect(field(tester), 'Go on');

      await tester.tap(_key('chat-watching-message-send'));
      await tester.pump();
      take().complete(true);
      await tester.pumpAndSettle();
      expect(field(tester), isEmpty);
      await tester.pump(const Duration(seconds: 1));
      final prefs = await SharedPreferences.getInstance();
      expect(prefs.getString('oc.draft.team-message.furiosa.laptop'), isNull);
      expect(tester.takeException(), isNull);
    });
  });
}
