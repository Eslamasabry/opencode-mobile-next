// Open a project for the OpenCode server this app runs in Termux ("This
// phone · Termux"): the folder browser lists folders through Termux (the
// `oc/termux` channel, mocked here as the other Termux tests do), only
// when that server is in use and Termux can run the app's commands.
import 'dart:async';
import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/platform/platform_capabilities.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/termux/bridge.dart';
import 'package:opencode_mobile/termux/termux_folders.dart';
import 'package:opencode_mobile/ui/kit/kit_text.dart';
import 'package:opencode_mobile/ui/screens/project_folder_actions.dart';
import 'package:shared_preferences/shared_preferences.dart';

class _Store extends ProfileStore {
  _Store({required super.prefs, required this.profile});

  final ServerProfile profile;

  @override
  List<ServerProfile> get profiles => [profile];

  @override
  String? get activeId => profile.id;
}

class _Controller extends ConnectionController {
  _Controller(super.store);

  final opened = <String?>[];
  final probed = <String>[];

  @override
  Future<void> selectLocation({String? directory, String? workspace}) async {
    opened.add(directory);
    locationError = null;
  }

  @override
  Future<String?> probeProjectFolder(String directory) async {
    probed.add(directory);
    return null;
  }
}

final _termuxProfile = ServerProfile(
  id: 'phone',
  name: 'This phone · Termux',
  baseUrl: 'http://127.0.0.1:${TermuxBridge.managedServerPort}',
  username: 'opencode',
);

final _remoteProfile = ServerProfile(
  id: 'laptop',
  name: 'Laptop',
  baseUrl: 'http://100.64.0.2:4096',
  username: 'opencode',
);

/// Termux's Ubuntu as far as folders go: path -> its child folders (name,
/// git). Answers the app's scripts the way they print.
const _tree = <String, List<(String, bool)>>{
  '/root/projects': [('demo', true), ('work', false)],
  '/root/projects/work': [('api', true)],
};

