// Real transcript and composer states for the G6 overflow matrix. The state
// names follow docs/ux-system/kit-api/Kit{Composer,Message,Turn,...}.md.
import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/widgets/request_routes.dart';

import 'kit_overflow_scenes.dart';

void _noop() {}

String _prompt(KitSceneCopy c) => c.t(
  'Check why the queued message disappeared after reconnecting.',
  'تحقق من سبب اختفاء الرسالة المنتظرة بعد إعادة الاتصال.',
);

String _reply(KitSceneCopy c) => c.t(
  'The draft is still saved. I checked the reconnect path and added a '
      'regression test before changing the queue.',
  'المسودة ما زالت محفوظة. تحققت من إعادة الاتصال وأضفت اختباراً قبل '
      'تغيير قائمة الرسائل.',
);

List<KitAttachment> _attachments(KitSceneCopy c, {bool thumbnail = false}) => [
  KitAttachment(
    id: 'queue-source',
    label: 'lib/state/offline_queue.dart:120–148',
    kind: KitAttachmentKind.reference,
    detail: c.t('Recovered', 'مستعاد'),
    onOpen: _noop,
  ),
  KitAttachment(
    id: 'reconnect-image',
    label: 'reconnect-screenshot.png',
    kind: KitAttachmentKind.image,
    thumbnail: thumbnail
        ? KitImageSource.memory(
            base64Decode(
              'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAIAAACQd1PeAAAADElEQVQI12P4z8AAAAMBAQAY3Y2wAAAAAElFTkSuQmCC',
            ),
          )
        : null,
    onOpen: _noop,
  ),
];

KitToolRow _tool(KitSceneCopy c, KitToolStatus status, {bool open = false}) =>
    KitToolRow(
      kind: KitToolKind.shell,
      title: c.t(
        'Run the queue regression tests',
        'تشغيل اختبارات قائمة الرسائل',
      ),
      status: status,
      detail: status == KitToolStatus.failed
          ? c.t('The reconnect test failed', 'فشل اختبار إعادة الاتصال')
          : c.t('Checking saved messages', 'التحقق من الرسائل المحفوظة'),
      note: KitMarkdown(
        c.t(
          'This checks that reconnecting keeps the draft until the server '
              'confirms it.',
          'يتحقق هذا من بقاء المسودة حتى يؤكد الخادم استلامها.',
        ),
        role: KitTextRole.secondary,
      ),
      body: [
        KitCodeBlock(
          text: status == KitToolStatus.failed
              ? 'Expected: 1 saved message\nActual: 0 saved messages'
              : '00:02 +12: All tests passed!',
          language: 'text',
        ),
      ],
      expanded: open,
      onExpansionChanged: (_) {},
    );

