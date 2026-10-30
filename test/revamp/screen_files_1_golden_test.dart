// Golden renders of screen-files-1's pages (wave 2b), rebuilt from kit
// parts: Files (the tree under the Project tab's one bar, the row menu, the
// kit viewer as a sheet and beside the list; the Changes sheet is gone,
// slice-P3.7a: the row opens the diff) and the
// Project tab (loaded, and with no project open offering the chooser).
// Phone 412x915 and one wide window (1280x800), dark and light (owner
// decision 2026-09-27: no Arabic), with the app's real fonts at DPR 1.
//
// Regenerate deliberately:
//   flutter test --update-goldens test/revamp/screen_files_1_golden_test.dart
// and look at every changed image before committing it.
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/models.dart';
import '../support/fake_server_gateway.dart';
import 'package:opencode_mobile/domain/product_repository.dart';
import '../support/fake_event_channel.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/kit/kit_top_bar.dart';
import 'package:opencode_mobile/ui/screens/files_screen.dart';
import 'package:opencode_mobile/ui/screens/project_hub_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../../tool/capture/fixtures.dart' show captureTheme, loadCaptureFonts;

const _phone = Size(412, 915);
const _wide = Size(1280, 800);

String _name(String shot, Size size, bool light) => [
  shot,
  if (size != _phone) '${size.width.toInt()}x${size.height.toInt()}',
  light ? 'light' : 'dark',
].join('_');

const _readme = '''
# shopfront

The storefront app: catalogue, cart and checkout.

## Run it

```sh
flutter run
```
''';

const _main = '''
import 'package:flutter/material.dart';

import 'checkout/checkout_page.dart';

void main() {
  runApp(const Shopfront());
}

class Shopfront extends StatelessWidget {
  const Shopfront({super.key});

  @override
  Widget build(BuildContext context) =>
      const MaterialApp(home: CheckoutPage());
}
''';

class _Api extends FakeServerGateway {
  _Api() : super(baseUrl: 'http://localhost');

  @override
  Future<List<Session>> sessions() async => [];

  @override
  Future<List<FileNode>> listFiles([String path = '']) async => [
    FileNode(name: '.github', path: '.github', isDir: true),
    FileNode(name: 'android', path: 'android', isDir: true),
    FileNode(name: 'lib', path: 'lib', isDir: true),
    FileNode(name: 'test', path: 'test', isDir: true),
    FileNode(name: 'README.md', path: 'README.md', isDir: false),
    FileNode(name: 'main.dart', path: 'main.dart', isDir: false),
    FileNode(name: 'pubspec.yaml', path: 'pubspec.yaml', isDir: false),
    FileNode(
      name: 'analysis_options.yaml',
      path: 'analysis_options.yaml',
      isDir: false,
    ),
  ];

  @override
  Future<List<String>> findFile(String query) async => const [];

  @override
  Future<FileContent> fileContent(String path) async => path.endsWith('.md')
      ? const FileContent(_readme, mimeType: 'text/markdown')
      : const FileContent(_main, mimeType: 'text/plain');
}

class _Repository implements ProductRepository {
  @override
  void setLocation({String? directory, String? workspace}) {}

  @override
  Future<List<VersionControlFile>> listFileStatuses() async => const [
    VersionControlFile(
      path: 'main.dart',
      status: 'modified',
      additions: 6,
      deletions: 2,
    ),
    VersionControlFile(
      path: 'lib/cart/cart_bloc.dart',
      status: 'modified',
      additions: 1,
      deletions: 1,
    ),
    VersionControlFile(
      path: 'test/checkout_test.dart',
      status: 'added',
      additions: 24,
      deletions: 0,
    ),
  ];

  @override
  Future<List<WorkspaceProject>> listProjects() async => const [];

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

Future<void> _shot(
  WidgetTester tester,
  String shot, {
  required bool light,
  required Widget Function(ConnectionController controller) home,
  Size size = _phone,
  String? directory = '/srv/shopfront',
  Future<void> Function()? then,
}) async {
  tester.view.physicalSize = size;
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  debugDefaultTargetPlatformOverride = TargetPlatform.android;
  SharedPreferences.setMockInitialValues({});
  final controller =
      ConnectionController(
          ProfileStore(prefs: await SharedPreferences.getInstance()),
        )
        ..api = _Api()
        ..repository = _Repository()
        ..directory = directory
        ..status = StreamStatus.connected;
  addTearDown(controller.dispose);
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
          home: Scaffold(body: SafeArea(child: home(controller))),
        ),
      ),
    );
    await tester.pumpAndSettle();
    if (then != null) await then();
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

Widget _files(ConnectionController controller) => FilesScreen(
  controller: controller,
  onAttachFile: (_, _) async {},
  topBar: (path, search, view) => KitTopBar(
    title: path.isEmpty ? 'Files' : path.split('/').last,
    exit: KitTopBarExit.back,
    onExit: () {},
    actions: [search],
    menu: view,
  ),
);

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(loadCaptureFonts);

  for (final light in [false, true]) {
    final mode = light ? 'light' : 'dark';

    testWidgets('files loaded · $mode', (tester) async {
      await _shot(tester, 'files_list_loaded', light: light, home: _files);
    });
    testWidgets('files two panes with the viewer · $mode', (tester) async {
      await _shot(
        tester,
        'files_viewer_pane',
        light: light,
        size: _wide,
        home: _files,
        then: () => tester.tap(find.text('main.dart')),
      );
    });
    testWidgets('files row menu · $mode', (tester) async {
      await _shot(
        tester,
        'files_row_menu',
        light: light,
        home: _files,
        then: () => tester.longPress(find.text('main.dart')),
      );
    });
    testWidgets('files viewer sheet · $mode', (tester) async {
      await _shot(
        tester,
        'files_viewer_sheet',
        light: light,
        home: _files,
        then: () => tester.tap(find.text('README.md')),
      );
    });
    testWidgets('project hub · $mode', (tester) async {
      await _shot(
        tester,
        'project_hub_loaded',
        light: light,
        home: (controller) => ProjectHub(controller: controller),
      );
    });
    testWidgets('project hub wide · $mode', (tester) async {
      await _shot(
        tester,
        'project_hub_loaded',
        light: light,
        size: _wide,
        home: (controller) => ProjectHub(controller: controller),
      );
    });
    testWidgets('project hub with no project · $mode', (tester) async {
      await _shot(
        tester,
        'project_hub_no_project',
        light: light,
        directory: null,
        home: (controller) => ProjectHub(controller: controller),
      );
    });
  }
}
