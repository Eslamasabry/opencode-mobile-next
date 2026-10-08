import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';
import 'package:opencode_mobile/diagnostics/app_diagnostics.dart';
import 'package:opencode_mobile/diagnostics/perf_trace.dart';
import 'package:opencode_mobile/main.dart' as app;
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/kit/kit_motion.dart';
import 'package:path_provider/path_provider.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'bd9_device_smoke_fixture.dart';
import 'bd9_conversation_finder.dart';
import 'bd9_smoke_reporting.dart';

/// Only fixture profiles are exposed. No secure-storage method is called.
class _SmokeStore extends ProfileStore {
  _SmokeStore({required super.prefs, required this.fixture});
  ServerProfile fixture;

  @override
  List<ServerProfile> get profiles => [fixture];
  @override
  ServerProfile? get active => activeId == fixture.id ? fixture : null;
  @override
  Future<List<ServerProfile>> load() async => profiles;
  @override
  Future<void> upsert(ServerProfile profile) async {
    if (profile.id != fixture.id) throw StateError('fixture_profile_required');
    fixture = profile;
  }
}

void main() {
  final binding = IntegrationTestWidgetsFlutterBinding.ensureInitialized();
  var phase = Bd9SmokePhase.initializing;
  Bd9SmokePhase? failedPhase;
  final integrationReporter = reportTestException;
  reportTestException = (details, description) => integrationReporter(
    Bd9SmokeFailureDetails.from(details, failedPhase ?? phase),
    description,
  );
  PerfTrace.logSink = null;
  KitMotion.loops = false;

  testWidgets('BD9 release launch to local server conversation list', (
    tester,
  ) async {
    phase = Bd9SmokePhase.fixture;
    final server = await Bd9DeviceSmokeFixture.start();
    AppDiagnosticsController? diagnostics;
    try {
      phase = Bd9SmokePhase.preferences;
      final profile = ServerProfile(
        id: 'bd9-release-smoke',
        name: 'BD9 local fixture',
        baseUrl: server.baseUrl,
      );
      // The SDK mock preference store is in memory; existing Android data stays
      // untouched. The profile store above never reads the real Keystore.
      SharedPreferences.setMockInitialValues({
        'oc.activeProfile': profile.id,
        'oc.locale': 'en',
      });
      final store = _SmokeStore(
        prefs: await SharedPreferences.getInstance(),
        fixture: profile,
      );
      final activeDiagnostics = AppDiagnosticsController();
      diagnostics = activeDiagnostics;
      phase = Bd9SmokePhase.bootstrap;
      await tester.pumpWidget(
        app.AppBootstrapGate(
          diagnostics: activeDiagnostics,
          loader: () async => AppBootstrap(store),
          controllerFactory: (store, diagnostics) => ConnectionController(
            store,
            diagnostics: diagnostics,
            // The fixture is an HTTP server, not a Termux-hosted runtime.
            localWakeLockEnsurer: () async {},
          ),
        ),
      );
      phase = Bd9SmokePhase.conversation;
      final deadline = DateTime.now().add(const Duration(seconds: 45));
      final conversation = bd9ConversationFinder();
      while (conversation.evaluate().isEmpty &&
          DateTime.now().isBefore(deadline)) {
        await tester.pump(const Duration(milliseconds: 150));
      }
      expect(
        conversation,
        findsOneWidget,
        reason: 'fixture_conversation_not_visible',
      );
      await bd9RefreshConversations(tester);
      while ((!server.reads.contains('/global/health') ||
              !server.reads.contains('/experimental/session')) &&
          DateTime.now().isBefore(deadline)) {
        await tester.pump(const Duration(milliseconds: 150));
      }
      expect(server.reads, contains('/global/health'));
      expect(server.reads, contains('/experimental/session'));
      expect(
        server.refusedWrites,
        isEmpty,
        reason: 'fixture_must_remain_read_only',
      );

      // This image contains only the synthetic fixture. The native runner
      // converts it to a small JPG for the host-side proof artifact.
      phase = Bd9SmokePhase.screenshot;
      await binding.convertFlutterSurfaceToImage();
      await tester.pump();
      final image = await binding.takeScreenshot('bd9-conversations');
      final output = await getExternalStorageDirectory();
      expect(output, isNotNull, reason: 'screenshot_directory_unavailable');
      await File(
        '${output!.path}/bd9-conversations.png',
      ).writeAsBytes(image, flush: true);
    } catch (_) {
      failedPhase ??= phase;
      rethrow;
    } finally {
      phase = Bd9SmokePhase.cleanup;
      await tester.pumpWidget(const SizedBox.shrink());
      await tester.pump();
      diagnostics?.dispose();
      await server.close();
    }
    phase = Bd9SmokePhase.complete;
  }, timeout: const Timeout(Duration(seconds: 90)));
}
