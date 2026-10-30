// Gallery for slice S3 of the style cleanup (docs/audits/
// style-coherence-2026-10-21.md §6): the controls that now ask the style
// resolver, in each style, light and dark. The switch (its track a
// `toggle`, its thumb a round `control`), the buttons (the primary's words
// through hook H3, a disabled one, the tertiary underline from the
// `divider` role), the status tags (`control`), an icon button, the
// composer's send, mic and Stop circles (`control`, the accent and Stop's
// own fill kept) and the floating "+" (`floating`). Solid here is today's
// look; every other gallery keeps it.
//
// Regenerate deliberately:
//   flutter test --update-goldens test/goldens/kit/kit_controls_styles_golden_test.dart
// and look at every changed image before committing it.
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/ui/app_iconography.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/kit/style/kit_style_theme.dart';

import 'kit_gallery.dart';

void _noop() {}

final _idle = TextEditingController(text: 'Also run the signing tests');
final _busy = TextEditingController();
final _empty = TextEditingController();
final _focus = FocusNode();

class _Controls extends StatelessWidget {
  const _Controls();

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final gap = SizedBox(height: tokens.space4);
    return Padding(
      padding: EdgeInsets.symmetric(horizontal: tokens.gutter),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          KitRowGroup(
            label: 'Switches',
            margin: EdgeInsets.zero,
            children: [
              KitSwitchRow(
                title: 'Notify when done',
                value: false,
                onChanged: (_) {},
              ),
              KitSwitchRow(
                title: 'Keep the screen on',
                value: true,
                onChanged: (_) {},
              ),
              const KitSwitchRow(
                title: 'Run in the background',
                value: true,
                onChanged: null,
                disabledReason: 'Turn on notifications first',
              ),
              KitSwitchRow(
                title: 'Morning summary',
                supporting: 'Weekdays at 9:00',
                value: true,
                onChanged: (_) {},
                onOpen: _noop,
                openLabel: 'Edit schedule',
              ),
            ],
          ),
          gap,
          Row(
            children: [
              Expanded(
                child: KitButton.secondary(label: 'Not now', onPressed: () {}),
              ),
              SizedBox(width: tokens.space3),
              Expanded(
                child: KitButton.primary(label: 'Send', onPressed: () {}),
              ),
            ],
          ),
          SizedBox(height: tokens.space3),
          Row(
            children: [
              const Expanded(
                child: KitButton.primary(label: 'Start', onPressed: null),
              ),
              SizedBox(width: tokens.space3),
              KitButton.tertiary(label: 'Just show me', onPressed: () {}),
            ],
          ),
          gap,
          Wrap(
            spacing: tokens.space2,
            runSpacing: tokens.space2,
            crossAxisAlignment: WrapCrossAlignment.center,
            children: [
              for (final (label, tone) in const [
                ('Needs you', KitStatusTagTone.needsYou),
                ('Running', KitStatusTagTone.running),
                ('Done', KitStatusTagTone.done),
                ('Failed', KitStatusTagTone.failed),
              ])
                KitStatusTag(label: label, tone: tone),
              KitIconButton(
                icon: AppIconography.copy,
                tooltip: 'Copy',
                onPressed: () {},
              ),
            ],
          ),
          gap,
          KitComposer(
            controller: _idle,
            focusNode: _focus,
            hint: 'Ask OpenCode…',
            onSend: _noop,
            onTools: _noop,
            onVoice: _noop,
          ),
          SizedBox(height: tokens.space3),
          KitComposer(
            controller: _busy,
            focusNode: FocusNode(),
            hint: 'Steer while it works…',
            onSend: _noop,
            busy: true,
            onTools: _noop,
            onVoice: _noop,
          ),
          SizedBox(height: tokens.space3),
          // The mic beside nothing to send: the quiet disc (not the accent).
          KitComposer(
            controller: _empty,
            focusNode: FocusNode(),
            hint: 'Ask OpenCode…',
            onSend: _noop,
            quiet: true,
            onTools: _noop,
            onVoice: _noop,
          ),
          SizedBox(
            height: KitFloatingAction.size + tokens.space6,
            child: KitFloatingAction(
              label: 'New conversation',
              onPressed: _noop,
              child: const SizedBox.expand(),
            ),
          ),
        ],
      ),
    );
  }
}

void main() {
  setUpAll(loadKitGalleryFonts);

  for (final style in KitSurfaceStyle.values) {
    for (final light in [false, true]) {
      testWidgets('controls · ${style.name} · '
          '${light ? 'light' : 'dark'}', (tester) async {
        await kitGalleryPart(
          tester,
          name: kitGalleryName(
            'kit_controls_${style.name}',
            const Size(412, 915),
            light: light,
          ),
          size: const Size(412, 915),
          light: light,
          child: KitEffectsScope(
            effects: KitEffects(surfaceStyle: style),
            // As main.dart: the style's own roles (hook H6).
            child: kitStyleTheme(child: const _Controls()),
          ),
        );
      });
    }
  }
}
