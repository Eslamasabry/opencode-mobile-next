// slice-P10.3-P6.3, the P6.3 half (docs/qa/slice-P10.3-P6.3-2026-09-28/p63):
// a direct task given to the team on a phone host whose planner is off —
// the sheet while the host answers, then the team page once the sheet
// closed (waiting for a worker; a refused assignment) — at 412x915 and
// 1280x800 with the app's real fonts. The same file runs on the base
// commit for the "before" images (it drives only keys both share).
//
// Regenerate deliberately:
//   flutter test --update-goldens test/revamp/slice_p63_golden_test.dart
// and look at every changed image before committing it.

import 'dart:async';
import 'dart:io';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/orchestration_gateway.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/orchestration/adapters/fixture/fixture_gateway.dart';
import 'package:opencode_mobile/state/orchestration.dart';
import 'package:opencode_mobile/state/orchestration_store.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/screens/team/team_home_screen.dart';
import 'package:opencode_mobile/ui/screens/team_conversation/team_conversation.dart'
    show TeamConversationScreen;
import 'package:shared_preferences/shared_preferences.dart';

import '../../tool/capture/fixtures.dart' show captureTheme, loadCaptureFonts;

const _phone = Size(412, 915);
const _wide = Size(1280, 800);
final _clock = DateTime.utc(2026, 9, 28, 9, 30);

String _fixturePath() {
  var dir = Directory.current;
  for (var i = 0; i < 5; i++) {
    final candidate = Directory('${dir.path}/tool/qa/gascity_fixture');
    if (candidate.existsSync()) return candidate.path;
    dir = dir.parent;
  }
  throw StateError('tool/qa/gascity_fixture not found');
}

class _Gateway extends FixtureOrchestrationGateway {
  _Gateway()
    : super(
        fixturePath: _fixturePath(),
        hostMode: OrchestrationHostMode.phone,
        url: 'http://127.0.0.1:8472',
      );

  Completer<void>? holdAssign;
  bool refuseAssign = false;

  @override
  OrchestrationCapabilities get capabilities =>
      OrchestrationCapabilities.gascityLoopback;

  @override
  Future<List<OrchestrationProject>> projects() async => const [
    OrchestrationProject(id: 'ocproof', name: 'ocproof', rig: 'ocproof'),
  ];

  @override
  Future<List<OrchestrationRun>> runs({String? projectId}) async => const [];

  @override
  Future<List<OrchestrationAgent>> agents() async => const [
    OrchestrationAgent(
      id: 'gastown.mayor',
      name: 'gastown.mayor',
      state: AgentState.stopped,
      rawState: 'suspended',
      raw: {'name': 'gastown.mayor', 'suspended': true},
    ),
  ];

  @override
  Future<MutationReceipt> createWork({
    required String title,
    String? description,
    String? projectId,
    required String requestId,
  }) async => MutationReceipt(
    id: requestId,
    status: MutationReceiptStatus.accepted,
    upstreamStatus: 201,
    raw: {'id': 'oc-7fk', 'title': title},
  );

  @override
  Future<MutationReceipt> assign(
    String workId, {
    required String agentId,
    required String requestId,
  }) async {
    await holdAssign?.future;
    if (refuseAssign) {
      return MutationReceipt.rejected(
        requestId,
        'sling: pool ocproof/gastown.polecat is suspended',
      );
    }
    return MutationReceipt(
      id: requestId,
      status: MutationReceiptStatus.accepted,
      upstreamStatus: 202,
      correlationId: 'corr-$requestId',
    );
  }
}

enum _Shot {
  sheetSending('sheet_sending', _phone),
  homeWaiting('home_waiting', _phone),
  homeWaitingWide('home_waiting', _wide),
  homeRefused('home_assign_refused', _phone);

  const _Shot(this.state, this.size);
  final String state;
  final Size size;

  String get name {
    final sized = size == _phone
        ? ''
        : '_${size.width.toInt()}x${size.height.toInt()}';
    return 'slice_p63_$state${sized}_light';
  }
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(loadCaptureFonts);

  for (final shot in _Shot.values) {
    testWidgets(shot.name, (tester) async {
      tester.view.physicalSize = shot.size;
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      SharedPreferences.setMockInitialValues({});
      final gateway = _Gateway()
        ..refuseAssign = shot == _Shot.homeRefused
        ..holdAssign = shot == _Shot.sheetSending ? Completer<void>() : null;
      final config = OrchestrationConfig(
        provider: OrchestrationProvider.fixture,
        url: 'http://127.0.0.1:8472',
        city: 'phone',
        enabledAt: DateTime.utc(2026, 9, 27),
      );
      final team = OrchestrationController(
        profile: ServerProfile(
          id: 'p63-golden',
          name: 'This phone',
          baseUrl: 'http://127.0.0.1:4096',
          orchestration: config,
        ),
        config: config,
        store: OrchestrationStore(await SharedPreferences.getInstance()),
        probe: (_) async => ProbeFound(host: gateway.host!, city: 'phone'),
        gatewayFactory: (_, _) => gateway,
        now: () => _clock,
        mutationTimeout: const Duration(minutes: 10),
      );
      addTearDown(team.dispose);
      await team.start();
      final boundary = GlobalKey();
      debugDefaultTargetPlatformOverride = TargetPlatform.android; // ARCH-11
      try {
        await tester.pumpWidget(
          MaterialApp(
            debugShowCheckedModeBanner: false,
            theme: captureTheme(light: true),
            localizationsDelegates: AppLocalizations.localizationsDelegates,
            supportedLocales: AppLocalizations.supportedLocales,
            builder: (context, child) => MediaQuery(
              data: MediaQuery.of(context).copyWith(disableAnimations: true),
              child: RepaintBoundary(key: boundary, child: child),
            ),
            home: TeamHomeScreen(controller: team, now: () => _clock),
          ),
        );
        await tester.pumpAndSettle();
        await tester.tap(find.byKey(const ValueKey('team-home-start-run')));
        await tester.pumpAndSettle();
        await tester.enterText(
          find.byKey(const ValueKey('team-start-run-direct-title')),
          'Add a docstring to add() in calc.py',
        );
        await tester.pump();
        await tester.tap(
          find.byKey(const ValueKey('team-start-run-direct-send')),
        );
        if (shot == _Shot.sheetSending) {
          // Past the button's ease into its disabled look (KitMotion.standard),
          // while the send is still held so the spinner shows.
          for (var i = 0; i < 20; i++) {
            await tester.pump(const Duration(milliseconds: 16));
          }
        } else {
          await tester.pumpAndSettle();
          final conversation = find.byType(TeamConversationScreen);
          if (conversation.evaluate().isNotEmpty) {
            Navigator.of(tester.element(conversation)).pop();
            await tester.pumpAndSettle();
          }
        }
        await expectLater(
          find.byKey(boundary),
          matchesGoldenFile('goldens/${shot.name}.png'),
        );
      } finally {
        debugDefaultTargetPlatformOverride = null;
      }
      gateway.holdAssign?.complete();
      await tester.pumpWidget(const SizedBox());
      await tester.pump(const Duration(minutes: 11));
    });
  }
}
