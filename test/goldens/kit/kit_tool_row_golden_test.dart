// Gallery (gate G4) for KitToolRow (docs/ux-system/kit-api/KitToolRow.md):
// every listed state at 412x915 in dark and light, each over a
// transcript-like ground with a line of the reply above it; the default
// (done, closed, with a path) at 1280x800 and at 2.0 text. Phone and one
// wide size only, English only (owner decision 2026-09-27: no Arabic or RTL
// shots).
//
// Regenerate deliberately:
//   flutter test --update-goldens test/goldens/kit/kit_tool_row_golden_test.dart
// and look at every changed image before committing it.
import 'package:clock/clock.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/ui/kit/chat/kit_markdown.dart';
import 'package:opencode_mobile/ui/kit/chat/kit_tool_row.dart';
import 'package:opencode_mobile/ui/kit/kit_code_block.dart';
import 'package:opencode_mobile/ui/kit/kit_diff_view.dart';
import 'package:opencode_mobile/ui/kit/kit_text.dart';

import 'kit_gallery.dart';

/// A reply's prose, the steps under it, and the reply's next line.
Widget _scene(List<Widget> rows) => Padding(
  padding: const EdgeInsetsDirectional.symmetric(horizontal: 16),
  child: Column(
    crossAxisAlignment: CrossAxisAlignment.stretch,
    mainAxisSize: MainAxisSize.min,
    children: [
      const KitText(
        'I will look at how checkout settles, then fix the flaky test.',
      ),
      const SizedBox(height: 8),
      ...rows,
      const SizedBox(height: 8),
      const KitText('The test passes now; the change is one line.'),
    ],
  ),
);

const _read = KitToolRow(
  kind: KitToolKind.read,
  title: 'Read',
  path: 'test/checkout/checkout_test.dart',
  status: KitToolStatus.done,
  duration: Duration(seconds: 2),
);

final _output = [
  for (var i = 1; i <= 30; i++)
    i == 30 ? 'All tests passed!' : '00:0${i ~/ 10} +$i: checkout step $i',
].join('\n');

final _states = <String, List<Widget> Function()>{
  'running': () => [
    _read,
    const KitToolRow(
      kind: KitToolKind.shell,
      title: 'Running flutter test',
      detail: 'test/checkout_test.dart',
      status: KitToolStatus.running,
    ),
  ],
  'waiting_for_you': () => [
    _read,
    const KitToolRow(
      kind: KitToolKind.shell,
      title: 'Run flutter test',
      detail: 'Needs your permission',
      status: KitToolStatus.waitingForYou,
    ),
  ],
  'done': () => [
    _read,
    const KitToolRow(
      kind: KitToolKind.search,
      title: 'Searched for applyCoupon',
      detail: '3 matches',
      status: KitToolStatus.done,
      duration: Duration(seconds: 1),
    ),
  ],
  'done_open': () => [
    _read,
    KitToolRow(
      kind: KitToolKind.shell,
      title: 'Ran flutter test',
      detail: 'Exit code 0 · passed',
      status: KitToolStatus.done,
      duration: const Duration(seconds: 42),
      expanded: true,
      note: const KitMarkdown(
        'Running the checkout suite to confirm the fix.',
        role: KitTextRole.secondary,
      ),
      body: [KitCodeBlock(text: _output, kind: KitCodeKind.output)],
    ),
  ],
  'edit_open': () => [
    KitToolRow(
      kind: KitToolKind.edit,
      title: 'Edited',
      path: 'test/checkout/checkout_test.dart',
      added: 4,
      removed: 1,
      status: KitToolStatus.done,
      duration: const Duration(seconds: 1),
      expanded: true,
      body: [
        KitDiffView(
          files: [
            KitDiffFile.fromTexts(
              'test/checkout/checkout_test.dart',
              before: [
                'await tester.pump();',
                "expect(find.text(r'\$42.00'), findsOneWidget);",
              ].join('\n'),
              after: [
                'await tester.pumpUntil(',
                '  () => bloc.state is CheckoutSettled,',
                '  timeout: const Duration(seconds: 5),',
                ');',
                "expect(find.text(r'\$42.00'), findsOneWidget);",
              ].join('\n'),
            ),
          ],
        ),
      ],
    ),
  ],
  'failed': () => [
    _read,
    const KitToolRow(
      kind: KitToolKind.shell,
      title: 'Ran flutter test',
      detail: 'Exit code 1 · 2 tests failed',
      status: KitToolStatus.failed,
      duration: Duration(seconds: 38),
    ),
  ],
  'failed_retry': () => [
    _read,
    KitToolRow(
      kind: KitToolKind.shell,
      title: 'Ran flutter test',
      detail: 'Exit code 1 · 2 tests failed',
      status: KitToolStatus.failed,
      duration: const Duration(seconds: 38),
      onRetry: () {},
    ),
  ],
  'not_run': () => [
    _read,
    const KitToolRow(
      kind: KitToolKind.edit,
      title: 'Edit',
      path: 'lib/checkout/checkout_bloc.dart',
      status: KitToolStatus.notRun,
    ),
  ],
  'agent': () => [
    KitToolRow.agent(
      title: 'Delegated to explore',
      status: KitToolStatus.running,
      task: 'Find where checkout totals are computed and what awaits them',
      startedAt: clock.now().subtract(const Duration(minutes: 3)),
      onOpen: () {},
    ),
  ],
  'agent_done': () => [
    KitToolRow.agent(
      title: 'Delegated to explore',
      status: KitToolStatus.done,
      task: 'Find where checkout totals are computed and what awaits them',
      onOpen: () {},
    ),
  ],
};

List<Widget> _default() => const [
  _read,
  KitToolRow(
    kind: KitToolKind.list,
    title: 'Listed',
    path: 'lib/checkout/',
    status: KitToolStatus.done,
  ),
];

void main() {
  setUpAll(loadKitGalleryFonts);

  for (final light in [false, true]) {
    final mode = light ? 'light' : 'dark';

    for (final entry in _states.entries) {
      testWidgets('kit_tool_row ${entry.key} · $mode', (tester) async {
        await kitGalleryPart(
          tester,
          name: kitGalleryName(
            'kit_tool_row_${entry.key}',
            const Size(412, 915),
            light: light,
          ),
          size: const Size(412, 915),
          light: light,
          child: _scene(entry.value()),
        );
      });
    }

    testWidgets('kit_tool_row default · 1280x800 · $mode', (tester) async {
      await kitGalleryPart(
        tester,
        name: kitGalleryName(
          'kit_tool_row_default',
          const Size(1280, 800),
          light: light,
        ),
        size: const Size(1280, 800),
        light: light,
        child: _scene(_default()),
      );
    });

    for (final size in kitGalleryScaledSizes) {
      final at = kitGallerySize(size);
      testWidgets('kit_tool_row default · 2.0 text · $at · $mode', (
        tester,
      ) async {
        await kitGalleryPart(
          tester,
          name: kitGalleryName(
            'kit_tool_row_default',
            size,
            light: light,
            text2: true,
          ),
          size: size,
          light: light,
          textScale: 2,
          child: _scene(_default()),
        );
      });
    }
  }
}
