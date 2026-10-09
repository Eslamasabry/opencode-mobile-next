// The turn the step-timeline gallery and the before/after captures draw: a
// thought, a read, a search, a thought, a file write, a command and, while
// the work runs, an edit in progress. Shared so every picture shows the same
// work. Not a test file.
import 'package:flutter/material.dart';
import 'package:opencode_mobile/ui/kit/chat/kit_markdown.dart';
import 'package:opencode_mobile/ui/kit/chat/kit_message.dart';
import 'package:opencode_mobile/ui/kit/chat/kit_step_timeline.dart';
import 'package:opencode_mobile/ui/kit/chat/kit_tool_row.dart';
import 'package:opencode_mobile/ui/kit/chat/kit_work_line.dart';
import 'package:opencode_mobile/ui/kit/kit_code_block.dart';
import 'package:opencode_mobile/ui/kit/kit_status_mark.dart';
import 'package:opencode_mobile/ui/kit/kit_text.dart';

/// English first, Arabic second.
typedef StepWords = String Function(String en, String ar);

const stepTimelineHomeScreen = '''import 'package:flutter/material.dart';

class HomeScreen extends StatelessWidget {
  const HomeScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Shopfront')),
      body: const ProductGrid(),
    );
  }
}
''';

/// What the work did, as the work line counts it.
KitWorkCounts stepTimelineCounts({required bool running}) => KitWorkCounts(
  read: 1,
  searched: 1,
  edited: running ? 2 : 1,
  ran: 1,
  steps: 2,
);

/// The steps of the turn, as the transcript builds them. [preview] draws the
/// file write with its preview card (the timeline's own addition; the plain
/// list has none).
List<Widget> stepTimelineSteps(
  StepWords t, {
  required bool running,
  bool preview = true,
}) => [
  KitMessage.thought(
    heading: t('Looking at how the app starts', 'أنظر كيف يبدأ التطبيق'),
    body: KitMarkdown(
      t(
        'The router is in lib/ui/router.dart; the home route is missing.',
        'المسار الرئيسي غير موجود في الموجّه.',
      ),
      role: KitTextRole.secondary,
    ),
  ),
  KitToolRow(
    kind: KitToolKind.read,
    title: t('Read', 'قراءة'),
    path: 'lib/ui/router.dart',
    status: KitToolStatus.done,
    duration: const Duration(seconds: 1),
  ),
  KitToolRow(
    kind: KitToolKind.search,
    title: t('Search', 'بحث'),
    detail: t('AppRouter · 6 matches', 'AppRouter · 6 نتائج'),
    status: KitToolStatus.done,
  ),
  KitMessage.thought(
    heading: t(
      'The home route needs its own screen and a test',
      'يحتاج المسار الرئيسي إلى شاشة واختبار',
    ),
    body: KitMarkdown(
      t(
        'I will add the screen first, then wire the route.',
        'سأضيف الشاشة أولاً ثم أربط المسار.',
      ),
      role: KitTextRole.secondary,
    ),
  ),
  KitToolRow(
    kind: KitToolKind.edit,
    title: t('Write', 'كتابة'),
    path: 'lib/ui/home_screen.dart',
    added: 12,
    status: KitToolStatus.done,
    duration: const Duration(seconds: 1),
    preview: preview ? KitStepPreview.fromText(stepTimelineHomeScreen) : null,
    body: [
      KitCodeBlock(
        text: stepTimelineHomeScreen,
        kind: KitCodeKind.code,
        fileName: 'home_screen.dart',
      ),
    ],
  ),
  KitToolRow(
    kind: KitToolKind.shell,
    title: t('Run', 'تشغيل'),
    path: 'flutter test test/home_test.dart',
    pathCut: KitMonoCut.end,
    status: KitToolStatus.done,
    duration: const Duration(seconds: 38),
  ),
  if (running)
    KitToolRow(
      kind: KitToolKind.edit,
      title: t('Edit', 'تعديل'),
      path: 'lib/ui/router.dart',
      status: KitToolStatus.running,
    ),
];

/// The work as one timeline, in the scene [scene]: `collapsed`, `expanded`,
/// `running` (folded, the live words) or `running-open`.
Widget stepTimelineWork(StepWords t, String scene) => Builder(
  builder: (context) {
    final running = scene.startsWith('running');
    final open = scene == 'expanded' || scene == 'running-open';
    final counts = stepTimelineCounts(running: running);
    final summary = KitWorkLine.summaryOf(context, counts);
    final live = running && !open;
    return KitStepTimeline(
      label: live
          ? t('Editing lib/ui/router.dart', 'تعديل lib/ui/router.dart')
          : summary,
      icon: KitStepTimeline.iconFor(
        read: counts.read,
        searched: counts.searched,
        edited: counts.edited,
        ran: counts.ran,
      ),
      mark: live ? const KitStatusMark(state: KitMarkState.working) : null,
      expanded: open,
      onPressed: () {},
      steps: stepTimelineSteps(t, running: running),
    );
  },
);
