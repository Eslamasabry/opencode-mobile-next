// G6 scenes for KitChoiceCards and KitHeroQuestion (the first-run welcome's
// question and answers). The matrix supplies compact and expanded windows,
// large text and both directions.
import 'package:opencode_mobile/ui/app_iconography.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';

import 'kit_overflow_scenes.dart';

final kitWelcomeOverflowScenes = <KitOverflowScene>[
  KitOverflowScene(
    const ['KitHeroQuestion'],
    'default',
    build: (_, c) => KitHeroQuestion(
      c.t('Where should your agent run?', 'أين يجب أن يعمل وكيلك؟'),
    ),
  ),
  for (final state in const ['default', 'disabled', 'working'])
    KitOverflowScene(
      const ['KitChoiceCards'],
      state,
      build: (_, c) => KitChoiceCards<String>(
        semanticsLabel: c.t(
          'Where should your agent run?',
          'أين يجب أن يعمل وكيلك؟',
        ),
        selected: 'phone',
        busy: state == 'working' ? 'phone' : null,
        onSelected: (_) {},
        choices: [
          KitChoice<String>(
            value: 'phone',
            leading: const KitIcon(AppIconography.phone),
            title: c.t('On this phone', 'على هذا الهاتف'),
            supporting: c.t(
              'About 4 minutes · 208 MB download',
              'نحو 4 دقائق · تنزيل 208 ميغابايت',
            ),
            enabled: state != 'disabled',
            disabledReason: state == 'disabled'
                ? c.t('Not on this device', 'غير متاح على هذا الجهاز')
                : null,
          ),
          KitChoice<String>(
            value: 'computer',
            leading: const KitIcon(AppIconography.computer),
            title: c.t('On my computer', 'على حاسوبي'),
            supporting: c.t(
              'Pair with a code, or type its address',
              'اقرن برمز، أو اكتب عنوانه',
            ),
          ),
        ],
      ),
    ),
];
