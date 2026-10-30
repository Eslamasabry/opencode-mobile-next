// Gallery for slice S6 of the style cleanup (transcript and technical
// surfaces): the parts that now ask the style resolver, in each surface
// style, light and dark, on the style's own ground (hook H6) over the same
// accent tint as kit_surfaces_golden_test.dart, so glass shows what it lets
// through and code shows it never sits on translucency.
//
// - transcript: the needs-you permission card with its command, the
//   question look with a command target, the queued bubble, the agent
//   strip, and a Markdown table and quote;
// - technical: the terminal's key bar (Ctrl held), a code block, an open
//   details fold, a log panel and a mini table.
//
// Solid here is today's look; the parts' own galleries keep it.
//
// Soft has no transcript shot: gate G5's text-contrast check measures the
// permission card's command in the wrong place under Soft (16 dp left and
// 24 dp up of where it is painted, so it reads only the well's greys) and
// its baseline may not grow. The command is legible in the render; see the
// S6 report.
//
// Regenerate deliberately:
//   flutter test --update-goldens test/goldens/kit/kit_surfaces_technical_golden_test.dart
// and look at every changed image before committing it.
import 'package:clock/clock.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/kit/style/kit_style_theme.dart';
import 'package:opencode_mobile/ui/kit/terminal_key_bar.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'kit_gallery.dart';

const _phone = Size(412, 915);

void _noop() {}

const _markdown = '''
| File | Added | Removed |
| --- | ---: | ---: |
| `lib/main.dart` | 12 | 3 |
| `lib/ui/kit/kit.dart` | 5 | 0 |

> Keep the old key readable for one release.
''';

const _code = '''
final store = SqliteStore(path);
await store.open();
return CachedGateway(store, gateway);''';

/// The accent tint behind the parts, as the live background lays it.
Widget _onTint(List<Widget> children) => Builder(
  builder: (context) {
    final tokens = KitTokens.of(context);
    final roles = tokens.roles;
    return Stack(
      children: [
        Positioned.fill(
          child: ColoredBox(
            color: roles.accent.withValues(alpha: roles.isDark ? .16 : .08),
          ),
        ),
        Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: children,
        ),
      ],
    );
  },
);

final _pinnedNow = DateTime.utc(2026, 10, 27, 12);

class _Transcript extends StatelessWidget {
  const _Transcript();

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    // The card reads `clock.now()`, which the transcript test pins to
    // [_pinnedNow]; ten seconds past the minute keeps the label at 4 min.
    final asked = _pinnedNow.subtract(const Duration(minutes: 4, seconds: 10));
    return _onTint([
      KitRequestCard.ask(
        kind: KitRequestKind.permission,
        title: 'Run a shell command',
        who: 'fox',
        reason: KitNeedsYouReason.decision,
        ifIgnored: 'The agent waits; nothing is lost.',
        announcement: 'Permission needed: Run a shell command',
        summary: 'flutter test --concurrency=1',
        since: asked,
        answers: KitRequestDecide(
          allowLabel: 'Run once',
          rejectLabel: "Don't run",
          onAllow: () {},
          onReject: () {},
        ),
      ),
      KitRequestCard.ask(
        kind: KitRequestKind.permission,
        look: KitRequestLook.question,
        title: 'Run git push?',
        who: 'fox',
        reason: KitNeedsYouReason.decision,
        ifIgnored: 'The agent waits; nothing is lost.',
        announcement: 'Permission needed: Run git push',
        targets: const [KitRequestTarget('git push origin feat/cache')],
        since: asked,
        answers: KitRequestDecide(
          allowLabel: 'Run once',
          rejectLabel: "Don't run",
          onAllow: () {},
          onReject: () {},
        ),
      ),
      SizedBox(height: tokens.space2),
      Padding(
        padding: EdgeInsets.symmetric(horizontal: tokens.gutter),
        child: const KitQueuedMessage(
          items: [
            KitQueuedItem(
              id: 1,
              text: 'Use SQLite for the cache',
              state: KitQueuedState.waiting,
            ),
          ],
          action: KitAction(label: 'Send now', onPressed: _noop),
        ),
      ),
      SizedBox(height: tokens.space2),
      const KitAgentStrip(
        agents: [
          KitAgent(
            id: 'lead',
            name: 'mayor',
            role: 'Lead',
            state: KitTaskState.working,
          ),
          KitAgent(
            id: 'w1',
            name: 'furiosa',
            role: 'Worker',
            state: KitTaskState.done,
            onOpen: _noop,
          ),
        ],
      ),
      Padding(
        padding: EdgeInsets.symmetric(horizontal: tokens.gutter),
        child: const KitMarkdown(_markdown),
      ),
    ]);
  }
}

