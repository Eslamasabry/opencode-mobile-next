// Behaviour of screen-shell-1's pages rebuilt from kit parts (wave 2b): the
// question sheet (Send says why it cannot send,
// Open conversation), the Claude Code gate in search, the desktop drop
// failure alert, the kit context region and the kit scrollbar.
import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/gestures.dart' show kSecondaryMouseButton;
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/api/sse.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/platform/platform_capabilities.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/desktop/file_drop.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart';
import 'package:opencode_mobile/ui/search/search_index.dart';
import 'package:shared_preferences/shared_preferences.dart';

class _Repository implements ProductRepository {
  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

class _Controller extends ConnectionController {
  _Controller(super.store);

  bool connected = true;
  @override
  bool get isConnected => connected && status == StreamStatus.connected;

  String? answeredPermission;
  String? reply;
  List<List<String>>? answers;

  @override
  Future<void> refreshSessions() async {}
  @override
  Future<void> refreshPendingPermissions() async {}
  @override
  Future<void> refreshPendingQuestions() async {}
  @override
  Future<void> refreshPendingForms() async {}

  @override
  Future<void> answerPermission(
    String id,
    String reply, {
    String? message,
    PendingRequestIdentity? expectedRequest,
  }) async {
    answeredPermission = id;
    this.reply = reply;
  }

