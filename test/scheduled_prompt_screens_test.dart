import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/models.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/scheduling/controller.dart';
import 'package:opencode_mobile/scheduling/models.dart';
import 'package:opencode_mobile/scheduling/platform.dart';
import 'package:opencode_mobile/scheduling/store.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/screens/scheduling/schedule_copy.dart';
import 'package:opencode_mobile/ui/screens/scheduling/schedule_routes.dart';
import 'package:opencode_mobile/ui/screens/scheduling/scheduled_prompts_screen.dart';

class _Backend implements ScheduledPromptBackend {
  final values = <String, String>{};
  bool failWrites = false;
  @override
  Future<String?> read(String key) async => values[key];
  @override
  Future<bool> compareAndSet(String key, String? expected, String value) async {
    if (failWrites) {
      throw const ScheduledPromptStoreException('storage_unavailable');
    }
    if (values[key] != expected) return false;
    values[key] = value;
    return true;
  }
}

class _Platform extends ScheduledPromptsPlatform {
  bool supported = true;
  @override
  Future<SchedulePlatformStatus> status() async => SchedulePlatformStatus(
    supported: supported,
    exactAllowed: true,
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
}

void main() {
  final now = DateTime(2030, 1, 7, 7);
  final l = lookupAppLocalizations(const Locale('en'));
  late _Backend backend;
  late ScheduledPromptStore store;
  late ScheduledPromptsController controller;
  late ScheduledPrompt target;
  late _Platform platform;
  ScheduledPromptRun? opened;

  setUp(() {
    backend = _Backend();
    platform = _Platform();
    store = ScheduledPromptStore(backend: backend, now: () => now);
    controller = ScheduledPromptsController(
      profileId: 'profile',
      store: store,
      platform: platform,
      now: () => now,
    );
    target = ScheduledPrompt(
      id: 'morning',
      profileId: 'profile',
      profileFingerprint: 'routing',
      prompt: 'Summarize overnight changes',
      projectPath: '/project',
      sessionId: 'session',
      agent: 'build',
      recurrence: const ScheduleRecurrence.weekdays(),
      createdAt: now,
      nextRunAt: now.add(const Duration(hours: 1)),
    );
    opened = null;
  });

  Future<void> pump(WidgetTester tester, {Widget? home}) async {
    tester.view.physicalSize = const Size(600, 1100);
    tester.view.devicePixelRatio = 1;
    addTearDown(() {
      tester.view.resetPhysicalSize();
      tester.view.resetDevicePixelRatio();
    });
    addTearDown(controller.dispose);
    await tester.pumpWidget(
      MaterialApp(
        theme: AppTheme.light(),
        locale: const Locale('en'),
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home:
            home ??
            ScheduledPromptsScreen(
              controller: controller,
              target: target,
              serverName: 'Laptop',
              onOpenRun: (run) async {
                opened = run;
              },
            ),
      ),
    );
    await tester.pumpAndSettle();
  }

  Future<void> tapText(WidgetTester tester, String text) async {
    final finder = find.text(text).last;
    await tester.ensureVisible(finder);
    await tester.tap(finder);
    await tester.pumpAndSettle();
  }

  String clock(int h, int m) => scheduleClock(l, h, m);

  Finder wheel(String key) => find.byKey(ValueKey('schedule-wheel-$key'));

  Future<void> typePrompt(WidgetTester tester, String text) async {
    await tester.enterText(
      find.descendant(
        of: find.byKey(const ValueKey('schedule-prompt')),
        matching: find.byType(EditableText),
      ),
      text,
    );
    await tester.pumpAndSettle();
  }

  testWidgets('empty list: one calm line, and the sheet saves a weekday '
      'schedule only once it has words', (tester) async {
    await pump(tester);
    expect(find.text(l.scheduleEmpty), findsOneWidget);
    await tapText(tester, l.scheduleNew);
    // The main action names the moment: 9:00 on weekdays by default.
    expect(find.text(l.scheduleForTime(clock(9, 0))), findsOneWidget);
    await tapText(tester, l.scheduleForTime(clock(9, 0)));
    expect(find.text(l.scheduleNeedPrompt), findsOneWidget);
    expect(await store.load('profile'), isEmpty);
    await typePrompt(tester, 'Prepare the morning summary');
    await tapText(tester, l.scheduleForTime(clock(9, 0)));
    final saved = (await store.load('profile')).single;
    expect(saved.prompt, 'Prepare the morning summary');
    expect(saved.recurrence.kind, ScheduleRecurrenceKind.weekdays);
    expect(saved.nextRunAt?.toLocal(), DateTime(2030, 1, 7, 9));
    // Opened from a conversation: the run goes there by default.
    expect(saved.sessionId, 'session');
    expect(find.text(l.scheduleEmpty), findsNothing);
    expect(find.text('Prepare the morning summary'), findsOneWidget);
    expect(find.text(l.scheduleRuleWeekdays(clock(9, 0))), findsOneWidget);
  });

  testWidgets('rolling the time wheel renames the main action and saves '
      'every day at that time', (tester) async {
    await pump(tester);
    await tapText(tester, l.scheduleNew);
    await typePrompt(tester, 'Check the build');
    await tapText(tester, l.scheduleEveryDay);
    await tester.drag(wheel('time'), const Offset(0, -52));
    await tester.pumpAndSettle();
    expect(find.text(l.scheduleForTime(clock(9, 15))), findsOneWidget);
    await tapText(tester, l.scheduleForTime(clock(9, 15)));
    final saved = (await store.load('profile')).single;
    expect(saved.recurrence.kind, ScheduleRecurrenceKind.daily);
    expect((saved.recurrence.hour, saved.recurrence.minute), (9, 15));
    expect(saved.nextRunAt?.toLocal(), DateTime(2030, 1, 7, 9, 15));
  });

  testWidgets('custom runs every few hours from now', (tester) async {
    await pump(tester);
    await tapText(tester, l.scheduleNew);
    await typePrompt(tester, 'Check the build');
    await tapText(tester, l.scheduleCustom);
    expect(wheel('hours'), findsOneWidget);
    await tapText(tester, l.scheduleForHours(3));
    final saved = (await store.load('profile')).single;
    expect(saved.recurrence.kind, ScheduleRecurrenceKind.interval);
    expect(saved.recurrence.hours, 3);
    expect(saved.nextRunAt?.toLocal(), DateTime(2030, 1, 7, 10));
  });

  testWidgets('once takes a day and a time and refuses a moment gone by', (
    tester,
  ) async {
    await store.save(
      target.copyWith(
        recurrence: ScheduleRecurrence.once(
          now.subtract(const Duration(hours: 1)),
        ),
        enabled: false,
        clearNextRunAt: true,
      ),
    );
    await pump(tester);
    await tester.tap(find.text('Summarize overnight changes'));
    await tester.pumpAndSettle();
    expect(wheel('day'), findsOneWidget);
    expect(find.text(l.scheduleWheelToday), findsWidgets);
    await tapText(tester, l.scheduleForToday(clock(6, 0)));
    expect(find.text(l.scheduleNeedFuture), findsOneWidget);
    // Tomorrow, same time: it saves.
    await tester.drag(wheel('day'), const Offset(0, -52));
    await tester.pumpAndSettle();
    await tapText(tester, l.scheduleForTomorrow(clock(6, 0)));
    final saved = (await store.load('profile')).single;
    expect(saved.recurrence.kind, ScheduleRecurrenceKind.once);
    expect(saved.recurrence.at?.toLocal(), DateTime(2030, 1, 8, 6));
    expect(saved.enabled, isTrue);
    expect(saved.revision, 2);
  });

  testWidgets('each row: the prompt, one state line and a switch that '
      'pauses and resumes', (tester) async {
    await store.save(target);
    await pump(tester);
    expect(find.text('Summarize overnight changes'), findsOneWidget);
    expect(find.text(l.scheduleRuleWeekdays(clock(8, 0))), findsOneWidget);
    expect(find.textContaining('2030'), findsNothing);
    await tester.tap(find.byType(Switch).first);
    await tester.pumpAndSettle();
    expect((await store.load('profile')).single.enabled, isFalse);
    expect(
      find.text('${l.schedulePaused} · ${l.scheduleRuleWeekdays(clock(8, 0))}'),
      findsOneWidget,
    );
    await tester.tap(find.byType(Switch).first);
    await tester.pumpAndSettle();
    expect((await store.load('profile')).single.enabled, isTrue);
  });

  testWidgets('the sheet deletes a schedule and its history after asking', (
    tester,
  ) async {
    await store.save(target);
    await store.recordRun(
      'profile',
      ScheduledPromptRun(
        id: 'run',
        scheduleId: target.id,
        scheduledAt: now,
        startedAt: now,
        status: ScheduledPromptRunStatus.ran,
        sessionId: 'session',
      ),
    );
    await pump(tester);
    expect(
      find.text(
        '${l.scheduleRuleWeekdays(clock(8, 0))} · ${l.scheduleSent(l.scheduleDayToday)}',
      ),
      findsOneWidget,
    );
    await tester.tap(find.text('Summarize overnight changes'));
    await tester.pumpAndSettle();
    expect(find.text(l.scheduleEdit), findsOneWidget);
    await tapText(tester, l.scheduleDelete);
    expect(
      find.text(l.scheduleDeleteNamedTitle('Summarize overnight changes')),
      findsOneWidget,
    );
    expect(find.text(l.scheduleDeleteBody), findsOneWidget);
    await tapText(tester, l.scheduleDelete);
    expect(await store.load('profile'), isEmpty);
    expect(await store.history('profile'), isEmpty);
  });

  testWidgets('a finished one-time schedule says what it did and offers '
      'Delete, not a switch', (tester) async {
    await store.save(
      target.copyWith(
        enabled: false,
        clearNextRunAt: true,
        recurrence: ScheduleRecurrence.once(now),
      ),
    );
    await pump(tester);
    expect(find.text(l.scheduleFinished), findsOneWidget);
    expect(find.textContaining(l.schedulePaused), findsNothing);
    expect(find.byType(Switch), findsNWidgets(1)); // the morning summary's
    expect(find.byTooltip(l.scheduleDelete), findsOneWidget);
  });

  testWidgets('a run that did not go out says why and opens its '
      'conversation', (tester) async {
    await store.save(target);
    await store.recordRun(
      'profile',
      ScheduledPromptRun(
        id: 'needs-you',
        scheduleId: target.id,
        scheduledAt: now,
        startedAt: now,
        finishedAt: now,
        status: ScheduledPromptRunStatus.failed,
        sessionId: 'session',
        errorCode: 'needs_you',
        late: true,
      ),
    );
    await pump(tester);
    expect(
      find.textContaining(l.scheduleNotSent(l.scheduleDayToday)),
      findsOneWidget,
    );
    await tester.tap(find.text('Summarize overnight changes'));
    await tester.pumpAndSettle();
    expect(find.text(l.scheduleNeedsYou), findsOneWidget);
    await tester.tap(find.byKey(const ValueKey('schedule-last-run')));
    await tester.pumpAndSettle();
    expect(opened?.sessionId, 'session');
    expect(opened?.errorCode, 'needs_you');
  });

  testWidgets('from a conversation the run can go to that conversation or '
      'a new one each time', (tester) async {
    await pump(tester);
    await tapText(tester, l.scheduleNew);
    await typePrompt(tester, 'Keep going');
    expect(find.text(l.scheduleInThisConversation), findsOneWidget);
    await tester.tap(find.byKey(const ValueKey('schedule-where')));
    await tester.pumpAndSettle();
    await tapText(tester, l.scheduleNewConversation);
    expect(find.text(l.scheduleNewConversation), findsOneWidget);
    await tapText(tester, l.scheduleForTime(clock(9, 0)));
    expect((await store.load('profile')).single.sessionId, isNull);
  });

  testWidgets('a failed save stays in the sheet and saves nothing', (
    tester,
  ) async {
    await pump(tester);
    backend.failWrites = true;
    await tapText(tester, l.scheduleNew);
    await typePrompt(tester, 'Prepare the morning summary');
    await tapText(tester, l.scheduleForTime(clock(9, 0)));
    expect(find.text(l.scheduleSaveFailed), findsWidgets);
    expect(find.text(l.scheduleNew), findsWidgets);
    backend.failWrites = false;
    expect(await store.load('profile'), isEmpty);
  });

  testWidgets(
    'unsupported platform explains unavailability without create action',
    (tester) async {
      platform.supported = false;
      await pump(tester);
      expect(find.text(l.scheduleUnsupported), findsOneWidget);
      expect(find.text(l.scheduleNew), findsNothing);
    },
  );

  test('each run failure says what happened and what to do next', () {
    final l = lookupAppLocalizations(const Locale('en'));
    final copy = {
      for (final code in [
        'auth_required',
        'cleartext_unconfirmed',
        'agent_failed',
        'unavailable',
        'needs_you',
        'certificate_changed',
        'certificate_invalid',
      ])
        code: scheduleErrorCopy(l, code),
    };
    // A password prompt or an agent error is not "a request to answer", and
    // neither is an unreachable server.
    expect(copy['auth_required'], contains('sign in'));
    expect(copy['cleartext_unconfirmed'], contains('allow HTTP'));
    expect(copy['agent_failed'], contains('error'));
    expect(copy['unavailable'], contains('server could not be reached'));
    expect(copy['certificate_changed'], contains('certificate changed'));
    expect(copy['certificate_changed'], contains('Open the server'));
    expect(copy.values.toSet(), hasLength(copy.length));
  });

  test('a schedule for a conversation keeps that conversation\'s model', () {
    // 2026-10-17 emulator proof: the conversation used a free model, the app
    // default was a model whose sign-in had lapsed, and the scheduled run
    // switched the conversation to the default, so no reply came back.
    final appDefault = ModelRef(providerID: 'openai', modelID: 'gpt-5.6-sol');
    final existing = scheduleSelectionSeed(
      sessionSelection: SessionSelection(
        model: ModelRef(providerID: 'opencode', modelID: 'free-model'),
        agent: 'plan',
      ),
      defaultModel: appDefault,
      defaultAgent: 'build',
    );
    expect(existing.model, isNull);
    expect(existing.agent, 'plan');

    final unknownAgent = scheduleSelectionSeed(
      sessionSelection: const SessionSelection(agentKnown: false),
      defaultModel: appDefault,
      defaultAgent: 'build',
    );
    expect(unknownAgent.agent, 'build');

    final fresh = scheduleSelectionSeed(
      sessionSelection: null,
      defaultModel: appDefault,
      defaultAgent: 'build',
    );
    expect(fresh.model, appDefault);
    expect(fresh.agent, 'build');
  });
}