void main() {
  late List<String> scripts;
  late List<String> listed;
  late bool installed;
  late bool hang;
  late Set<String> made;

  setUp(() {
    debugPlatformCapabilities = const PlatformCapabilities.android();
    scripts = [];
    listed = [];
    installed = true;
    hang = false;
    made = {};
  });

  tearDown(() => debugPlatformCapabilities = null);

  Map<String, Object> result(String stdout) => {
    'stdout': stdout,
    'stderr': '',
    'exitCode': 0,
    'err': -1,
    'errorMessage': '',
  };

  void mockTermux(WidgetTester tester) {
    tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
      const MethodChannel('oc/termux'),
      (call) async {
        switch (call.method) {
          case 'getCapabilities':
            return <String, Object>{
              'installed': installed,
              'version': '0.118',
              'serviceAvailable': installed,
              'protocolSupported': installed,
              'permissionGranted': installed,
            };
          case 'runInTermux':
            final script = (call.arguments as Map)['script'] as String;
            scripts.add(script);
            final encoded = RegExp(
              r"' -- '([A-Za-z0-9+/=]*)'",
            ).firstMatch(script)?[1];
            if (encoded == null) return result('');
            final path = utf8.decode(base64.decode(encoded));
            if (script.contains('oc-folder-created')) {
              made.add(path);
              return result('oc-folder-created\n');
            }
            listed.add(path);
            if (hang) return Completer<Map<String, Object>>().future;
            final children = _tree[path];
            if (children == null) return result('oc-folders-missing\n');
            return result(
              // proot-distro's own warnings come first on some phones.
              'proot warning: can\'t sanitize binding "/proc/self/fd/0"\n'
              'oc-folders-ok\n'
              '${children.map((c) => 'oc-dir\t${c.$2 ? 'g' : '-'}\t${c.$1}\n').join()}',
            );
        }
        return null;
      },
    );
    addTearDown(
      () => tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
        const MethodChannel('oc/termux'),
        null,
      ),
    );
  }

  Future<(_Controller, ValueNotifier<String?>)> open(
    WidgetTester tester, {
    ServerProfile? profile,
  }) async {
    SharedPreferences.setMockInitialValues({});
    final controller = _Controller(
      _Store(
        prefs: await SharedPreferences.getInstance(),
        profile: profile ?? _termuxProfile,
      ),
    );
    addTearDown(controller.dispose);
    mockTermux(tester);
    final picked = ValueNotifier<String?>(null);
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: Builder(
            builder: (context) => TextButton(
              onPressed: () async => picked.value =
                  await ProjectFolderActions.openFolder(context, controller),
              child: const Text('go'),
            ),
          ),
        ),
      ),
    );
    await tester.tap(find.text('go'));
    return (controller, picked);
  }

  // The header's title is the folder shown.
  String shownPath(WidgetTester tester) => tester
      .widget<KitText>(
        find.byWidgetPredicate(
          (widget) => widget is KitText && widget.role == KitTextRole.title,
        ),
      )
      .text;

  testWidgets('the Termux server browses its folders through Termux', (
    tester,
  ) async {
    final (controller, picked) = await open(tester);
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('open-project-browse')));
    await tester.pumpAndSettle();
    expect(shownPath(tester), 'projects');
    expect(find.text('demo'), findsOneWidget);
    expect(find.textContaining('proot'), findsNothing);
    expect(scripts.last, TermuxFolders.listScript('/root/projects'));

    await tester.tap(find.byKey(const ValueKey('folder-browse-work')));
    await tester.pumpAndSettle();
    expect(shownPath(tester), 'work');
    expect(find.text('api'), findsOneWidget);
    await tester.tap(find.byKey(const ValueKey('folder-browser-up')));
    await tester.pumpAndSettle();
    expect(listed, ['/root/projects', '/root/projects/work', '/root/projects']);

    await tester.tap(find.byKey(const ValueKey('in-app-project-demo')));
    await tester.pumpAndSettle();
    expect(picked.value, '/root/projects/demo');
    expect(controller.opened, ['/root/projects/demo']);
    expect(controller.probed, isEmpty);
  });

  testWidgets('a new project is made in Termux in the project space', (
    tester,
  ) async {
    final (controller, picked) = await open(tester);
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('open-project-new')));
    await tester.pumpAndSettle();
    await tester.enterText(
      find.byKey(const ValueKey('phone-new-folder-name')),
      'cli',
    );
    await tester.tap(find.byKey(const ValueKey('phone-new-folder-create')));
    await tester.pumpAndSettle();
    expect(made, {'/root/projects/cli'});
    expect(scripts.last, TermuxFolders.createScript('/root/projects/cli'));
    expect(picked.value, '/root/projects/cli');
    expect(controller.opened, ['/root/projects/cli']);
  });

  testWidgets('a slow Termux shows skeleton rows, then says it took too '
      'long', (tester) async {
    hang = true;
    await open(tester);
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 400));
    await tester.tap(find.byKey(const ValueKey('open-project-browse')));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 400));
    expect(find.byKey(const ValueKey('kit-skeleton-rows')), findsOneWidget);
    await tester.pump(TermuxFolders.defaultTimeout);
    await tester.pumpAndSettle();
    expect(find.text('This folder can’t be shown'), findsOneWidget);
    expect(find.text('It took too long to answer. Try again.'), findsOneWidget);
    hang = false;
    await tester.tap(find.byKey(const ValueKey('folder-browser-retry')));
    await tester.pumpAndSettle();
    expect(find.text('demo'), findsOneWidget);
  });

  testWidgets('without a usable Termux there is no browser, only a path', (
    tester,
  ) async {
    installed = false;
    await open(tester);
    await tester.pumpAndSettle();
    expect(find.byKey(const ValueKey('in-app-projects')), findsNothing);
    expect(find.byKey(const ValueKey('open-folder-path')), findsOneWidget);
    expect(scripts, isEmpty);
  });

  testWidgets('a remote server keeps Enter a path', (tester) async {
    await open(tester, profile: _remoteProfile);
    await tester.pumpAndSettle();
    expect(find.byKey(const ValueKey('in-app-projects')), findsNothing);
    expect(find.byKey(const ValueKey('open-folder-path')), findsOneWidget);
    expect(scripts, isEmpty);
  });
}
