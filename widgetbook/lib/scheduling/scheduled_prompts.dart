import 'package:flutter/widgets.dart';
import 'package:opencode_mobile/scheduling/controller.dart';
import 'package:opencode_mobile/scheduling/models.dart';
import 'package:opencode_mobile/scheduling/platform.dart';
import 'package:opencode_mobile/scheduling/store.dart';
import 'package:opencode_mobile/ui/screens/scheduling/schedule_sheet.dart';
import 'package:opencode_mobile/ui/screens/scheduling/scheduled_prompts_screen.dart';
import 'package:widgetbook/widgetbook.dart';

import '../catalog.dart';

class _MemoryBackend implements ScheduledPromptBackend {
  final values = <String, String>{};
  bool fail = false;
  @override
  Future<String?> read(String key) async {
    if (fail) throw const ScheduledPromptStoreException('storage_unavailable');
    return values[key];
  }

  @override
  Future<bool> compareAndSet(String key, String? expected, String value) async {
    if (values[key] != expected) return false;
    values[key] = value;
    return true;
  }
}

class _Platform extends ScheduledPromptsPlatform {
  const _Platform({this.supported = true});
  final bool supported;
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
  @override
  Future<void> requestExactAlarm() async {}
}

class _PreviewController extends ScheduledPromptsController {
  _PreviewController({
    required super.store,
    required super.platform,
    required super.now,
    required this.freezeLoading,
  }) : super(profileId: 'preview');
  final bool freezeLoading;
  @override
  Future<void> load() async {
    if (!freezeLoading) return super.load();
    loading = true;
    platformStatus = await platform.status();
    notifyListeners();
  }
}

enum _Scene {
  empty,
  loading,
  scheduled,
  paused,
  history,
  longContent,
  unsupported,
  error,
  weekdays,
  once,
  longPrompt,
}

class _ScheduleScene extends StatefulWidget {
  const _ScheduleScene(this.scene);
  final _Scene scene;
  @override
  State<_ScheduleScene> createState() => _ScheduleSceneState();
}

class _ScheduleSceneState extends State<_ScheduleScene> {
  static final _now = DateTime(2026, 10, 15, 7);
  late final ScheduledPromptsController controller;
  late final ScheduledPrompt target;
  late final Future<void> ready;
  @override
  void initState() {
    super.initState();
    final backend = _MemoryBackend();
    final store = ScheduledPromptStore(backend: backend, now: () => _now);
    controller = _PreviewController(
      store: store,
      platform: _Platform(supported: widget.scene != _Scene.unsupported),
      now: () => _now,
      freezeLoading: widget.scene == _Scene.loading,
    );
    target = ScheduledPrompt(
      id: 'morning',
      profileId: 'preview',
      profileFingerprint: 'preview-routing',
      prompt: widget.scene == _Scene.longPrompt
          ? 'Review overnight project changes, summarize unresolved questions, '
                'and prepare a detailed morning briefing covering implementation, '
                'verification, release readiness, and the next decisions for the team.'
          : 'Summarize overnight project changes',
      projectPath: '/root/projects/launch',
      sessionId: 'conversation',
      agent: 'build',
      recurrence: widget.scene == _Scene.once
          ? ScheduleRecurrence.once(_now.add(const Duration(hours: 1)))
          : const ScheduleRecurrence.weekdays(),
      createdAt: _now,
      nextRunAt: _now.add(const Duration(hours: 1)),
      enabled: widget.scene != _Scene.paused,
    );
    ready = () async {
      if ({
        _Scene.scheduled,
        _Scene.paused,
        _Scene.history,
        _Scene.longContent,
      }.contains(widget.scene)) {
        await store.save(target);
      }
      if (widget.scene == _Scene.longContent) {
        for (var index = 1; index <= 9; index++) {
          await store.save(
            ScheduledPrompt(
              id: 'morning-$index',
              profileId: target.profileId,
              profileFingerprint: target.profileFingerprint,
              prompt: target.prompt,
              projectPath: target.projectPath,
              recurrence: ScheduleRecurrence.interval(
                hours: index,
                anchor: _now,
              ),
              createdAt: _now,
              nextRunAt: _now.add(Duration(hours: index)),
            ),
          );
        }
      }
      if (widget.scene == _Scene.history) {
        for (final (index, status, reason) in [
          (0, ScheduledPromptRunStatus.ran, null),
          (1, ScheduledPromptRunStatus.failed, 'needs_you'),
          (2, ScheduledPromptRunStatus.skipped, 'busy'),
        ]) {
          final time = _now.subtract(Duration(hours: index + 1));
          await store.recordRun(
            'preview',
            ScheduledPromptRun(
              id: 'run-$index',
              scheduleId: target.id,
              scheduledAt: time,
              startedAt: time,
              finishedAt: time,
              status: status,
              sessionId: 'conversation',
              errorCode: reason,
            ),
          );
        }
      }
      backend.fail = widget.scene == _Scene.error;
      await controller.load();
    }();
  }

