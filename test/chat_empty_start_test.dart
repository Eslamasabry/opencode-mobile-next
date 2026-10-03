import 'support/complete_message_history.dart';

import 'dart:async';
import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/ui/kit/kit_text.dart' show KitText;
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/api/sse.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/state/review_handoff.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// A server whose project folder and transcript the test decides.
class _Api extends OpenCodeApi with CompleteMessageHistory {
  _Api() : super(baseUrl: 'http://localhost');

  /// The project folder's top-level listing; a pending completer holds the
  /// answer back.
  List<FileNode> files = [];
  Completer<List<FileNode>>? filesGate;
  List<MessageWithParts> transcript = [];
  final List<String> prompts = [];

  @override
  Future<List<Session>> sessions() async => [];

  @override
  Future<Map<String, String>> sessionStatuses() async => const {};

  @override
  Future<List<MessageWithParts>> messages(String id) async =>
      List.of(transcript);

  @override
  Future<Session> session(String id) async => Session(id: id);

  @override
  Future<List<FileNode>> listFiles([String path = '']) =>
      filesGate?.future ?? Future.value(files);

  @override
  Future<void> promptAsync(
    String sessionID, {
    required String text,
    ModelRef? model,
    String? agent,
    String? variant,
    List<PromptAttachment> attachments = const [],
    List<PromptAgentMention> agentMentions = const [],
    PromptDelivery? delivery,
  }) async {
    prompts.add(text);
  }
}

/// Answers Git status the way a server does for the case under test.
class _Repository implements ProductRepository {
  _Repository(this.health);

  final VersionControlHealth? health;

  @override
  Future<List<CommandInfo>> listCommands() async => const [];

  @override
  Future<List<ReferenceInfo>> listReferences() async => const [];