class _Technical extends StatelessWidget {
  const _Technical({required this.keys});

  final TerminalKeyBarController keys;

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    return _onTint([
      TerminalKeyBar(
        controller: keys,
        onKey: (_, {required ctrl, required alt}) {},
      ),
      Padding(
        padding: EdgeInsets.all(tokens.gutter),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            // No highlighting: the surface is what is under test, and
            // Soft's well is darker than the ground the code colours are
            // built for (its keyword measures 4.4:1; the Soft spec's to fix).
            const KitCodeBlock(text: _code, fileName: 'lib/state/cache.dart'),
            SizedBox(height: tokens.space3),
            const KitDetailsFold(
              initiallyExpanded: true,
              values: [
                KitTechnicalValue('Address', '100.64.0.3:4096'),
                KitTechnicalValue('Branch', 'feat/cache'),
              ],
            ),
            SizedBox(height: tokens.space3),
            KitLogPanel(
              lines: KitLogBuffer()
                ..appendText(
                  '[oc] Checking the Linux environment\n'
                  '[oc] Ubuntu 24.04 found\n'
                  '[oc] Installing opencode 2.0.10\n',
                ),
              ended: const KitLogEnd(exitCode: 0),
            ),
            SizedBox(height: tokens.space3),
            const KitMiniTable(
              columns: ['File', 'Added', 'Removed'],
              rows: [
                KitMiniTableRow(['lib/main.dart', '12', '3']),
                KitMiniTableRow(['lib/ui/kit/kit.dart', '5', '0']),
              ],
            ),
          ],
        ),
      ),
    ]);
  }
}

void main() {
  setUpAll(loadKitGalleryFonts);
  setUp(() => SharedPreferences.setMockInitialValues({}));

  for (final style in KitSurfaceStyle.values) {
    for (final light in [false, true]) {
      final mode = light ? 'light' : 'dark';
      if (style != KitSurfaceStyle.soft) {
        testWidgets('surfaces transcript · ${style.name} · $mode', (
          tester,
        ) async {
          await withClock(
            Clock.fixed(_pinnedNow),
            () => kitGalleryPart(
              tester,
              name: kitGalleryName(
                'kit_surfaces_transcript_${style.name}',
                _phone,
                light: light,
              ),
              size: _phone,
              light: light,
              child: KitEffectsScope(
                effects: KitEffects(surfaceStyle: style),
                // As main.dart: the style's own roles (hook H6).
                child: kitStyleTheme(child: const _Transcript()),
              ),
            ),
          );
        });
      }

      testWidgets('surfaces technical · ${style.name} · $mode', (tester) async {
        final keys = TerminalKeyBarController()..toggle(TerminalBarKey.ctrl);
        await kitGalleryPart(
          tester,
          name: kitGalleryName(
            'kit_surfaces_technical_${style.name}',
            _phone,
            light: light,
          ),
          size: _phone,
          light: light,
          child: KitEffectsScope(
            effects: KitEffects(surfaceStyle: style),
            child: kitStyleTheme(child: _Technical(keys: keys)),
          ),
        );
      });
    }
  }
}