  @override
  void dispose() {
    controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => FutureBuilder<void>(
    future: ready,
    builder: (context, snapshot) {
      if (snapshot.connectionState != ConnectionState.done) {
        return const SizedBox.shrink();
      }
      if (widget.scene == _Scene.weekdays ||
          widget.scene == _Scene.once ||
          widget.scene == _Scene.longPrompt) {
        return _SheetOpener(controller: controller, target: target);
      }
      return ScheduledPromptsScreen(
        controller: controller,
        target: target,
        serverName: 'Laptop',
        onOpenRun: (_) async {},
      );
    },
  );
}

/// The creation sheet, opened over an empty page as a tap opens it.
class _SheetOpener extends StatefulWidget {
  const _SheetOpener({required this.controller, required this.target});
  final ScheduledPromptsController controller;
  final ScheduledPrompt target;
  @override
  State<_SheetOpener> createState() => _SheetOpenerState();
}

class _SheetOpenerState extends State<_SheetOpener> {
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      showScheduleSheet(
        context,
        controller: widget.controller,
        seed: widget.target,
        existing: widget.target,
        where: 'launch · Laptop',
      );
    });
  }

  @override
  Widget build(BuildContext context) => const SizedBox.expand();
}

CatalogComponent scheduledPromptsScreen() => catalogComponent(
  area: 'Schedules',
  screen: 'ScheduledPromptsScreen',
  name: 'Scheduled prompts',
  useCases: [
    WidgetbookUseCase(
      name: 'Loading',
      builder: (_) => const _ScheduleScene(_Scene.loading),
    ),
    WidgetbookUseCase(
      name: 'Empty',
      builder: (_) => const _ScheduleScene(_Scene.empty),
    ),
    WidgetbookUseCase(
      name: 'Next run',
      builder: (_) => const _ScheduleScene(_Scene.scheduled),
    ),
    WidgetbookUseCase(
      name: 'Paused',
      builder: (_) => const _ScheduleScene(_Scene.paused),
    ),
    WidgetbookUseCase(
      name: 'Run history',
      builder: (_) => const _ScheduleScene(_Scene.history),
    ),
    WidgetbookUseCase(
      name: 'Storage error',
      builder: (_) => const _ScheduleScene(_Scene.error),
    ),
    WidgetbookUseCase(
      name: 'Many schedules',
      builder: (_) => const _ScheduleScene(_Scene.longContent),
    ),
    WidgetbookUseCase(
      name: 'Unavailable platform',
      builder: (_) => const _ScheduleScene(_Scene.unsupported),
    ),
  ],
);

CatalogComponent scheduleEditorScreen() => catalogComponent(
  area: 'Schedules',
  screen: 'showScheduleSheet',
  name: 'Schedule sheet',
  useCases: [
    WidgetbookUseCase(
      name: 'Every weekday',
      builder: (_) => const _ScheduleScene(_Scene.weekdays),
    ),
    WidgetbookUseCase(
      name: 'Once',
      builder: (_) => const _ScheduleScene(_Scene.once),
    ),
    WidgetbookUseCase(
      name: 'Long prompt',
      builder: (_) => const _ScheduleScene(_Scene.longPrompt),
    ),
  ],
);
