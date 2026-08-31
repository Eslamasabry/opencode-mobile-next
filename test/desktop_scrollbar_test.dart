import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/api/sse.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/desktop/desktop_interaction.dart';
import 'package:opencode_mobile/ui/screens/files_screen.dart';
import 'package:opencode_mobile/ui/screens/home_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// A widget test that runs with the platform reported as Linux desktop. The
/// override must be cleared inside the body: flutter_test asserts no
/// foundation debug variable outlives the test, so tearDown runs too late.
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

class _ListApi extends OpenCodeApi {
  _ListApi() : super(baseUrl: 'http://localhost');

  @override
  Future<List<Session>> sessions() async => [];

  @override
  Future<List<FileNode>> listFiles([String path = '']) async => [
    for (var i = 0; i < 60; i++)
      FileNode(name: 'file$i.dart', path: 'file$i.dart', isDir: false),
  ];
}

class _ListRepository implements ProductRepository {
  @override
  void setLocation({String? directory, String? workspace}) {}

  @override
  Future<List<WorkspaceProject>> listProjects() async => [];

  @override
  Future<List<WorkspaceInfo>> listWorkspaces() async => [];

  @override
  Future<List<TerminalProcess>> listTerminals() async => [];

  @override
  Future<CatalogSnapshot> loadCatalog() async =>
      const CatalogSnapshot(providers: [], models: [], agents: []);

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

Future<ConnectionController> _controller() async {
  SharedPreferences.setMockInitialValues({});
  final prefs = await SharedPreferences.getInstance();
  return ConnectionController(ProfileStore(prefs: prefs))
    ..api = _ListApi()
    ..repository = _ListRepository()
    ..status = StreamStatus.connected;
}

Widget _app(Widget home) => MaterialApp(
  scrollBehavior: const AppScrollBehavior(),
  localizationsDelegates: AppLocalizations.localizationsDelegates,
  supportedLocales: AppLocalizations.supportedLocales,
  home: home,
);

Iterable<Scrollbar> _scrollbars(WidgetTester tester) =>
    tester.widgetList<Scrollbar>(find.byType(Scrollbar));

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  desktopTest('a controller-owning list gets a pinned, single scrollbar', (
    tester,
  ) async {
    final connection = await _controller();
    addTearDown(connection.dispose);

    await tester.pumpWidget(
      _app(Scaffold(body: FilesScreen(controller: connection))),
    );
    await tester.pumpAndSettle();

    final bars = _scrollbars(tester).toList();
    expect(bars, hasLength(1), reason: 'exactly one thumb, never a pair');
    expect(bars.single.thumbVisibility, isTrue);
    expect(bars.single.controller, isNotNull);
  });

  testWidgets('android gets no desktop scrollbar', (tester) async {
    final connection = await _controller();
    addTearDown(connection.dispose);

    await tester.pumpWidget(
      _app(Scaffold(body: FilesScreen(controller: connection))),
    );
    await tester.pumpAndSettle();

    expect(find.byType(Scrollbar), findsNothing);
  });

  desktopTest('the behaviour pins a thumb wherever a controller exists', (
    tester,
  ) async {
    final controller = ScrollController();
    addTearDown(controller.dispose);
    await tester.pumpWidget(
      _app(
        Scaffold(
          body: ListView.builder(
            controller: controller,
            itemCount: 100,
            itemBuilder: (context, index) => Text('row $index'),
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();

    final bar = tester.widget<Scrollbar>(find.byType(Scrollbar));
    expect(bar.thumbVisibility, isTrue);
  });

  desktopTest('a controllerless list still gets Material\'s fading thumb', (
    tester,
  ) async {
    // thumbVisibility asserts on a scrollbar with no attached position, so a
    // scrollable that owns no controller must fall back rather than crash.
    await tester.pumpWidget(
      _app(
        Scaffold(
          body: ListView.builder(
            primary: false,
            itemCount: 100,
            itemBuilder: (context, index) => Text('row $index'),
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.byType(Scrollbar), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  desktopTest('the shell destinations all scroll with a pinned thumb', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(420, 900);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final connection = await _controller();
    addTearDown(connection.dispose);

    await tester.pumpWidget(
      ProviderScope(
        overrides: [connProvider.overrideWithValue(connection)],
        child: _app(const HomeScreen()),
      ),
    );
    await tester.pumpAndSettle();

    for (final destination in const [
      'Workspace',
      'Files',
      'Activity',
      'More',
    ]) {
      await tester.tap(
        find.descendant(
          of: find.byType(NavigationBar),
          matching: find.text(destination),
        ),
      );
      await tester.pumpAndSettle();
      final bars = _scrollbars(tester).where((bar) => bar.controller != null);
      expect(
        bars.where((bar) => bar.thumbVisibility == true),
        isNotEmpty,
        reason: 'destination $destination has no pinned scrollbar',
      );
    }
  });
}