  @override
  Future<void> answerQuestion(
    String id,
    List<List<String>> answers, {
    PendingRequestIdentity? expectedRequest,
  }) async {
    this.answers = answers;
  }
}

const _question = PendingQuestion(
  id: 'q-1',
  sessionID: 'ses_q',
  prompts: [
    QuestionPrompt(
      title: 'Target',
      question: 'Where should this deploy?',
      multiple: false,
      custom: true,
      choices: [
        QuestionChoice(label: 'Staging', description: 'Test first'),
        QuestionChoice(label: 'Production', description: 'Live'),
      ],
    ),
  ],
);

Future<_Controller> _controller({bool requests = true}) async {
  SharedPreferences.setMockInitialValues({});
  final prefs = await SharedPreferences.getInstance();
  final controller = _Controller(ProfileStore(prefs: prefs))
    ..repository = _Repository()
    ..status = StreamStatus.connected;
  controller.sessionsById = {
    'ses_run': Session(
      id: 'ses_run',
      title: 'Build feature',
      directory: '/work/oc_app',
    ),
  };
  controller.busySessions = {'ses_run'};
  if (requests) {
    controller.permissions = {
      'perm-1': PermissionRequest(
        id: 'perm-1',
        sessionID: 'ses_run',
        permission: 'edit',
        patterns: const ['lib/main.dart'],
      ),
    };
    controller.questions = {'q-1': _question};
  }
  return controller;
}

Widget _app(Widget home) => MaterialApp(
  localizationsDelegates: AppLocalizations.localizationsDelegates,
  supportedLocales: AppLocalizations.supportedLocales,
  home: home,
);

Future<void> _size(WidgetTester tester, Size size) async {
  tester.view.physicalSize = size;
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
}

void desktopTest(
  String description,
  Future<void> Function(WidgetTester tester) body,
) {
  testWidgets(description, (tester) async {
    debugDefaultTargetPlatformOverride = TargetPlatform.linux;
    try {
      await body(tester);
    } finally {
      debugDefaultTargetPlatformOverride = null;
    }
  });
}

void main() {
  group('question sheet', () {
    testWidgets('Send says why it cannot send until every prompt is answered', (
      tester,
    ) async {
      await _size(tester, const Size(412, 915));
      final controller = await _controller();
      addTearDown(controller.dispose);
      var opened = 0;
      await tester.pumpWidget(
        _app(
          Builder(
            builder: (context) => Scaffold(
              body: Center(
                child: KitButton.primary(
                  label: 'Open',
                  onPressed: () => showQuestionSheet(
                    context,
                    controller,
                    _question,
                    onOpenConversation: () => opened++,
                  ),
                ),
              ),
            ),
          ),
        ),
      );
      await tester.tap(find.text('Open'));
      await tester.pumpAndSettle();

      expect(find.text('Answer every question first.'), findsOneWidget);
      await tester.enterText(find.byType(TextField), 'Canary');
      await tester.pump();
      expect(find.text('Answer every question first.'), findsNothing);

      await tester.ensureVisible(
        find.byKey(const ValueKey('question-open-conversation')),
      );
      await tester.pumpAndSettle();
      await tester.tap(
        find.byKey(const ValueKey('question-open-conversation')),
      );
      await tester.pumpAndSettle();
      expect(opened, 1);
      expect(find.byKey(const ValueKey('question-sheet')), findsNothing);
    });

    testWidgets('without a server the reason says to reconnect', (
      tester,
    ) async {
      await _size(tester, const Size(412, 915));
      final controller = await _controller();
      controller.repository = null;
      addTearDown(controller.dispose);
      await tester.pumpWidget(
        _app(
          Builder(
            builder: (context) => Scaffold(
              body: Center(
                child: KitButton.primary(
                  label: 'Open',
                  onPressed: () =>
                      showQuestionSheet(context, controller, _question),
                ),
              ),
            ),
          ),
        ),
      );
      await tester.tap(find.text('Open'));
      await tester.pumpAndSettle();
      expect(find.text('Reconnect to the server to answer.'), findsOneWidget);
    });
  });

  group('search', () {
    Future<_Controller> plain() async {
      final controller = await _controller(requests: false);
      addTearDown(controller.dispose);
      return controller;
    }

    final en = lookupAppLocalizations(const Locale('en'));

    test('Claude Code explains its gate where Termux is missing', () async {
      final controller = await plain();
      final desktop = searchIndex(
        en,
        SearchScope(
          controller: controller,
          platform: const PlatformCapabilities(platform: TargetPlatform.linux),
          desktop: true,
        ),
      ).map((entry) => entry.id);
      expect(desktop, contains('inside-phone-claude-code-unavailable'));
      expect(desktop, isNot(contains('inside-phone-claude-code')));

      final phone = searchIndex(
        en,
        SearchScope(
          controller: controller,
          platform: const PlatformCapabilities.android(),
          desktop: false,
        ),
      ).map((entry) => entry.id);
      expect(phone, contains('inside-phone-claude-code'));
      expect(phone, isNot(contains('inside-phone-claude-code-unavailable')));
    });

    testWidgets('opening it says why and where Claude Code runs', (
      tester,
    ) async {
      final controller = await plain();
      final scope = SearchScope(
        controller: controller,
        platform: const PlatformCapabilities(platform: TargetPlatform.linux),
        desktop: true,
      );
      final entry = searchEntries(
        en,
        scope,
        'claude code',
      ).firstWhere((e) => e.id == 'inside-phone-claude-code-unavailable');
      await tester.pumpWidget(
        _app(
          Builder(
            builder: (context) => Scaffold(
              body: Center(
                child: KitButton.primary(
                  label: 'Open',
                  onPressed: () => entry.open(context, scope),
                ),
              ),
            ),
          ),
        ),
      );
      await tester.tap(find.text('Open'));
      await tester.pumpAndSettle();
      expect(
        find.byKey(const ValueKey('search-claude-code-gate')),
        findsOneWidget,
      );
      expect(find.text('Not on this device'), findsOneWidget);
      expect(find.textContaining('Paseo'), findsOneWidget);
    });
  });

  group('desktop', () {
    desktopTest('a failed drop says so in the kit alert, never the error', (
      tester,
    ) async {
      await tester.pumpWidget(
        _app(
          Scaffold(
            body: DesktopFileDropTarget(
              onDrop: (_) async => throw StateError('/home/secret/path'),
              child: const SizedBox.expand(),
            ),
          ),
        ),
      );
      final state = tester.state<DesktopFileDropTargetState>(
        find.byType(DesktopFileDropTarget),
      );
      unawaited(
        state.debugHandleDrop([
          DroppedFile(
            name: 'a.txt',
            mimeType: 'text/plain',
            length: () async => 1,
            readBytes: () async => Uint8List(1),
          ),
        ]),
      );
      await tester.pumpAndSettle();
      expect(find.byKey(const ValueKey('desktop-drop-failed')), findsOneWidget);
      expect(find.text('Could not attach dropped files'), findsOneWidget);
      expect(find.textContaining('/home/secret'), findsNothing);
    });

    desktopTest('the context region opens the kit menu by click and keys', (
      tester,
    ) async {
      var ran = 0;
      await tester.pumpWidget(
        _app(
          Scaffold(
            body: Center(
              child: KitContextRegion(
                menu: () => [
                  KitMenuItem(
                    label: 'Rename',
                    key: const ValueKey('menu-rename'),
                    onSelected: () => ran++,
                  ),
                ],
                child: const SizedBox(width: 200, height: 60),
              ),
            ),
          ),
        ),
      );
      await tester.tapAt(
        tester.getCenter(find.byType(KitContextRegion)),
        buttons: kSecondaryMouseButton,
      );
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const ValueKey('menu-rename')));
      await tester.pumpAndSettle();
      expect(ran, 1);

      // Shift+F10 on the focused region opens the same menu.
      await tester.sendKeyEvent(LogicalKeyboardKey.tab);
      await tester.pump();
      await tester.sendKeyDownEvent(LogicalKeyboardKey.shift);
      await tester.sendKeyEvent(LogicalKeyboardKey.f10);
      await tester.sendKeyUpEvent(LogicalKeyboardKey.shift);
      await tester.pumpAndSettle();
      expect(find.byKey(const ValueKey('menu-rename')), findsOneWidget);
    });

    testWidgets('off desktop the region is the child alone', (tester) async {
      await tester.pumpWidget(
        _app(
          KitContextRegion(
            menu: () => const [],
            child: const SizedBox(key: ValueKey('child')),
          ),
        ),
      );
      expect(
        find.descendant(
          of: find.byType(KitContextRegion),
          matching: find.byType(GestureDetector),
        ),
        findsNothing,
      );
    });

    desktopTest('KitScrollArea hands a controller and pins one thumb', (
      tester,
    ) async {
      ScrollController? given;
      await tester.pumpWidget(
        MaterialApp(
          scrollBehavior: const KitScrollBehavior(),
          home: KitScrollArea(
            builder: (controller) {
              given = controller;
              return ListView(
                controller: controller,
                children: [
                  for (var i = 0; i < 60; i++)
                    SizedBox(height: 40, child: Text('Row $i')),
                ],
              );
            },
          ),
        ),
      );
      await tester.pumpAndSettle();
      expect(given, isNotNull);
      expect(find.byType(Scrollbar), findsOneWidget);
      expect(
        tester.widget<Scrollbar>(find.byType(Scrollbar)).thumbVisibility,
        isTrue,
      );
    });

    testWidgets('off desktop KitScrollArea gives no controller', (
      tester,
    ) async {
      ScrollController? given = ScrollController();
      addTearDown(given.dispose);
      await tester.pumpWidget(
        MaterialApp(
          home: KitScrollArea(
            builder: (controller) {
              given = controller;
              return ListView(children: const [SizedBox(height: 40)]);
            },
          ),
        ),
      );
      expect(given, isNull);
    });

    desktopTest('KitScrollbar keeps one thumb over its own box', (
      tester,
    ) async {
      final controller = ScrollController();
      addTearDown(controller.dispose);
      await tester.pumpWidget(
        MaterialApp(
          scrollBehavior: const KitScrollBehavior(),
          home: KitScrollbar(
            controller: controller,
            child: ListView(
              controller: controller,
              children: [
                for (var i = 0; i < 60; i++) const SizedBox(height: 40),
              ],
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();
      expect(find.byType(Scrollbar), findsOneWidget);
    });
  });
}
