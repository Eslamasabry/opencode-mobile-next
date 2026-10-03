// Golden renders of slice-P3.7a "One diff component": every diff opens on
// the kit's KitDiffView with its one "Change 1 of N" navigator. Three doors:
// Files' changed-files row (straight into Review, no sheet in between), a
// changed file on Run results (straight into the diff, no record sheet), and
// the read-only diff page ([DiffPage], which replaced widgets/diff_view.dart).
// Phone 412x915 and one wide window (1280x800), dark and light, plus the
// diff page right to left; the app's real fonts at DPR 1.
//
// Regenerate deliberately:
//   flutter test --update-goldens test/revamp/slice_p37a_golden_test.dart
// and look at every changed image before committing it.
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/api/sse.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/kit/kit_top_bar.dart';
import 'package:opencode_mobile/ui/screens/files_screen.dart';
import 'package:opencode_mobile/ui/screens/review_workspace.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../../tool/capture/fixtures.dart'
    show captureTheme, loadCaptureFonts, sampleDiffs;

const _phone = Size(412, 915);
const _wide = Size(1280, 800);

String _name(String shot, Size size, bool light, {bool rtl = false}) => [
  'slice_p37a_$shot',
  if (size != _phone) '${size.width.toInt()}x${size.height.toInt()}',
  if (rtl) 'rtl',
  light ? 'light' : 'dark',
].join('_');

class _Api extends OpenCodeApi {
  _Api() : super(baseUrl: 'http://localhost');

  @override
  Future<List<Session>> sessions() async => [];

  @override
  Future<List<FileNode>> listFiles([String path = '']) async => [
    FileNode(name: 'lib', path: 'lib', isDir: true),
    FileNode(name: 'test', path: 'test', isDir: true),
    FileNode(name: 'README.md', path: 'README.md', isDir: false),
    FileNode(name: 'pubspec.yaml', path: 'pubspec.yaml', isDir: false),
  ];

  @override
  Future<List<String>> findFile(String query) async => const [];
}

class _Repository implements ProductRepository {
  @override
  void setLocation({String? directory, String? workspace}) {}

  @override
  Future<List<VersionControlFile>> listFileStatuses() async => const [
    VersionControlFile(
      path: 'test/checkout_test.dart',
      status: 'modified',
      additions: 5,
      deletions: 2,
    ),
    VersionControlFile(
      path: 'lib/checkout/checkout_bloc.dart',
      status: 'modified',
      additions: 6,
      deletions: 2,
    ),
  ];

  @override
  Future<List<FileDiff>> listVcsDiffs(VcsDiffMode mode) async => sampleDiffs();

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
  bool rtl = false,
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
        ..directory = '/srv/shopfront'
        ..status = StreamStatus.connected;
  addTearDown(controller.dispose);
  ReviewWorkspace.clearCache();
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
            child: Directionality(
              textDirection: rtl ? TextDirection.rtl : TextDirection.ltr,
              child: child!,
            ),
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
      matchesGoldenFile('goldens/${_name(shot, size, light, rtl: rtl)}.png'),
    );
  } finally {
    await tester.pumpWidget(const SizedBox.shrink());
    debugDefaultTargetPlatformOverride = null;
  }
}

Widget _files(ConnectionController controller) => FilesScreen(
  controller: controller,
  onAttachFile: (_, _) async {},
  topBar: (folder) => KitTopBar(
    title: folder ?? 'Files',
    exit: KitTopBarExit.back,
    onExit: () {},
  ),
);

Widget _diffPage(ConnectionController _) => DiffPage(diffs: sampleDiffs());

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(loadCaptureFonts);

  for (final light in [false, true]) {
    final mode = light ? 'light' : 'dark';
    for (final size in [_phone, _wide]) {
      final at = size == _phone ? 'phone' : 'wide';

      testWidgets('files changed row opens the diff · $at · $mode', (
        tester,
      ) async {
        await _shot(
          tester,
          'files_changes',
          light: light,
          size: size,
          home: _files,
          then: () =>
              tester.tap(find.byKey(const ValueKey('files-changes-card'))),
        );
      });
      testWidgets('diff page · $at · $mode', (tester) async {
        await _shot(
          tester,
          'diff_page',
          light: light,
          size: size,
          home: _diffPage,
        );
      });
    }
  }
  testWidgets('diff page right to left · dark', (tester) async {
    await _shot(tester, 'diff_page', light: false, rtl: true, home: _diffPage);
  });
}
