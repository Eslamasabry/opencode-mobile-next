// Synthetic file-browser evidence. No server, credentials or real file reads.
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/ui/screens/files_screen.dart';

import '../../test/support/setup_capture_preferences.dart';
import 'fixtures.dart';

class _Api extends CaptureApi {
  _Api(this.empty);
  final bool empty;
  @override
  Future<List<FileNode>> listFiles([String path = '']) async => empty
      ? []
      : path == 'src'
      ? [FileNode(name: 'main.dart', path: 'src/main.dart', isDir: false)]
      : [
          for (final name in ['.git', 'dist', 'node_modules', 'src'])
            FileNode(name: name, path: name, isDir: true),
          for (final name in [
            '.gitignore',
            'index.html',
            'package-lock.json',
            'package.json',
            'README.md',
          ])
            FileNode(name: name, path: name, isDir: false),
        ];
}

class _Repository extends CaptureRepository {
  _Repository(this.empty);
  final bool empty;
  @override
  Future<List<VersionControlFile>> listFileStatuses() async => empty
      ? []
      : [
          for (final name in [
            '.gitignore',
            'index.html',
            'package-lock.json',
            'package.json',
            'README.md',
            'src/main.dart',
          ])
            VersionControlFile(
              path: name,
              status: 'added',
              additions: name == 'package-lock.json' ? 848 : 18,
              deletions: 0,
            ),
        ];
}

const _captureSet = String.fromEnvironment(
  'FILES_CAPTURE_SET',
  defaultValue: 'after',
);

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(loadCaptureFonts);
  for (final light in [false, true]) {
    for (final scale in [1.0, 2.0]) {
      for (final empty in [false, true]) {
        testWidgets('Files ${light ? 'light' : 'dark'} $scale empty=$empty', (
          tester,
        ) async {
          tester.view.physicalSize = const Size(1080, 2400);
          tester.view.devicePixelRatio = 2.625;
          tester.platformDispatcher.textScaleFactorTestValue = scale;
          addTearDown(tester.view.resetPhysicalSize);
          addTearDown(tester.view.resetDevicePixelRatio);
          addTearDown(tester.platformDispatcher.clearTextScaleFactorTestValue);
          final store = SeededProfileStore(
            prefs: await setupCapturePreferences(),
            seeded: [],
          );
          final controller = CaptureController(store)
            ..api = _Api(empty)
            ..repository = _Repository(empty)
            ..status = StreamStatus.connected;
          addTearDown(controller.dispose);
          final boundary = GlobalKey();
          await tester.pumpWidget(
            captureApp(
              home: Scaffold(
                appBar: AppBar(title: const Text('Files')),
                body: FilesScreen(controller: controller),
              ),
              boundaryKey: boundary,
              controller: controller,
              light: light,
            ),
          );
          await tester.pumpAndSettle();
          expect(tester.takeException(), isNull);
          final tone = light ? 'light' : 'dark';
          await writePng(
            'docs/qa/clear-files/$_captureSet/files-$tone-${scale.toInt()}x-${empty ? 'empty' : 'populated'}.png',
            await capturePng(tester, boundary),
          );
          if (!empty) {
            await tester.tap(find.text('src'));
            await tester.pumpAndSettle();
            expect(find.text('main.dart'), findsOneWidget);
            expect(tester.takeException(), isNull);
            await writePng(
              'docs/qa/clear-files/$_captureSet/files-$tone-${scale.toInt()}x-folder.png',
              await capturePng(tester, boundary),
            );
          }
          await tester.pumpWidget(const SizedBox.shrink());
          await tester.pump();
        });
      }
    }
  }
}