  @override
  Future<VersionControlHealth> loadVersionControlHealth() async {
    final value = health;
    if (value == null) throw StateError('no git status');
    return value;
  }

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

FileNode _file(String name, {bool dir = false}) =>
    FileNode(name: name, path: name, isDir: dir);

VersionControlFile _change(String path) => VersionControlFile(
  path: path,
  status: 'modified',
  additions: 1,
  deletions: 0,
);

/// A Git repository created with the project: no commit yet.
const _unbornGit = VersionControlHealth(
  changes: [],
  setupState: VersionControlSetupState.git,
);

Future<ConnectionController> _controller(
  _Api api, {
  String? directory = '/root/projects/my-app',
  VersionControlHealth? health = _unbornGit,
}) async {
  SharedPreferences.setMockInitialValues({
    'oc.profiles': jsonEncode([
      {
        'id': 'profile-1',
        'name': 'Test server',
        'baseUrl': 'http://localhost',
        'username': '',
      },
    ]),
    'oc.activeProfile': 'profile-1',
  });
  final preferences = await SharedPreferences.getInstance();
  final store = ProfileStore(prefs: preferences);
  await store.load();
  final controller = ConnectionController(store)
    ..api = api
    ..status = StreamStatus.connected
    ..directory = directory
    ..repository = _Repository(health);
  return controller;
}

/// Bounded pump: the chat screen keeps some indicators alive.
Future<void> _settle(WidgetTester tester) async {
  for (var i = 0; i < 8; i++) {
    await tester.pump(const Duration(milliseconds: 120));
  }
}

Future<void> _pumpChat(
  WidgetTester tester,
  ConnectionController controller, {
  double keyboard = 0,
  double textScale = 1,
  bool reduceMotion = false,
  Locale? locale,
}) async {
  addTearDown(controller.dispose);
  await tester.pumpWidget(
    ProviderScope(
      overrides: [connProvider.overrideWithValue(controller)],
      child: MaterialApp(
        locale: locale,
        supportedLocales: AppLocalizations.supportedLocales,
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        builder: (context, child) => MediaQuery(
          data: MediaQuery.of(context).copyWith(
            viewInsets: EdgeInsets.only(bottom: keyboard),
            textScaler: TextScaler.linear(textScale),
            disableAnimations: reduceMotion,
          ),
          child: child!,
        ),
        home: ChatScreen(
          sessionID: 'session-1',
          handoffStore: ReviewHandoffStore(),
        ),
      ),
    ),
  );
  await _settle(tester);
}

Finder _starter(String label) => find.byKey(ValueKey('chat-starter-$label'));

final _composerField = find.byKey(const Key('chat-composer-field'));

// The composer is a KitField (a TextFormField) since a1410445/0b6c298c.
String _composerText(WidgetTester tester) =>
    tester.widget<TextFormField>(_composerField).controller?.text ?? '';

void _useSurface(WidgetTester tester, Size size) {
  tester.view.physicalSize = size;
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
          const MethodChannel('plugins.it_nomads.com/flutter_secure_storage'),
          (_) async => null,
        );
  });

  testWidgets('a brand-new project says so and offers ways to make '
      'something', (tester) async {
    final api = _Api();
    await _pumpChat(tester, await _controller(api));

    expect(find.byKey(const ValueKey('chat-start-name')), findsOneWidget);
    // The folder name is isolated for bidi (COPY-30), so match inside it.
    // The header's project chip names it too (intended), so ask the start
    // header's own name.
    expect(
      tester
          .widget<KitText>(find.byKey(const ValueKey('chat-start-name')))
          .text,
      contains('my-app'),
    );
    expect(find.text('Empty folder · Git'), findsOneWidget);
    expect(find.byKey(const ValueKey('chat-start-tip')), findsOneWidget);
    for (final label in [
      'Build a small web page',
      'Write a Python script that…',
      'Start a Node.js project',
      'Set up a README',
    ]) {
      // The row builds the starters it shows; the rest scroll into view.
      await tester.scrollUntilVisible(
        _starter(label),
        120,
        scrollable: find.descendant(
          of: find.byKey(const ValueKey('chat-starters')),
          matching: find.byType(Scrollable),
        ),
      );
      expect(_starter(label), findsOneWidget, reason: label);
    }
    // Nothing that assumes code already exists.
    expect(_starter('What changed recently?'), findsNothing);
    expect(_starter('Find and fix a bug'), findsNothing);
    // The old placeholder block is gone.
    expect(find.text('Start coding'), findsNothing);
  });

  testWidgets('.git alone still counts as an empty folder', (tester) async {
    final api = _Api()..files = [_file('.git', dir: true)];
    await _pumpChat(tester, await _controller(api));

    expect(find.text('Empty folder · Git'), findsOneWidget);
    expect(_starter('Build a small web page'), findsOneWidget);
  });

  testWidgets('a project with history offers "what changed recently"', (
    tester,
  ) async {
    final api = _Api()
      ..files = [_file('lib', dir: true), _file('README.md'), _file('.git')];
    final controller = await _controller(
      api,
      health: VersionControlHealth(
        branch: 'main',
        setupState: VersionControlSetupState.git,
        changes: [_change('a'), _change('b'), _change('c')],
      ),
    );
    await _pumpChat(tester, controller);

    expect(find.text('2 items · Git · 3 changes'), findsOneWidget);
    // The row builds the starters it shows; the rest scroll into view.
    final row = find.descendant(
      of: find.byKey(const ValueKey('chat-starters')),
      matching: find.byType(Scrollable),
    );
    for (final label in [
      'Explain this project',
      'What changed recently?',
      'Find and fix a bug',
      'Add tests',
    ]) {
      await tester.scrollUntilVisible(_starter(label), 120, scrollable: row);
      expect(_starter(label), findsOneWidget, reason: label);
    }
    expect(_starter('Build a small web page'), findsNothing);
  });

  testWidgets('files without a commit do not offer "what changed recently"', (
    tester,
  ) async {
    // An unborn branch reads back as HEAD from `git rev-parse`.
    final api = _Api()..files = [_file('main.py')];
    final controller = await _controller(
      api,
      health: const VersionControlHealth(
        branch: 'HEAD',
        setupState: VersionControlSetupState.git,
        changes: [],
      ),
    );
    await _pumpChat(tester, controller);

    expect(find.text('1 item · Git'), findsOneWidget);
    expect(_starter('Explain this project'), findsOneWidget);
    expect(_starter('What changed recently?'), findsNothing);
  });

  testWidgets('starters wait for the folder, then a slow server gives way', (
    tester,
  ) async {
    final api = _Api()..filesGate = Completer<List<FileNode>>();
    await _pumpChat(tester, await _controller(api));

    // Looking, not guessing: no starter set that could swap under a finger.
    expect(find.text('Looking at the folder…'), findsOneWidget);
    expect(find.byKey(const ValueKey('chat-starters')), findsNothing);

    // The listing never comes: after a moment the general starters show.
    await tester.pump(const Duration(seconds: 3));
    await _settle(tester);
    expect(_starter('Explain this project'), findsOneWidget);

    // A late answer still sharpens them.
    api.filesGate!.complete([]);
    await _settle(tester);
    expect(_starter('Build a small web page'), findsOneWidget);
    expect(find.text('Empty folder · Git'), findsOneWidget);
  });

  testWidgets('tapping a starter fills the composer and sends nothing', (
    tester,
  ) async {
    final api = _Api();
    await _pumpChat(tester, await _controller(api));

    await tester.tap(_starter('Write a Python script that…'));
    await _settle(tester);

    // The sentence is left open for the person to finish.
    expect(_composerText(tester), 'Write a Python script that ');
    final field = tester.widget<TextField>(
      find.descendant(of: _composerField, matching: find.byType(TextField)),
    );
    expect(field.controller!.selection.baseOffset, 27);
    expect(field.focusNode!.hasFocus, isTrue);
    expect(api.prompts, isEmpty);
    // Chips leave once there is text, and come back when it is cleared.
    expect(find.byKey(const ValueKey('chat-starters')), findsNothing);
    await tester.enterText(_composerField, '');
    await _settle(tester);
    expect(find.byKey(const ValueKey('chat-starters')), findsOneWidget);
  });

  testWidgets('typing hides the starters', (tester) async {
    final api = _Api();
    await _pumpChat(tester, await _controller(api));
    expect(find.byKey(const ValueKey('chat-starters')), findsOneWidget);

    await tester.enterText(_composerField, 'hello');
    await tester.pump();
    expect(find.byKey(const ValueKey('chat-starters')), findsNothing);
  });

  testWidgets('a conversation with a message shows no starters or header', (
    tester,
  ) async {
    final api = _Api()
      ..transcript = [
        MessageWithParts(
          info: MessageInfo(
            id: 'm1',
            sessionID: 'session-1',
            role: 'user',
            time: MsgTime(created: 1, completed: 2),
          ),
          parts: [
            Part(id: 'p1', messageID: 'm1', type: 'text', text: 'Hi there'),
          ],
        ),
      ];
    await _pumpChat(tester, await _controller(api));

    expect(find.text('Hi there'), findsWidgets);
    expect(find.byKey(const ValueKey('chat-starters')), findsNothing);
    expect(find.byKey(const ValueKey('chat-start-name')), findsNothing);
  });

  testWidgets('with the keyboard up the starters sit on the composer and the '
      'header is one uncut line', (tester) async {
    _useSurface(tester, const Size(400, 800));
    final api = _Api();
    await _pumpChat(tester, await _controller(api), keyboard: 320);

    final row = find.byKey(const ValueKey('chat-starters'));
    expect(row, findsOneWidget);
    final rowRect = tester.getRect(row);
    final composerRect = tester.getRect(_composerField);
    // Directly above where you type, and above the keyboard. In a window
    // this short the Ask first chip steps aside, so nothing but a thin strip
    // may sit between them; when the chip shows it sits in that strip.
    expect(rowRect.bottom, lessThanOrEqualTo(composerRect.top));
    final chip = find.byKey(const Key('auto-approval-indicator'));
    if (chip.evaluate().isNotEmpty) {
      final chipRect = tester.getRect(chip);
      expect(chipRect.top, greaterThanOrEqualTo(rowRect.bottom));
      expect(chipRect.bottom, lessThanOrEqualTo(composerRect.top));
    }
    expect(composerRect.top - rowRect.bottom, lessThan(72));
    expect(rowRect.bottom, lessThanOrEqualTo(800 - 320));
    // The header compacts to one line and the tip is not drawn at all.
    expect(
      find.byKey(const ValueKey('chat-start-header-compact')),
      findsOneWidget,
    );
    expect(find.byKey(const ValueKey('chat-start-tip')), findsNothing);
    final name = tester.getRect(find.byKey(const ValueKey('chat-start-name')));
    expect(name.bottom, lessThanOrEqualTo(rowRect.top));
    expect(tester.takeException(), isNull);
  });

  testWidgets('with the keyboard down the tip shows in full above the '
      'starters', (tester) async {
    _useSurface(tester, const Size(400, 800));
    final api = _Api();
    await _pumpChat(tester, await _controller(api));

    final tip = tester.getRect(find.byKey(const ValueKey('chat-start-tip')));
    final row = tester.getRect(find.byKey(const ValueKey('chat-starters')));
    expect(tip.bottom, lessThanOrEqualTo(row.top));
    expect(tip.top, greaterThanOrEqualTo(0));
  });

  testWidgets('320dp at 2.5x text fits, keyboard up or down, with 48dp '
      'chips', (tester) async {
    _useSurface(tester, const Size(320, 640));
    final api = _Api();
    await _pumpChat(tester, await _controller(api), textScale: 2.5);
    expect(tester.takeException(), isNull);
    final chip = _starter('Build a small web page');
    expect(chip, findsOneWidget);
    expect(tester.getSize(chip).height, greaterThanOrEqualTo(48));
    // The room the layout reserves for the row really holds it.
    expect(
      tester.getSize(find.byKey(const ValueKey('chat-starters'))).height,
      lessThanOrEqualTo(chatStartersHeight(const TextScaler.linear(2.5))),
    );
    // The row scrolls sideways to reach the last starter.
    final keyboardDownRow = find.byKey(const ValueKey('chat-starters'));
    await tester.drag(keyboardDownRow, const Offset(-600, 0));
    await _settle(tester);
    expect(tester.takeException(), isNull);

    // Now with the keyboard up. Since the composer rebuild on kit parts
    // (e28442b0, chat-3) the taller composer leaves 320dp at 2.5x text with
    // the keyboard up less room than the row needs, so the row steps aside
    // (the empty chat's rule: the composer never loses a pixel to a
    // shortcut). Whatever shows is whole: nothing overflows, the composer
    // sits above the keyboard, and a row that shows sits above it.
    await _pumpChat(
      tester,
      await _controller(api),
      textScale: 2.5,
      keyboard: 280,
    );
    expect(tester.takeException(), isNull);
    expect(find.byKey(const ValueKey('chat-start-tip')), findsNothing);
    expect(_composerField, findsOneWidget);
    expect(tester.getRect(_composerField).bottom, lessThanOrEqualTo(640 - 280));
    final row = find.byKey(const ValueKey('chat-starters'));
    if (row.evaluate().isNotEmpty) {
      expect(
        tester.getRect(row).bottom,
        lessThanOrEqualTo(tester.getRect(_composerField).top),
      );
    }
  });

  testWidgets('a window too short for the row keeps the composer whole', (
    tester,
  ) async {
    // Landscape phone at 2x text: the composer needs every pixel.
    _useSurface(tester, const Size(640, 320));
    final api = _Api();
    await _pumpChat(tester, await _controller(api), textScale: 2);

    expect(tester.takeException(), isNull);
    expect(find.byKey(const ValueKey('chat-start-tip')), findsNothing);
    expect(tester.getRect(_composerField).bottom, lessThanOrEqualTo(320));
    // Where the row still fits, it sits on the composer; where it would not,
    // it is left out rather than squeezing the composer.
    final row = find.byKey(const ValueKey('chat-starters'));
    if (row.evaluate().isNotEmpty) {
      expect(
        tester.getRect(row).bottom,
        lessThanOrEqualTo(tester.getRect(_composerField).top),
      );
    }

    await _pumpChat(tester, await _controller(api), textScale: 3.2);
    expect(tester.takeException(), isNull);
    expect(find.byKey(const ValueKey('chat-starters')), findsNothing);
    expect(tester.getRect(_composerField).bottom, lessThanOrEqualTo(320));
  });

  testWidgets('Arabic reads right to left with Arabic starters', (
    tester,
  ) async {
    _useSurface(tester, const Size(400, 800));
    final ar = lookupAppLocalizations(const Locale('ar'));
    final api = _Api();
    await _pumpChat(tester, await _controller(api), locale: const Locale('ar'));

    expect(find.text('${ar.chatStartEmptyFolder} · Git'), findsOneWidget);
    final first = _starter(ar.chatStartBuildWebPage);
    final second = _starter(ar.chatStartPythonScript);
    expect(first, findsOneWidget);
    // The first starter sits at the right edge, the next to its left.
    expect(
      tester.getRect(first).right,
      greaterThan(tester.getRect(second).right),
    );
    expect(tester.getRect(first).right, greaterThan(400 - 40));
    // The name is at the start (right) of the header too.
    final name = tester.getRect(find.byKey(const ValueKey('chat-start-name')));
    expect(name.right, greaterThan(400 - 60));

    // Arabic labels run long; the row scrolls toward the left to reach it.
    await tester.ensureVisible(second);
    await _settle(tester);
    await tester.tap(second);
    await _settle(tester);
    expect(_composerText(tester), 'اكتب سكربت بايثون ');
    expect(api.prompts, isEmpty);
  });

  testWidgets('the header holds still: no blinking caret', (tester) async {
    final api = _Api();
    await _pumpChat(tester, await _controller(api));
    // The drawing's own caret says the conversation is ready (chat-5); the
    // header asks for no frames once it has arrived.
    expect(find.byKey(const ValueKey('chat-start-caret')), findsNothing);
    await tester.pump(const Duration(seconds: 1));
    expect(tester.binding.hasScheduledFrame, isFalse);
  });

  testWidgets('reduced motion shows the starters at once', (tester) async {
    final api = _Api();
    await _pumpChat(tester, await _controller(api), reduceMotion: true);
    await tester.pump();
    expect(_starter('Build a small web page'), findsOneWidget);
    expect(tester.binding.hasScheduledFrame, isFalse);
  });
}
