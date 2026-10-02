// G6 scenes for transcript, composer, board and log parts merged in R06.
// QA: docs/qa/kit-gates-manifest-2026-09-27/README.md deferred their shared
// samples until integration. These exercise the actual public components.
import 'package:flutter/material.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';

import 'kit_overflow_scenes.dart';

void _noop() {}

KitMarkdown _prose(KitSceneCopy c, [String? text]) => KitMarkdown(
  text ??
      c.t(
        'Review the checkout changes before the next release.',
        'راجع تغييرات الدفع قبل الإصدار القادم.',
      ),
  selectable: false,
);

KitTaskCard _task(KitSceneCopy c, {KitTaskState mark = KitTaskState.working}) =>
    KitTaskCard(
      key: const ValueKey('checkout-review'),
      title: c.t(
        'Review checkout accessibility',
        'مراجعة إمكانية الوصول للدفع',
      ),
      mark: mark,
      onOpen: _noop,
      meta: [
        KitTaskMeta(
          c.t('High priority', 'أولوية عالية'),
          priority: KitPriority.high,
        ),
        KitTaskMeta(c.t('Reviewer', 'مراجع')),
      ],
    );

final kitChatOverflowScenes = <KitOverflowScene>[
  for (final state in KitTaskState.values)
    KitOverflowScene(
      ['KitAgentStrip'],
      state.name,
      build: (_, c) => KitAgentStrip(
        agents: [
          KitAgent(
            id: 'lead',
            name: c.t('Reviewer', 'مراجع'),
            role: c.t('Lead', 'قائد'),
            state: state,
          ),
          KitAgent(
            id: 'worker',
            name: c.t('Researcher', 'باحث'),
            state: KitTaskState.done,
            onOpen: _noop,
          ),
        ],
      ),
    ),
  KitOverflowScene(
    ['KitAgentStrip'],
    'empty',
    build: (_, _) => const KitAgentStrip(agents: []),
  ),
  for (final state in [
    'idle',
    'empty',
    'sending',
    'busy',
    'offline',
    'read-only',
  ])
    KitOverflowScene(
      ['KitComposer'],
      state,
      build: (_, c) => _ComposerScene(copy: c, state: state),
    ),
  KitOverflowScene(
    ['KitComposerChips'],
    'model',
    build: (_, c) => KitComposerChips.model(
      label: c.t('Review model · High effort', 'نموذج المراجعة · جهد عالٍ'),
      contextUsed: 0.82,
      onPressed: _noop,
    ),
  ),
  KitOverflowScene(
    ['KitComposerChips'],
    'attachments',
    build: (_, c) => KitComposerChips.attachments(
      items: [
        KitAttachment(
          id: 'file',
          label: 'checkout_review_notes.md',
          kind: KitAttachmentKind.file,
          onOpen: _noop,
        ),
      ],
      onRemove: (_) {},
    ),
  ),
  KitOverflowScene(
    ['KitComposerChips'],
    'suggestions',
    build: (_, c) => KitComposerChips.suggestions(
      suggestions: [
        KitSuggestion(
          id: 'review',
          label: c.t('Review checkout', 'مراجعة الدفع'),
          kind: KitSuggestionKind.command,
          description: c.t(
            'Inspect the current working changes',
            'فحص تغييرات العمل الحالية',
          ),
        ),
      ],
      onSelected: (_) {},
    ),
  ),
  KitOverflowScene(
    ['KitComposerChips'],
    'empty',
    build: (_, _) =>
        KitComposerChips.attachments(items: const [], onRemove: (_) {}),
  ),
  KitOverflowScene(
    ['KitComposerStatusStrip'],
    'default',
    build: (_, c) => KitComposerStatusStrip(
      chips: [
        KitChip(label: c.t('Automatic approvals', 'موافقات تلقائية')),
        KitChip(label: c.t('Background', 'الخلفية')),
      ],
      model: KitComposerChips.model(
        label: c.t('Review model', 'نموذج المراجعة'),
        onPressed: _noop,
      ),
    ),
  ),
  KitOverflowScene(
    ['KitComposerStatusStrip'],
    'empty',
    build: (_, _) => const KitComposerStatusStrip(chips: []),
  ),
  KitOverflowScene(['KitMarkdown'], 'default', build: (_, c) => _prose(c)),
  KitOverflowScene(
    ['KitMarkdown'],
    'streaming',
    build: (_, _) => const KitMarkdown(
      '```dart\nfinal review = inspectCheckout();',
      selectable: false,
    ),
  ),
  KitOverflowScene(
    ['KitMarkdown'],
    'non-interactive',
    build: (_, c) => KitMarkdown(
      c.t(
        '**Review** the [checkout](https://example.com/review).',
        '**راجع** [الدفع](https://example.com/review).',
      ),
      interactive: false,
      selectable: false,
    ),
  ),
  KitOverflowScene(
    ['KitMarkdown'],
    'empty',
    build: (_, _) => const KitMarkdown(''),
  ),
  KitOverflowScene(
    ['KitMessage'],
    'prompt',
    build: (_, c) => KitMessage.prompt(body: _prose(c)),
  ),
  KitOverflowScene(
    ['KitMessage'],
    'reply',
    build: (_, c) => KitMessage.reply(body: _prose(c)),
  ),
  for (final state in ['folded', 'open', 'working'])
    KitOverflowScene(
      ['KitMessage'],
      'thought-$state',
      build: (_, c) => KitMessage.thought(
        body: _prose(c),
        heading: c.t('Checking the changes', 'فحص التغييرات'),
        working: state == 'working',
        expanded: state == 'open',
      ),
    ),
  KitOverflowScene(
    ['KitMessage'],
    'notice',
    build: (_, c) => KitMessage.notice(
      text: c.t(
        'The server could not finish this request.',
        'تعذر على الخادم إنهاء هذا الطلب.',
      ),
      failed: true,
      detail: _prose(
        c,
        c.t(
          'Connect again and retry the request.',
          'أعد الاتصال وحاول الطلب مرة أخرى.',
        ),
      ),
      expanded: true,
      action: KitAction(
        label: c.t('Try again', 'حاول مجدداً'),
        onPressed: _noop,
      ),
    ),
  ),
  for (final working in [false, true])
    KitOverflowScene(
      ['KitMessage'],
      working ? 'marker-working' : 'marker',
      build: (_, c) => KitMessage.marker(
        text: c.t('Continuing the conversation', 'متابعة المحادثة'),
        working: working,
      ),
    ),
  for (final state in KitQueuedState.values)
    KitOverflowScene(
      ['KitQueuedMessage'],
      state.name,
      build: (_, c) => KitQueuedMessage(
        items: [
          KitQueuedItem(
            id: 'queued',
            text: c.t(
              'Review the checkout when the current reply ends.',
              'راجع الدفع عند انتهاء الرد الحالي.',
            ),
            state: state,
            reason:
                state == KitQueuedState.failed ||
                    state == KitQueuedState.notConfirmed
                ? c.t(
                    'The connection closed before confirmation.',
                    'أغلق الاتصال قبل التأكيد.',
                  )
                : null,
          ),
        ],
        action: state == KitQueuedState.failed
            ? KitAction(
                label: c.t('Try again', 'حاول مجدداً'),
                onPressed: _noop,
              )
            : null,
      ),
    ),
  for (final state in KitToolStatus.values)
    KitOverflowScene(
      ['KitToolRow'],
      state.name,
      build: (_, c) => KitToolRow(
        kind: KitToolKind.read,
        title: c.t('Read checkout configuration', 'قراءة إعدادات الدفع'),
        status: state,
        path: 'lib/checkout/configuration.dart',
        body: [
          KitText(
            c.t(
              'The configuration uses the saved server address.',
              'تستخدم الإعدادات عنوان الخادم المحفوظ.',
            ),
          ),
        ],
        expanded: state == KitToolStatus.failed,
      ),
    ),
  for (final phase in KitTurnPhase.values)
    KitOverflowScene(
      ['KitTurn'],
      phase.name,
      build: (_, c) => KitTurn(
        prompt: KitMessage.prompt(body: _prose(c)),
        blocks: [
          KitMessage.reply(
            body: _prose(
              c,
              c.t('The checkout review is complete.', 'اكتملت مراجعة الدفع.'),
            ),
          ),
        ],
        phase: phase,
        footer: KitTurnFooter(
          copyText: () => 'Checkout review',
          meta: c.t('Review model · 12k tokens', 'نموذج المراجعة · 12 ألف رمز'),
        ),
      ),
    ),
  for (final state in KitWorkState.values)
    KitOverflowScene(
      ['KitWorkLine'],
      state.name,
      build: (_, c) => KitWorkLine(
        counts: const KitWorkCounts(read: 3, edited: 1),
        state: state,
        now: c.t('Reading checkout configuration', 'قراءة إعدادات الدفع'),
        steps: [
          KitText(c.t('Read configuration', 'قراءة الإعدادات')),
          KitText(c.t('Updated the checkout test', 'تحديث اختبار الدفع')),
        ],
        expanded: state == KitWorkState.endedFailed,
      ),
    ),
  KitOverflowScene(
    ['KitWorkLine'],
    'open',
    build: (_, c) => KitWorkLine(
      counts: const KitWorkCounts(read: 3),
      state: KitWorkState.done,
      steps: [KitText(c.t('Read configuration', 'قراءة الإعدادات'))],
      expanded: true,
    ),
  ),
  KitOverflowScene(
    ['KitBoardLane'],
    'loaded',
    host: KitOverflowHost.fill,
    build: (_, c) => KitBoardLane(cards: [_task(c)]),
  ),
  KitOverflowScene(
    ['KitBoardLane'],
    'empty',
    host: KitOverflowHost.fill,
    build: (_, c) => KitBoardLane(
      cards: const [],
      empty: KitText(c.t('No tasks in this lane', 'لا مهام في هذا المسار')),
    ),
  ),
  KitOverflowScene(
    ['KitBoardLane'],
    'loading',
    host: KitOverflowHost.fill,
    build: (_, _) => const KitBoardLane.loading(),
  ),
  for (final loading in [false, true])
    KitOverflowScene(
      ['KitBoardLanes'],
      loading ? 'loading' : 'loaded',
      host: KitOverflowHost.fill,
      build: (_, c) => KitBoardLanes(
        columns: [
          KitBoardColumn(label: c.t('Working', 'قيد التنفيذ'), count: 1),
          KitBoardColumn(
            label: c.t('Ready for review', 'جاهز للمراجعة'),
            count: 0,
          ),
        ],
        selected: 0,
        onSelected: (_) {},
        loading: loading,
        laneBuilder: (_, i) => KitBoardLane(
          cards: i == 0 ? [_task(c)] : const [],
          empty: KitText(c.t('No tasks to review', 'لا توجد مهام للمراجعة')),
        ),
      ),
    ),
  for (final state in KitTaskState.values)
    KitOverflowScene(
      ['KitTaskCard'],
      state.name,
      build: (_, c) => _task(c, mark: state),
    ),
  KitOverflowScene(
    ['KitPriorityGlyph'],
    'default',
    build: (_, _) => const KitPriorityGlyph(priority: KitPriority.high),
  ),
  for (final layout in [KitWorkGraphLayout.rows, KitWorkGraphLayout.layers])
    KitOverflowScene(
      ['KitWorkGraph'],
      layout.name,
      host: KitOverflowHost.fill,
      build: (_, c) => KitWorkGraph(
        layout: layout,
        nodes: [
          KitWorkGraphNode(
            id: 'review',
            title: c.t('Review checkout', 'مراجعة الدفع'),
            mark: KitTaskState.needsYou,
            stuck: true,
          ),
          KitWorkGraphNode(
            id: 'release',
            title: c.t('Prepare the release', 'تجهيز الإصدار'),
            mark: KitTaskState.waiting,
            dependsOn: const ['review'],
          ),
        ],
        onOpen: (_) {},
      ),
    ),
  KitOverflowScene(
    ['KitWorkGraph'],
    'empty',
    host: KitOverflowHost.fill,
    build: (_, _) => KitWorkGraph(nodes: const [], onOpen: (_) {}),
  ),
  for (final state in ['empty', 'live', 'quiet', 'ended', 'failed', 'dropped'])
    KitOverflowScene(
      ['KitLogPanel'],
      state,
      build: (_, c) => _LogScene(copy: c, state: state),
    ),
];

