import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api/sse.dart';
import 'package:opencode_mobile/builtin/builtin_linux.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/platform/platform_capabilities.dart';
import 'package:opencode_mobile/platform/storage_access.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/screens/files_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

class _Api extends OpenCodeApi {
  _Api(this.nodes) : super(baseUrl: 'http://localhost');

  final List<FileNode> nodes;

  @override
  Future<List<FileNode>> listFiles([String path = '']) async => nodes;

  @override
  Future<List<String>> findFile(String query) async => const [];

  @override
  Future<List<Session>> sessions() async => const [];

  @override
  Future<Map<String, String>> sessionStatuses() async => const {};
}

class _Store extends ProfileStore {
  _Store({required super.prefs});

  final profile = ServerProfile(
    id: 'builtin',
    name: 'This phone',
    baseUrl: BuiltinLinux.serverUrl,
    username: BuiltinLinux.serverUsername,
  );

  @override
  List<ServerProfile> get profiles => [profile];

  @override
  String? get activeId => profile.id;
}

Future<ConnectionController> _controller(
  List<FileNode> nodes,
  String directory,
) async {
  SharedPreferences.setMockInitialValues({});
  final prefs = await SharedPreferences.getInstance();
  return ConnectionController(_Store(prefs: prefs))
    ..api = _Api(nodes)
    ..directory = directory
    ..status = StreamStatus.connected;
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  late StorageAccess access;

  setUp(() {
    debugPlatformCapabilities = const PlatformCapabilities(
      platform: TargetPlatform.android,
      isWeb: false,
    );
    access = StorageAccess.notGranted;
    StorageAccessBridge.statusOverride = () async => access;
    StorageAccessBridge.openOverride = () async =>
        access = StorageAccess.granted;
  });

  tearDown(() {
    debugPlatformCapabilities = null;
    StorageAccessBridge.statusOverride = null;
    StorageAccessBridge.openOverride = null;
  });

  Future<void> show(
    WidgetTester tester,
    ConnectionController controller,
  ) async {
    addTearDown(controller.dispose);
    await tester.pumpWidget(
      MaterialApp(
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: Scaffold(body: FilesScreen(controller: controller)),
      ),
    );
    await tester.pumpAndSettle();
  }

  testWidgets('only dotfiles in a shared-storage folder without access ask '
      'for access instead of looking empty', (tester) async {
    final controller = await _controller([
      FileNode(name: '.git', path: '.git', isDir: true),
    ], '/sdcard/CodeAnything');
    await show(tester, controller);
    expect(find.text('Files are hidden'), findsOneWidget);
    expect(find.text('Folder is empty'), findsNothing);
    expect(find.text('Allow access to files'), findsOneWidget);
  });

  testWidgets('with access, or in the app project space, the listing is '
      'untouched', (tester) async {
    access = StorageAccess.granted;
    await show(
      tester,
      await _controller([
        FileNode(name: '.git', path: '.git', isDir: true),
      ], '/sdcard/CodeAnything'),
    );
    expect(find.text('Files are hidden'), findsNothing);
    expect(
      find.text('This folder has only hidden files and folders.'),
      findsOneWidget,
    );

    access = StorageAccess.notGranted;
    await show(
      tester,
      await _controller([
        FileNode(name: '.git', path: '.git', isDir: true),
      ], '/root/projects/app'),
    );
    expect(find.text('Files are hidden'), findsNothing);
  });

  testWidgets('visible files never trigger the notice', (tester) async {
    await show(
      tester,
      await _controller([
        FileNode(name: 'package.json', path: 'package.json', isDir: false),
      ], '/sdcard/CodeAnything'),
    );
    expect(find.text('Files are hidden'), findsNothing);
    expect(find.text('package.json'), findsOneWidget);
  });

  testWidgets('allowing access reloads the folder', (tester) async {
    final nodes = <FileNode>[FileNode(name: '.git', path: '.git', isDir: true)];
    final controller = await _controller(nodes, '/sdcard/CodeAnything');
    await show(tester, controller);
    nodes.add(
      FileNode(name: 'package.json', path: 'package.json', isDir: false),
    );
    await tester.tap(find.text('Allow access to files'));
    await tester.pumpAndSettle();
    // The explanation sheet, then its confirm.
    await tester.tap(find.byKey(const ValueKey('storage-access-allow')));
    await tester.pumpAndSettle();
    expect(find.text('package.json'), findsOneWidget);
    expect(find.text('Files are hidden'), findsNothing);
  });
}
