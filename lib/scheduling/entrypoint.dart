import 'package:flutter/services.dart';
import 'package:flutter/widgets.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../state/profiles.dart';
import 'controller.dart';
import 'models.dart';
import 'platform.dart';
import 'recurrence.dart';
import 'runner.dart';
import 'store.dart';

@pragma('vm:entry-point')
Future<void> scheduledPromptMain() async {
  WidgetsFlutterBinding.ensureInitialized();
  const channel = ScheduledPromptsPlatform.channel;
  var executing = false;
  channel.setMethodCallHandler((call) async {
    if (call.method != 'execute') throw MissingPluginException();
    if (executing || call.arguments is! Map) {
      return {'status': 'failed', 'error': 'unavailable'};
    }
    executing = true;
    final args = Map<String, dynamic>.from(call.arguments as Map);
    try {
      return await executeScheduledPrompt(args);
    } on ScheduledPromptStoreException catch (error) {
      return {'status': 'failed', 'error': error.code};
    } catch (_) {
      return {'status': 'failed', 'error': 'unavailable'};
    } finally {
      executing = false;
    }
  });
  await channel.invokeMethod<void>('ready');
}

/// Called only after native durable claim, never from an editor or notification.
Future<Map<String, Object?>> executeScheduledPrompt(
  Map<String, dynamic> args, {
  ScheduledPromptStore? store,
  ScheduledPromptsPlatform platform = const ScheduledPromptsPlatform(),
  ScheduledPromptRunner? runner,
  DateTime Function()? clock,
}) async {
  final now = clock ?? DateTime.now;
  store ??= ScheduledPromptStore();
  final profileId = args['profileId'] as String;
  final scheduleId = args['scheduleId'] as String;
  if (scheduleId == '__digest') {
    final digest = await store.digest(profileId);
    final cutoff = now();
    if (!digest.enabled) return {'status': 'skipped'};
    final since =
        digest.lastDeliveredAt ?? cutoff.subtract(const Duration(hours: 24));
    final runs = (await store.history(profileId))
        .where(
          (r) =>
              r.status != ScheduledPromptRunStatus.running &&
              (r.finishedAt ?? r.startedAt).isAfter(since) &&
              !(r.finishedAt ?? r.startedAt).isAfter(cutoff),
        )
        .toList();
    if (!await store.claimDigest(profileId, digest, cutoff)) {
      return {'status': 'skipped'};
    }
    await syncScheduledPrompts(profileId, store, platform, cutoff);
    return {
      'status': 'digest',
      'ran': runs.where((r) => r.status == ScheduledPromptRunStatus.ran).length,
      'failed': runs
          .where((r) => r.status == ScheduledPromptRunStatus.failed)
          .length,
      'skipped': runs
          .where((r) => r.status == ScheduledPromptRunStatus.skipped)
          .length,
    };
  }
  final schedule = (await store.load(
    profileId,
  )).where((s) => s.id == scheduleId).firstOrNull;
  if (schedule == null ||
      !schedule.enabled ||
      schedule.revision != args['revision'] ||
      schedule.nextRunAt?.millisecondsSinceEpoch != args['scheduledAtMs']) {
    return {'status': 'skipped', 'error': 'stale_target'};
  }
  final due = scheduledPromptDue(schedule, now());
  if (due == null) return {'status': 'skipped', 'error': 'not_due'};
  final existing = (await store.history(profileId))
      .where((r) => r.id == scheduledOccurrenceId(schedule, due.scheduledAt))
      .firstOrNull;
  ScheduledPromptRun run;
  if (existing != null && existing.status != ScheduledPromptRunStatus.running) {
    run = existing;
  } else if (existing != null ||
      args['recoveryOnly'] == true ||
      !due.shouldRun) {
    final interrupted = existing != null || args['recoveryOnly'] == true;
    run = ScheduledPromptRun(
      id: scheduledOccurrenceId(schedule, due.scheduledAt),
      scheduleId: schedule.id,
      scheduledAt: due.scheduledAt,
      startedAt: existing?.startedAt ?? now(),
      finishedAt: now(),
      status: interrupted
          ? ScheduledPromptRunStatus.failed
          : ScheduledPromptRunStatus.skipped,
      late: due.late,
      sessionId: existing?.sessionId,
      errorCode: interrupted ? 'interrupted' : 'missed',
    );
  } else {
    // Native has claimed this occurrence. Retain an in-flight record before
    // crossing the network boundary, so any interrupted engine is never resent.
    final admittedAt = now();
    await store.recordRun(
      profileId,
      ScheduledPromptRun(
        id: scheduledOccurrenceId(schedule, due.scheduledAt),
        scheduleId: schedule.id,
        scheduledAt: due.scheduledAt,
        startedAt: admittedAt,
        status: ScheduledPromptRunStatus.running,
        late: due.late,
      ),
    );
    final prefs = runner == null ? await SharedPreferences.getInstance() : null;
    runner ??= ScheduledPromptRunner(
      loadProfile: ProfileStore(prefs: prefs!).loadForWidgetAction,
    );
    final result = await runner.execute(
      schedule,
      deadlineEpochMs: args['deadlineEpochMs'] as int,
      onSessionCreated: (sessionId) => store!.recordRun(
        profileId,
        ScheduledPromptRun(
          id: scheduledOccurrenceId(schedule, due.scheduledAt),
          scheduleId: schedule.id,
          scheduledAt: due.scheduledAt,
          startedAt: admittedAt,
          status: ScheduledPromptRunStatus.running,
          late: due.late,
          sessionId: sessionId,
        ),
      ),
      isCurrent: () async {
        final current = (await store!.load(
          profileId,
        )).where((s) => s.id == scheduleId).firstOrNull;
        return current?.enabled == true &&
            current?.revision == schedule.revision &&
            current?.nextRunAt == schedule.nextRunAt;
      },
    );
    run = ScheduledPromptRun(
      id: result.id,
      scheduleId: result.scheduleId,
      scheduledAt: result.scheduledAt,
      startedAt: admittedAt,
      status: result.status,
      late: result.late,
      finishedAt: result.finishedAt,
      sessionId: result.sessionId,
      errorCode: result.errorCode,
    );
  }
  if (existing == null || existing.status == ScheduledPromptRunStatus.running) {
    await store.recordRun(profileId, run);
  }
  await store.advanceOccurrence(schedule, due.nextRunAt);
  await syncScheduledPrompts(profileId, store, platform, now());
  return {
    'status': run.status.name,
    'error': run.errorCode,
    'sessionId': run.sessionId,
    'late': run.late,
  };
}
