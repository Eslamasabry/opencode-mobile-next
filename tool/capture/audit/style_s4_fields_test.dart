// AUDIT TOOLING ONLY (style cleanup slice S4, 2026-10-21): the composer's
// `/` suggestions over a real chat, which no other capture opens. Style,
// mode and the rest come from AUDIT_* (see audit_env.dart):
//
//   AUDIT_STYLE=paper AUDIT_MODE=light AUDIT_OUT=<root> TZ=UTC \
//     flutter test tool/capture/audit/style_s4_fields_test.dart
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart';

import '../../../test/support/setup_capture_preferences.dart';
import '../fixtures.dart';

const _out = 'build/audit-s4';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(loadCaptureFonts);

  testWidgets('s4 suggestions', (tester) async {
    const secure = MethodChannel(
      'plugins.it_nomads.com/flutter_secure_storage',
    );
    tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
      secure,
      (call) async => call.method == 'readAll' ? <String, String>{} : null,
    );
    tester.view.physicalSize = const Size(412, 915);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    final controller = await captureController(
      prefs: await setupCapturePreferences(),
      api: CaptureApi(),
    );
    addTearDown(controller.dispose);
    controller.catalog = sampleCatalog();
    final key = GlobalKey();
    await tester.pumpWidget(
      captureApp(
        home: const ChatScreen(sessionID: checkoutSessionID),
        boundaryKey: key,
        controller: controller,
      ),
    );
    for (var i = 0; i < 10; i++) {
      await tester.pump(const Duration(milliseconds: 120));
    }
    await tester.enterText(find.byType(EditableText).last, '/');
    await tester.runAsync(
      () => Future<void>.delayed(const Duration(milliseconds: 200)),
    );
    for (var i = 0; i < 10; i++) {
      await tester.pump(const Duration(milliseconds: 120));
    }
    await writePng(
      '$_out/suggestions.png',
      await capturePng(tester, key, pixelRatio: 2),
    );
    await tester.pumpWidget(const SizedBox.shrink());
    await tester.pump(const Duration(seconds: 1));
  });
}
