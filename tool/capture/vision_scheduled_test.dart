// Captures of Scheduled prompts (vision slice, 2026-10-20,
// docs/qa/vision-scheduled-2026-10-20/README.md): the real
// ScheduledPromptsScreen and its creation sheet, the live aurora painted
// (still), real fonts and blurred shadows, no server and no alarms. Phone
// 412x915 dp at 2x pixels, one full screen per image.
//
//   TZ=UTC flutter test --concurrency=1 tool/capture/vision_scheduled_test.dart
//   TZ=UTC flutter test --concurrency=1 --dart-define=VISION_OUT=<dir> \
//     tool/capture/vision_scheduled_test.dart
//
// Output: /home/eslam/Storage2/launch-footage/vision-scheduled/<scene>.png
//
// The data is invented and plain: a project "neon-breaker" on a saved server
// "Laptop", on Tuesday 20 October 2026 at 8:10 in the morning.
import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/scheduling/controller.dart';
import 'package:opencode_mobile/scheduling/models.dart';
import 'package:opencode_mobile/scheduling/platform.dart';
import 'package:opencode_mobile/scheduling/store.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/screens/scheduling/scheduled_prompts_screen.dart';

import '../../test/goldens/kit/kit_gallery.dart' show loadKitGalleryFonts;
import 'fixtures.dart';
// AUDIT TOOLING (audit/style-coherence): no-op unless AUDIT_STYLE is set.
import 'audit/audit_env.dart';

const _out = String.fromEnvironment(
  'VISION_OUT',
  defaultValue: '/home/eslam/Storage2/launch-footage/vision-scheduled',
);

/// What is on the screen.
enum _State {
  /// Four schedules, the last runs went out.
  list,

  /// Nothing scheduled yet.
  empty,

  /// The sheet making a new weekday schedule, prompt typed.
  sheetNew,

  /// The sheet with Once: a day and a time.
  sheetOnce,

  /// The sheet with Custom: every few hours.
  sheetCustom,

  /// An existing schedule whose last run did not go out.
  sheetEdit,

  /// Exact alarms are not allowed on this phone.
  exactOff,
}

enum _Scene {
  mainDark('main-dark'),
  mainLight('main-light', light: true),
  empty('empty-dark', state: _State.empty),
  sheetNew('sheet-new-dark', state: _State.sheetNew),
  sheetNewLight('sheet-new-light', light: true, state: _State.sheetNew),
  sheetOnce('sheet-once-dark', state: _State.sheetOnce),
  sheetCustom('sheet-custom-dark', state: _State.sheetCustom),
  sheetEdit('sheet-edit-last-run-dark', state: _State.sheetEdit),
  exactOff('exact-timing-off-dark', state: _State.exactOff),
  small('small-320-text2-dark', size: Size(320, 693), scale: 2),
  smallSheet(
    'small-320-text2-sheet-dark',
    size: Size(320, 693),
    scale: 2,
    state: _State.sheetNew,
  ),
  arabic('arabic-rtl-dark', locale: Locale('ar')),
  arabicSheet(
    'arabic-rtl-sheet-dark',
    locale: Locale('ar'),
    state: _State.sheetNew,
  );

  const _Scene(
    this.file, {
    this.light = false,
    this.state = _State.list,
    this.size = const Size(412, 915),
    this.scale = 1,
    this.locale = const Locale('en'),
  });
  final String file;
  final bool light;
  final _State state;
  final Size size;
  final double scale;
  final Locale locale;
}

class _Backend implements ScheduledPromptBackend {
  final values = <String, String>{};
  @override
  Future<String?> read(String key) async => values[key];
  @override
  Future<bool> compareAndSet(String key, String? expected, String value) async {
    if (values[key] != expected) return false;
    values[key] = value;
    return true;
  }
}

class _Platform extends ScheduledPromptsPlatform {
  const _Platform({this.exact = true});
  final bool exact;
  @override
  Future<SchedulePlatformStatus> status() async => SchedulePlatformStatus(
    supported: true,
    exactAllowed: exact,
    batteryExempt: true,
    notificationsAllowed: true,
  );
  @override
  Future<void> replaceProfile(
    String profileId,
    List<Map<String, Object?>> entries, {
    DateTime? digestAt,
  }) async {}
  @override
  Future<List<Map<String, dynamic>>> readReceipts(String profileId) async => [];
  @override
  Future<void> acknowledgeReceipts(String profileId, List<String> ids) async {}
  @override
  Future<void> requestExactAlarm() async {}
}

final _now = DateTime(2026, 10, 20, 8, 10);

ScheduledPrompt _schedule(
  String id,
  String prompt,
  ScheduleRecurrence recurrence, {
  DateTime? next,
  bool enabled = true,
  String project = '/root/projects/neon-breaker',
}) => ScheduledPrompt(
  id: id,
  profileId: 'laptop',
  profileFingerprint: 'laptop-routing',
  prompt: prompt,
  projectPath: project,
  recurrence: recurrence,
  createdAt: _now.subtract(const Duration(days: 9)),
  nextRunAt: next?.toUtc(),
  enabled: enabled,
);

