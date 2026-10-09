// Coverage ratchet for what this phone's Termux tells the app: the scanner's
// process list, the result of stopping processes, and the storage scan, as
// the scripts print them (JSON over the oc/termux channel) and drawn by the
// real "Running on this phone" and "Storage on this phone" pages (see
// paseo_coverage_support.dart for the rules).
import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/screens/termux_processes_screen.dart';
import 'package:opencode_mobile/ui/screens/termux_storage_screen.dart';

import '../../tool/capture/fixtures.dart';
import '../goldens/kit/kit_gallery.dart' show loadKitGalleryFonts;
import 'paseo_coverage_support.dart';
import 'servers_support.dart' show frames;

/// The `oc/termux` channel as a phone whose tools answer by verb.
class Phone {
  String listing = '[]';
  String stopResult = '{"stopped":[],"killed":[],"remaining":[],"refused":[]}';
  String state = 'idle';
  String report = '';
  final stops = <String>[];

  Map<String, Object> _result(String stdout) => {
    'stdout': stdout,
    'stderr': '',
    'exitCode': 0,
    'err': -1,
    'errorMessage': '',
  };

  Future<Object?> handle(MethodCall call) async {
    if (call.method != 'runInTermux') return true;
    final script = (call.arguments as Map)['script'] as String;
    final verb = RegExp(
      r'''exec "\$TOOLS" ([a-z-]+)(?: '([^']*)')?\n$''',
    ).firstMatch(script);
    switch (verb?.group(1)) {
      case 'procs-scan':
        return _result('$listing\n');
      case 'procs-stop':
        stops.add(verb!.group(2)!);
        return _result('$stopResult\n');
      case 'storage-status':
        return _result(
          'state=$state\n__OC_TOOLS_LOG__\n\n__OC_TOOLS_JSON__\n$report\n',
        );
    }
    return _result('');
  }
}

Future<GlobalKey> mount(WidgetTester tester, Phone phone, Widget home) async {
  tester.view.physicalSize = const Size(412, 2400);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  const channel = MethodChannel('oc/termux');
  TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
      .setMockMethodCallHandler(channel, phone.handle);
  addTearDown(
    () => TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, null),
  );
  final boundary = GlobalKey();
  await tester.pumpWidget(
    RepaintBoundary(
      key: boundary,
      child: MaterialApp(
        theme: captureTheme(),
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: home,
      ),
    ),
  );
  await frames(tester, 20);
  return boundary;
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(() async {
    await loadKitGalleryFonts();
    await loadCaptureFonts();
  });
  final family = CoverageFamily('terminal_termux', prefix: '');
  registerLedgerTests(family);

  for (final variant in family.cases) {
    final id = variant['id'] as String;
    testWidgets('termux · $id', (tester) async {
      final phone = Phone();
      final kind = variant['kind'] as String;
      final seen = <String>[];
      late GlobalKey boundary;
      if (kind == 'storage') {
        final payload = Map<String, dynamic>.from(variant['payload'] as Map);
        phone.state = payload['state'] as String;
        if (payload['report'] != null) {
          phone.report = jsonEncode(payload['report']);
        }
        boundary = await mount(
          tester,
          phone,
          TermuxStorageScreen(
            now: () =>
                DateTime.fromMillisecondsSinceEpoch(1788950000 * 1000 + 120000),
            pollInterval: const Duration(milliseconds: 50),
          ),
        );
      } else {
        phone.listing = jsonEncode(
          kind == 'processes'
              ? variant['payload']
              : [
                  {
                    'pid': 5300,
                    'ppid': 1,
                    'group': 'build_daemons',
                    'name': 'java',
                    'cmd': 'java GradleDaemon 8.5',
                    'cpu_pct': 0.5,
                    'cpu_seconds': 90,
                    'rss_kb': 300032,
                    'elapsed_s': 4000,
                    'cwd': '/work/shop',
                    'orphan_reason': null,
                    'protected': false,
                  },
                ],
        );
        if (kind == 'stop') phone.stopResult = jsonEncode(variant['payload']);
        boundary = await mount(
          tester,
          phone,
          const TermuxProcessesScreen(
            refreshInterval: Duration(seconds: 60),
            sampleDelay: Duration(milliseconds: 50),
          ),
        );
      }
      seen.add(screenText(tester).join('\n'));
      await writeCasePng(tester, boundary, 'term_$id');
      // Open what a row opens (a process's details, a category's paths).
      final opens = switch (kind) {
        'processes' => [
          // A protected row sends the person to This phone; it has no sheet.
          for (final p in (variant['payload'] as List).cast<Map>())
            if (p['protected'] != true) p['name'] as String,
        ],
        'storage' => const ['Build caches'],
        _ => const <String>[],
      };
      for (final name in opens) {
        final target = find.text(name);
        if (target.evaluate().isEmpty) continue;
        await tester.ensureVisible(target.first);
        await tester.tap(target.first, warnIfMissed: false);
        await frames(tester, 12);
        seen.add(screenText(tester).join('\n'));
        final fold = find.text('Details');
        if (kind == 'processes' && fold.evaluate().isNotEmpty) {
          await tester.tap(fold.last, warnIfMissed: false);
          await frames(tester, 8);
          seen.add(screenText(tester).join('\n'));
        }
        if (name == opens.first) {
          await writeCasePng(tester, boundary, 'term_${id}_opened');
        }
        final navigator = tester.state<NavigatorState>(
          find.byType(Navigator).first,
        );
        await navigator.maybePop();
        await frames(tester, 8);
      }
      if (kind == 'stop') {
        // Stop the Gradle daemon from its details, after asking.
        await tester.tap(find.byKey(const Key('termux-proc-5300')));
        await frames(tester, 12);
        await tester.tap(find.byKey(const Key('termux-procs-details-stop')));
        await frames(tester, 12);
        seen.add(screenText(tester).join('\n'));
        await tester.tap(find.byKey(const Key('termux-procs-confirm-stop')));
        await frames(tester, 16);
        seen.add(screenText(tester).join('\n'));
        await writeCasePng(tester, boundary, 'term_${id}_after');
      }
      final screen = seen.join('\n');
      final problems = checkCase(family, variant, screen, primaryText: screen);
      await tester.pumpWidget(const SizedBox.shrink());
      await tester.pump(const Duration(seconds: 1));
      expect(problems, isEmpty, reason: 'screen text:\n${flat(screen)}');
    });
  }
}
