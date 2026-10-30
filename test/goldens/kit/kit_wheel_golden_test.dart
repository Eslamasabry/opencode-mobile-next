// Gallery (gate G4) for KitWheel (lib/ui/kit/kit_wheel.dart): the
// scheduling sheet's time wheel, its default at the phone and the wide size
// (also at text 2.0, where the wheel caps its own growth at 1.5), dark and
// light. It declares no other state (a disabled wheel is hidden instead).
//
// Regenerate deliberately:
//   TZ=UTC flutter test --update-goldens test/goldens/kit/kit_wheel_golden_test.dart
// and look at every changed image before committing it.
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/ui/kit/kit_wheel.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'kit_gallery.dart';

void _noop(Object? _) {}

Widget _wheel() => Padding(
  padding: const EdgeInsets.all(16),
  child: KitWheel(
    columns: [
      KitWheelColumn<int>(
        values: [for (var h = 1; h <= 12; h++) h],
        selected: 9,
        label: (h) => '$h',
        onChanged: _noop,
        semanticsLabel: 'Hour',
      ),
      KitWheelColumn<String>(
        values: const ['00', '15', '30', '45'],
        selected: '00',
        label: (m) => m,
        onChanged: _noop,
        semanticsLabel: 'Minute',
      ),
      KitWheelColumn<String>(
        values: const ['AM', 'PM'],
        selected: 'AM',
        label: (m) => m,
        onChanged: _noop,
        semanticsLabel: 'Morning or evening',
      ),
    ],
  ),
);

void main() {
  setUpAll(loadKitGalleryFonts);
  setUp(() => SharedPreferences.setMockInitialValues({}));

  for (final light in [false, true]) {
    final mode = light ? 'light' : 'dark';
    // kitGalleryScaledSizes is the phone and the wide size.
    for (final size in kitGalleryScaledSizes) {
      testWidgets('default · ${kitGallerySize(size)} · $mode', (tester) async {
        await kitGalleryPart(
          tester,
          name: kitGalleryName('kit_wheel_default', size, light: light),
          size: size,
          light: light,
          child: _wheel(),
        );
      });
      testWidgets('default · text 2.0 · ${kitGallerySize(size)} · $mode', (
        tester,
      ) async {
        await kitGalleryPart(
          tester,
          name: kitGalleryName(
            'kit_wheel_default',
            size,
            light: light,
            text2: true,
          ),
          size: size,
          light: light,
          textScale: 2,
          child: _wheel(),
        );
      });
    }
  }
}
