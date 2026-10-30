// Many-item runs for the kit's list-like slots (docs/ux-system/kit-v2.md
// §8.6), pumped by the capacity shards like any scene. Each run fills one
// slot with 50 items (or, for a slot the part caps with an assert, exactly
// its `shows`), every label through KitSceneCopy.t so the stress fixtures
// reach them too, and names the widget each item draws as, so the gate can
// count how many reach the screen.
//
// test/kit/kit_capacity_test.dart reads every List/Iterable/Set field of
// every exported part from the source; each must have a `lists` entry in
// test/kit/kit_capacity.json, and an entry that says items are drawn needs
// a run here (or `via` another slot's run). Append a run when a part gains
// a list slot.
import 'package:flutter/material.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';

import 'kit_overflow_scenes.dart';

/// One slot filled with [count] items.
class KitListStress {
  const KitListStress(
    this.slot, {
    required this.item,
    required this.build,
    this.count = 50,
    this.host = KitOverflowHost.list,
  });

  /// `Part.field`, as the contract's `lists` names it.
  final String slot;

  /// The widget type each item is drawn as (private names allowed).
  final String item;
  final int count;
  final Widget Function(BuildContext context, KitSceneCopy c, int count) build;
  final KitOverflowHost host;

  String get part => slot.substring(0, slot.indexOf('.'));
  String get field => slot.substring(slot.indexOf('.') + 1);

  KitOverflowScene get scene => KitOverflowScene(
    [part],
    '$count items in $field',
    host: host,
    build: (context, c) => build(context, c, count),
  );
}

void _noop() {}

String _label(KitSceneCopy c, int i) =>
    c.t('Item number $i in the list', 'العنصر رقم $i في القائمة');

final kitListStress = <KitListStress>[
  KitListStress(
    'KitRowGroup.children',
    item: 'KitRow',
    build: (_, c, n) => KitRowGroup(
      label: c.t('Servers', 'الخوادم'),
      children: [
        for (var i = 1; i <= n; i++)
          KitRow(
            leading: const KitRowIcon(AppIconography.terminal),
            title: _label(c, i),
            onTap: _noop,
          ),
      ],
    ),
  ),
  KitListStress(
    'KitMenuPanel.items',
    item: '_KitMenuItemTile',
    build: (_, c, n) => KitMenuPanel(
      items: [
        for (var i = 1; i <= n; i++)
          KitMenuItem(label: _label(c, i), onSelected: _noop),
      ],
      onSelected: (_) {},
    ),
  ),
  KitListStress(
    'KitFilterChips.chips',
    item: 'KitFilterChip',
    build: (_, c, n) => KitFilterChips(
      chips: [
        for (var i = 1; i <= n; i++)
          KitFilterChip(
            label: _label(c, i),
            selected: i == 1,
            onPressed: _noop,
          ),
      ],
    ),
  ),
  KitListStress(
    'KitChipWrap.children',
    item: 'KitChip',
    build: (_, c, n) => KitChipWrap(
      children: [for (var i = 1; i <= n; i++) KitChip(label: _label(c, i))],
    ),
  ),
  KitListStress(
    'KitChoiceList.choices',
    item: 'KitChoiceRow',
    build: (_, c, n) => KitChoiceList<int>.single(
      choices: [
        for (var i = 1; i <= n; i++) KitChoice(value: i, title: _label(c, i)),
      ],
      selected: 1,
      onSelected: (_) {},
    ),
  ),
  KitListStress(
    'KitSessionTabs.tabs',
    item: '_Pill',
    build: (_, c, n) => KitSessionTabs(
      semanticsLabel: c.t('Open terminals', 'الطرفيات المفتوحة'),
      tabs: [
        for (var i = 1; i <= n; i++)
          KitSessionTab(label: _label(c, i), selected: i == 1, onTap: _noop),
      ],
    ),
  ),
  KitListStress(
    'KitBreadcrumb.segments',
    item: 'KitTappable',
    build: (_, c, n) => KitBreadcrumb(
      rootLabel: c.t('Project', 'المشروع'),
      segments: [for (var i = 1; i <= n; i++) c.t('folder$i', 'مجلد$i')],
      onSelected: (_) {},
    ),
  ),
  KitListStress(
    'KitTabStrip.tabs',
    item: '_KitTabView',
    count: 8,
    build: (_, c, n) => KitTabStrip(
      selected: 0,
      onSelected: (_) {},
      semanticsLabel: c.t('Task columns', 'أعمدة المهام'),
      tabs: [for (var i = 1; i <= n; i++) KitTab(label: _label(c, i))],
    ),
  ),
  KitListStress(
    'KitPhraseChips.phrases',
    item: 'KitChip',
    build: (_, c, n) => KitPhraseChips(
      phrases: [for (var i = 1; i <= n; i++) _label(c, i)],
      onChoose: (_) {},
      onEdit: (_) {},
      onHide: (_) {},
    ),
  ),
  KitListStress(
    'KitActionStack.tertiary',
    item: 'KitButton',
    count: 2,
    build: (_, c, n) => KitActionStack(
      primary: KitAction(
        label: c.t('Connect to this server', 'الاتصال بهذا الخادم'),
        onPressed: _noop,
      ),
      tertiary: [
        for (var i = 1; i <= n; i++)
          KitAction(label: _label(c, i), onPressed: _noop),
      ],
    ),
  ),
  KitListStress(
    'KitKeyValue.rows',
    item: 'KitText',
    count: 20,
    build: (_, c, n) => KitKeyValue(
      rows: [
        for (var i = 1; i <= n; i++)
          KitKeyValueRow(
            label: _label(c, i),
            value: c.t('$i files', '$i ملفات'),
          ),
      ],
    ),
  ),
  KitListStress(
    'KitMiniTable.rows',
    item: 'KitText',
    count: 20,
    build: (_, c, n) => KitMiniTable(
      columns: [for (var j = 1; j <= 6; j++) c.t('Column $j', 'العمود $j')],
      rows: [
        for (var i = 1; i <= n; i++)
          KitMiniTableRow([for (var j = 1; j <= 6; j++) _label(c, i * 10 + j)]),
      ],
    ),
  ),
];
