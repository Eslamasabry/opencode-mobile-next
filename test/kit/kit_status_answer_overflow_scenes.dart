// G6 scenes for KitStatusAnswer (a status page's one big answer). The matrix
// supplies compact and expanded windows, large text and both directions.
import 'package:opencode_mobile/ui/kit/kit.dart';

import 'kit_overflow_scenes.dart';

void _noop() {}

final kitStatusAnswerOverflowScenes = <KitOverflowScene>[
  KitOverflowScene(
    const ['KitStatusAnswer'],
    'default',
    build: (_, c) => KitStatusAnswer(
      tone: KitAnswerTone.paused,
      answer: c.t('Android closed the server', 'أغلق Android الخادم'),
      line: c.t(
        'It restarts by itself in about 40 s',
        'يُعاد تشغيله تلقائيًا خلال نحو 40 ث',
      ),
      action: KitAction(
        label: c.t("Use this phone's server", 'استخدم خادم هذا الهاتف'),
        onPressed: _noop,
      ),
    ),
  ),
  KitOverflowScene(
    const ['KitStatusAnswer'],
    'calm',
    build: (_, c) => KitStatusAnswer(
      answer: c.t('No conversations yet', 'لا توجد محادثات بعد'),
      scale: KitAnswerScale.calm,
      line: c.t(
        'Say what you want to build, and an agent gets to work.',
        'قل ما تريد بناءه، ويبدأ وكيل العمل عليه.',
      ),
    ),
  ),
];
