// AUDIT TOOLING ONLY (slice S1 of the style cleanup, 2026-10-21): the S1
// pieces no other capture opens on their own: the folder browser in a framed
// sheet (showKitFramedSheet), a term bubble and a tip bubble, each over the
// real Conversations home. Style, mode and the rest come from AUDIT_* (see
// audit_env.dart); run through tool/capture/audit/run_style_audit.sh.
import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart';
import 'package:opencode_mobile/ui/screens/home_screen.dart';
import 'package:opencode_mobile/ui/widgets/folder_browser.dart';
import 'package:opencode_mobile/builtin/builtin_folders.dart';

import '../../../test/support/setup_capture_preferences.dart';
import '../fixtures.dart';

const _out = 'build/audit-s1';

void _mockPlatform(WidgetTester tester) {
  const secure = MethodChannel('plugins.it_nomads.com/flutter_secure_storage');
  const info = MethodChannel('dev.fluttercommunity.plus/package_info');
  final messenger = tester.binding.defaultBinaryMessenger;
  messenger.setMockMethodCallHandler(
    secure,
    (call) async => call.method == 'readAll' ? <String, String>{} : null,
  );
  messenger.setMockMethodCallHandler(
    info,
    (call) async => <String, dynamic>{
      'appName': 'OpenCode Mobile',
      'packageName': 'com.opencode.mobile',
      'version': '1.0.44',
      'buildNumber': '52',
      'buildSignature': '',
    },
  );
  addTearDown(() {
    messenger.setMockMethodCallHandler(secure, null);
    messenger.setMockMethodCallHandler(info, null);
  });
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(() async {
    await loadCaptureFonts();
    chatScreenFor = (id) => ChatScreen(sessionID: id);
  });

  Future<void> settle(WidgetTester tester, [int frames = 12]) async {
    for (var i = 0; i < frames; i++) {
      await tester.pump(const Duration(milliseconds: 100));
    }
  }

  Future<GlobalKey> mountHome(WidgetTester tester) async {
    _mockPlatform(tester);
    tester.view.physicalSize = const Size(412, 915);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    final controller = await captureController(
      prefs: await setupCapturePreferences(),
      api: CaptureApi(),
    );
    addTearDown(controller.dispose);
    controller.catalog = sampleCatalog();
    final boundary = GlobalKey();
    await tester.pumpWidget(
      captureApp(
        home: const HomeScreen(initialTab: 0),
        boundaryKey: boundary,
        controller: controller,
        routes: {
          '/home': (_) => const Scaffold(),
          '/servers': (_) => const Scaffold(),
          '/guide': (_) => const Scaffold(),
        },
      ),
    );
    await tester.runAsync(
      () => Future<void>.delayed(const Duration(milliseconds: 300)),
    );
    await settle(tester);
    return boundary;
  }

  BuildContext homeContext(WidgetTester tester) =>
      tester.element(find.byType(HomeScreen));

  Future<void> shoot(WidgetTester tester, GlobalKey key, String name) async {
    await writePng(
      '$_out/$name.png',
      await capturePng(tester, key, pixelRatio: 2),
    );
    await tester.pumpWidget(const SizedBox.shrink());
    await tester.pump(const Duration(seconds: 1));
  }

  testWidgets('s1 folder', (tester) async {
    final key = await mountHome(tester);
    unawaited(
      showKitFramedSheet<FolderBrowserChoice>(
        homeContext(tester),
        builder: (_) => FolderBrowserSheet(
          list: (path) async => [
            for (final name in ['shopfront', 'notes', 'api-server'])
              FolderEntry(
                name: name,
                path: '$path/$name',
                isGit: name != 'notes',
                inside: const ['lib', 'test', 'README.md'],
              ),
          ],
          knownProjects: () async => {'/root/projects/shopfront'},
          start: '/root/projects',
        ),
      ),
    );
    await tester.runAsync(
      () => Future<void>.delayed(const Duration(milliseconds: 200)),
    );
    await settle(tester);
    await shoot(tester, key, 'folder');
  });

  testWidgets('s1 term', (tester) async {
    final key = await mountHome(tester);
    unawaited(
      showKitTerm(
        tester.element(find.byType(KitTopBar).first),
        term: 'Context',
        explanation: 'What the model can see of this conversation.',
      ),
    );
    await settle(tester);
    await shoot(tester, key, 'term');
  });

  testWidgets('s1 tip', (tester) async {
    final key = await mountHome(tester);
    final entry = OverlayEntry(
      builder: (_) => const PositionedDirectional(
        start: 16,
        end: 16,
        bottom: 220,
        child: Align(
          alignment: AlignmentDirectional.centerStart,
          child: KitTipBubble(
            message: 'Hold a conversation to pin or rename it.',
          ),
        ),
      ),
    );
    Overlay.of(homeContext(tester), rootOverlay: true).insert(entry);
    await settle(tester);
    await shoot(tester, key, 'tip');
  });
}
