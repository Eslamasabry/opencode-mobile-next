// Opening a folder in the phone's shared storage without typing its path:
// the folder browser's "This phone's storage" place (consent first, then
// folder by folder), and, for a server that is not on this phone, the
// folders opened before as tappable rows above the path field.
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/builtin/builtin_folders.dart';
import 'package:opencode_mobile/builtin/builtin_linux.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/platform/phone_storage_folders.dart';
import 'package:opencode_mobile/platform/platform_capabilities.dart';
import 'package:opencode_mobile/platform/storage_access.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/state/shared_project_roots.dart';
import 'package:opencode_mobile/ui/kit/kit_text.dart';
import 'package:opencode_mobile/ui/screens/project_folder_actions.dart';
import 'package:opencode_mobile/ui/screens/shared_storage_access_flow.dart';
import 'package:shared_preferences/shared_preferences.dart';

class _Store extends ProfileStore {
  _Store({required super.prefs, required this.profile, this.recent = const []});

  final ServerProfile profile;
  final List<String> recent;

  @override
  List<ServerProfile> get profiles => [profile];

  @override
  String? get activeId => profile.id;

  @override
  List<ProfileLocation> recentLocations(String profileId) => [
    for (final directory in recent) ProfileLocation(directory: directory),
  ];
}

class _Controller extends ConnectionController {
  _Controller(super.store);

  final opened = <String?>[];
  final probed = <String>[];
  String? probeAnswer;

  @override
  Future<void> selectLocation({String? directory, String? workspace}) async {
    opened.add(directory);
    locationError = null;
  }

  @override
  Future<String?> probeProjectFolder(String directory) async {
    probed.add(directory);
    return probeAnswer;
  }
}

class _Linux extends BuiltinLinux {
  final created = <String>[];

  @override
  Future<BuiltinLinuxStatus> status() async =>
      const BuiltinLinuxStatus(installed: true, phase: BuiltinLinuxPhase.ready);

  @override
  Future<BuiltinLinuxRunResult> run(
    String script, {
    Duration timeout = const Duration(minutes: 2),
  }) async {
    final path = RegExp(r"dir='([^']*)'").firstMatch(script)?[1];
    if (path == null) {
      return const BuiltinLinuxRunResult(exitCode: 127, output: 'unexpected');
    }
    created.add(path);
    return BuiltinLinuxRunResult(exitCode: 0, output: 'created $path\n');
  }
}

const _root = PhoneStorageFolders.root;

FolderEntry _folder(
  String parent,
  String name, {
  List<String>? inside,
  int more = 0,
}) =>
    FolderEntry(name: name, path: '$parent/$name', inside: inside, more: more);

/// The phone's folders: path -> entries.
final _phone = <String, List<FolderEntry>>{
  _root: [
    _folder(_root, '.thumbnails'),
    _folder(_root, 'Download', inside: ['a.pdf']),
    _folder(_root, 'Projects', inside: ['package.json', 'src'], more: 12),
    _folder(_root, 'Empty', inside: const []),
  ],
  '$_root/Projects': [
    _folder('$_root/Projects', 'app', inside: ['x']),
  ],
  '$_root/Download': const [],
};

final _inApp = ServerProfile(
  id: 'builtin',
  name: 'This phone',
  baseUrl: BuiltinLinux.serverUrl,
  username: BuiltinLinux.serverUsername,
);

final _remote = ServerProfile(
  id: 'laptop',
  name: 'Laptop',
  baseUrl: 'https://laptop.example.com',
  username: 'opencode',
);

