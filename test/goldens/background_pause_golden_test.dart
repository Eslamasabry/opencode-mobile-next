// Golden renders of the "background connection paused" notice (FD3) in the
// app status line, as main.dart hosts it above every page: each reason,
// resuming and a resume Android did not confirm, at 412x915, light and dark,
// with the app's real fonts. Evidence copies: docs/qa/battery-pause-2026-10-08/.
//
// Regenerate deliberately:
//   flutter test --update-goldens test/goldens/background_pause_golden_test.dart
// and look at every changed image before committing it.

import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/builtin/app_exit_recovery.dart';
import 'package:opencode_mobile/domain/app_diagnostics_gateway.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/platform/app_exit.dart';
import 'package:opencode_mobile/state/background_pause_notice.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/widgets/app_connection_status.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'package:opencode_mobile/ui/app_theme.dart';

import '../../tool/capture/fixtures.dart' show captureTheme;
import '../support/fake_pause_gateway.dart';
import 'kit/kit_gallery.dart' show loadKitGalleryFonts;

class _Store extends ProfileStore {
  _Store({required super.prefs});

  @override
  List<ServerProfile> get profiles => const [];
}

/// Today at 3:10 PM: the line then reads "at 3:10 PM" whenever this runs.
DateTime _today1510() {
  final now = DateTime.now();
  return DateTime(now.year, now.month, now.day, 15, 10);
}

/// The capture theme as main.dart adapts it for [locale], with Arabic
/// falling back to Noto Sans Arabic as on a device (kit gallery, TEST-8).
ThemeData _theme({required bool light, required Locale locale}) {
  final theme = AppTheme.forLocale(captureTheme(light: light), locale);
  if (locale.languageCode != 'ar') return theme;
  const fallback = ['Noto Sans Arabic'];
  final text = theme.textTheme.apply(fontFamilyFallback: fallback);
  final button = theme.textButtonTheme.style;
  final buttonText = button?.textStyle;
  return theme.copyWith(
    extensions: [
      ...theme.extensions.values,
      KitTokens.fromRoles(ThemeRoles.resolve(theme), text),
    ],
    textTheme: text,
    textButtonTheme: button == null || buttonText == null
        ? theme.textButtonTheme
        : TextButtonThemeData(
            style: button.copyWith(
              textStyle: WidgetStateProperty.resolveWith(
                (states) => buttonText
                    .resolve(states)
                    ?.copyWith(fontFamilyFallback: fallback),
              ),
            ),
          ),
  );
}

const _page = KitScreen(
  topBar: KitTopBar(title: 'Chats'),
  body: _Rows(),
);

class _Rows extends StatelessWidget {
  const _Rows();

  @override
  Widget build(BuildContext context) => ListView(
    children: const [
      KitRow(
        title: 'Fix the login redirect',
        supporting: TextSpan(text: 'Laptop · 2 min ago'),
      ),
      KitRow(
        title: 'Write release notes',
        supporting: TextSpan(text: 'Laptop · 1 h ago'),
      ),
      KitRow(
        title: 'Phone setup checks',
        supporting: TextSpan(text: 'This phone · today'),
      ),
    ],
  );
}

Future<void> _golden(
  WidgetTester tester,
  String name, {
  required bool light,
  required FakePauseGateway gateway,
  Locale locale = const Locale('en'),
  Future<void> Function(BackgroundPauseNotice notice)? before,
}) async {
  tester.view.physicalSize = const Size(412, 915);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  SharedPreferences.setMockInitialValues({});
  final prefs = await SharedPreferences.getInstance();
  final notice = BackgroundPauseNotice(
    gateway: gateway,
    preferences: prefs,
    refreshOnForeground: false,
  );
  final controller = ConnectionController(_Store(prefs: prefs));
  final recovery = AppExitRecovery(bridge: AppLifecycleBridge());
  final boundary = GlobalKey();
  final navigator = GlobalKey<NavigatorState>();
  try {
    await tester.pumpWidget(
      RepaintBoundary(
        key: boundary,
        child: ProviderScope(
          overrides: [
            connProvider.overrideWithValue(controller),
            appExitRecoveryProvider.overrideWithValue(recovery),
            backgroundPauseNoticeProvider.overrideWithValue(notice),
          ],
          child: MaterialApp(
            navigatorKey: navigator,
            debugShowCheckedModeBanner: false,
            theme: _theme(light: light, locale: locale),
            locale: locale,
            localizationsDelegates: AppLocalizations.localizationsDelegates,
            supportedLocales: AppLocalizations.supportedLocales,
            builder: (context, child) => AppConnectionStatusScope(
              controller: controller,
              navigatorKey: navigator,
              child: child!,
            ),
            home: _page,
          ),
        ),
      ),
    );
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 500));
    if (before != null) {
      await before(notice);
      await tester.pump();
      await tester.pump(KitMotion.standard);
      await tester.pump(KitMotion.standard);
    }
    expect(tester.takeException(), isNull);
    await expectLater(
      find.byKey(boundary),
      matchesGoldenFile(
        'background_pause_${name}_${light ? 'light' : 'dark'}.png',
      ),
    );
  } finally {
    await tester.pumpWidget(const SizedBox.shrink());
    notice.dispose();
    controller.dispose();
    recovery.dispose();
    await tester.pump();
  }
}

void main() {
  setUpAll(loadKitGalleryFonts);

  for (final light in [true, false]) {
    final theme = light ? 'light' : 'dark';
    for (final (name, reason) in [
      ('time_limit', BackgroundPauseReason.timeLimit),
      ('restricted', BackgroundPauseReason.batteryRestricted),
      ('user_stopped', BackgroundPauseReason.userStopped),
      ('interrupted', BackgroundPauseReason.interrupted),
    ]) {
      testWidgets('$name, $theme', (tester) async {
        await _golden(
          tester,
          name,
          light: light,
          gateway: FakePauseGateway(pausedFor(reason, at: _today1510())),
        );
      });
    }

    testWidgets('resuming, $theme', (tester) async {
      final gateway = FakePauseGateway(
        pausedFor(BackgroundPauseReason.timeLimit, at: _today1510()),
      )..gate = Completer();
      await _golden(
        tester,
        'resuming',
        light: light,
        gateway: gateway,
        before: (notice) async => unawaited(notice.resume()),
      );
      gateway.gate!.complete(
        BackgroundResumeResult(
          gateway.pause,
          error: DiagnosticsError.resumeFailed,
        ),
      );
    });

    testWidgets('resume failed, $theme', (tester) async {
      final paused = pausedFor(
        BackgroundPauseReason.timeLimit,
        at: _today1510(),
      );
      await _golden(
        tester,
        'resume_failed',
        light: light,
        gateway: FakePauseGateway(paused)
          ..answer = () => BackgroundResumeResult(
            paused,
            error: DiagnosticsError.resumeFailed,
          ),
        before: (notice) => notice.resume(),
      );
    });

    testWidgets('Arabic time limit, $theme', (tester) async {
      await _golden(
        tester,
        'time_limit_ar',
        light: light,
        locale: const Locale('ar'),
        gateway: FakePauseGateway(
          pausedFor(BackgroundPauseReason.timeLimit, at: _today1510()),
        ),
      );
    });
  }
}
