// Gallery (gate G4) for KitSenseAsk: an ask for a photo, file or voice note, at 412x915 and 1280x800, dark and
// light, at 2.0 text, and once right to left.
//
// Regenerate deliberately:
//   flutter test --update-goldens test/goldens/kit/kit_sense_ask_golden_test.dart
// and look at every changed image before committing it.
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';

import 'kit_gallery.dart';

Widget _pad(Widget child) =>
    Padding(padding: const EdgeInsets.symmetric(horizontal: 16), child: child);

KitSenseAsk _ask({int picked = 0, bool sending = false}) => KitSenseAsk(
  kind: KitSenseKind.photo,
  purpose: 'Show me the screen where the error appears.',
  actions: [
    KitSenseAction(label: 'Take photo', onPressed: () {}),
    KitSenseAction(label: 'Choose photo', onPressed: () {}),
  ],
  items: [
    for (var i = 1; i <= picked; i++)
      KitSenseItem(
        id: 'p$i',
        name: 'IMG_000$i.jpg',
        removeLabel: 'Remove IMG_000$i.jpg',
        detail: '2.1 MB',
      ),
  ],
  max: 2,
  countLabel: '$picked of 2',
  maxReachedLabel: 'That is the most it can take',
  sendLabel: 'Send photos',
  sendDisabledReason: 'Add a photo first',
  onSend: picked > 0 ? () {} : null,
  onRemove: (_) {},
  sending: sending,
);

final _states = <String, Widget Function()>{
  'default': () => _pad(_ask(picked: 1)),
  'disabled': () => _pad(_ask()),
  'working': () => _pad(_ask(picked: 2, sending: true)),
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
          name: kitGalleryName('kit_sense_ask_default', size, light: light),
          size: size,
          light: light,
          child: _states['default']!(),
        );
      });
    }

    testWidgets('disabled · 412x915 · $mode', (tester) async {
      await kitGalleryPart(
        tester,
        name: kitGalleryName('kit_sense_ask_disabled', _phone, light: light),
        size: _phone,
        light: light,
        child: _states['disabled']!(),
      );
    });

    testWidgets('working · 412x915 · $mode', (tester) async {
      await kitGalleryPart(
        tester,
        name: kitGalleryName('kit_sense_ask_working', _phone, light: light),
        size: _phone,
        light: light,
        child: _states['working']!(),
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
          'kit_sense_ask_default',
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
        'kit_sense_ask_default',
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
