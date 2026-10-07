// G6 scenes for the agent-card parts: the frame, key values, the mini table,
// the chart and the sense ask. The matrix supplies compact and expanded
// windows, large text and both directions.
import 'package:opencode_mobile/ui/kit/kit.dart';

import 'kit_overflow_scenes.dart';

void _noop() {}

KitKeyValue _facts(KitSceneCopy c) => KitKeyValue(
  rows: [
    KitKeyValueRow(label: c.t('Duration', 'المدة'), value: '4:12'),
    KitKeyValueRow(
      label: c.t('Branch', 'الفرع'),
      value: c.t(
        'feature/a-very-long-branch-name-that-keeps-going',
        'feature/اسم-فرع-طويل-جدا-يستمر-في-الامتداد',
      ),
    ),
  ],
);

KitAgentCard _card(
  KitSceneCopy c, {
  KitAgentCardMode mode = KitAgentCardMode.full,
  bool ask = true,
}) => KitAgentCard(
  eyebrow: c.t('Claude Code asks', 'يسأل Claude Code'),
  title: c.t(
    'Which branch should I deploy to the staging server?',
    'أي فرع يجب أن أنشره على خادم التجربة؟',
  ),
  body: [_facts(c)],
  ask: ask
      ? KitButton.secondary(
          label: c.t('Deploy main', 'نشر الفرع الرئيسي'),
          onPressed: _noop,
        )
      : null,
  mode: mode,
  receiptLabel: c.t('Sent: main', 'أُرسل: الرئيسي'),
  onUndo: _noop,
  expandLabel: c.t('Show the card', 'عرض البطاقة'),
  passedOverLabel: c.t('Not answered', 'لم يُجب'),
  unreadableLabel: c.t('The card could not be shown', 'تعذر عرض البطاقة'),
  detailsLabel: c.t('Details', 'التفاصيل'),
  details: 'unknown key "x" at nodes[2]',
);

KitSenseAsk _sense(
  KitSceneCopy c, {
  int picked = 1,
  bool sending = false,
}) => KitSenseAsk(
  kind: KitSenseKind.photo,
  purpose: c.t(
    'Show me the screen where the error appears.',
    'أرني الشاشة التي يظهر فيها الخطأ.',
  ),
  actions: [
    KitSenseAction(label: c.t('Take photo', 'التقاط صورة'), onPressed: _noop),
    KitSenseAction(label: c.t('Choose photo', 'اختيار صورة'), onPressed: _noop),
  ],
  items: [
    for (var i = 1; i <= picked; i++)
      KitSenseItem(
        id: 'p$i',
        name: 'IMG_000$i.jpg',
        removeLabel: c.t('Remove photo', 'إزالة الصورة'),
        detail: '2.1 MB',
      ),
  ],
  max: 2,
  countLabel: c.t('$picked of 2', '$picked من 2'),
  maxReachedLabel: c.t('That is the most it can take', 'هذا أقصى ما يقبله'),
  sendLabel: c.t('Send photos', 'إرسال الصور'),
  sendDisabledReason: c.t('Add a photo first', 'أضف صورة أولا'),
  onSend: picked > 0 ? _noop : null,
  onRemove: (_) {},
  sending: sending,
);

final kitAgentCardsOverflowScenes = <KitOverflowScene>[
  KitOverflowScene(
    const ['KitAgentCard'],
    'default',
    build: (_, c) => _card(c),
  ),
  KitOverflowScene(
    const ['KitAgentCard'],
    'answered',
    build: (_, c) => _card(c, mode: KitAgentCardMode.receipt),
  ),
  KitOverflowScene(
    const ['KitAgentCard'],
    'disabled',
    build: (_, c) => _card(c, mode: KitAgentCardMode.passedOver),
  ),
  KitOverflowScene(
    const ['KitKeyValue'],
    'default',
    build: (_, c) => _facts(c),
  ),
  KitOverflowScene(
    const ['KitMiniTable'],
    'default',
    build: (_, c) => KitMiniTable(
      columns: [
        c.t('File', 'الملف'),
        c.t('Added', 'أضيف'),
        c.t('Removed', 'حذف'),
        c.t('Status', 'الحالة'),
      ],
      rows: [
        KitMiniTableRow([
          'lib/ui/kit/kit_chart.dart',
          '140',
          '3',
          c.t('Changed', 'تغيّر'),
        ]),
        KitMiniTableRow(['docs/old.md', '0', '86', c.t('Removed', 'حُذف')]),
      ],
    ),
  ),
  KitOverflowScene(
    const ['KitChart'],
    'default',
    build: (_, c) => KitChart(
      kind: KitChartKind.line,
      unit: c.t('files', 'ملفات'),
      labels: ['1', '2', '3', '4', '5'],
      series: [
        KitChartSeries(
          name: c.t('Added', 'أضيف'),
          values: const [3, 7, 4, 10, 6],
        ),
        KitChartSeries(
          name: c.t('Removed', 'حذف'),
          values: const [1, 2, 6, 3, 2],
        ),
      ],
    ),
  ),
  KitOverflowScene(
    const ['KitSenseAsk'],
    'default',
    build: (_, c) => _sense(c),
  ),
  KitOverflowScene(
    const ['KitSenseAsk'],
    'disabled',
    build: (_, c) => _sense(c, picked: 0),
  ),
  KitOverflowScene(
    const ['KitSenseAsk'],
    'working',
    build: (_, c) => _sense(c, picked: 2, sending: true),
  ),
];
