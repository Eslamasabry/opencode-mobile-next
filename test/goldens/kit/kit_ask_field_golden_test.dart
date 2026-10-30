// Gallery (gate G4) for KitAskField (the preview's "Ask for a change…"
// field): empty with the mic and typed with Send, disabled, working, at 412x915 and 1280x800,
// dark and light, and at 2.0 text; the sent note and the quiet look.
//
// Regenerate deliberately:
//   flutter test --update-goldens test/goldens/kit/kit_ask_field_golden_test.dart
// and look at every changed image before committing it.
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';

import 'kit_gallery.dart';

void _noop() {}

Widget _part({
  String text = '',
  String? note,
  bool prominent = true,
  bool enabled = true,
  KitAskFieldPhase phase = KitAskFieldPhase.ready,
}) => Padding(
  padding: const EdgeInsets.all(16),
  child: KitAskField(
    controller: TextEditingController(text: text),
    focusNode: FocusNode(),
    hint: 'Ask for a change…',
    sendLabel: 'Send to the conversation',
    voiceLabel: 'Speak your change',
    onSend: _noop,
    onVoice: _noop,
    note: note,
    prominent: prominent,
    enabled: enabled,
    phase: phase,
  ),
);

void main() {
  setUpAll(loadKitGalleryFonts);

  for (final light in [false, true]) {
    final mode = light ? 'light' : 'dark';
    for (final size in kitGalleryScaledSizes) {
      testWidgets('empty · ${kitGallerySize(size)} · $mode', (tester) async {
        await kitGalleryPart(
          tester,
          name: kitGalleryName('kit_ask_field_empty', size, light: light),
          size: size,
          light: light,
          child: _part(),
        );
      });
    }
    testWidgets('typed · 412x915 · $mode', (tester) async {
      await kitGalleryPart(
        tester,
        name: 'kit_ask_field_typed_$mode',
        size: kitGalleryScaledSizes.first,
        light: light,
        child: _part(text: 'Make the ball a little faster'),
      );
    });
    testWidgets('answered · 412x915 · $mode', (tester) async {
      await kitGalleryPart(
        tester,
        name: 'kit_ask_field_answered_$mode',
        size: kitGalleryScaledSizes.first,
        light: light,
        child: _part(note: 'Sent to the conversation'),
      );
    });
    testWidgets('disabled · 412x915 · $mode', (tester) async {
      await kitGalleryPart(
        tester,
        name: 'kit_ask_field_disabled_$mode',
        size: kitGalleryScaledSizes.first,
        light: light,
        child: _part(enabled: false),
      );
    });
    testWidgets('working · 412x915 · $mode', (tester) async {
      await kitGalleryPart(
        tester,
        name: 'kit_ask_field_working_$mode',
        size: kitGalleryScaledSizes.first,
        light: light,
        child: _part(
          text: 'Make the ball a little faster',
          phase: KitAskFieldPhase.working,
        ),
      );
    });
    testWidgets('quiet · 412x915 · $mode', (tester) async {
      await kitGalleryPart(
        tester,
        name: 'kit_ask_field_quiet_$mode',
        size: kitGalleryScaledSizes.first,
        light: light,
        child: _part(prominent: false),
      );
    });
  }

  for (final size in kitGalleryScaledSizes) {
    testWidgets('empty · 2.0 text · ${kitGallerySize(size)} · dark', (
      tester,
    ) async {
      await kitGalleryPart(
        tester,
        name: kitGalleryName(
          'kit_ask_field_empty',
          size,
          light: false,
          text2: true,
        ),
        size: size,
        light: false,
        textScale: 2,
        child: _part(),
      );
    });
  }
}
