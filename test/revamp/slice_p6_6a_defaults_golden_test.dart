// Golden renders of slice-P6.6a "Defaults instead of questions": Review
// changes opening the view that has changes and saying so once, the Work
// tab after it opened the server's only project by itself, and Settings'
// Model row naming the server's default. Phone 412x915 and one wide window
// (1280x800), dark and light, real fonts at DPR 1.
//
// Regenerate deliberately:
//   flutter test --update-goldens test/revamp/slice_p6_6a_defaults_golden_test.dart
// and look at every changed image before committing it.
import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/kit/kit_undo.dart';
import 'package:opencode_mobile/ui/screens/review_workspace.dart';
import 'package:opencode_mobile/ui/screens/settings_screen.dart';
import 'package:opencode_mobile/ui/widgets/default_notices.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../../tool/capture/fixtures.dart';

const _phone = Size(412, 915);
const _wide = Size(1280, 800);

String _name(String shot, Size size, bool light) => [
  'p66a_$shot',
  if (size != _phone) '${size.width.toInt()}x${size.height.toInt()}',
  light ? 'light' : 'dark',
].join('_');

final _changes = [
  FileDiff(
    file: 'lib/checkout/checkout_page.dart',
    status: 'modified',
    additions: 2,
    deletions: 2,
    patch:
        '@@ -12,6 +12,6 @@ class CheckoutPage extends StatelessWidget {\n'
        '   @override\n'
        '   Widget build(BuildContext context) {\n'
        '-    final total = cart.total;\n'
        '-    return Text(total.toString());\n'
        '+    final total = cart.totalWithTax;\n'
        '+    return Text(formatPrice(total));\n'
        '   }\n'
        ' }\n',
  ),
  FileDiff(
    file: 'lib/cart/cart_bloc.dart',
    status: 'modified',
    additions: 1,
    deletions: 1,
    patch:
        '@@ -40,3 +40,3 @@ class CartBloc {\n'
        '   void clear() => _items.clear();\n'
        '-  num get total => _items.fold(0, (a, b) => a + b.price);\n'
        '+  num get totalWithTax => _items.fold(0, (a, b) => a + b.gross);\n'
        '   int get count => _items.length;\n',
  ),
];

void _mockPlatform(WidgetTester tester) {
  final messenger = tester.binding.defaultBinaryMessenger;
  const secure = MethodChannel('plugins.it_nomads.com/flutter_secure_storage');
  const info = MethodChannel('dev.fluttercommunity.plus/package_info');
  const termux = MethodChannel('oc/termux');
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
  messenger.setMockMethodCallHandler(termux, (call) async => null);
  addTearDown(() {
    messenger.setMockMethodCallHandler(secure, null);
    messenger.setMockMethodCallHandler(info, null);
    messenger.setMockMethodCallHandler(termux, null);
  });
}

Future<void> _reviewShot(
  WidgetTester tester, {
  required bool light,
  Size size = _phone,
}) async {
  tester.view.physicalSize = size;
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  debugDefaultTargetPlatformOverride = TargetPlatform.android;
  // The profile row is saved, as the app's ProfileStore keeps it: the
  // defaults store only speaks for a server that still exists.
  SharedPreferences.setMockInitialValues({
    'oc.profiles': jsonEncode([
      {'id': 'laptop'},
    ]),
  });
  ReviewWorkspace.clearCache();
  resetDefaultNoticesForTest();
  final boundary = GlobalKey();
  try {
    await tester.pumpWidget(
      RepaintBoundary(
        key: boundary,
        child: MaterialApp(
          debugShowCheckedModeBanner: false,
          theme: captureTheme(light: light),
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          builder: (context, child) => MediaQuery(
            data: MediaQuery.of(context).copyWith(disableAnimations: true),
            child: child!,
          ),
          home: ReviewWorkspace(
            profileId: 'laptop',
            // This conversation changed nothing; the project has changes.
            loadDiffs: () async => const [],
            loadWorkingTreeDiffs: () async => _changes,
            loadBranchDiffs: () async => _changes,
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);
    // The shot is about the notice: it must be on screen.
    expect(find.textContaining('it is the view with changes'), findsOneWidget);
    await expectLater(
      find.byKey(boundary),
      matchesGoldenFile('goldens/${_name('review_scope', size, light)}.png'),
    );
  } finally {
    KitUndo.commitPending();
    await tester.pumpWidget(const SizedBox.shrink());
    debugDefaultTargetPlatformOverride = null;
  }
}

class _Api extends CaptureApi {
  @override
  ServerCapabilities get capabilities => ServerCapabilities.allV1;

  @override
  Future<Health> health() async => Health(healthy: true, version: '1.18.25');
}

class _Repository extends CaptureRepository {
  @override
  Future<TerminalShellSettings> loadTerminalShellSettings() async =>
      const TerminalShellSettings(selected: '', options: []);
}

Future<void> _settingsShot(
  WidgetTester tester, {
  required bool light,
  Size size = _phone,
}) async {
  _mockPlatform(tester);
  tester.view.physicalSize = size;
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  debugDefaultTargetPlatformOverride = TargetPlatform.android;
  SharedPreferences.setMockInitialValues({});
  final prefs = await SharedPreferences.getInstance();
  final store = SeededProfileStore(
    prefs: prefs,
    seeded: [
      ServerProfile(
        id: 'laptop',
        name: 'Laptop',
        baseUrl: 'http://192.168.1.20:4096',
        password: 'synthetic',
      ),
    ],
  );
  final ConnectionController controller = CaptureController(store)
    ..api = _Api()
    ..repository = _Repository()
    ..status = StreamStatus.connected
    ..directory = projectDirectory
    ..version = '1.18.25';
  controller.catalog = sampleCatalog();
  final model = controller.catalog!.models.first;
  // Not picked by the person: the server's default, as connect resolves it.
  controller.selectedModel = ModelRef(
    providerID: model.providerID,
    modelID: model.id,
  );
  controller.appearance.value = light
      ? AppAppearance.light
      : AppAppearance.dark;
  final boundary = GlobalKey();
  try {
    await tester.pumpWidget(
      captureApp(
        home: SettingsScreen(
          controller: controller,
          initialGroup: SettingsGroup.agent,
        ),
        boundaryKey: boundary,
        controller: controller,
        light: light,
        routes: {'/servers': (_) => const Scaffold()},
      ),
    );
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 500));
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);
    await expectLater(
      find.byKey(boundary),
      matchesGoldenFile('goldens/${_name('settings_model', size, light)}.png'),
    );
  } finally {
    await tester.pumpWidget(const SizedBox.shrink());
    controller.dispose();
    await tester.pump();
    debugDefaultTargetPlatformOverride = null;
  }
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(loadCaptureFonts);

  for (final light in [false, true]) {
    final mode = light ? 'light' : 'dark';
    for (final size in [_phone, _wide]) {
      final where = size == _phone ? 'phone' : 'wide';
      testWidgets('review opens the view with changes · $where · $mode', (
        tester,
      ) async {
        await _reviewShot(tester, light: light, size: size);
      });
      testWidgets('Settings names the default model · $where · $mode', (
        tester,
      ) async {
        await _settingsShot(tester, light: light, size: size);
      });
    }
  }
}
