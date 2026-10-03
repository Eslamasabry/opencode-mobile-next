// Golden renders of screen-shell-1's pages (wave 2b): the
// question sheet, the Claude Code explanation from search, and the two kit
// parts the unit moved into the kit (KitContextRegion's menu, KitScrollbar's
// pinned thumb). Phone 412x915 and one wide window (1280x800), dark and
// light (owner decision 2026-09-27: no Arabic), with the app's real fonts at
// DPR 1.
//
// Regenerate deliberately:
//   flutter test --update-goldens test/revamp/screen_shell_1_golden_test.dart
// and look at every changed image before committing it.
import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/gestures.dart' show kSecondaryMouseButton;
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/api/sse.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/platform/platform_capabilities.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/app_iconography.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart';
import 'package:opencode_mobile/ui/search/search_index.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../../tool/capture/fixtures.dart' show captureTheme, loadCaptureFonts;

class _Repository implements ProductRepository {
  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

class _Controller extends ConnectionController {
  _Controller(super.store);

  @override
  bool get isConnected => status == StreamStatus.connected;
  @override
  int get unknownAttentionProfileCount => 0;
  @override
  bool get keepLiveInBackground => true;
  @override
  Future<void> refreshSessions() async {}
  @override
  Future<void> refreshPendingPermissions() async {}
  @override
  Future<void> refreshPendingQuestions() async {}
  @override
  Future<void> refreshPendingForms() async {}
}

const _question = PendingQuestion(
  id: 'q-1',
  sessionID: 'ses_q',
  prompts: [
    QuestionPrompt(
      title: 'Deploy target',
      question: 'Where should the release build go first?',
      multiple: false,
      custom: true,
      choices: [
        QuestionChoice(label: 'Staging', description: 'Test environment'),
        QuestionChoice(label: 'Production', description: 'Live users'),
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
  if (!requests) return controller;
  controller.sessionsById = {
    'ses_run': Session(
      id: 'ses_run',
      title: 'Build the release',
      directory: '/work/oc_app',
    ),
    'ses_sub': Session(id: 'ses_sub', parentID: 'ses_run'),
    'ses_q': Session(id: 'ses_q', title: 'Ship 1.0.45'),
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
  controller.questions = {'q-1': _question};
  return controller;
}

const _phone = Size(412, 915);
const _wide = Size(1280, 800);

String _name(String shot, Size size, bool light) => [
  shot,
  if (size != _phone) '${size.width.toInt()}x${size.height.toInt()}',
  light ? 'light' : 'dark',
].join('_');

/// Pumps [home] (or opens [open] over a blank page), runs [act], settles and
/// compares the whole window.
Future<void> _shot(
  WidgetTester tester,
  String shot, {
  required bool light,
  Size size = _phone,
  Widget? home,
  bool desktop = false,
  FutureOr<void> Function(BuildContext context)? open,
  Future<void> Function(WidgetTester tester)? act,
}) async {
  tester.view.physicalSize = size;
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  debugDefaultTargetPlatformOverride = desktop
      ? TargetPlatform.linux
      : TargetPlatform.android;
  final boundary = GlobalKey();
  try {
    late BuildContext context;
    await tester.pumpWidget(
      RepaintBoundary(
        key: boundary,
        child: MaterialApp(
          debugShowCheckedModeBanner: false,
          theme: captureTheme(light: light),
          scrollBehavior: const KitScrollBehavior(),
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          builder: (context, child) => MediaQuery(
            data: MediaQuery.of(context).copyWith(disableAnimations: true),
            child: child!,
          ),
          home: Builder(
            builder: (inner) {
              context = inner;
              return home ?? const Scaffold(body: SizedBox.expand());
            },
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();
    if (open != null) unawaited(Future.sync(() => open(context)));
    await tester.pumpAndSettle();
    if (act != null) await act(tester);
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);
    await expectLater(
      find.byKey(boundary),
      matchesGoldenFile('goldens/${_name(shot, size, light)}.png'),
    );
  } finally {
    await tester.pumpWidget(const SizedBox.shrink());
    debugDefaultTargetPlatformOverride = null;
  }
}

void main() {
  setUpAll(loadCaptureFonts);

  for (final light in [false, true]) {
    final theme = light ? 'light' : 'dark';

    testWidgets('question sheet unanswered ($theme)', (tester) async {
      final controller = await _controller();
      addTearDown(controller.dispose);
      await _shot(
        tester,
        'shell_question_sheet_unanswered',
        light: light,
        open: (context) => showQuestionSheet(
          context,
          controller,
          _question,
          onOpenConversation: () {},
        ),
      );
    });

    testWidgets('search explains the Claude Code gate ($theme)', (
      tester,
    ) async {
      final controller = await _controller(requests: false);
      addTearDown(controller.dispose);
      final en = lookupAppLocalizations(const Locale('en'));
      final scope = SearchScope(
        controller: controller,
        platform: const PlatformCapabilities(platform: TargetPlatform.linux),
        desktop: true,
      );
      final entry = searchIndex(
        en,
        scope,
      ).firstWhere((e) => e.id == 'inside-phone-claude-code-unavailable');
      await _shot(
        tester,
        'shell_search_claude_code_gate',
        light: light,
        size: _wide,
        desktop: true,
        open: (context) => entry.open(context, scope),
      );
    });

    testWidgets('kit context region menu open ($theme)', (tester) async {
      await _shot(
        tester,
        'kit_context_region_open',
        light: light,
        size: _wide,
        desktop: true,
        home: Scaffold(
          body: Center(
            child: KitContextRegion(
              menu: () => [
                KitMenuItem(
                  label: 'Rename conversation',
                  icon: AppIconography.edit,
                  onSelected: () {},
                ),
                KitMenuItem(
                  label: 'Archive conversation',
                  icon: AppIconography.archive,
                  onSelected: () {},
                ),
                KitMenuItem(
                  label: 'Delete conversation',
                  icon: AppIconography.delete,
                  destructive: true,
                  onSelected: () {},
                ),
              ],
              child: const SizedBox(
                width: 480,
                child: KitRowGroup(
                  children: [
                    KitRow(
                      leading: KitRowIcon(AppIconography.chat),
                      title: 'Build the release',
                    ),
                  ],
                ),
              ),
            ),
          ),
        ),
        act: (tester) => tester.tapAt(
          tester.getCenter(find.text('Build the release')),
          buttons: kSecondaryMouseButton,
        ),
      );
    });

    testWidgets('kit scrollbar pinned on a desktop ($theme)', (tester) async {
      await _shot(
        tester,
        'kit_scrollbar_default',
        light: light,
        size: _wide,
        desktop: true,
        home: Scaffold(
          body: KitScrollArea(
            builder: (controller) => ListView(
              controller: controller,
              children: [
                for (var i = 0; i < 40; i++)
                  KitRow(
                    leading: const KitRowIcon(AppIconography.chat),
                    title: 'Conversation ${i + 1}',
                  ),
              ],
            ),
          ),
        ),
      );
    });
  }
}
