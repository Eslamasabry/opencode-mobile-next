// G6 scenes for the Chats home parts: the feed item, its status tag, the
// filter chips and the floating action. The matrix supplies compact and
// expanded windows, large text and both directions.
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
          gitLabel: 'Git',
          title: c.t(
            'Rename the settings page and move its tests next to it',
            'إعادة تسمية صفحة الإعدادات ونقل اختباراتها بجانبها',
          ),
          preview: c.t(
            'Done. Four files changed and the tests pass.',
            'تم. تغيّرت أربعة ملفات والاختبارات تنجح.',
          ),
          tag: KitStatusTag(
            label: c.t('Needs you', 'بانتظارك'),
            tone: KitStatusTagTone.needsYou,
          ),
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
];
