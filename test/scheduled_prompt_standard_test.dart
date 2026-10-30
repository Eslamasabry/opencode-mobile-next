import 'dart:convert';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/scheduling/controller.dart';
import 'package:opencode_mobile/scheduling/models.dart';
import 'package:opencode_mobile/scheduling/platform.dart';
import 'package:opencode_mobile/scheduling/store.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/screens/scheduling/schedule_sheet.dart';
import 'package:opencode_mobile/ui/screens/scheduling/scheduled_prompts_screen.dart';

import '../widgetbook/test/standard_measure.dart';

class _MemoryBackend implements ScheduledPromptBackend {
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
  @override
  Future<SchedulePlatformStatus> status() async => const SchedulePlatformStatus(
    supported: true,
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

Future<void> _loadFonts() async {
  for (final (family, path) in [
    ('AppSans', 'assets/fonts/geist/Geist-Variable.ttf'),
    ('AppMono', 'assets/fonts/geist/GeistMono-Variable.ttf'),
    ('AppPhosphorRegular', 'assets/fonts/phosphor/Phosphor.ttf'),
    ('AppPhosphorDuotone', 'assets/fonts/phosphor/Phosphor-Duotone.ttf'),
    ('AppPhosphorFill', 'assets/fonts/phosphor/Phosphor-Fill.ttf'),
    (
      'Noto Sans Arabic',
      '/usr/share/fonts/truetype/noto/NotoSansArabicUI-Regular.ttf',
    ),
    // The Arabic theme's face: Latin words in it (a prompt, a project) are
    // measured in a real font, as on a phone, not in the test's blocks.
    ('sans-serif', 'tool/capture/fonts/Roboto-Regular.ttf'),
  ]) {
    final file = File(path);
    if (!file.existsSync()) continue;
    final loader = FontLoader(family)
      ..addFont(Future.value(ByteData.sublistView(file.readAsBytesSync())));
    await loader.load();
  }
}

enum _Scene { empty, populated, history, weekdayEditor, onceEditor }

/// The list with its creation sheet opened over it, as a tap opens it.
class _SheetHost extends StatefulWidget {
  const _SheetHost({required this.controller, required this.target});
  final ScheduledPromptsController controller;
  final ScheduledPrompt target;
  @override
  State<_SheetHost> createState() => _SheetHostState();
}

class _SheetHostState extends State<_SheetHost> {
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      showScheduleSheet(
        context,
        controller: widget.controller,
        seed: widget.target,
        existing: widget.target.recurrence.kind == ScheduleRecurrenceKind.once
            ? widget.target
            : null,
        where: 'launch · Laptop',
      );
    });
  }

  // The sheet alone: the list's own main action under the scrim is not
  // part of the sheet's first view.
  @override
  Widget build(BuildContext context) => const SizedBox.expand();
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(_loadFonts);

  for (final scene in _Scene.values) {
    testWidgets('scheduled prompt screen standard ${scene.name}', (
      tester,
    ) async {
      addTearDown(() {
        tester.view.resetPhysicalSize();
        tester.view.resetDevicePixelRatio();
      });
      for (final (name, locale, scale, dark) in [
        ('en-1.0', const Locale('en'), 1.0, false),
        ('en-2.0', const Locale('en'), 2.0, false),
        ('ar-1.0', const Locale('ar'), 1.0, false),
        ('en-dark-1.0', const Locale('en'), 1.0, true),
      ]) {
        final now = DateTime(2030, 1, 7, 7);
        final store = ScheduledPromptStore(
          backend: _MemoryBackend(),
          now: () => now,
        );
        final controller = ScheduledPromptsController(
          profileId: 'preview',
          store: store,
          platform: _Platform(),
          now: () => now,
        );
        final target = ScheduledPrompt(
          id: 'morning',
          profileId: 'preview',
          profileFingerprint: 'preview-routing',
          prompt: 'Summarize overnight changes',
          projectPath: '/root/projects/launch',
          sessionId: 'conversation',
          agent: 'build',
          recurrence: scene == _Scene.onceEditor
              ? ScheduleRecurrence.once(now.add(const Duration(hours: 1)))
              : const ScheduleRecurrence.weekdays(),
          createdAt: now,
          nextRunAt: now.add(const Duration(hours: 1)),
        );
        if (scene == _Scene.populated || scene == _Scene.history) {
          await store.save(target);
        }
        if (scene == _Scene.history) {
          for (final (index, status, reason) in [
            (0, ScheduledPromptRunStatus.ran, null),
            (1, ScheduledPromptRunStatus.failed, 'needs_you'),
            (2, ScheduledPromptRunStatus.skipped, 'busy'),
          ]) {
            final time = now.subtract(Duration(hours: index + 1));
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
        await controller.load();
        final editor =
            scene == _Scene.weekdayEditor || scene == _Scene.onceEditor;
        final baseTheme = dark ? AppTheme.dark() : AppTheme.light();
        final firstViewRules = name == 'en-1.0';
        try {
          final measured = await measurePass(
            tester,
            name: name,
            home: (_) => editor
                ? _SheetHost(controller: controller, target: target)
                : ScheduledPromptsScreen(
                    controller: controller,
                    target: target,
                    serverName: 'Laptop',
                    onOpenRun: (_) async {},
                  ),
            theme: AppTheme.forLocale(baseTheme, locale),
            locale: locale,
            scale: scale,
            delegates: AppLocalizations.localizationsDelegates,
            locales: AppLocalizations.supportedLocales,
            firstViewRules: firstViewRules,
          );
          expect(
            measured.skipped,
            isFalse,
            reason: 'A real screen must be measured.',
          );
          final issues = findings(measured, firstViewRules: firstViewRules);
          expect(
            issues,
            isEmpty,
            reason:
                '${scene.name}: ${jsonEncode(issues.map((f) => f.toJson()).toList())}',
          );
          if (editor && firstViewRules) {
            expect(
              measured.mainActions,
              1,
              reason: 'Save must be visible in the first view.',
            );
          }
        } finally {
          controller.dispose();
        }
      }
    });
  }
}
