// Gallery (gate G4) for KitAgentCard: the frame for an agent's card, at 412x915 and 1280x800, dark and
// light, at 2.0 text, and once right to left.
//
// Regenerate deliberately:
//   flutter test --update-goldens test/goldens/kit/kit_agent_card_golden_test.dart
// and look at every changed image before committing it.
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';

import 'kit_gallery.dart';

Widget _pad(Widget child) =>
    Padding(padding: const EdgeInsets.symmetric(horizontal: 16), child: child);

KitAgentCard _card({
  KitAgentCardMode mode = KitAgentCardMode.full,
  bool ask = false,
  bool undo = false,
}) => KitAgentCard(
  eyebrow: ask ? 'Claude Code asks' : 'Claude Code reports',
  title: ask ? 'Which branch should I deploy?' : 'The tests finished',
  body: const [
    KitKeyValue(
      rows: [
        KitKeyValueRow(label: 'Duration', value: '4 min 12 s'),
        KitKeyValueRow(label: 'Files changed', value: '7'),
      ],
    ),
  ],
  ask: ask ? KitButton.secondary(label: 'Deploy main', onPressed: () {}) : null,
  mode: mode,
  receiptLabel: 'Sent: main',
  onUndo: undo ? () {} : null,
  expandLabel: 'Show the card',
  passedOverLabel: 'Not answered',
  unreadableLabel: 'The card could not be shown',
  detailsLabel: 'Details',
  details: 'unknown key "x" at nodes[2]',
);

final _states = <String, Widget Function()>{
  'default': () => _pad(_card(ask: true)),
  'report': () => _pad(_card()),
  'answered': () => _pad(_card(mode: KitAgentCardMode.receipt, undo: true)),
  'disabled': () => _pad(_card(mode: KitAgentCardMode.passedOver)),
  'unreadable': () => _pad(_card(mode: KitAgentCardMode.unreadable)),
};

const _phone = Size(412, 915);

void main() {
  setUpAll(loadKitGalleryFonts);

  for (final light in [false, true]) {
    final mode = light ? 'light' : 'dark';
    for (final size in kitGalleryScaledSizes) {
      testWidgets('default · ${kitGallerySize(size)} · $mode', (tester) async {
        await kitGalleryPart(
          tester,
          name: kitGalleryName('kit_agent_card_default', size, light: light),
          size: size,
          light: light,
          child: _states['default']!(),
        );
      });
    }

    testWidgets('report · 412x915 · $mode', (tester) async {
      await kitGalleryPart(
        tester,
        name: kitGalleryName('kit_agent_card_report', _phone, light: light),
        size: _phone,
        light: light,
        child: _states['report']!(),
      );
    });

    testWidgets('answered · 412x915 · $mode', (tester) async {
      await kitGalleryPart(
        tester,
        name: kitGalleryName('kit_agent_card_answered', _phone, light: light),
        size: _phone,
        light: light,
        child: _states['answered']!(),
      );
    });

    testWidgets('disabled · 412x915 · $mode', (tester) async {
      await kitGalleryPart(
        tester,
        name: kitGalleryName('kit_agent_card_disabled', _phone, light: light),
        size: _phone,
        light: light,
        child: _states['disabled']!(),
      );
    });

    testWidgets('unreadable · 412x915 · $mode', (tester) async {
      await kitGalleryPart(
        tester,
        name: kitGalleryName('kit_agent_card_unreadable', _phone, light: light),
        size: _phone,
        light: light,
        child: _states['unreadable']!(),
      );
    });
  }

  for (final size in kitGalleryScaledSizes) {
    testWidgets('default · 2.0 text · ${kitGallerySize(size)} · dark', (
      tester,
    ) async {
      await kitGalleryPart(
        tester,
        name: kitGalleryName(
          'kit_agent_card_default',
          size,
          light: false,
          text2: true,
        ),
        size: size,
        light: false,
        textScale: 2,
        child: _states['default']!(),
      );
    });
  }

  testWidgets('default · right to left · dark', (tester) async {
    await kitGalleryPart(
      tester,
      name: kitGalleryName(
        'kit_agent_card_default',
        _phone,
        light: false,
        ar: true,
      ),
      size: _phone,
      light: false,
      locale: const Locale('ar'),
      child: _states['default']!(),
    );
  });
}
