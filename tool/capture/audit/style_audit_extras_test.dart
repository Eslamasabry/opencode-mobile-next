// AUDIT TOOLING ONLY (branch audit/style-coherence, 2026-10-21): the floating
// pieces no other capture opens on their own: a popover menu, a dialog with
// a field, the Undo snackbar and a destructive confirmation, each over the
// real Conversations home. Style, mode and the rest come from AUDIT_* (see
// audit_env.dart); run through tool/capture/audit/run_style_audit.sh.
import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart';
import 'package:opencode_mobile/ui/screens/home_screen.dart';

import '../../../test/support/setup_capture_preferences.dart';
import '../fixtures.dart';

const _out = 'build/audit-extras';

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

  testWidgets('audit menu', (tester) async {
    final key = await mountHome(tester);
    unawaited(
      showKitMenu(
        homeContext(tester),
        position: const Offset(300, 180),
        items: [
          KitMenuItem(label: 'Rename', icon: Icons.edit, onSelected: () {}),
          KitMenuItem(
            label: 'Pin to top',
            icon: Icons.push_pin,
            onSelected: () {},
          ),
          KitMenuItem(label: 'Share', icon: Icons.share, onSelected: () {}),
          KitMenuItem(
            label: 'Delete',
            icon: Icons.delete,
            destructive: true,
            onSelected: () {},
          ),
        ],
      ),
    );
    await settle(tester);
    await shoot(tester, key, 'menu');
  });

  testWidgets('audit dialog', (tester) async {
    final key = await mountHome(tester);
    unawaited(
      showKitInputDialog(
        homeContext(tester),
        title: 'Rename conversation',
        label: 'Name',
        initial: 'Fix the basket after a restart',
        confirmLabel: 'Rename',
      ),
    );
    await settle(tester);
    await shoot(tester, key, 'dialog');
  });

  testWidgets('audit undo', (tester) async {
    final key = await mountHome(tester);
    showKitUndo(
      homeContext(tester),
      message: 'Conversation archived',
      onUndo: () {},
    );
    await settle(tester, 6);
    await shoot(tester, key, 'undo');
  });

  testWidgets('audit confirm', (tester) async {
    final key = await mountHome(tester);
    unawaited(
      showKitConfirm(
        homeContext(tester),
        title: 'Delete this conversation?',
        body: 'The messages leave this server for good.',
        confirmLabel: 'Delete',
        kind: KitConfirmKind.values.last,
        consequences: const ['3 changed files stay on the computer'],
      ),
    );
    await settle(tester);
    await shoot(tester, key, 'confirm');
  });
}