Future<void> _seed(ScheduledPromptStore store, _State state) async {
  if (state == _State.empty || state == _State.sheetNew) return;
  final tests = _schedule(
    'tests',
    'Run the tests and sum up what failed',
    const ScheduleRecurrence.weekdays(hour: 9),
    next: DateTime(2026, 10, 20, 9),
  );
  await store.save(tests);
  await store.save(
    _schedule(
      'deps',
      'Check for dependency updates',
      const ScheduleRecurrence.daily(hour: 7, minute: 30),
      next: DateTime(2026, 10, 21, 7, 30),
    ),
  );
  await store.save(
    _schedule(
      'notes',
      'Write the release notes for this week',
      ScheduleRecurrence.once(DateTime(2026, 10, 23, 17)),
      next: DateTime(2026, 10, 23, 17),
    ),
  );
  await store.save(
    _schedule(
      'readme',
      'Tidy the README',
      ScheduleRecurrence.interval(hours: 3, anchor: _now),
      enabled: false,
    ),
  );
  Future<void> ran(
    String id,
    String schedule,
    DateTime at, {
    ScheduledPromptRunStatus status = ScheduledPromptRunStatus.ran,
    String? error,
  }) => store.recordRun(
    'laptop',
    ScheduledPromptRun(
      id: id,
      scheduleId: schedule,
      scheduledAt: at.toUtc(),
      startedAt: at.toUtc(),
      finishedAt: at.toUtc(),
      status: status,
      sessionId: 'ses_$id',
      errorCode: error,
    ),
  );
  await ran('t1', 'tests', DateTime(2026, 10, 19, 9));
  await ran(
    'd1',
    'deps',
    DateTime(2026, 10, 20, 7, 30),
    status: state == _State.sheetEdit
        ? ScheduledPromptRunStatus.failed
        : ScheduledPromptRunStatus.ran,
    error: state == _State.sheetEdit ? 'unavailable' : null,
  );
}

Widget _app(Widget home, _Scene scene) => MaterialApp(
  debugShowCheckedModeBanner: false,
  theme: AppTheme.forLocale(captureTheme(light: scene.light), scene.locale),
  locale: scene.locale,
  supportedLocales: AppLocalizations.supportedLocales,
  localizationsDelegates: AppLocalizations.localizationsDelegates,
  builder: (context, child) => MediaQuery(
    data: MediaQuery.of(
      context,
    ).copyWith(textScaler: TextScaler.linear(scene.scale)),
    child: auditWrap(context, child!),
  ),
  home: home,
);

Future<void> _settle(WidgetTester tester) async {
  for (var i = 0; i < 12; i++) {
    await tester.pump(const Duration(milliseconds: 100));
  }
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(loadKitGalleryFonts);
  setUp(() {
    KitLiveBackground.paintWithoutLoops = true;
    KitMotion.loops = false;
  });
  tearDown(() {
    KitLiveBackground.paintWithoutLoops = false;
    KitLiveBackground.frozenSeconds = null;
  });

  for (final scene in _Scene.values) {
    testWidgets(scene.file, (tester) async {
      debugDefaultTargetPlatformOverride = TargetPlatform.android;
      // Blurred shadows as a phone draws them; put back before the end.
      debugDisableShadows = false;
      tester.view.physicalSize = scene.size * 2;
      tester.view.devicePixelRatio = 2;
      addTearDown(tester.view.reset);
      KitLiveBackground.frozenSeconds = 21;
      final store = ScheduledPromptStore(backend: _Backend(), now: () => _now);
      await tester.runAsync(() => _seed(store, scene.state));
      final controller = ScheduledPromptsController(
        profileId: 'laptop',
        store: store,
        platform: _Platform(exact: scene.state != _State.exactOff),
        now: () => _now,
      );
      addTearDown(controller.dispose);
      final boundary = GlobalKey();
      await tester.pumpWidget(
        RepaintBoundary(
          key: boundary,
          child: _app(const SizedBox.expand(key: ValueKey('opener')), scene),
        ),
      );
      unawaited(
        pushKitPage<void>(
          tester.element(find.byKey(const ValueKey('opener'))),
          (_) => ScheduledPromptsScreen(
            controller: controller,
            serverName: 'Laptop',
            target: _schedule(
              'target',
              '',
              const ScheduleRecurrence.weekdays(hour: 9),
            ),
            onOpenRun: (_) async {},
          ),
        ),
      );
      await tester.runAsync(
        () => Future<void>.delayed(const Duration(milliseconds: 100)),
      );
      await _settle(tester);
      switch (scene.state) {
        case _State.sheetNew || _State.sheetOnce || _State.sheetCustom:
          await tester.tap(find.byKey(const ValueKey('schedule-new')));
          await _settle(tester);
          await tester.enterText(
            find.descendant(
              of: find.byKey(const ValueKey('schedule-prompt')),
              matching: find.byType(EditableText),
            ),
            scene.state == _State.sheetNew
                ? (scene.locale.languageCode == 'ar'
                      ? 'شغّل الاختبارات ولخّص ما فشل'
                      : 'Run the tests and sum up what failed')
                : scene.state == _State.sheetOnce
                ? 'Write the release notes for this week'
                : 'Check the build is still green',
          );
          FocusManager.instance.primaryFocus?.unfocus();
          await _settle(tester);
          if (scene.state != _State.sheetNew) {
            final l = lookupAppLocalizations(scene.locale);
            await tester.tap(
              find.text(
                scene.state == _State.sheetOnce
                    ? l.scheduleOnce
                    : l.scheduleCustom,
              ),
            );
            await _settle(tester);
          }
        case _State.sheetEdit:
          await tester.tap(find.byKey(const ValueKey('schedule-deps')));
          await _settle(tester);
        case _State.list || _State.empty || _State.exactOff:
          break;
      }
      expect(tester.takeException(), isNull);
      await writePng(
        '$_out/${scene.file}.png',
        await capturePng(tester, boundary, pixelRatio: 2),
      );
      await tester.pumpWidget(const SizedBox.shrink());
      await tester.pump(const Duration(seconds: 1));
      debugDisableShadows = true;
      debugDefaultTargetPlatformOverride = null;
    });
  }
}
