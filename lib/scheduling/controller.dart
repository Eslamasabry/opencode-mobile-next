import 'package:flutter/foundation.dart';

import 'models.dart';
import 'platform.dart';
import 'recurrence.dart';
import 'store.dart';

DateTime nextDigestAt(ScheduledPromptDigest digest, DateTime now) {
  final local = now.toLocal();
  var next = DateTime(
    local.year,
    local.month,
    local.day,
    digest.hour,
    digest.minute,
  );
  if (!next.isAfter(now)) {
    next = DateTime(
      local.year,
      local.month,
      local.day + 1,
      digest.hour,
      digest.minute,
    );
  }
  return next.toUtc();
}

Future<void> syncScheduledPrompts(
  String profileId,
  ScheduledPromptStore store,
  ScheduledPromptsPlatform platform,
  DateTime now,
) async {
  final schedules = await store.load(profileId);
  final digest = await store.digest(profileId);
  await platform.replaceProfile(profileId, [
    for (final schedule in schedules)
      if (schedule.enabled && schedule.nextRunAt != null)
        {
          'id': schedule.id,
          'revision': schedule.revision,
          'dueAtMs': schedule.nextRunAt!.millisecondsSinceEpoch,
        },
  ], digestAt: digest.enabled ? nextDigestAt(digest, now) : null);
}

/// One profile's editor/list state. The store remains the cross-engine authority.
class ScheduledPromptsController extends ChangeNotifier {
  ScheduledPromptsController({
    required this.profileId,
    ScheduledPromptStore? store,
    ScheduledPromptsPlatform? platform,
    DateTime Function()? now,
  }) : store = store ?? ScheduledPromptStore(),
       platform = platform ?? const ScheduledPromptsPlatform(),
       now = now ?? DateTime.now;
  final String profileId;
  final ScheduledPromptStore store;
  final ScheduledPromptsPlatform platform;
  final DateTime Function() now;
  List<ScheduledPrompt> schedules = const [];
  List<ScheduledPromptRun> runs = const [];
  ScheduledPromptDigest digest = const ScheduledPromptDigest();
  SchedulePlatformStatus platformStatus = const SchedulePlatformStatus();
  bool loading = false;
  String? errorCode;
  bool _disposed = false;

  void _changed() {
    if (!_disposed) notifyListeners();
  }

  Future<void> load() async {
    loading = true;
    errorCode = null;
    _changed();
    try {
      platformStatus = await platform.status();
      if (!platformStatus.supported) {
        errorCode = 'unsupported';
        return;
      }
      await _mergeReceipts();
      schedules = await store.load(profileId);
      runs = await store.history(profileId);
      digest = await store.digest(profileId);
      await syncScheduledPrompts(profileId, store, platform, now());
    } on ScheduledPromptStoreException catch (error) {
      errorCode = error.code;
    } catch (_) {
      errorCode = 'storage_unavailable';
    } finally {
      loading = false;
      _changed();
    }
  }

  Future<void> _mergeReceipts() async {
    for (final receipt in await platform.readReceipts(profileId)) {
      final id = receipt['id'];
      final scheduleId = receipt['scheduleId'];
      final dueMs = receipt['scheduledAtMs'];
      final finishMs = receipt['finishedAtMs'];
      if (id is! String ||
          scheduleId is! String ||
          dueMs is! int ||
          finishMs is! int) {
        throw const ScheduledPromptStoreException('storage_corrupt');
      }
      final schedules = await store.load(profileId);
      final schedule = schedules.where((s) => s.id == scheduleId).firstOrNull;
      final history = await store.history(profileId);
      final existing = history.where((r) => r.id == id).firstOrNull;
      if (schedule != null) {
        final due = DateTime.fromMillisecondsSinceEpoch(dueMs, isUtc: true);
        final finish = DateTime.fromMillisecondsSinceEpoch(
          finishMs,
          isUtc: true,
        );
        if (existing == null ||
            existing.status == ScheduledPromptRunStatus.running) {
          final error = switch (receipt['error']) {
            'timeout' => 'timeout',
            'unavailable' => 'unavailable',
            _ => 'interrupted',
          };
          await store.recordRun(
            profileId,
            ScheduledPromptRun(
              id: id,
              scheduleId: scheduleId,
              scheduledAt: due,
              startedAt: existing?.startedAt ?? finish,
              finishedAt: finish,
              status: ScheduledPromptRunStatus.failed,
              sessionId: existing?.sessionId,
              late: finish.difference(due) > scheduledPromptLateTolerance,
              errorCode: error,
            ),
          );
        }
        // A persisted terminal receipt may precede a crash during advancement.
        // Advance it too, while preserving edits made since that occurrence.
        if (schedule.revision == receipt['revision'] &&
            schedule.nextRunAt == due) {
          await store.advanceOccurrence(
            schedule,
            nextScheduledOccurrence(schedule.recurrence, now()),
          );
        }
      }
      await platform.acknowledgeReceipts(profileId, [id]);
    }
  }

  Future<void> _mutate(Future<void> Function() change) async {
    errorCode = null;
    try {
      await change();
      await syncScheduledPrompts(profileId, store, platform, now());
      schedules = await store.load(profileId);
      runs = await store.history(profileId);
      digest = await store.digest(profileId);
    } on ScheduledPromptStoreException catch (error) {
      errorCode = error.code;
      rethrow;
    } catch (_) {
      errorCode = 'storage_unavailable';
      rethrow;
    } finally {
      _changed();
    }
  }

  Future<void> save(ScheduledPrompt schedule) => _mutate(() async {
    if (schedule.profileId != profileId || !platformStatus.supported) {
      throw const ScheduledPromptStoreException('unsupported');
    }
    await store.save(schedule);
  });
  Future<void> setEnabled(String id, bool enabled) =>
      _mutate(() => store.setEnabled(profileId, id, enabled));
  Future<void> delete(String id) => _mutate(() => store.delete(profileId, id));
  Future<void> setDigest(ScheduledPromptDigest value) =>
      _mutate(() => store.setDigest(profileId, value));
  Future<void> refreshPlatformStatus() async {
    platformStatus = await platform.status();
    _changed();
  }

  Future<void> requestExactAlarm() => platform.requestExactAlarm();
  @override
  void dispose() {
    _disposed = true;
    super.dispose();
  }
}