final kitOverflowChatScenes = <KitOverflowScene>[
  for (final permission in [true, false])
    KitOverflowScene(
      const ['showKitRequestSheet'],
      permission ? 'permission' : 'question',
      host: KitOverflowHost.modal,
      open: (context, c) async {
        final routes = RequestRoutes();
        await showKitRequestSheet(
          context,
          routes: routes,
          card: KitRequestCard.ask(
            kind: permission
                ? KitRequestKind.permission
                : KitRequestKind.question,
            title: permission
                ? c.t('Run the regression tests', 'تشغيل اختبارات التحقق')
                : c.t(
                    'Which release should I check?',
                    'أي إصدار تريد التحقق منه؟',
                  ),
            who: c.t('Queue reviewer', 'مراجع الرسائل'),
            server: c.t('Office workstation', 'محطة العمل في المكتب'),
            reason: KitNeedsYouReason.decision,
            ifIgnored: c.t(
              'The agent waits for your answer.',
              'ينتظر الوكيل إجابتك.',
            ),
            announcement: c.t(
              'The agent needs your answer',
              'يحتاج الوكيل إلى إجابتك',
            ),
            answers: permission
                ? const KitRequestDecide(onAllow: _noop, onReject: _noop)
                : KitRequestChoose<String>(
                    choices: [
                      KitChoice(
                        value: 'stable',
                        title: c.t(
                          'Current stable release',
                          'الإصدار المستقر الحالي',
                        ),
                      ),
                      KitChoice(
                        value: 'preview',
                        title: c.t(
                          'Next preview release',
                          'الإصدار التجريبي التالي',
                        ),
                      ),
                    ],
                    onChosen: (_) {},
                  ),
          ),
          fullText: permission
              ? 'flutter test --no-pub test/offline_queue_test.dart'
              : c.t(
                  'Choose the release used by the affected device.',
                  'اختر الإصدار المستخدم على الجهاز المتأثر.',
                ),
          details: [
            KitTechnicalValue(
              c.t('Project', 'المشروع'),
              '/home/workspace/opencode-mobile',
            ),
          ],
        );
      },
    ),
  for (final state in [
    'mixed',
    'all working',
    'needs you',
    'paused',
    'all done',
    'lead only',
    'overflowing',
    'empty',
  ])
    KitOverflowScene(
      const ['KitAgentStrip'],
      state,
      build: (_, c) => KitAgentStrip(
        agents: [
          if (state != 'empty')
            for (
              var i = 0;
              i <
                  (state == 'lead only'
                      ? 1
                      : state == 'overflowing'
                      ? 8
                      : 3);
              i++
            )
              KitAgent(
                id: i,
                name: i == 0
                    ? c.t('Planner', 'المخطط')
                    : c.t('Queue reviewer $i', 'مراجع الرسائل $i'),
                role: i == 0 ? c.t('Lead', 'القائد') : c.t('Worker', 'عامل'),
                state: switch (state) {
                  'all done' => KitTaskState.done,
                  'needs you' when i == 1 => KitTaskState.needsYou,
                  'mixed' when i == 1 => KitTaskState.waiting,
                  'mixed' when i == 2 => KitTaskState.done,
                  _ => KitTaskState.working,
                },
                paused: state == 'paused',
                onOpen: i == 0 ? null : _noop,
              ),
        ],
      ),
    ),
  for (final state in [
    'idle empty',
    'idle with text',
    'sending',
    'busy empty',
    'busy with text',
    'busy with delivery choice',
    'busy cannot send yet',
    'offline',
    'read-only',
    'focused',
    for (final phase in KitVoicePhase.values) 'voice ${phase.name}',
  ])
    KitOverflowScene(
      const ['KitComposer'],
      state,
      build: (_, c) => _ComposerScene(copy: c, state: state),
    ),
  for (final state in KitModelChipState.values)
    KitOverflowScene(
      const ['KitComposerChips'],
      'model ${state.name}',
      build: (_, c) => KitComposerChips.model(
        label: c.t('Sonnet 4.5 · High', 'Sonnet 4.5 · عالٍ'),
        state: state,
        onPressed: _noop,
      ),
    ),
  for (final used in [0.75, 0.97])
    KitOverflowScene(
      const ['KitComposerChips'],
      used == 0.75 ? 'context warning' : 'context almost full',
      build: (_, _) => KitComposerChips.model(
        label: 'Sonnet 4.5',
        contextUsed: used,
        onPressed: _noop,
      ),
    ),
  KitOverflowScene(
    const ['KitComposerChips'],
    'narrow',
    build: (_, _) => Align(
      alignment: AlignmentDirectional.centerStart,
      child: SizedBox(
        width: 48,
        child: KitComposerChips.model(label: 'Sonnet 4.5', onPressed: _noop),
      ),
    ),
  ),
  for (final state in ['editable', 'read-only', 'thumbnail', 'detail', 'empty'])
    KitOverflowScene(
      const ['KitComposerChips'],
      'attachments $state',
      build: (_, c) => KitComposerChips.attachments(
        items: state == 'empty'
            ? const []
            : _attachments(c, thumbnail: state == 'thumbnail'),
        onRemove: state == 'read-only' ? null : (_) {},
      ),
    ),
  for (final count in [0, 3, 7])
    KitOverflowScene(
      const ['KitComposerChips'],
      'suggestions ${count == 0
          ? 'empty'
          : count == 3
          ? 'list'
          : 'capped'}',
      build: (_, c) => KitComposerChips.suggestions(
        suggestions: [
          for (var i = 0; i < count; i++)
            KitSuggestion(
              id: i,
              label: i.isEven
                  ? '/review-$i'
                  : c.t('Queue reviewer $i', 'مراجع $i'),
              kind: i.isEven
                  ? KitSuggestionKind.command
                  : KitSuggestionKind.agent,
              description: c.t(
                'Review the pending changes before the next release.',
                'مراجعة التغييرات المنتظرة قبل الإصدار التالي.',
              ),
            ),
        ],
        onSelected: (_) {},
        onShowAll: count > 5 ? _noop : null,
      ),
    ),
  for (final empty in [false, true])
    KitOverflowScene(
      const ['KitComposerStatusStrip'],
      empty ? 'empty' : 'standing facts',
      build: (_, c) => KitComposerStatusStrip(
        chips: [
          if (!empty) ...[
            KitChip.action(
              label: c.t('Automatic approvals', 'موافقات تلقائية'),
              onPressed: _noop,
            ),
            KitChip(label: c.t('Background', 'في الخلفية')),
            KitChip(
              label: c.t('Waiting for the next step', 'بانتظار الخطوة التالية'),
            ),
          ],
        ],
      ),
    ),
  for (final state in ['default', 'streaming', 'non-interactive', 'empty'])
    KitOverflowScene(
      const ['KitMarkdown'],
      state,
      build: (_, c) => KitMarkdown(
        state == 'empty'
            ? ''
            : state == 'streaming'
            ? '${_reply(c)}\n\n```dart\nfinal pending = queue.where((item) => '
                  'item.profileId == activeProfile);'
            : c.t(
                '## Reconnect check\n\n${_reply(c)}\n\n'
                    '- Keep the draft.\n- Wait for confirmation.\n\n'
                    '> The server may still be working.\n\n'
                    '| File | Result |\n| --- | --- |\n'
                    '| `offline_queue.dart` | Passed |\n\n'
                    '[Read the guide](https://example.com/guide).',
                '## التحقق من الاتصال\n\n${_reply(c)}\n\n'
                    '- احتفظ بالمسودة.\n- انتظر التأكيد.\n\n'
                    '> قد يكون الخادم ما زال يعمل.\n\n'
                    '| الملف | النتيجة |\n| --- | --- |\n'
                    '| `offline_queue.dart` | ناجح |\n\n'
                    '[قراءة الدليل](https://example.com/guide).',
              ),
        interactive: state != 'non-interactive',
      ),
    ),
  for (final state in ['plain', 'attachments', 'time'])
    KitOverflowScene(
      const ['KitMessage'],
      'prompt $state',
      build: (_, c) => KitMessage.prompt(
        body: KitMarkdown(_prompt(c), selectable: false),
        attachments: state == 'attachments' ? _attachments(c) : const [],
        time: state == 'time' ? DateTime(2026, 9, 28, 10, 42) : null,
        menu: [
          KitMenuItem(
            label: c.t('Edit and resend', 'تعديل وإرسال'),
            onSelected: _noop,
          ),
        ],
      ),
    ),
  KitOverflowScene(
    const ['KitMessage'],
    'reply',
    build: (_, c) => KitMessage.reply(body: KitMarkdown(_reply(c))),
  ),
  for (final state in ['working', 'folded', 'open'])
    KitOverflowScene(
      const ['KitMessage'],
      'thought $state',
      build: (_, c) => KitMessage.thought(
        body: KitMarkdown(_reply(c), role: KitTextRole.secondary),
        working: state == 'working',
        took: state == 'working' ? null : const Duration(seconds: 12),
        expanded: state == 'open',
        onExpansionChanged: (_) {},
      ),
    ),
  for (final state in ['quiet', 'failed', 'action', 'open'])
    KitOverflowScene(
      const ['KitMessage'],
      'notice $state',
      build: (_, c) => KitMessage.notice(
        text: c.t('Project instructions updated', 'تم تحديث تعليمات المشروع'),
        technical: 'AGENTS.md',
        failed: state == 'failed',
        detail: KitMarkdown(_reply(c), role: KitTextRole.secondary),
        expanded: state == 'open',
        onExpansionChanged: (_) {},
        action: state == 'action'
            ? KitAction(
                label: c.t('View changes', 'عرض التغييرات'),
                onPressed: _noop,
              )
            : null,
      ),
    ),
  for (final working in [false, true])
    KitOverflowScene(
      const ['KitMessage'],
      'marker ${working ? 'working' : 'still'}',
      build: (_, c) => KitMessage.marker(
        text: working
            ? c.t('Compacting the conversation', 'تلخيص المحادثة')
            : c.t(
                'Switched to the review agent',
                'تم التبديل إلى وكيل المراجعة',
              ),
        working: working,
      ),
    ),
  for (final phase in KitTurnPhase.values)
    KitOverflowScene(
      const ['KitTurn'],
      phase.name,
      build: (_, c) => _turn(c, phase),
    ),
  for (final state in ['starting slow', 'finished latest', 'highlighted'])
    KitOverflowScene(
      const ['KitTurn'],
      state,
      build: (_, c) => _turn(
        c,
        state == 'starting slow'
            ? KitTurnPhase.starting
            : KitTurnPhase.finished,
        slow: state == 'starting slow',
        latest: state == 'finished latest',
        highlighted: state == 'highlighted',
      ),
    ),
  for (final status in KitToolStatus.values)
    for (final open in [false, true])
      KitOverflowScene(
        const ['KitToolRow'],
        '${status.name} ${open ? 'open' : 'folded'}',
        build: (_, c) => _tool(c, status, open: open),
      ),
  KitOverflowScene(
    const ['KitToolRow'],
    'agent',
    build: (_, c) => KitToolRow.agent(
      title: c.t('Queue reviewer · Worker', 'مراجع الرسائل · عامل'),
      task: _prompt(c),
      status: KitToolStatus.running,
      onOpen: _noop,
    ),
  ),
  for (final state in KitQueuedState.values)
    KitOverflowScene(
      const ['KitQueuedMessage'],
      state.name,
      build: (_, c) => KitQueuedMessage(items: [_queued(c, state)]),
    ),
  KitOverflowScene(
    const ['KitQueuedMessage'],
    'sending slow',
    build: (_, c) => KitQueuedMessage(
      items: [_queued(c, KitQueuedState.sending, slow: true)],
    ),
  ),
  KitOverflowScene(
    const ['KitQueuedMessage'],
    'mixed',
    build: (_, c) => KitQueuedMessage(
      items: [
        _queued(c, KitQueuedState.afterThisReply),
        _queued(c, KitQueuedState.notConfirmed),
        _queued(c, KitQueuedState.failed),
      ],
      action: KitAction(
        label: c.t('Try again', 'إعادة المحاولة'),
        onPressed: _noop,
      ),
      secondaryAction: KitAction(label: c.t('Edit', 'تعديل'), onPressed: _noop),
    ),
  ),
  KitOverflowScene(
    const ['KitQueuedMessage'],
    'empty',
    build: (_, _) => const KitQueuedMessage(items: []),
  ),
  for (final state in KitWorkState.values)
    for (final expanded in [false, true])
      KitOverflowScene(
        const ['KitWorkLine'],
        '${state.name} ${expanded ? 'expanded' : 'folded'}',
        build: (_, c) => KitWorkLine(
          counts: const KitWorkCounts(read: 3, edited: 1, ran: 2),
          state: state,
          expanded: expanded,
          onExpansionChanged: (_) {},
          now: state == KitWorkState.running
              ? c.t('Checking the reconnect path', 'التحقق من إعادة الاتصال')
              : null,
          steps: [
            KitMessage.thought(
              body: KitMarkdown(_reply(c), role: KitTextRole.secondary),
            ),
            _tool(c, switch (state) {
              KitWorkState.running => KitToolStatus.running,
              KitWorkState.waitingForYou => KitToolStatus.waitingForYou,
              KitWorkState.done => KitToolStatus.done,
              KitWorkState.endedFailed => KitToolStatus.failed,
              KitWorkState.stopped => KitToolStatus.stopped,
            }, open: state == KitWorkState.endedFailed),
          ],
        ),
      ),
  for (final expanded in [false, true])
    KitOverflowScene(
      const ['KitStepTimeline'],
      expanded ? 'expanded' : 'folded',
      build: (_, c) => KitStepTimeline(
        label: c.t(
          'Read 3 files · edited 1 file · ran 8 commands · 6 other steps',
          'قرأ 3 ملفات · عدّل ملفًا واحدًا · شغّل 8 أوامر · 6 خطوات أخرى',
        ),
        icon: AppIconography.terminal,
        expanded: expanded,
        onPressed: _noop,
        steps: [
          KitMessage.thought(
            heading: _reply(c),
            body: KitMarkdown(_reply(c), role: KitTextRole.secondary),
          ),
          KitToolRow(
            kind: KitToolKind.edit,
            title: c.t('Write the queue test', 'كتابة اختبار القائمة'),
            path: 'test/queue/offline_queue_reconnect_test.dart',
            added: 12,
            status: KitToolStatus.done,
            duration: const Duration(seconds: 3),
            preview: KitStepPreview.fromText(
              List.generate(
                8,
                (i) => 'expect(queue.items, hasLength($i)); // ${_reply(c)}',
              ).join('\n'),
            ),
            body: [KitCodeBlock(text: _reply(c), kind: KitCodeKind.code)],
          ),
          _tool(c, KitToolStatus.running),
        ],
      ),
    ),
];

