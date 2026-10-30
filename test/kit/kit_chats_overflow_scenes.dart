// G6 scenes for the Chats home parts: the feed item, its status tag, the
// filter chips and the floating action. The matrix supplies compact and
// expanded windows, large text and both directions.
import 'package:opencode_mobile/domain/agent_persona.dart';
import 'package:flutter/material.dart';
import 'package:opencode_mobile/ui/app_iconography.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';

import 'kit_overflow_scenes.dart';

void _noop() {}

final kitChatsOverflowScenes = <KitOverflowScene>[
  KitOverflowScene(
    const ['KitFeedItem'],
    'default',
    build: (_, c) => KitRowGroup(
      leadingIcons: false,
      children: [
        KitFeedItem(
          project: c.t('opencode-mobile', 'تطبيق-المحمول'),
          agent: 'Claude Code',
          title: c.t(
            'Rename the settings page and move its tests next to it',
            'إعادة تسمية صفحة الإعدادات ونقل اختباراتها بجانبها',
          ),
          state: c.t('Needs you', 'بانتظارك'),
          stateTone: KitFeedStateTone.needsYou,
          time: c.t('2 h ago', 'قبل ساعتين'),
          onTap: _noop,
        ),
        KitFeedItem(
          project: c.t('beta', 'بيتا'),
          title: c.t('Add dark mode', 'إضافة الوضع الداكن'),
          time: c.t('3 min ago', 'قبل ٣ دقائق'),
          onTap: _noop,
        ),
      ],
    ),
  ),
  KitOverflowScene(
    const ['KitStatusTag'],
    'default',
    build: (_, c) => Wrap(
      spacing: 8,
      runSpacing: 8,
      children: [
        KitStatusTag(
          label: c.t('Needs you', 'بانتظارك'),
          tone: KitStatusTagTone.needsYou,
        ),
        KitStatusTag(
          label: c.t('Running', 'قيد العمل'),
          tone: KitStatusTagTone.running,
        ),
      ],
    ),
  ),
  KitOverflowScene(
    const ['KitFilterChips', 'KitFilterChip'],
    'default',
    build: (_, c) => KitFilterChips(
      chips: [
        KitFilterChip(
          label: c.t('Needs you · 2', 'بانتظارك · ٢'),
          needsYou: true,
          selected: true,
          onPressed: _noop,
        ),
        KitFilterChip(
          label: c.t('Running', 'قيد العمل'),
          selected: false,
          onPressed: _noop,
        ),
      ],
    ),
  ),
  KitOverflowScene(
    const ['KitFloatingAction'],
    'default',
    host: KitOverflowHost.fill,
    build: (_, c) => KitFloatingAction(
      label: c.t('New conversation', 'محادثة جديدة'),
      icon: AppIconography.add,
      onPressed: _noop,
      child: const SizedBox.expand(),
    ),
  ),
  KitOverflowScene(
    const ['KitAgentLine'],
    'default',
    build: (_, c) => KitAgentLine(
      agent: c.t('Claude Code', 'كلود كود'),
      place: c.t(
        'in a project with a very long name indeed',
        'في مشروع باسم طويل جدًا فعلًا',
      ),
      semanticsLabel: c.t('Change the agent', 'غيّر الوكيل'),
      onPressed: _noop,
    ),
  ),
  KitOverflowScene(
    const ['KitAskField'],
    'default',
    build: (_, c) => KitAskField(
      controller: TextEditingController(
        text: c.t(
          'Make the ball a little faster and add a combo counter',
          'اجعل الكرة أسرع قليلًا وأضف عدّاد الضربات',
        ),
      ),
      focusNode: FocusNode(),
      hint: c.t('Ask for a change…', 'اطلب تغييرًا…'),
      sendLabel: c.t('Send to the conversation', 'أرسل إلى المحادثة'),
      onSend: _noop,
      onVoice: _noop,
      note: c.t('Sent to the conversation', 'أُرسل إلى المحادثة'),
    ),
  ),
  KitOverflowScene(
    const ['KitSessionTabs'],
    'default',
    build: (_, c) => KitSessionTabs(
      semanticsLabel: c.t('Open terminals', 'الطرفيات المفتوحة'),
      tabs: [
        for (var i = 0; i < 5; i++)
          KitSessionTab(
            label: c.t('A terminal with a long name $i', 'طرفية باسم طويل $i'),
            selected: i == 0,
            onTap: _noop,
          ),
      ],
    ),
  ),
  KitOverflowScene(
    const ['KitStartBox'],
    'default',
    build: (_, c) => KitStartBox(
      controller: TextEditingController(),
      focusNode: FocusNode(),
      question: c.t('What would you like to build?', 'ماذا تريد أن تبني؟'),
    ),
  ),
  KitOverflowScene(
    const ['KitAgentChoice', 'KitAgentGrid', 'KitAgentGridSpan'],
    'default',
    build: (_, c) => KitAgentGrid(
      children: [
        KitAgentChoice(
          persona: AgentPersona.claudeCode,
          state: KitAgentChoiceState.selected,
          onPressed: _noop,
        ),
        KitAgentChoice(
          persona: AgentPersona.openCode,
          state: KitAgentChoiceState.needsSetup,
          onPressed: _noop,
        ),
        KitAgentChoice(
          persona: AgentPersona.codex,
          state: KitAgentChoiceState.available,
          onPressed: _noop,
        ),
      ],
    ),
  ),
];