void main() {
  late _Linux linux;
  late _Controller controller;
  late StorageAccess access;
  late bool grant;
  late int asked;
  String? result;

  Future<void> build({
    ServerProfile? profile,
    List<String> recent = const [],
  }) async {
    SharedPreferences.setMockInitialValues({});
    controller = _Controller(
      _Store(
        prefs: await SharedPreferences.getInstance(),
        profile: profile ?? _inApp,
        recent: recent,
      ),
    );
  }

  setUp(() {
    debugPlatformCapabilities = const PlatformCapabilities.android();
    linux = _Linux();
    access = StorageAccess.notGranted;
    grant = true;
    asked = 0;
    result = null;
    ProjectFolderActions.builtinLinuxOverride = linux;
    ProjectFolderActions.folderListerOverride = (path) async =>
        path == '/root/projects'
        ? [const FolderEntry(name: 'demo', path: '/root/projects/demo')]
        : const [];
    ProjectFolderActions.phoneListerOverride = (path) async {
      final entries = _phone[path];
      if (entries == null) {
        throw const FolderListException(FolderListProblem.denied);
      }
      return entries;
    };
    StorageAccessBridge.statusOverride = () async => access;
    StorageAccessBridge.openOverride = () async {
      asked++;
      if (grant) access = StorageAccess.granted;
    };
    SharedStorageAccessFlow.confinedRunningOverride = (_) async => false;
    SharedProjectRoots.pushOverride = (_) async {};
  });

  tearDown(() {
    debugPlatformCapabilities = null;
    ProjectFolderActions.builtinLinuxOverride = null;
    ProjectFolderActions.folderListerOverride = null;
    ProjectFolderActions.phoneListerOverride = null;
    StorageAccessBridge.statusOverride = null;
    StorageAccessBridge.openOverride = null;
    SharedStorageAccessFlow.confinedRunningOverride = null;
    SharedProjectRoots.pushOverride = null;
    controller.dispose();
  });

  Future<void> openSheet(WidgetTester tester) async {
    tester.view
      ..devicePixelRatio = 1
      ..physicalSize = const Size(412, 915);
    addTearDown(tester.view.reset);
    await tester.pumpWidget(
      MaterialApp(
        supportedLocales: AppLocalizations.supportedLocales,
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        home: Scaffold(
          body: Builder(
            builder: (context) => TextButton(
              onPressed: () async => result =
                  await ProjectFolderActions.openFolder(context, controller),
              child: const Text('go'),
            ),
          ),
        ),
      ),
    );
    await tester.tap(find.text('go'));
    await tester.pumpAndSettle();
    // The sheet opens on its start page; these tests browse from there.
    final browse = find.byKey(const ValueKey('open-project-browse'));
    if (browse.evaluate().isNotEmpty) {
      await tester.tap(browse);
      await tester.pumpAndSettle();
    }
  }

  Future<void> tapKey(WidgetTester tester, String key) async {
    await tester.ensureVisible(find.byKey(ValueKey(key)));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(ValueKey(key)));
    await tester.pumpAndSettle();
  }

  // The header's title is the folder shown.
  String title(WidgetTester tester) => tester
      .widget<KitText>(
        find.byWidgetPredicate(
          (widget) => widget is KitText && widget.role == KitTextRole.title,
        ),
      )
      .text;

  Future<void> choosePhone(WidgetTester tester) async {
    await tester.tap(find.byKey(const ValueKey('folder-browser-places')));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('place-phone')));
    await tester.pumpAndSettle();
  }

  testWidgets('access missing: consent first, then the phone listing', (
    tester,
  ) async {
    await build();
    await openSheet(tester);
    // The place is one quiet line under the title.
    expect(find.text('Project space'), findsOneWidget);
    await choosePhone(tester);
    expect(find.byKey(const ValueKey('storage-access-allow')), findsOneWidget);
    expect(find.text('Download'), findsNothing);
    await tester.tap(find.byKey(const ValueKey('storage-access-allow')));
    await tester.pumpAndSettle();
    expect(asked, 1);
    expect(title(tester), 'Internal storage');
    expect(find.text('This phone'), findsOneWidget);
    // Folders first by the lister's order; hidden ones are not shown.
    expect(find.text('Download'), findsOneWidget);
    expect(find.text('.thumbnails'), findsNothing);
    expect(find.text('package.json, src, 12 more'), findsOneWidget);
    expect(find.text('Empty', skipOffstage: false), findsWidgets);
    // Nothing is opened until asked; the root itself is never offered.
    expect(find.byKey(const ValueKey('folder-browser-open')), findsNothing);
    expect(controller.opened, isEmpty);
  });

  testWidgets('access already on: no question, hidden folders on a switch', (
    tester,
  ) async {
    access = StorageAccess.granted;
    await build();
    await openSheet(tester);
    await choosePhone(tester);
    expect(find.byKey(const ValueKey('storage-access-allow')), findsNothing);
    expect(find.text('Projects'), findsOneWidget);
    await tester.tap(find.byKey(const ValueKey('kit-sheet-menu')));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Show hidden folders'));
    await tester.pumpAndSettle();
    expect(find.text('.thumbnails'), findsOneWidget);
  });

  testWidgets('goes into a folder, up again, and shows the trail', (
    tester,
  ) async {
    access = StorageAccess.granted;
    await build();
    await openSheet(tester);
    await choosePhone(tester);
    await tapKey(tester, 'in-app-project-Projects');
    expect(find.text('app'), findsOneWidget);
    expect(title(tester), 'Projects');
    expect(find.text('Open Projects'), findsOneWidget);
    await tapKey(tester, 'folder-browser-up');
    expect(title(tester), 'Internal storage');
    expect(find.text('Download'), findsOneWidget);
    await tapKey(tester, 'in-app-project-Download');
    expect(find.text('No folders in here'), findsOneWidget);
    expect(
      find.text('Open, make a new folder', skipOffstage: false),
      findsNothing,
    );
  });

  testWidgets('Open this folder returns the shared-storage path', (
    tester,
  ) async {
    access = StorageAccess.granted;
    await build();
    await openSheet(tester);
    await choosePhone(tester);
    await tapKey(tester, 'in-app-project-Projects');
    await tapKey(tester, 'folder-browser-open');
    expect(result, '$_root/Projects');
    expect(controller.opened, ['$_root/Projects']);
    // The existing gate recorded the root for AI Team.
    expect(await SharedProjectRoots.all('builtin'), ['$_root/Projects']);
  });

  testWidgets('a new project can be made on the phone with Change folder', (
    tester,
  ) async {
    access = StorageAccess.granted;
    await build();
    await openSheet(tester);
    await tapKey(tester, 'folder-browser-start');
    await tapKey(tester, 'open-project-new');
    await tapKey(tester, 'phone-new-folder-change');
    await choosePhone(tester);
    await tapKey(tester, 'in-app-project-Projects');
    await tapKey(tester, 'folder-browser-use');
    await tester.enterText(
      find.byKey(const ValueKey('phone-new-folder-name')),
      'fresh',
    );
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('phone-new-folder-create')));
    await tester.pumpAndSettle();
    expect(linux.created, ['$_root/Projects/fresh']);
    expect(result, '$_root/Projects/fresh');
  });

  testWidgets('refused access: back to the project space with a plain note', (
    tester,
  ) async {
    grant = false;
    await build();
    await openSheet(tester);
    await choosePhone(tester);
    await tester.tap(find.byKey(const ValueKey('storage-access-allow')));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Close'));
    await tester.pumpAndSettle();
    expect(
      find.byKey(const ValueKey('folder-browser-phone-refused')),
      findsOneWidget,
    );
    // The project space still works.
    expect(find.text('demo'), findsOneWidget);
    expect(title(tester), 'projects');
  });

  testWidgets('a folder opened before is a row on the start page', (
    tester,
  ) async {
    access = StorageAccess.granted;
    await build(recent: ['/sdcard/CodeAnything', '/root/projects/demo']);
    await openSheet(tester);
    await tapKey(tester, 'folder-browser-start');
    expect(find.text('Opened before'), findsOneWidget);
    expect(find.text('CodeAnything'), findsOneWidget);
    expect(find.text('demo'), findsOneWidget);
    await tapKey(tester, 'open-project-recent-0');
    expect(result, '$_root/CodeAnything');
  });

  testWidgets('remote server: recent folders are rows above the path', (
    tester,
  ) async {
    await build(
      profile: _remote,
      recent: ['/home/me/work/api', '/home/me/notes'],
    );
    await openSheet(tester);
    expect(find.text('Folders'), findsOneWidget);
    expect(find.text('api'), findsOneWidget);
    // No fake browsing, no place switch.
    expect(find.text('This phone'), findsNothing);
    // The field starts from the folder used last.
    final field = tester.widget<EditableText>(
      find.descendant(
        of: find.byKey(const ValueKey('open-folder-path')),
        matching: find.byType(EditableText),
      ),
    );
    expect(field.controller.text, '/home/me/work/');
    await tapKey(tester, 'remote-recent-1');
    expect(result, '/home/me/notes');
    expect(controller.opened, ['/home/me/notes']);
  });

  testWidgets('remote server: a typed path is checked on the server', (
    tester,
  ) async {
    await build(profile: _remote, recent: ['/home/me/work/api']);
    controller.probeAnswer = 'That folder was not found on the server.';
    await openSheet(tester);
    await tester.enterText(
      find.byKey(const ValueKey('open-folder-path')),
      '/home/me/work/web',
    );
    await tester.tap(find.byKey(const ValueKey('open-folder-confirm')));
    await tester.pumpAndSettle();
    expect(find.textContaining('was not found'), findsOneWidget);
    controller.probeAnswer = null;
    await tester.tap(find.byKey(const ValueKey('open-folder-confirm')));
    await tester.pumpAndSettle();
    expect(result, '/home/me/work/web');
  });
}