KitTurn _turn(
  KitSceneCopy c,
  KitTurnPhase phase, {
  bool slow = false,
  bool latest = false,
  bool highlighted = false,
}) => KitTurn(
  phase: phase,
  prompt: KitMessage.prompt(body: KitMarkdown(_prompt(c), selectable: false)),
  blocks: [
    if (phase != KitTurnPhase.starting)
      KitMessage.reply(body: KitMarkdown(_reply(c))),
    if (phase == KitTurnPhase.failed)
      KitNotice(
        tone: AppStatusTone.failure,
        title: c.t('The server stopped answering', 'توقف الخادم عن الرد'),
        message: c.t('Your prompt is saved here.', 'رسالتك محفوظة هنا.'),
      ),
    if (phase == KitTurnPhase.waitingForYou)
      KitMessage.notice(
        text: c.t(
          'Allow the agent to run the tests?',
          'السماح للوكيل بتشغيل الاختبارات؟',
        ),
        action: KitAction(
          label: c.t('Review request', 'مراجعة الطلب'),
          onPressed: _noop,
        ),
      ),
  ],
  since: slow ? DateTime(2026, 1, 1) : null,
  latest: latest,
  highlighted: highlighted,
  footer: KitTurnFooter(
    copyText: () => _reply(c),
    meta: c.t(
      'Sonnet 4.5 · 12k tokens · 10:42',
      'Sonnet 4.5 · 12 ألف رمز · 10:42',
    ),
    menu: [
      KitMenuItem(
        label: c.t('Read aloud', 'قراءة بصوت عالٍ'),
        onSelected: _noop,
      ),
    ],
  ),
);

