import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/builtin/project_export.dart';
import 'package:opencode_mobile/builtin/project_export_controller.dart';

class _Platform extends Fake implements ProjectExportPlatform {
  int exports = 0;
  int picks = 0;
  Completer<String?>? picker;
  @override
  Future<String?> filesDir() async => '/fixture';
  @override
  Future<int> savedServers() async => 0;
  @override
  Future<int> cacheBytes() async => 0;
  @override
  Future<void> cancel() async {}
  @override
  Future<String?> pickDestination(String name) async {
    picks++;
    return picker == null ? 'content://fixture/backup' : picker!.future;
  }

  @override
  Future<ProjectExportResult> export({
    required String destination,
    required String planPath,
    required void Function(int bytesDone) onProgress,
  }) async {
    exports++;
    return const ProjectExportResult.done(bytes: 0, files: 0);
  }
}

void main() {
  for (final disposing in [false, true]) {
    test(
      '${disposing ? 'dispose' : 'cancel'} during planning prevents export',
      () async {
        final platform = _Platform();
        final planned = Completer<List<ProjectExportEntry>>();
        final planning = Completer<void>();
        final controller = ProjectExportController(
          platform: platform,
          scan: (_) async => AppStorageFacts.empty,
          plan: (_, _) {
            planning.complete();
            return planned.future;
          },
          writePlan: (_) async => '/nonexistent/export.plan',
        );
        await controller.load();
        final operation = controller.export();
        await planning.future;
        if (disposing) {
          controller.dispose();
        } else {
          await controller.cancel();
        }
        planned.complete([]);
        await operation;
        expect(platform.exports, 0);
        if (!disposing) controller.dispose();
      },
    );
  }
  test(
    'picker reserves one export and disposal stops late picker results',
    () async {
      final platform = _Platform()..picker = Completer<String?>();
      final controller = ProjectExportController(
        platform: platform,
        scan: (_) async => AppStorageFacts.empty,
        plan: (_, _) async => [],
        writePlan: (_) async => '/nonexistent/export.plan',
      );
      await controller.load();
      final first = controller.export();
      final second = controller.export();
      controller.dispose();
      platform.picker!.complete('content://fixture/backup');
      await Future.wait([first, second]);
      expect(platform.picks, 1);
      expect(platform.exports, 0);
    },
  );
}
