// Overflow scenes for the kit's core parts (action stack to text), part of
// kit_overflow_scenes.dart.
part of 'kit_overflow_scenes.dart';

final _coreOverflowScenes = <KitOverflowScene>[
  // kit_action_stack.dart
  KitOverflowScene(
    const ['KitActionStack'],
    'default',
    build: (_, c) => KitActionStack(
      primary: KitAction(
        label: c.t('Connect to this server', 'الاتصال بهذا الخادم'),
        onPressed: _noop,
      ),
      secondary: KitAction(
        label: c.t('Scan the code instead', 'مسح الرمز بدلاً من ذلك'),
        onPressed: _noop,
      ),
      tertiary: _tertiary(c),
    ),
  ),
  // kit_ask_line.dart
  KitOverflowScene(
    const ['KitAskLine'],
    'default',
    build: (_, c) => KitAskLine(
      icon: AppIconography.info,
      question: c.t(
        'Keep the phone awake while agents work?',
        'إبقاء الهاتف مستيقظاً أثناء عمل الوكلاء؟',
      ),
      accept: KitAction(label: c.t('Keep awake', 'إبقاؤه'), onPressed: _noop),
      decline: KitAction(label: c.t('Not now', 'ليس الآن'), onPressed: _noop),
    ),
  ),
  // kit_buttons.dart
  KitOverflowScene(
    const ['KitButton'],
    'default',
    build: (_, c) => Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        KitButton.primary(
          label: c.t('Send to the agent', 'إرسال إلى الوكيل'),
          icon: AppIconography.send,
          onPressed: _noop,
        ),
        KitButton.secondary(
          label: c.t('Save as a draft', 'حفظ كمسودة'),
          onPressed: _noop,
        ),
        KitButton.tertiary(
          label: c.t('Discard the message', 'تجاهل الرسالة'),
          destructive: true,
          onPressed: _noop,
        ),
        Row(
          children: [
            KitButton.secondary(
              label: c.t('Retry', 'إعادة'),
              expand: false,
              onPressed: _noop,
            ),
          ],
        ),
      ],
    ),
  ),
  KitOverflowScene(
    const ['KitButton'],
    'working',
    build: (_, c) => KitButton.primary(
      label: c.t('Sending to the agent', 'جارٍ الإرسال إلى الوكيل'),
      working: true,
      onPressed: _noop,
    ),
  ),
  KitOverflowScene(
    const ['KitButton'],
    'disabled',
    build: (_, c) => KitButton.primary(
      label: c.t('Send to the agent', 'إرسال إلى الوكيل'),
      onPressed: null,
    ),
  ),
  KitOverflowScene(
    const ['KitActionBlock'],
    'default',
    build: (_, c) => KitActionBlock(
      primary: KitAction(
        label: c.t('Start the server', 'تشغيل الخادم'),
        onPressed: _noop,
      ),
      secondary: KitAction(
        label: c.t('Choose another folder', 'اختيار مجلد آخر'),
        onPressed: _noop,
      ),
      tertiary: [
        ..._tertiary(c),
        KitAction(label: c.t('Report', 'إبلاغ'), onPressed: _noop),
      ],
    ),
  ),
  KitOverflowScene(
    const ['KitActionBlock'],
    'disabled',
    build: (_, c) => KitActionBlock(
      primary: KitAction(
        label: c.t('Start the server', 'تشغيل الخادم'),
        onPressed: null,
      ),
      secondary: KitAction(
        label: c.t('Choose another folder', 'اختيار مجلد آخر'),
        onPressed: null,
      ),
    ),
  ),
  KitOverflowScene(
    const ['KitInset'],
    'default',
    build: (_, c) => KitInset(
      child: Text(
        c.t(
          'Agents keep working while the phone is locked.',
          'يواصل الوكلاء العمل والهاتف مقفل.',
        ),
      ),
    ),
  ),
  // kit_icon_button.dart
  KitOverflowScene(
    const ['KitIconButton'],
    'default',
    build: (_, c) => Row(
      children: [
        KitIconButton(
          icon: AppIconography.copy,
          label: c.t('Copy', 'نسخ'),
          onPressed: _noop,
        ),
        KitIconButton(
          icon: AppIconography.close,
          label: c.t('Close', 'إغلاق'),
          onPressed: null,
        ),
      ],
    ),
  ),
  // kit_illustration.dart
  KitOverflowScene(
    const ['KitIllustration'],
    'default',
    build: (_, c) => Center(
      child: KitIllustration(
        scene: const KitPortalScene(),
        semanticLabel: c.t('A doorway', 'باب'),
      ),
    ),
  ),
  // kit_panel.dart
  KitOverflowScene(
    const ['KitPanel'],
    'default',
    build: (_, c) => KitPanel(
      tone: AppStatusTone.attention,
      icon: AppIconography.info,
      title: c.t('Battery saver is on', 'موفر البطارية مفعّل'),
      onTap: _noop,
      child: Text(
        c.t(
          'Android may pause agents after a few minutes in the background.',
          'قد يوقف أندرويد الوكلاء بعد دقائق في الخلفية.',
        ),
      ),
    ),
  ),
  // kit_notice.dart
  KitOverflowScene(
    const ['KitNotice'],
    'default',
    build: (_, c) => KitNotice(
      tone: AppStatusTone.failure,
      icon: AppIconography.info,
      title: c.t('The key was not accepted', 'لم يُقبل المفتاح'),
      message: c.t(
        'The provider said the key has expired. Paste a new one.',
        'قال المزوّد إن المفتاح منتهٍ. الصق مفتاحاً جديداً.',
      ),
      notes: [
        c.t('Keys start with sk-', 'تبدأ المفاتيح بـ sk-'),
        c.t('Nothing was saved', 'لم يُحفظ شيء'),
      ],
      actions: _tertiary(c),
      onDismiss: _noop,
    ),
  ),
  // kit_progress.dart
  KitOverflowScene(
    const ['KitLoadingBar'],
    'loading',
    build: (_, c) =>
        KitLoadingBar(loading: true, label: c.t('Loading', 'جارٍ التحميل')),
  ),
  KitOverflowScene(
    const ['KitSkeletonRows'],
    'loading',
    build: (_, _) => const KitSkeletonRows(),
  ),
  KitOverflowScene(
    const ['KitProgressView'],
    'working',
    build: (_, c) => Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        KitProgressView(
          progress: KitProgress.known(
            0.62,
            caption: c.t(
              '29 of 30 MB · about 1 min left',
              '29 من 30 م.ب · نحو دقيقة متبقية',
            ),
          ),
        ),
        KitProgressView(
          progress: KitProgress.waiting(
            caption: c.t('Waiting for the server', 'بانتظار الخادم'),
          ),
        ),
      ],
    ),
  ),
  // kit_request_card.dart
  KitOverflowScene(
    const ['KitRequestCard'],
    'default',
    build: (_, c) => KitRequestCard(
      icon: AppIconography.terminal,
      title: c.t('Run a command?', 'تشغيل أمر؟'),
      announcement: c.t('The agent asks to run', 'يطلب الوكيل التشغيل'),
      tone: AppStatusTone.attention,
      summary: c.t(
        'Push the release branch to the shared remote',
        'دفع فرع الإصدار إلى المستودع المشترك',
      ),
      detail: 'git push origin main --force-with-lease --no-verify',
      primary: KitAction(
        label: c.t('Allow once', 'السماح مرة'),
        onPressed: _noop,
      ),
      secondary: KitAction(label: c.t('Deny', 'رفض'), onPressed: _noop),
      tertiary: [
        KitAction(
          label: c.t('Always allow in this project', 'السماح دائماً هنا'),
          onPressed: _noop,
        ),
      ],
    ),
  ),
  KitOverflowScene(
    const ['KitRequestCard'],
    'disabled',
    build: (_, c) => KitRequestCard(
      icon: AppIconography.terminal,
      title: c.t('Run a command?', 'تشغيل أمر؟'),
      announcement: c.t('The agent asks to run', 'يطلب الوكيل التشغيل'),
      summary: c.t('Answered on another device', 'أُجيب على جهاز آخر'),
      primary: KitAction(
        label: c.t('Allow once', 'السماح مرة'),
        onPressed: null,
      ),
      secondary: KitAction(label: c.t('Deny', 'رفض'), onPressed: null),
    ),
  ),
  // kit_row.dart
  KitOverflowScene(
    const ['KitRow'],
    'default',
    build: (_, c) => Column(
      children: [
        _row(c),
        KitRow(
          title: c.t(
            'A conversation title that runs long enough to wrap twice',
            'عنوان محادثة طويل بما يكفي ليلتف على سطرين',
          ),
          titleMaxLines: 2,
          supportingMaxLines: 2,
          supporting: TextSpan(
            text: c.t(
              'Updated 3 minutes ago in the release project',
              'حُدّثت قبل 3 دقائق في مشروع الإصدار',
            ),
          ),
          trailing: KitButton.tertiary(
            label: c.t('Open', 'فتح'),
            onPressed: _noop,
          ),
          onTap: _noop,
        ),
      ],
    ),
  ),
  KitOverflowScene(
    const ['KitRow'],
    'disabled',
    build: (_, c) => _row(c, enabled: false),
  ),
  // kit_row_parts.dart
  KitOverflowScene(
    const ['KitRowIcon', 'KitChevron'],
    'default',
    build: (_, c) => Column(
      children: [
        _row(c),
        KitRow(
          leading: const KitRowIcon(AppIconography.check, current: true),
          title: c.t('English', 'العربية'),
          trailing: const KitChevron(),
          onTap: _noop,
        ),
      ],
    ),
  ),
  KitOverflowScene(
    const ['KitRowMenu'],
    'default',
    build: (_, c) => _row(
      c,
      trailing: KitRowMenu(
        tooltip: c.t('More', 'المزيد'),
        items: [
          KitMenuItem(label: c.t('Rename', 'إعادة التسمية'), onSelected: _noop),
          KitMenuItem(
            label: c.t('Delete', 'حذف'),
            destructive: true,
            onSelected: _noop,
          ),
        ],
      ),
    ),
  ),
  KitOverflowScene(
    const ['KitRowMenu'],
    'disabled',
    build: (_, c) => _row(
      c,
      trailing: KitRowMenu(
        enabled: false,
        items: [
          KitMenuItem(label: c.t('Rename', 'إعادة التسمية'), onSelected: _noop),
        ],
      ),
    ),
  ),
  KitOverflowScene(
    const ['KitSwitchRow'],
    'default',
    build: (_, c) => KitSwitchRow(
      leading: const KitRowIcon(AppIconography.settings),
      title: c.t(
        'Keep agents working in the background',
        'إبقاء الوكلاء يعملون في الخلفية',
      ),
      supporting: c.t(
        'Android allows about six hours a day',
        'يسمح أندرويد بنحو ست ساعات يومياً',
      ),
      value: true,
      onChanged: (_) {},
    ),
  ),
  KitOverflowScene(
    const ['KitSwitchRow'],
    'disabled',
    build: (_, c) => KitSwitchRow(
      title: c.t('Vibrate when a reply arrives', 'الاهتزاز عند وصول رد'),
      value: false,
      onChanged: null,
    ),
  ),
  KitOverflowScene(
    const ['KitExpandRow'],
    'default',
    build: (_, c) => KitExpandRow(
      leading: const KitRowIcon(AppIconography.branch),
      title: c.t('Other ways to connect', 'طرق أخرى للاتصال'),
      supporting: TextSpan(
        text: c.t(
          'Scan a code or type an address',
          'امسح رمزاً أو اكتب عنواناً',
        ),
      ),
      children: [_row(c)],
    ),
  ),
  KitOverflowScene(
    const ['KitExpandRow'],
    'expanded',
    build: (_, c) => KitExpandRow(
      title: c.t('Other ways to connect', 'طرق أخرى للاتصال'),
      initiallyExpanded: true,
      children: [_row(c), _row(c)],
    ),
  ),
  // kit_screen.dart
  KitOverflowScene(
    const ['KitScreen'],
    'default',
    host: KitOverflowHost.fill,
    build: (_, c) => KitScreen(
      header: [KitText(c.t('Servers', 'الخوادم'), role: KitTextRole.label)],
      body: ListView(children: [_row(c), _row(c), _row(c)]),
      bottom: KitActionBlock(
        primary: KitAction(
          label: c.t('Add a server', 'إضافة خادم'),
          onPressed: _noop,
        ),
        tertiary: _tertiary(c),
      ),
    ),
  ),
  KitOverflowScene(
    const ['KitScreen'],
    'loading',
    host: KitOverflowHost.fill,
    build: (_, c) => KitScreen(
      loading: true,
      loadingLabel: c.t('Loading servers', 'جارٍ تحميل الخوادم'),
      body: const KitSkeletonRows(),
    ),
  ),
  // kit_sheet.dart (KitSheet, showKitSheet)
  KitOverflowScene(
    const ['KitSheet', 'showKitSheet'],
    'default',
    host: KitOverflowHost.modal,
    open: (context, c) => _sheet(context, c),
  ),
  KitOverflowScene(
    const ['KitSheet', 'showKitSheet'],
    'full',
    host: KitOverflowHost.modal,
    open: (context, c) => _sheet(context, c, height: KitSheetHeight.full),
  ),
  KitOverflowScene(
    const ['KitSheet', 'showKitSheet'],
    'loading',
    host: KitOverflowHost.modal,
    open: (context, c) => _sheet(context, c, loading: true),
  ),
  KitOverflowScene(
    const ['KitSheet', 'showKitSheet'],
    'disabled',
    host: KitOverflowHost.modal,
    open: (context, c) => _sheet(context, c, disabled: true),
  ),
  KitOverflowScene(
    const ['KitSheet'],
    'inline',
    host: KitOverflowHost.fill,
    build: (_, c) => Align(
      alignment: AlignmentDirectional.bottomCenter,
      child: KitSheet(
        title: c.t('Language', 'اللغة'),
        subtitle: c.t('Words across the whole app', 'الكلمات في كل التطبيق'),
        primary: KitAction(
          label: c.t('Use English', 'استخدام العربية'),
          onPressed: _noop,
        ),
        secondary: KitAction(
          label: c.t('Keep current', 'إبقاء الحالية'),
          onPressed: _noop,
        ),
        onClose: _noop,
        child: _row(c),
      ),
    ),
  ),
  // kit_confirm_sheet.dart (KitConfirmSheet, showKitConfirm)
  KitOverflowScene(
    const ['KitConfirmSheet', 'showKitConfirm'],
    'neutral',
    host: KitOverflowHost.modal,
    open: (context, c) => _confirm(context, c),
  ),
  KitOverflowScene(
    const ['KitConfirmSheet', 'showKitConfirm'],
    'destructive',
    host: KitOverflowHost.modal,
    open: (context, c) =>
        _confirm(context, c, kind: KitConfirmKind.destructive, full: true),
  ),
  KitOverflowScene(
    const ['KitConfirmSheet', 'showKitConfirm'],
    'stop',
    host: KitOverflowHost.modal,
    open: (context, c) => _confirm(context, c, kind: KitConfirmKind.stop),
  ),
  for (final (state, working, failed) in const [
    ('working', true, false),
    ('error', false, true),
  ])
    KitOverflowScene(
      const ['KitConfirmSheet'],
      state,
      build: (_, c) => KitConfirmSheet(
        title: c.t('Stop the agent?', 'إيقاف الوكيل؟'),
        body: c.t(
          'The reply so far stays in the conversation.',
          'يبقى الرد حتى الآن في المحادثة.',
        ),
        confirmLabel: c.t('Stop working', 'إيقاف العمل'),
        kind: KitConfirmKind.stop,
        working: working,
        failed: failed,
        onConfirm: _noop,
        onCancel: _noop,
      ),
    ),
  // kit_skeleton_transcript.dart
  KitOverflowScene(
    const ['KitSkeletonTranscript'],
    'loading',
    build: (_, _) => const KitSkeletonTranscript(),
  ),
  // kit_state_view.dart
  KitOverflowScene(
    const ['KitStateView'],
    'error',
    host: KitOverflowHost.fill,
    build: (_, c) => KitStateView(
      icon: AppIconography.info,
      tone: AppStatusTone.failure,
      title: c.t('Could not reach the server', 'تعذّر الوصول إلى الخادم'),
      body: c.t(
        'Check that the computer is awake and on the same network.',
        'تأكد أن الحاسوب مستيقظ وعلى الشبكة نفسها.',
      ),
      primary: KitAction(
        label: c.t('Try again', 'إعادة المحاولة'),
        onPressed: _noop,
      ),
      secondary: KitAction(
        label: c.t('Edit the server', 'تعديل الخادم'),
        onPressed: _noop,
      ),
      tertiary: _tertiary(c),
      details:
          'SocketException: Connection refused (OS Error: 111), '
          'address = 192.168.1.20, port = 4096',
      detailNotes: [c.t('Is the server running?', 'هل الخادم يعمل؟')],
    ),
  ),
  KitOverflowScene(
    const ['KitStateView'],
    'working',
    host: KitOverflowHost.fill,
    build: (_, c) => KitStateView(
      icon: AppIconography.terminal,
      tone: AppStatusTone.progress,
      title: c.t('Setting up the phone', 'جارٍ تجهيز الهاتف'),
      body: c.t(
        'This takes a few minutes the first time.',
        'يستغرق ذلك دقائق في المرة الأولى.',
      ),
      progress: KitProgress.known(
        0.4,
        caption: c.t(
          '12 of 30 MB · about 2 min left',
          '12 من 30 م.ب · نحو دقيقتين',
        ),
      ),
      secondary: KitAction(
        label: c.t('Stop setup', 'إيقاف التجهيز'),
        onPressed: _noop,
      ),
    ),
  ),
  KitOverflowScene(
    const ['KitStateView'],
    'empty',
    build: (_, c) => KitStateView(
      icon: AppIconography.search,
      size: KitStateSize.inline,
      title: c.t('No matching files', 'لا ملفات مطابقة'),
      body: c.t('Try a shorter name.', 'جرّب اسماً أقصر.'),
      primary: KitAction(
        label: c.t('Clear the search', 'مسح البحث'),
        onPressed: _noop,
      ),
    ),
  ),
  KitOverflowScene(
    const ['KitStateView'],
    'illustrated',
    host: KitOverflowHost.fill,
    build: (_, c) => KitStateView(
      icon: AppIconography.info,
      illustration: const KitPortalScene(),
      title: c.t('Connect your first server', 'اتصل بأول خادم'),
      body: c.t(
        'Your agents run there; this phone steers them.',
        'يعمل وكلاؤك هناك، وهذا الهاتف يوجّههم.',
      ),
      primary: KitAction(
        label: c.t('Add a server', 'إضافة خادم'),
        onPressed: _noop,
      ),
    ),
  ),
  // kit_status_line.dart
  KitOverflowScene(
    const ['KitStatusLine'],
    'default',
    build: (_, c) => KitStatusLine(
      icon: AppIconography.info,
      tone: AppStatusTone.attention,
      message: c.t(
        'Reconnecting to the workstation on the office network',
        'جارٍ إعادة الاتصال بمحطة العمل على شبكة المكتب',
      ),
      supporting: c.t('Last reply 2 minutes ago', 'آخر رد قبل دقيقتين'),
      action: KitAction(
        label: c.t('Try now', 'المحاولة الآن'),
        onPressed: _noop,
      ),
      more: _tertiary(c),
      onDismiss: _noop,
      dismissTooltip: c.t('Dismiss', 'إغلاق'),
    ),
  ),
  KitOverflowScene(
    const ['KitStatusLine'],
    'together',
    build: (_, c) => KitStatusLine(
      icon: AppIconography.check,
      tone: AppStatusTone.ok,
      message: c.t('Saved to the server', 'حُفظ على الخادم'),
      action: KitAction(label: c.t('Undo', 'تراجع'), onPressed: _noop),
      controlsTogether: true,
    ),
  ),
  // kit_status_mark.dart
  KitOverflowScene(
    const ['KitStatusMark'],
    'default',
    build: (_, _) => Wrap(
      children: [
        for (final state in KitMarkState.values) KitStatusMark(state: state),
      ],
    ),
  ),
  // kit_task_mark.dart
  KitOverflowScene(
    const ['KitTaskMark'],
    'default',
    build: (_, _) => Wrap(
      children: [
        for (final state in KitTaskState.values) KitTaskMark(state: state),
      ],
    ),
  ),
  // motion/
  KitOverflowScene(
    const ['KitAnimatedRows'],
    'default',
    build: (_, c) => KitAnimatedRows(
      children: [
        KeyedSubtree(key: const ValueKey('first'), child: _row(c)),
        KeyedSubtree(key: const ValueKey('second'), child: _row(c)),
      ],
    ),
  ),
  KitOverflowScene(
    const ['KitRefresh'],
    'default',
    host: KitOverflowHost.fill,
    build: (_, c) => KitRefresh(
      onRefresh: () async {},
      child: ListView(children: [_row(c), _row(c)]),
    ),
  ),
  KitOverflowScene(
    const ['KitReveal', 'KitEntrance'],
    'default',
    build: (_, c) => Column(
      children: [
        KitReveal(child: _row(c)),
        KitEntrance(child: _row(c)),
      ],
    ),
  ),
  KitOverflowScene(
    const ['KitTabSwitcher'],
    'default',
    host: KitOverflowHost.fill,
    build: (_, c) => KitTabSwitcher(
      index: 0,
      children: [
        ListView(children: [_row(c)]),
        ListView(children: [_row(c), _row(c)]),
      ],
    ),
  ),
  // glass/
  KitOverflowScene(
    const ['KitGlass'],
    'default',
    build: (_, c) => KitGlass(child: _row(c)),
  ),
  // kit_effects.dart (lib/state/effects.dart)
  KitOverflowScene(
    const ['KitEffectsScope'],
    'default',
    build: (_, c) => KitEffectsScope(
      effects: const KitEffects(motion: KitMotionLevel.off),
      child: _row(c),
    ),
  ),
  // kit_arrival.dart (slice-P9.4): a row arrived at, its wash on.
  KitOverflowScene(
    const ['KitArrival', 'KitArrivalScope'],
    'default',
    build: (_, c) => KitArrivalScope(
      rowId: 'arrived',
      child: KitArrival(id: 'arrived', child: _row(c)),
    ),
  ),
  // kit_text.dart (visual language merge, before the wave-0b gates)
  KitOverflowScene(
    const ['KitText'],
    'default',
    build: (_, c) => Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        for (final role in KitTextRole.values)
          KitText(
            role == KitTextRole.mono
                ? r'$ flutter test test/checkout_test.dart'
                : c.t(
                    'Fix the flaky checkout test before the release',
                    'إصلاح اختبار الدفع غير المستقر قبل الإصدار',
                  ),
            role: role,
          ),
      ],
    ),
  ),
];
