// Coverage ratchet for the saved server record: what the Servers list, a
// row's Details and the editor tell a person about a server they saved
// (see paseo_coverage_support.dart for the rules). Each case is the record
// the way it is stored; the app parses it with its own ServerProfile.fromJson.
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/state/profiles.dart';

import '../../tool/capture/fixtures.dart';
import '../goldens/kit/kit_gallery.dart' show loadKitGalleryFonts;
import '../support/server_editor.dart';
import 'paseo_coverage_support.dart';
import 'servers_support.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(() async {
    await loadKitGalleryFonts();
    await loadCaptureFonts();
  });
  final family = CoverageFamily('servers_profile', prefix: '');
  registerLedgerTests(family);

  for (final variant in family.cases) {
    final id = variant['id'] as String;
    testWidgets('saved server · $id', (tester) async {
      tester.view.physicalSize = const Size(412, 915);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      mockNoTermux();
      final record = Map<String, dynamic>.from(variant['payload'] as Map);
      final profile = ServerProfile.fromJson(record);
      final (store, controller) = await serversState(profiles: [profile]);
      addTearDown(controller.dispose);
      final boundary = GlobalKey();
      await tester.pumpWidget(serversApp(boundary, store, controller));
      await tester.pumpAndSettle();
      final seen = <String>[];
      String read() => screenText(tester).join('\n');
      seen.add(read());
      await writeCasePng(tester, boundary, 'srvprofile_$id');

      Future<void> openMenuItem(String label) async {
        await tester.longPress(
          find.byKey(ValueKey('server-row-${profile.id}')),
        );
        await frames(tester, 4);
        await tester.tap(find.text(label).last);
        await frames(tester, 8);
      }

      // Details: the address, the project folder, the version.
      await openMenuItem('Details');
      seen.add(read());
      await writeCasePng(tester, boundary, 'srvprofile_${id}_details');
      await tester.tapAt(const Offset(10, 10));
      await frames(tester, 6);

      // Edit: the name, the address, the folder, the username.
      await openMenuItem('Edit');
      if (find
          .byKey(const ValueKey('server-editor-more-options-header'))
          .evaluate()
          .isNotEmpty) {
        await openServerMoreOptions(tester);
      }
      seen.add(read());
      await writeCasePng(tester, boundary, 'srvprofile_${id}_edit');

      final problems = checkCase(
        family,
        variant,
        seen.join('\n'),
        primaryText: seen.join('\n'),
      );
      // An older build saved "Paseo daemon 0.8.0 (experimental)": the list
      // says the number, never the label.
      if (seen.join('\n').contains('experimental')) {
        problems.add('the version still carries an internal label');
      }
      expect(
        problems,
        isEmpty,
        reason: 'screen text:\n${flat(seen.join('\n'))}',
      );
    });
  }
}