class _ComposerScene extends StatefulWidget {
  const _ComposerScene({required this.copy, required this.state});
  final KitSceneCopy copy;
  final String state;
  @override
  State<_ComposerScene> createState() => _ComposerSceneState();
}

class _ComposerSceneState extends State<_ComposerScene> {
  late final _controller = TextEditingController(
    text: widget.state == 'empty'
        ? ''
        : widget.copy.t('Review the checkout changes', 'راجع تغييرات الدفع'),
  );
  final _focus = FocusNode();
  @override
  void dispose() {
    _controller.dispose();
    _focus.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => KitComposer(
    controller: _controller,
    focusNode: _focus,
    hint: widget.copy.t('Ask OpenCode…', 'اسأل OpenCode…'),
    onSend: _noop,
    onStop: _noop,
    busy: widget.state == 'busy',
    sending: widget.state == 'sending',
    offline: widget.state == 'offline',
    readOnlyReason: widget.state == 'read-only'
        ? widget.copy.t(
            'This conversation is read only',
            'هذه المحادثة للقراءة فقط',
          )
        : null,
    onTools: _noop,
    onOpenEditor: _noop,
  );
}

class _LogScene extends StatefulWidget {
  const _LogScene({required this.copy, required this.state});
  final KitSceneCopy copy;
  final String state;
  @override
  State<_LogScene> createState() => _LogSceneState();
}

class _LogSceneState extends State<_LogScene> {
  late final _buffer = KitLogBuffer(capacity: 3);
  @override
  void initState() {
    super.initState();
    if (widget.state != 'empty') {
      for (var i = 0; i < (widget.state == 'dropped' ? 5 : 2); i++) {
        _buffer.add(KitLogLine('Reviewing checkout configuration: step $i'));
      }
    }
  }

  @override
  void dispose() {
    _buffer.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => KitLogPanel(
    lines: _buffer,
    title: widget.copy.t('Review output', 'مخرجات المراجعة'),
    live: widget.state == 'live',
    ended: switch (widget.state) {
      'ended' => const KitLogEnd(exitCode: 0),
      'failed' => KitLogEnd(
        exitCode: 1,
        failed: true,
        reason: widget.copy.t('The review command failed', 'فشل أمر المراجعة'),
      ),
      _ => null,
    },
  );
}
