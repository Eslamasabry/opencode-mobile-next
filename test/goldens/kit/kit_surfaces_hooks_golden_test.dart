// Gallery for slice S9 of the style cleanup (the last shared hooks): the
// parts that gained a hook, in each surface style, light and dark, on the
// style's own ground (hook H6) over the same accent tint as
// kit_surfaces_golden_test.dart.
//
// - your prompt and the queued bubble, from the one prompt bubble
//   decoration (kitPromptBubbleDecoration);
// - a lazy row group with its label (KitSliverRowGroup: flush-start rows
//   under a panel set on the page) and a section label (the section rule
//   right above the words, where a style draws one);
// - the one-time code as the style's field;
// - the ask field with the field halo the start box has (kitFieldHalo);
// - a secondary and a link (the link underline token). The disabled
//   answer shows in the controls gallery: on this accent wash Glass's
//   disabled words (text3 on its clear control) measure 4.1:1, which gate
//   G5 fails; that is the Glass spec's to answer through `disabled`;
// - the floating "+" (it sinks while held under Brutal; at rest here).
//
// Every hook is at its default in the real specs, so this is what each
// style draws today; the specs' agents move it from their own files.
//
// Regenerate deliberately:
//   flutter test --update-goldens test/goldens/kit/kit_surfaces_hooks_golden_test.dart
// and look at every changed image before committing it.
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/ui/app_iconography.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/kit/style/kit_style_theme.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'kit_gallery.dart';

const _phone = Size(412, 915);

void _noop() {}

class _Hooks extends StatefulWidget {
  const _Hooks();

  @override
  State<_Hooks> createState() => _HooksState();
}

class _HooksState extends State<_Hooks> {
  final _ask = TextEditingController();
  final _focus = FocusNode();

  @override
  void dispose() {
    _ask.dispose();
    _focus.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
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
          children: [
            SizedBox(height: tokens.space3),
            Padding(
              padding: EdgeInsets.symmetric(horizontal: tokens.gutter),
              child: const KitMessage.prompt(
                body: KitMarkdown(
                  'Fix the flaky checkout test.',
                  selectable: false,
                ),
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
              ),
            ),
            SizedBox(
              height: 200,
              child: CustomScrollView(
                physics: const NeverScrollableScrollPhysics(),
                slivers: [
                  KitSliverRowGroup(
                    label: 'Recent',
                    gapBefore: tokens.space4,
                    children: [
                      KitRow(
                        title: 'Fix the checkout test',
                        leading: KitRow.icon(context, AppIconography.chat),
                        onTap: _noop,
                      ),
                      KitRow(
                        title: 'Cache the catalogue',
                        leading: KitRow.icon(context, AppIconography.chat),
                        onTap: _noop,
                      ),
                    ],
                  ),
                ],
              ),
            ),
            const KitSectionLabel('Pair this phone', gapBefore: 0),
            Padding(
              padding: EdgeInsets.symmetric(horizontal: tokens.gutter),
              child: const KitOneTimeCode(code: 'VPFI-KC2ZK'),
            ),
            SizedBox(height: tokens.space4),
            Padding(
              padding: EdgeInsets.symmetric(horizontal: tokens.gutter),
              child: KitAskField(
                controller: _ask,
                focusNode: _focus,
                hint: 'Ask for a change…',
                sendLabel: 'Send',
                onSend: _noop,
                onVoice: _noop,
                voiceLabel: 'Speak',
              ),
            ),
            SizedBox(height: tokens.space4),
            Padding(
              padding: EdgeInsets.symmetric(horizontal: tokens.gutter),
              child: Row(
                children: [
                  Expanded(
                    child: KitButton.secondary(
                      label: 'Not now',
                      onPressed: _noop,
                    ),
                  ),
                  SizedBox(width: tokens.space3),
                  KitButton.tertiary(label: 'Just show me', onPressed: _noop),
                ],
              ),
            ),
            SizedBox(
              height: KitFloatingAction.size + tokens.space6,
              child: const KitFloatingAction(
                label: 'New conversation',
                onPressed: _noop,
                child: SizedBox.expand(),
              ),
            ),
          ],
        ),
      ],
    );
  }
}

void main() {
  setUpAll(loadKitGalleryFonts);
  setUp(() => SharedPreferences.setMockInitialValues({}));

  for (final style in KitSurfaceStyle.values) {
    for (final light in [false, true]) {
      testWidgets('surfaces hooks · ${style.name} · '
          '${light ? 'light' : 'dark'}', (tester) async {
        await kitGalleryPart(
          tester,
          name: kitGalleryName(
            'kit_surfaces_hooks_${style.name}',
            _phone,
            light: light,
          ),
          size: _phone,
          light: light,
          child: KitEffectsScope(
            effects: KitEffects(surfaceStyle: style),
            // As main.dart: the style's own roles (hook H6).
            child: kitStyleTheme(child: const _Hooks()),
          ),
        );
      });
    }
  }
}
