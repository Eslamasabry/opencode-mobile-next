// Gallery (gate G4) for the receipt states of KitQueuedMessage
// (docs/design/command-receipts-contract.md): checking, uncertain, uncertain
// after a check, and storage full, at 412x915, dark and light.
//
// Deterministic (TEST-11): no item waits on `since`, so no KitSince timer
// runs.
//
// Regenerate deliberately:
//   flutter test --update-goldens test/goldens/kit/kit_queued_receipt_golden_test.dart
// and look at every changed image before committing it.
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/ui/kit/chat/kit_queued_message.dart';
import 'package:opencode_mobile/ui/kit/kit_buttons.dart';
import 'package:opencode_mobile/ui/kit/kit_menu.dart';
import 'package:opencode_mobile/ui/kit/kit_technical_value.dart';
import 'package:opencode_mobile/ui/kit/kit_text.dart';

import 'kit_gallery.dart';

void _noop() {}

const _menu = [
  KitMenuItem(label: 'Try again', onSelected: _noop),
  KitMenuItem(label: 'Edit draft', onSelected: _noop),
  KitMenuItem(label: 'Discard draft', onSelected: _noop, destructive: true),
];

const _details = [
  KitTechnicalValue('Sent', '2026-10-02T14:00:12.000'),
  KitTechnicalValue('Command ID', 'queue:queued-1759413612000000'),
  KitTechnicalValue('Receipt ID', 'msg_0a1b2c3d4e5fAbCdEfGhIjKlMn'),
];

KitQueuedItem _item(
  KitQueuedState state, {
  DateTime? checkedAt,
  bool details = false,
}) => KitQueuedItem(
  id: 1,
  text: 'Use SQLite for the cache',
  state: state,
  checkedAt: checkedAt,
  details: details ? _details : const [],
  detailNotes: details
      ? const ['The connection ended before we could confirm receipt.']
      : const [],
  menu: _menu,
);

Widget _scene(KitQueuedMessage bubble) => Padding(
  padding: const EdgeInsets.symmetric(horizontal: 16),
  child: Column(
    crossAxisAlignment: CrossAxisAlignment.stretch,
    mainAxisSize: MainAxisSize.min,
    children: [
      const KitText(
        'I added the cache interface and a first in-memory version. '
        'Tell me which store you want behind it.',
      ),
      const SizedBox(height: 16),
      bubble,
      const SizedBox(height: 96),
    ],
  ),
);

const _check = KitAction(label: 'Try again', onPressed: _noop);

final _states = <String, Widget>{
  'checking': _scene(
    KitQueuedMessage(
      items: [_item(KitQueuedState.checking, details: true)],
      action: const KitAction(
        label: 'Try again',
        onPressed: null,
        working: true,
      ),
    ),
  ),
  'uncertain': _scene(
    KitQueuedMessage(
      items: [_item(KitQueuedState.uncertain, details: true)],
      action: _check,
    ),
  ),
  'uncertain_checked': _scene(
    KitQueuedMessage(
      items: [
        _item(
          KitQueuedState.uncertain,
          checkedAt: DateTime(2026, 10, 2, 14, 2),
          details: true,
        ),
      ],
      action: _check,
    ),
  ),
  'storage_full': _scene(
    KitQueuedMessage(items: [_item(KitQueuedState.storageFull)]),
  ),
};

const _phone = Size(412, 915);

void main() {
  setUpAll(loadKitGalleryFonts);

  for (final light in [false, true]) {
    final mode = light ? 'light' : 'dark';
    for (final MapEntry(key: state, value: child) in _states.entries) {
      testWidgets('receipt $state · 412x915 · $mode', (tester) async {
        await kitGalleryPart(
          tester,
          name: kitGalleryName(
            'kit_queued_receipt_$state',
            _phone,
            light: light,
          ),
          size: _phone,
          light: light,
          child: child,
        );
      });
    }
  }
}
