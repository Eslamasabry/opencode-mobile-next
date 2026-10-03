// Golden renders of the composer's approval chip on the design kit
// (docs/ux-system/kit-api/KitComposerChips.md): the real ChatScreen at
// 412x915, dark and light, with the app's real fonts and no server. The chip
// in each mode, the open mode menu, and the confirm step before a more
// permissive mode.
//
// Regenerate deliberately:
//   flutter test --update-goldens test/goldens/chat_approval_chip_golden_test.dart
// and look at every changed image before committing it.
//
// ignore_for_file: invalid_use_of_protected_member
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/state/automation_policy.dart';
import 'package:opencode_mobile/state/session_auto_approval.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../../tool/capture/fixtures.dart';

/// One page of history.
class _Api extends CaptureApi {
  @override
  Future<ServerPage<MessageWithParts>> messagePage(
    String id, {
    String? cursor,
    int limit = 100,
  }) async => ServerPage(items: cursor == null ? await messages(id) : const []);
}

/// A finished turn at fixed offsets from now, so nothing on screen depends
/// on the wall clock (the transcript shows no times by default).
List<MessageWithParts> _turn({bool reply = true}) {
  final now = DateTime.now().millisecondsSinceEpoch;
  return [
    MessageWithParts(
      info: messageInfo(
        'msg_user',
        'user',
        created: now - 95 * 1000,
        completed: now - 95 * 1000,
      ),
      parts: [
        Part(
          id: 'part_user',
          messageID: 'msg_user',
          type: 'text',
          text: userPrompt,
        ),
      ],
    ),
    if (reply)
      MessageWithParts(
        info: messageInfo(
          'msg_assistant',
          'assistant',
          created: now - 80 * 1000,
          completed: now - 4 * 1000,
        ),
        parts: [textPart('part_intro', answerIntro)],
      ),
  ];
}

Future<void> _golden(
  WidgetTester tester,
  String name, {
  required bool light,
  Future<void> Function(CaptureController controller)? prepare,
  Future<void> Function()? open,
}) async {
  tester.view.physicalSize = const Size(412, 915);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  SharedPreferences.setMockInitialValues({});
  final prefs = await SharedPreferences.getInstance();
  await AutomationPolicyController.forProfile(
    prefs,
    'laptop',
  ).setSupervision(AutomationSupervision.balanced);
  final api = _Api()
    ..busy = {}
    ..messagesHandler = (_) async => _turn();
  final controller = await captureController(prefs: prefs, api: api);
  await prepare?.call(controller);
  final boundary = GlobalKey();
  final navigatorKey = GlobalKey<NavigatorState>();
  try {
    await tester.pumpWidget(
      captureApp(
        home: ChatScreen(sessionID: checkoutSessionID),
        boundaryKey: boundary,
        navigatorKey: navigatorKey,
        controller: controller,
        light: light,
      ),
    );
    for (var i = 0; i < 8; i++) {
      await tester.pump(const Duration(milliseconds: 120));
    }
    await open?.call();
    expect(tester.takeException(), isNull);
    await expectLater(
      find.byKey(boundary),
      matchesGoldenFile('${name}_${light ? 'light' : 'dark'}.png'),
    );
  } finally {
    await tester.pumpWidget(const SizedBox.shrink());
    await tester.pump(const Duration(seconds: 10));
    controller.dispose();
  }
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(loadCaptureFonts);

  for (final light in [false, true]) {
    final mode = light ? 'light' : 'dark';
    Future<void> openMenu(WidgetTester tester) async {
      await tester.tap(find.byKey(const Key('auto-approval-indicator')));
      await tester.pumpAndSettle();
    }

    testWidgets(
      'chat · approval ask · $mode',
      (tester) async {
        await _golden(tester, 'chat_approval_ask', light: light);
      },
      variant: TargetPlatformVariant.only(TargetPlatform.android),
    );

    testWidgets(
      'chat · approval auto · $mode',
      (tester) async {
        await _golden(
          tester,
          'chat_approval_auto',
          light: light,
          prepare: (controller) => controller.setSessionAutoApproval(
            checkoutSessionID,
            const SessionAutoApproval(mode: AutoApprovalMode.autoOnce),
          ),
        );
      },
      variant: TargetPlatformVariant.only(TargetPlatform.android),
    );

    testWidgets(
      'chat · approval everything · $mode',
      (tester) async {
        await _golden(
          tester,
          'chat_approval_everything',
          light: light,
          prepare: (controller) => controller.setApprovesEverything(true),
        );
      },
      variant: TargetPlatformVariant.only(TargetPlatform.android),
    );

    testWidgets(
      'chat · approval menu · $mode',
      (tester) async {
        await _golden(
          tester,
          'chat_approval_menu',
          light: light,
          open: () => openMenu(tester),
        );
      },
      variant: TargetPlatformVariant.only(TargetPlatform.android),
    );

    testWidgets(
      'chat · approval confirm · $mode',
      (tester) async {
        await _golden(
          tester,
          'chat_approval_confirm',
          light: light,
          open: () async {
            await openMenu(tester);
            await tester.tap(find.byKey(const Key('approval-mode-everything')));
            await tester.pumpAndSettle();
          },
        );
      },
      variant: TargetPlatformVariant.only(TargetPlatform.android),
    );

    Future<void> openSheet(WidgetTester tester) async {
      await openMenu(tester);
      await tester.tap(find.byKey(const Key('approval-mode-settings')));
      await tester.pumpAndSettle();
    }

    testWidgets(
      'chat · approval sheet ask · $mode',
      (tester) async {
        await _golden(
          tester,
          'chat_approval_sheet_ask',
          light: light,
          open: () => openSheet(tester),
        );
      },
      variant: TargetPlatformVariant.only(TargetPlatform.android),
    );

    testWidgets(
      'chat · approval sheet auto · $mode',
      (tester) async {
        await _golden(
          tester,
          'chat_approval_sheet_auto',
          light: light,
          prepare: (controller) => controller.setSessionAutoApproval(
            checkoutSessionID,
            const SessionAutoApproval(mode: AutoApprovalMode.autoOnce),
          ),
          open: () => openSheet(tester),
        );
      },
      variant: TargetPlatformVariant.only(TargetPlatform.android),
    );

    testWidgets(
      'chat · approval sheet everything · $mode',
      (tester) async {
        await _golden(
          tester,
          'chat_approval_sheet_everything',
          light: light,
          prepare: (controller) => controller.setApprovesEverything(true),
          open: () => openSheet(tester),
        );
      },
      variant: TargetPlatformVariant.only(TargetPlatform.android),
    );
  }
}
