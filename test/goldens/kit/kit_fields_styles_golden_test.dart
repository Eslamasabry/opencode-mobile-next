// Gallery for slice S4 of the style cleanup (docs/audits/
// style-coherence-2026-10-21.md §6): the fields that now ask the style
// resolver (`KitSurfaceRole.field`), in each style, light and dark. A text
// field with a value, a multi-line one at rest (its edge reads at 3:1 in
// every style, Solid too), one with an error, a focused one (the style's
// focused answer and the accent ring outside it), the plain and the
// prominent search field (a pill, square under Paper's Sharp), the ask
// field with its round button, and the composer's model and attachment
// chips (`control`) and its suggestion panel (`content`). Solid here is
// today's look; every other gallery keeps it.
//
// Regenerate deliberately:
//   flutter test --update-goldens test/goldens/kit/kit_fields_styles_golden_test.dart
// and look at every changed image before committing it.
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/kit/style/kit_style_theme.dart';

import 'kit_gallery.dart';

void _noop() {}

class _Fields extends StatefulWidget {
  const _Fields();

  @override
  State<_Fields> createState() => _FieldsState();
}

class _FieldsState extends State<_Fields> {
  final _name = TextEditingController(text: 'Fix the basket after a restart');
  final _notes = TextEditingController();
  final _port = TextEditingController(text: '80800');
  final _focused = TextEditingController(text: 'shopfront');
  final _search = TextEditingController();
  final _settings = TextEditingController();
  final _ask = TextEditingController();
  final _askFocus = FocusNode();

  @override
  void dispose() {
    for (final c in [
      _name,
      _notes,
      _port,
      _focused,
      _search,
      _settings,
      _ask,
    ]) {
      c.dispose();
    }
    _askFocus.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final gap = SizedBox(height: tokens.space3);
    return Padding(
      padding: EdgeInsets.symmetric(horizontal: tokens.gutter),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          KitField(label: 'Name', controller: _name),
          gap,
          KitField(
            label: 'Anything to add?',
            controller: _notes,
            kind: KitFieldKind.multiline,
            hint: 'What you were doing when it went wrong',
            optional: true,
          ),
          gap,
          KitField(
            label: 'Port',
            controller: _port,
            kind: KitFieldKind.number,
            error: 'Use a port from 1 to 65535',
          ),
          gap,
          KitField(label: 'Project', controller: _focused, autofocus: true),
          gap,
          KitSearchField(
            label: 'Search conversations',
            controller: _search,
            onChanged: (_) {},
          ),
          gap,
          KitSearchField(
            label: 'Search settings',
            controller: _settings,
            onChanged: (_) {},
            prominent: true,
          ),
          gap,
          KitAskField(
            controller: _ask,
            focusNode: _askFocus,
            hint: 'Ask for a change…',
            sendLabel: 'Send',
            onSend: _noop,
            onVoice: _noop,
            voiceLabel: 'Speak',
          ),
          gap,
          Row(
            children: [
              Expanded(
                child: KitComposerChips.attachments(
                  items: const [
                    KitAttachment(
                      id: 'file',
                      label: 'checkout_test.dart',
                      kind: KitAttachmentKind.file,
                    ),
                  ],
                  onRemove: (_) {},
                ),
              ),
              KitComposerChips.model(label: 'Opus 5.5', onPressed: _noop),
            ],
          ),
          gap,
          KitComposerChips.suggestions(
            suggestions: const [
              KitSuggestion(
                id: 'review',
                label: '/review',
                kind: KitSuggestionKind.command,
                description: 'Review the changes on this branch',
              ),
              KitSuggestion(
                id: 'test',
                label: '/test',
                kind: KitSuggestionKind.command,
                description: 'Run the tests and fix what fails',
              ),
            ],
            onSelected: (_) {},
            onShowAll: _noop,
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
      testWidgets('fields · ${style.name} · '
          '${light ? 'light' : 'dark'}', (tester) async {
        await kitGalleryPart(
          tester,
          name: kitGalleryName(
            'kit_fields_${style.name}',
            const Size(412, 915),
            light: light,
          ),
          size: const Size(412, 915),
          light: light,
          child: KitEffectsScope(
            effects: KitEffects(surfaceStyle: style),
            // As main.dart: the style's own roles (hook H6).
            child: kitStyleTheme(child: const _Fields()),
          ),
        );
      });
    }
  }
}
