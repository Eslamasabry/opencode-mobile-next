// Golden renders of opening a folder in the phone's storage without typing
// its path: the folder browser's place switch, the consent question, the
// phone's folders (the top, and inside one), the plain note after access
// was refused, and a remote server's recent folders above the path field.
// 412x915, dark and light, with the app's real fonts.
//
// Regenerate deliberately:
//   flutter test --update-goldens test/goldens/phone_storage_browser_golden_test.dart
// and look at every changed image before committing it.
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/builtin/builtin_folders.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/platform/phone_storage_folders.dart';
import 'package:opencode_mobile/state/shared_storage_gate.dart';
import 'package:opencode_mobile/ui/screens/shared_storage_access_flow.dart';
import 'package:opencode_mobile/ui/widgets/folder_browser.dart';
import 'package:opencode_mobile/ui/widgets/remote_folder_picker.dart';

import '../../tool/capture/fixtures.dart' show captureTheme, loadCaptureFonts;

enum PhoneStorageScene {
  places,
  consent,
  root,
  inside,
  refused,
  remote,
  naming,
}

const _root = PhoneStorageFolders.root;

FolderEntry _folder(
  String parent,
  String name,
  List<String> inside,
  int more,
) => FolderEntry(name: name, path: '$parent/$name', inside: inside, more: more);

Future<List<FolderEntry>> _phone(String path) async => switch (path) {
  _root => [
    _folder(_root, 'Android', ['data', 'media'], 0),
    _folder(_root, 'CodeAnything', ['package.json', 'src'], 12),
    _folder(_root, 'DCIM', ['Camera'], 0),
    _folder(_root, 'Documents', ['notes.md', 'taxes.pdf'], 4),
    _folder(_root, 'Download', ['setup.apk', 'invoice.pdf'], 31),
    _folder(_root, 'Music', const [], 0),
  ],
  '$_root/CodeAnything' => [
    _folder('$_root/CodeAnything', 'docs', ['intro.md'], 0),
    _folder('$_root/CodeAnything', 'src', ['main.dart', 'app.dart'], 8),
    _folder('$_root/CodeAnything', 'test', ['app_test.dart'], 0),
  ],
  _ => const [],
};

Future<void> mountPhoneStorage(
  WidgetTester tester,
  PhoneStorageScene scene, {
  required bool light,
  required GlobalKey boundary,
}) async {
  tester.view.physicalSize = const Size(412, 915);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  var granted = scene != PhoneStorageScene.refused;
  Widget sheet() => switch (scene) {
    PhoneStorageScene.remote => RemoteFolderSheet(
      recent: const ['/home/sam/work/api', '/home/sam/notes'],
      projects: () async => ['/srv/shared/website'],
      probe: (_) async => null,
    ),
    _ => FolderBrowserSheet(
      list: (path) async => [
        const FolderEntry(name: 'demo', path: '/root/projects/demo'),
        const FolderEntry(name: 'notes', path: '/root/projects/notes'),
      ],
      phone: PhoneStoragePlace(
        list: _phone,
        ensureAccess: (_) async => granted,
        openedBefore: () async => [
          '$_root/CodeAnything',
          '$_root/Download/site',
        ],
      ),
    ),
  };
  await tester.pumpWidget(
    RepaintBoundary(
      key: boundary,
      child: MaterialApp(
        debugShowCheckedModeBanner: false,
        theme: captureTheme(light: light),
        locale: const Locale('en'),
        supportedLocales: AppLocalizations.supportedLocales,
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        home: Builder(
          builder: (context) {
            WidgetsBinding.instance.addPostFrameCallback((_) {
              if (scene == PhoneStorageScene.consent) {
                SharedStorageAccessFlow.resolve(
                  context,
                  SharedStorageBlock.appAccess,
                );
                return;
              }
              showModalBottomSheet<Object>(
                context: context,
                isScrollControlled: true,
                showDragHandle: true,
                builder: (_) => sheet(),
              );
            });
            return const Scaffold();
          },
        ),
      ),
    ),
  );
  await tester.pumpAndSettle();
  Future<void> tapKey(String key) async {
    await tester.tap(find.byKey(ValueKey(key)));
    await tester.pumpAndSettle();
  }

  if (scene == PhoneStorageScene.root ||
      scene == PhoneStorageScene.inside ||
      scene == PhoneStorageScene.naming ||
      scene == PhoneStorageScene.refused) {
    await tapKey('place-phone');
  }
  if (scene == PhoneStorageScene.inside || scene == PhoneStorageScene.naming) {
    await tapKey('in-app-project-CodeAnything');
  }
  if (scene == PhoneStorageScene.naming) {
    await tapKey('phone-new-folder');
  }
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(loadCaptureFonts);

  for (final light in [false, true]) {
    final mode = light ? 'light' : 'dark';
    for (final scene in PhoneStorageScene.values) {
      final name = 'phone_storage_${scene.name}';
      testWidgets(
        '$name · $mode',
        (tester) async {
          final boundary = GlobalKey();
          await mountPhoneStorage(
            tester,
            scene,
            light: light,
            boundary: boundary,
          );
          expect(tester.takeException(), isNull);
          await expectLater(
            find.byKey(boundary),
            matchesGoldenFile('${name}_$mode.png'),
          );
        },
        variant: TargetPlatformVariant.only(TargetPlatform.android),
      );
    }
  }
}