KitQueuedItem _queued(
  KitSceneCopy c,
  KitQueuedState state, {
  bool slow = false,
}) => KitQueuedItem(
  id: state.name,
  text: state == KitQueuedState.contextUpdate ? '' : _prompt(c),
  state: state,
  attachmentCount: state == KitQueuedState.contextUpdate ? 0 : 2,
  reason: state == KitQueuedState.failed || state == KitQueuedState.notConfirmed
      ? c.t(
          'The server did not confirm the message.',
          'لم يؤكد الخادم استلام الرسالة.',
        )
      : null,
  since: slow ? DateTime(2026, 1, 1) : null,
  menu: [KitMenuItem(label: c.t('Remove', 'إزالة'), onSelected: _noop)],
);

class _ComposerScene extends StatefulWidget {
  const _ComposerScene({required this.copy, required this.state});

  final KitSceneCopy copy;
  final String state;

  @override
  State<_ComposerScene> createState() => _ComposerSceneState();
}

class _ComposerSceneState extends State<_ComposerScene> {
  late final TextEditingController _controller = TextEditingController(
    text: widget.state.contains('empty') || widget.state.startsWith('voice ')
        ? ''
        : _prompt(widget.copy),
  );
  final _focus = FocusNode();
  final _level = ValueNotifier<double>(0.6);

