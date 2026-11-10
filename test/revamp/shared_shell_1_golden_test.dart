// Golden renders of shared-shell-1's pages (wave 2a): the Disconnect
// confirmation, the three local agent sheets and the shell's connection
// line, rebuilt from kit parts. Phone 412x915 and one wide window
// (1280x800), dark and light (owner decision 2026-09-27: no Arabic), with
// the app's real fonts at DPR 1.
//
// Regenerate deliberately:
//   flutter test --update-goldens test/revamp/shared_shell_1_golden_test.dart
// and look at every changed image before committing it.
import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/connection_status.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/widgets/connection_status_banner.dart';
import 'package:opencode_mobile/ui/widgets/safety_confirms.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../../tool/capture/fixtures.dart' show captureTheme, loadCaptureFonts;

class _Store extends ProfileStore {
  _Store({required super.prefs});

  final _profile = ServerProfile(
    id: 'studio',
    name: 'Studio box',
    baseUrl: 'http://localhost:4096',
  );

  @override
  List<ServerProfile> get profiles => [_profile];

  @override
  String? get activeId => _profile.id;
}

class _Controller extends ConnectionController {
  _Controller(super.store) : super(isIsolated: true);

  int queued = 0;
  int drafts = 0;

  /// The controller-owned connection truth the status line presents
  /// (3d64653c); an isolated controller's own is always hidden, so the
  /// connection-line shots set the phase they show.
  ConnectionStatusSnapshot? shownStatus;

  @override
  ConnectionStatusSnapshot get connectionStatus =>
      shownStatus ?? super.connectionStatus;

  @override
  int queuedPromptCountForProfile(String profileID) => queued;

  @override
  int draftCountForProfile(String profileID) => drafts;
}

Future<_Controller> _controller() async {
  SharedPreferences.setMockInitialValues({});
  return _Controller(_Store(prefs: await SharedPreferences.getInstance()));
}

/// The server stopped answering: what the connection line exists to say.
const _notAnswering = ConnectionStatusSnapshot(
  phase: ConnectionStatusPhase.notAnswering,
  profileId: 'studio',
  serverName: 'Studio box',
);

const _phone = Size(412, 915);
const _wide = Size(1280, 800);

String _name(String shot, Size size, bool light) => [
  shot,
  if (size != _phone) '${size.width.toInt()}x${size.height.toInt()}',
  light ? 'light' : 'dark',
].join('_');

/// Pumps [body] (the screen's content) or runs [open] against a context
/// under the navigator, settles, and compares the whole window.
Future<void> _shot(
  WidgetTester tester,
  String shot, {
  required bool light,
  Size size = _phone,
  Widget body = const SizedBox.expand(),
  FutureOr<void> Function(BuildContext context)? open,
  String? shows,
}) async {
  tester.view.physicalSize = size;
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  debugDefaultTargetPlatformOverride = TargetPlatform.android;
  final boundary = GlobalKey();
  try {
    late BuildContext context;
    await tester.pumpWidget(
      RepaintBoundary(
        key: boundary,
        child: MaterialApp(
          debugShowCheckedModeBanner: false,
          theme: captureTheme(light: light),
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          builder: (context, child) => MediaQuery(
            data: MediaQuery.of(context).copyWith(disableAnimations: true),
            child: child!,
          ),
          home: Scaffold(
            body: SafeArea(
              child: Builder(
                builder: (inner) {
                  context = inner;
                  return body;
                },
              ),
            ),
          ),
        ),
      ),
    );
    if (open != null) unawaited(Future.sync(() => open(context)));
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);
    // What the shot is about must be on screen, not an empty page.
    if (shows != null) expect(find.text(shows), findsOneWidget);
    await expectLater(
      find.byKey(boundary),
      matchesGoldenFile('goldens/${_name(shot, size, light)}.png'),
    );
  } finally {
    await tester.pumpWidget(const SizedBox.shrink());
    debugDefaultTargetPlatformOverride = null;
  }
}

void main() {
  setUpAll(loadCaptureFonts);

  for (final light in [false, true]) {
    final theme = light ? 'light' : 'dark';

    for (final size in [_phone, _wide]) {
      testWidgets('disconnect with messages waiting ($theme, $size)', (
        tester,
      ) async {
        final controller = await _controller()
          ..queued = 2
          ..drafts = 1;
        addTearDown(controller.dispose);
        await _shot(
          tester,
          'servers_settings_disconnect_sheet_waiting',
          light: light,
          size: size,
          open: (context) => confirmDisconnectServer(context, controller),
        );
      });
    }

    for (final size in [_phone, _wide]) {
      testWidgets('connection line lost ($theme, $size)', (tester) async {
        final controller = await _controller()
          ..shownStatus = _notAnswering;
        addTearDown(controller.dispose);
        await _shot(
          tester,
          'shell_embedded_connection_status_banner_lost',
          light: light,
          size: size,
          shows: "Studio box isn't answering",
          body: Column(
            children: [ConnectionStatusBanner(controller: controller)],
          ),
        );
      });
    }

    testWidgets('connection line, phone server ($theme)', (tester) async {
      final controller = await _controller()
        ..shownStatus = _notAnswering;
      addTearDown(controller.dispose);
      await _shot(
        tester,
        'shell_embedded_connection_status_banner_phone_stopped',
        light: light,
        shows: "OpenCode on this phone isn't answering",
        body: Column(
          children: [
            ConnectionStatusBanner(
              controller: controller,
              serverOnThisPhone: true,
              onRestartServer: () async {},
            ),
          ],
        ),
      );
    });
  }
}
