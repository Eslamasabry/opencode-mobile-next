// Settings > Help > Send a problem report: a redacted preview, Send, the
// report code; and the Report code row in About. Nothing leaves before Send.
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/platform/app_exit.dart';
import 'package:opencode_mobile/telemetry/app_log_ring.dart';
import 'package:opencode_mobile/telemetry/problem_report_collector.dart';
import 'package:opencode_mobile/telemetry/telemetry.dart';
import 'package:opencode_mobile/telemetry/telemetry_provider.dart';
import 'package:opencode_mobile/ui/screens/about_screen.dart' show AboutScreen;
import 'package:opencode_mobile/ui/screens/send_problem_report.dart';
import 'package:package_info_plus/package_info_plus.dart';

import 'support/telemetry_recorder.dart';

const _secret = 'sk-ant-api03-SHEETREDACTKEYabcdefghijklmnop';

Widget _app(Telemetry telemetry, Widget home) => ProviderScope(
  overrides: [telemetryProvider.overrideWithValue(telemetry)],
  child: MaterialApp(
    localizationsDelegates: AppLocalizations.localizationsDelegates,
    supportedLocales: AppLocalizations.supportedLocales,
    home: home,
  ),
);

/// Lets the package info (a real platform future) resolve, then settles.
Future<void> _settle(WidgetTester tester) async {
  await tester.pumpAndSettle();
  for (var i = 0; i < 20; i++) {
    await tester.runAsync(
      () => Future<void>.delayed(const Duration(milliseconds: 50)),
    );
    await tester.pumpAndSettle();
    if (find.byKey(const ValueKey('about-version')).evaluate().isNotEmpty) {
      break;
    }
  }
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() {
    // The exit history channel answers nothing, as on a phone before Android 11.
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(const MethodChannel('oc/none'), (_) async {
          return null;
        });
    PackageInfo.setMockInitialValues(
      appName: 'OpenCode Mobile',
      packageName: 'io.github.eslamasabry.opencode_mobile',
      version: '2.1.5',
      buildNumber: '63',
      buildSignature: '',
    );
    rootBundle
      ..evict('PRIVACY.md')
      ..evict('THIRD_PARTY_NOTICES.md');
  });

  testWidgets('the preview is redacted; Send uploads exactly it and shows '
      'the report code', (tester) async {
    tester.view.physicalSize = const Size(412, 915) * 3;
    tester.view.devicePixelRatio = 3;
    addTearDown(tester.view.reset);
    final adapter = RecordingAdapter();
    // Both switches off: the report is asked every time.
    final service = await startedService(adapter);
    final ring = AppLogRing()
      ..add('opened https://my-box.tail1234.ts.net:4096/session/1')
      ..add('config $_secret')
      ..add('wrote /data/user/0/io.github/files/home/secret-project/a.txt');
    final collector = ProblemReportCollector(
      telemetry: service,
      ring: ring,
      lifecycle: AppLifecycleBridge(channel: const MethodChannel('oc/none')),
      now: () => DateTime.utc(2026, 11, 8, 12),
    );
    await tester.pumpWidget(
      _app(
        service,
        Builder(
          builder: (context) => TextButton(
            onPressed: () => openSendProblemReport(
              context,
              telemetry: service,
              collector: collector,
            ),
            child: const Text('open'),
          ),
        ),
      ),
    );
    await tester.tap(find.text('open'));
    await tester.pumpAndSettle();

    expect(find.textContaining('opened [url]'), findsOneWidget);
    expect(find.textContaining('SHEETREDACT'), findsNothing);
    expect(find.textContaining('my-box'), findsNothing);
    expect(find.textContaining('secret-project'), findsNothing);
    final shown = await collector.build();
    expect(find.text('Send a problem report'), findsOneWidget);
    // Nothing has left the phone yet.
    expect(adapter.calls, isEmpty);

    await tester.tap(find.byKey(const ValueKey('send-problem-report-send')));
    await tester.pumpAndSettle();
    expect(adapter.reports, hasLength(1));
    expect(adapter.reports.single.text, shown);
    expect(adapter.reports.single.collecting, isFalse);
    expect(find.text('Report sent'), findsOneWidget);
    expect(find.text('abcdef01-1'), findsOneWidget);
    expect(
      find.byKey(const ValueKey('send-problem-report-copy-code')),
      findsOneWidget,
    );
  });

  testWidgets('closing the preview sends nothing', (tester) async {
    tester.view.physicalSize = const Size(412, 915) * 3;
    tester.view.devicePixelRatio = 3;
    addTearDown(tester.view.reset);
    final adapter = RecordingAdapter();
    final service = await startedService(adapter);
    await tester.pumpWidget(
      _app(
        service,
        Builder(
          builder: (context) => TextButton(
            onPressed: () => openSendProblemReport(
              context,
              telemetry: service,
              collector: ProblemReportCollector(
                telemetry: service,
                ring: AppLogRing()..add('a line'),
                lifecycle: AppLifecycleBridge(
                  channel: const MethodChannel('oc/none'),
                ),
              ),
            ),
            child: const Text('open'),
          ),
        ),
      ),
    );
    await tester.tap(find.text('open'));
    await tester.pumpAndSettle();
    expect(
      find.byKey(const ValueKey('send-problem-report-text')),
      findsOneWidget,
    );
    await tester.tapAt(const Offset(10, 10));
    await tester.pumpAndSettle();
    expect(
      find.byKey(const ValueKey('send-problem-report-text')),
      findsNothing,
    );
    expect(adapter.reports, isEmpty);
    expect(adapter.calls, isEmpty);
  });

  testWidgets('a failed send says so in plain words and can be tried again', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(412, 915) * 3;
    tester.view.devicePixelRatio = 3;
    addTearDown(tester.view.reset);
    final service = _Failing();
    await service.start();
    await tester.pumpWidget(
      _app(
        service,
        Builder(
          builder: (context) => TextButton(
            onPressed: () => openSendProblemReport(
              context,
              telemetry: service,
              collector: ProblemReportCollector(
                telemetry: service,
                ring: AppLogRing()..add('a line'),
                lifecycle: AppLifecycleBridge(
                  channel: const MethodChannel('oc/none'),
                ),
              ),
            ),
            child: const Text('open'),
          ),
        ),
      ),
    );
    await tester.tap(find.text('open'));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('send-problem-report-send')));
    await tester.pumpAndSettle();
    expect(
      find.byKey(const ValueKey('send-problem-report-failed')),
      findsOneWidget,
    );
    expect(find.textContaining('could not be sent'), findsOneWidget);
    expect(
      find.byKey(const ValueKey('send-problem-report-send')),
      findsOneWidget,
    );
  });

  testWidgets('About shows the report code with Copy; a build without the '
      'vendor shows no row', (tester) async {
    final adapter = RecordingAdapter();
    final service = await startedService(adapter);
    await tester.pumpWidget(_app(service, const AboutScreen()));
    await _settle(tester);
    expect(find.byKey(const ValueKey('about-report-code')), findsOneWidget);
    expect(find.text('Report code'), findsOneWidget);
    expect(find.text('abcdef01-0'), findsOneWidget);
    expect(
      find.byKey(const ValueKey('about-copy-report-code')),
      findsOneWidget,
    );

    final none = await startedService(RecordingAdapter(present: false));
    await tester.pumpWidget(_app(none, const AboutScreen(key: ValueKey('b'))));
    await _settle(tester);
    expect(find.byKey(const ValueKey('about-report-code')), findsNothing);
  });
}

class _Failing extends TelemetryService {
  _Failing() : super(adapter: RecordingAdapter());
  @override
  Future<String?> sendProblemReport(String redactedText) async => null;
}