  @override
  void initState() {
    super.initState();
    if (widget.state == 'focused') _focus.requestFocus();
  }

  @override
  void dispose() {
    _controller.dispose();
    _focus.dispose();
    _level.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final c = widget.copy;
    final state = widget.state;
    final voice = state.startsWith('voice ')
        ? KitVoicePhase.values.byName(state.substring(6))
        : null;
    return KitComposer(
      controller: _controller,
      focusNode: _focus,
      hint: c.t('Ask OpenCode…', 'اسأل OpenCode…'),
      onSend: _noop,
      onStop: _noop,
      onTools: _noop,
      onVoice: _noop,
      onOpenEditor: _noop,
      busy: state.startsWith('busy'),
      canSendWhileBusy: state != 'busy cannot send yet',
      onDeliveryChanged: state == 'busy with delivery choice' ? (_) {} : null,
      sending: state == 'sending',
      offline: state == 'offline',
      readOnlyReason: state == 'read-only'
          ? c.t('Watching this worker’s conversation', 'عرض محادثة هذا العامل')
          : null,
      voice: voice == null
          ? null
          : KitComposerVoice(
              phase: voice,
              onExit: _noop,
              conversation: true,
              level: _level,
              onListen: _noop,
              onStopListening: _noop,
              onStopSpeaking: _noop,
              onReadReply: _noop,
              readRepliesAloud: true,
              onReadRepliesAloudChanged: (_) {},
              reason: voice == KitVoicePhase.micDenied
                  ? c.t(
                      'Allow microphone access to speak.',
                      'اسمح باستخدام الميكروفون للتحدث.',
                    )
                  : voice == KitVoicePhase.failed
                  ? c.t(
                      'The recording could not be read.',
                      'تعذرت قراءة التسجيل.',
                    )
                  : null,
              fix:
                  voice == KitVoicePhase.micDenied ||
                      voice == KitVoicePhase.failed
                  ? KitAction(
                      label: voice == KitVoicePhase.micDenied
                          ? c.t('Allow microphone', 'السماح بالميكروفون')
                          : c.t('Try again', 'إعادة المحاولة'),
                      onPressed: _noop,
                    )
                  : null,
            ),
    );
  }
}
