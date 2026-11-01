import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/local_llm/local_llm_platform.dart';
import 'package:opencode_mobile/local_llm/local_llm_ui.dart';

import '../revamp/screen_voice_1_fixtures.dart';
import 'local_llm_fakes.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUp(rootBundle.clear);
  final en = lookupAppLocalizations(const Locale('en'));

  Future<void> open(WidgetTester tester, Rig rig) async {
    tester.view.physicalSize = voicePhone;
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    await tester.pumpWidget(
      voiceHost(home: LocalAssistantSettingsScreen(controller: rig.controller)),
    );
    await tester.pumpAndSettle();
  }

  testWidgets('a phone with too little memory is told why, with no download', (
    tester,
  ) async {
    final rig = await Rig.create(info: phone(totalMemoryMb: 3000));
    await open(tester, rig);
    expect(find.text(en.localAssistantUnavailableMemory), findsOneWidget);
    expect(find.byKey(const Key('local-assistant-download')), findsNothing);
    expect(find.byKey(const Key('local-assistant-switch')), findsNothing);
  });

  testWidgets('a capable phone sees the size before anything downloads', (
    tester,
  ) async {
    final rig = await Rig.create();
    await open(tester, rig);
    expect(find.text(en.localAssistantTitle), findsOneWidget);
    expect(find.textContaining('3.7 GB'), findsWidgets);
    expect(find.byKey(const Key('local-assistant-delete')), findsNothing);
    expect(rig.downloader.downloads, 0);
    await tester.tap(find.byKey(const Key('local-assistant-download')));
    await tester.pumpAndSettle();
    expect(rig.downloader.downloads, 1);
    expect(find.byKey(const Key('local-assistant-delete')), findsOneWidget);
  });

  testWidgets('off Wi-Fi the download waits for a choice', (tester) async {
    final rig = await Rig.create(network: mobile);
    await open(tester, rig);
    await tester.tap(find.byKey(const Key('local-assistant-download')));
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('local-assistant-needs-wifi')), findsOneWidget);
    expect(rig.downloader.downloads, 0);
    await tester.tap(find.byKey(const Key('local-assistant-mobile-data')));
    await tester.pumpAndSettle();
    expect(rig.downloader.downloads, 1);
  });

  testWidgets('delete asks first, then frees the model', (tester) async {
    final rig = await Rig.create(installed: true, enabled: true);
    await open(tester, rig);
    await tester.tap(find.byKey(const Key('local-assistant-delete')));
    await tester.pumpAndSettle();
    expect(rig.controller.installed, isTrue);
    await tester.tap(find.byKey(const Key('local-assistant-delete-confirm')));
    await tester.pumpAndSettle();
    expect(rig.controller.installed, isFalse);
  });

  testWidgets('the summary sheet streams, offers Stop, then Copy', (
    tester,
  ) async {
    final rig = await Rig.create(installed: true, enabled: true);
    tester.view.physicalSize = voicePhone;
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    late BuildContext hostContext;
    await tester.pumpWidget(
      voiceHost(
        home: Builder(
          builder: (context) {
            hostContext = context;
            return const SizedBox();
          },
        ),
      ),
    );
    unawaited(showLocalSummarySheet(hostContext, rig.controller, 'User: hi'));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 400));
    expect(find.byKey(const Key('local-summary-stop')), findsOneWidget);
    expect(find.text(en.localAssistantPreparing), findsOneWidget);

    rig.llm.emit(const LocalLlmToken('summary-1', 'They greeted'));
    await tester.pump();
    await tester.pump();
    expect(find.byKey(const Key('local-summary-text')), findsOneWidget);
    expect(
      find.textContaining('They greeted', findRichText: true),
      findsOneWidget,
    );
    expect(find.byKey(const Key('local-summary-stop')), findsOneWidget);

    rig.llm.emit(
      const LocalLlmDone(
        'summary-1',
        firstTokenMs: 800,
        totalMs: 1800,
        chunks: 2,
      ),
    );
    await tester.pump();
    await tester.pump();
    expect(find.byKey(const Key('local-summary-stop')), findsNothing);
    expect(find.byKey(const Key('local-summary-copy')), findsOneWidget);
    expect(find.textContaining('First words after 0.8 s'), findsOneWidget);
  });

  testWidgets('Stop in the sheet cancels on the device', (tester) async {
    final rig = await Rig.create(installed: true, enabled: true);
    tester.view.physicalSize = voicePhone;
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    late BuildContext hostContext;
    await tester.pumpWidget(
      voiceHost(
        home: Builder(
          builder: (context) {
            hostContext = context;
            return const SizedBox();
          },
        ),
      ),
    );
    unawaited(showLocalSummarySheet(hostContext, rig.controller, 'User: hi'));
    await tester.pump(const Duration(milliseconds: 400));
    rig.llm.emit(const LocalLlmToken('summary-1', 'Half'));
    await tester.pump();
    await tester.pump();
    await tester.tap(find.byKey(const Key('local-summary-stop')));
    await tester.pump();
    expect(rig.llm.calls, contains('cancel'));
    expect(find.text(en.localAssistantStopped), findsOneWidget);
    expect(find.byKey(const Key('local-summary-copy')), findsOneWidget);
  });
}
