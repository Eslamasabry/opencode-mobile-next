// Overflow scenes for row groups, remembered titles, sections and navigation,
// part of kit_overflow_scenes.dart.
part of 'kit_overflow_scenes.dart';

final _groupOverflowScenes = <KitOverflowScene>[
  // kit_row.dart: rows grouped on one panel, and a row's current value
  KitOverflowScene(
    const ['KitRowGroup', 'KitRowValue'],
    'default',
    build: (_, c) => KitRowGroup(
      label: c.t('Laptop on the office network', 'الحاسوب على شبكة المكتب'),
      children: [
        _row(c),
        KitRow(
          leading: const KitRowIcon(AppIconography.star),
          title: c.t('Model', 'النموذج'),
          trailing: const KitRowValue('Claude Sonnet 4'),
          onTap: _noop,
        ),
        KitRow(
          leading: const KitRowIcon(AppIconography.shield),
          title: c.t('What agents may do', 'ما يمكن للوكلاء فعله'),
          trailing: KitRowValue(c.t('Ask first', 'اسأل أولاً'), chevron: false),
          onTap: _noop,
        ),
      ],
    ),
  ),
  // ── Kit tier 1 (integration 2026-09-27). Arabic is dropped (owner
  // decision), so these scenes use the English copy in both directions.
  KitOverflowScene(
    const ['KitSurface'],
    'default',
    build: (_, c) => KitSurface(child: _row(c)),
  ),
  KitOverflowScene(
    const ['KitDivider'],
    'default',
    build: (_, c) => Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [_row(c), const KitDivider(), _row(c)],
    ),
  ),
  KitOverflowScene(
    const ['KitIcon', 'KitBrandMark'],
    'default',
    build: (_, _) => const Row(
      children: [
        KitIcon(AppIconography.terminal),
        SizedBox(width: 12),
        KitBrandMark(),
      ],
    ),
  ),
  KitOverflowScene(
    const ['KitChip', 'KitChipWrap'],
    'default',
    build: (_, _) => KitChipWrap(
      children: [
        const KitChip(label: 'feature/checkout-retry'),
        KitChip.action(label: 'Needs you', onPressed: _noop, selected: true),
        KitChip.removable(label: 'lib/checkout/retry.dart', onRemove: _noop),
        KitChip.count(label: 'Tasks', count: 3),
        KitChip.summary(label: 'Read 3 files · edited 1', onPressed: _noop),
      ],
    ),
  ),
  KitOverflowScene(
    const ['KitSegmented'],
    'default',
    build: (_, _) => KitSegmented<String>(
      segments: const [
        KitSegment(value: 'today', label: 'Today'),
        KitSegment(value: 'week', label: 'This week'),
        KitSegment(value: 'month', label: 'This month'),
      ],
      selected: 'week',
      onChanged: (_) {},
      semanticsLabel: 'Time range',
    ),
  ),
  KitOverflowScene(
    const ['KitMenuPanel'],
    'default',
    build: (_, _) => KitMenuPanel(
      items: [
        KitMenuItem(label: 'Rename conversation', onSelected: _noop),
        KitMenuItem(label: 'Archive conversation', onSelected: _noop),
        KitMenuItem(
          label: 'Delete conversation',
          onSelected: _noop,
          destructive: true,
        ),
      ],
      onSelected: (_) {},
    ),
  ),
  KitOverflowScene(
    const ['showKitMenu'],
    'default',
    host: KitOverflowHost.modal,
    open: (context, _) => showKitMenu(
      context,
      items: [
        KitMenuItem(label: 'Rename conversation', onSelected: _noop),
        KitMenuItem(label: 'Archive conversation', onSelected: _noop),
      ],
    ),
  ),
  KitOverflowScene(
    const ['KitTerm'],
    'default',
    build: (_, _) => const KitTerm(
      'MCP',
      explanation:
          'A way to give the assistant extra tools, such as your issue '
          'tracker or a database.',
    ),
  ),
  KitOverflowScene(
    const ['showKitTerm'],
    'default',
    host: KitOverflowHost.modal,
    open: (context, _) => showKitTerm(
      context,
      term: 'MCP',
      explanation:
          'A way to give the assistant extra tools, such as your issue '
          'tracker or a database.',
    ),
  ),
  KitOverflowScene(
    const ['showKitUndo'],
    'default',
    host: KitOverflowHost.modal,
    open: (context, _) {
      showKitUndo(
        context,
        message: 'Archived "Fix the flaky checkout test"',
        onUndo: _noop,
      );
      // Settle it at once so no undo window is left running after the scene.
      Future<void>.delayed(
        const Duration(milliseconds: 700),
        KitUndo.commitPending,
      );
    },
  ),
  KitOverflowScene(
    const ['KitBottomInset'],
    'default',
    build: (_, c) =>
        KitBottomInset(insets: const KitClearance(bottom: 80), child: _row(c)),
  ),
  // kit_last_known.dart (slice-speed-ui): remembered titles, refreshing.
  KitOverflowScene(
    const ['KitLastKnown'],
    'default',
    build: (_, _) => const KitLastKnown(
      updated: 'Updated 12m ago',
      rows: [
        KitLastKnownRow(
          title: 'Release notes for 1.0.45 and the store listing copy',
          detail: '1h ago',
        ),
        KitLastKnownRow(title: 'New conversation'),
      ],
    ),
  ),
  // chat/kit_transcript_excerpt.dart (slice-chat-speed-fixes): a chat's
  // saved end while its history loads.
  KitOverflowScene(
    const ['KitTranscriptExcerpt'],
    'default',
    build: (_, _) => const SizedBox(
      height: 360,
      child: KitTranscriptExcerpt(
        updated: 'Updated 12m ago',
        messages: [
          KitExcerptMessage(
            text: 'Fix the flaky checkout test before the release',
            fromPerson: true,
          ),
          KitExcerptMessage(
            text: 'The checkout test waited on a timer the stub never fired.',
            fromPerson: false,
          ),
        ],
      ),
    ),
  ),
  KitOverflowScene(
    const ['KitSince'],
    'default',
    build: (_, _) => KitSince(
      since: DateTime(2026),
      builder: (context, status) =>
          KitText('Waiting for the server to answer · ${status.phase.name}'),
    ),
  ),
  KitOverflowScene(
    const ['KitAvatar', 'KitImage', 'KitZoom'],
    'default',
    build: (_, _) => Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        const KitAvatar(name: 'Open AI'),
        const SizedBox(height: 16),
        SizedBox(
          width: 120,
          height: 90,
          child: KitImage(
            source: KitImageSource.provider(
              MemoryImage(Uint8List.fromList(_onePixelPng)),
            ),
            semanticsLabel: 'A photo',
          ),
        ),
        const SizedBox(height: 16),
        const SizedBox(
          width: 280,
          height: 200,
          child: KitZoom(
            label: 'Screenshot.png',
            child: SizedBox(
              width: 160,
              height: 120,
              child: ColoredBox(color: Color(0xFF3D6BFF)),
            ),
          ),
        ),
      ],
    ),
  ),
  KitOverflowScene(
    const ['KitQr'],
    'default',
    build: (_, _) => const KitQr(
      data: 'https://example.com/join/3f9c2a',
      semanticsLabel: 'Code to open this session on another device',
    ),
  ),
  KitOverflowScene(
    const ['KitLevelMeter'],
    'default',
    build: (_, _) => const KitLevelMeter(level: 0.6),
  ),
  KitOverflowScene(
    const ['KitSwatch', 'KitSwatchGrid', 'KitThemePreview'],
    'default',
    build: (context, _) {
      final roles = KitTokens.of(context).roles;
      return Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          KitSwatchGrid(
            label: 'Theme',
            children: [
              KitSwatch(
                roles: roles,
                label: 'Graphite',
                selected: true,
                onPressed: _noop,
              ),
              KitSwatch(
                roles: null,
                label: 'Material You',
                selected: false,
                onPressed: null,
                disabledReason: 'Needs Android 12 or later',
              ),
            ],
          ),
          const SizedBox(height: 16),
          KitThemePreview(roles: roles, label: 'Preview of Graphite'),
        ],
      );
    },
  ),
  KitOverflowScene(
    const ['KitTerminalView'],
    'default',
    build: (_, _) => const KitTerminalView.output(
      command: 'flutter test test/checkout_test.dart',
      output:
          '00:02 +12: All tests passed!\n'
          'Ran 12 tests in lib/checkout and lib/payments in 2.4 seconds',
    ),
  ),
  KitOverflowScene(
    const ['KitSelectable', 'KitLtr'],
    'default',
    build: (_, _) => const KitSelectable(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          KitText('Fix the flaky checkout test before the release'),
          KitLtr(child: KitText('OPENCODE_SERVER=http://127.0.0.1:4096')),
        ],
      ),
    ),
  ),
  KitOverflowScene(
    const ['KitConsequences'],
    'default',
    build: (_, _) => KitConsequences(
      items: const [
        KitConsequence('The conversation and its 42 messages are removed'),
        KitConsequence('Files the agent changed stay as they are'),
      ],
    ),
  ),
  KitOverflowScene(
    const ['KitSwap', 'KitSpin', 'KitDim'],
    'default',
    build: (_, c) => Row(
      children: [
        const KitSpin(turns: 0.25, child: KitIcon(AppIconography.retry)),
        const SizedBox(width: 12),
        // A drawing, not a glyph: a glyph is a paragraph (LOOK-14).
        const KitDim(
          child: SizedBox.square(
            dimension: 24,
            child: ColoredBox(color: Color(0xFF3D6BFF)),
          ),
        ),
        const SizedBox(width: 12),
        Expanded(child: KitSwap(child: _row(c))),
      ],
    ),
  ),
  KitOverflowScene(
    const ['KitAnimatedBox', 'KitAnimatedValue'],
    'default',
    build: (_, c) => KitAnimatedBox(
      level: KitSurfaceLevel.surface2,
      child: KitAnimatedValue(
        value: 0.4,
        builder: (context, value) => Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            _row(c),
            KitText('Uploaded ${(value * 100).round()} % of the build'),
          ],
        ),
      ),
    ),
  ),
  // kit_section_label.dart (slice-R4)
  KitOverflowScene(
    const ['KitSectionLabel'],
    'default',
    build: (_, c) => Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        KitSectionLabel(
          'Model context protocol servers on this workstation',
          explanation:
              'Tools the agent can call, served by programs this server runs.',
          trailing: const KitText(
            '3 connected',
            role: KitTextRole.caption,
            tone: KitTextTone.secondary,
          ),
        ),
        _row(c),
      ],
    ),
  ),
  // kit_sliver_row_group.dart (slice-R4)
  KitOverflowScene(
    const ['KitSliverRowGroup'],
    'default',
    host: KitOverflowHost.fill,
    build: (_, c) => CustomScrollView(
      slivers: [
        KitSliverRowGroup(
          label: 'Recent conversations in this project',
          itemCount: 3,
          itemBuilder: (_, _) => _row(c),
          children: [_row(c)],
        ),
      ],
    ),
  ),
  // kit-polish (2026-09-27): the text-2.0 cut-offs the kit-gates galleries
  // showed. An unavailable row whose reason wraps in full, with its enable
  // action under it from 1.3× text.
  KitOverflowScene(
    const ['KitRow'],
    'unavailable',
    build: (_, c) => KitRow.unavailable(
      title: c.t('Voice typing', 'الكتابة بالصوت'),
      reason: c.t(
        'Needs a voice model on this phone. It downloads once, then works '
            'without a connection.',
        'تحتاج إلى نموذج صوت على هذا الهاتف. يُنزَّل مرة واحدة ثم يعمل دون '
            'اتصال.',
      ),
      enable: KitAction(
        label: c.t('Download voice model', 'تنزيل نموذج الصوت'),
        onPressed: _noop,
      ),
    ),
  ),
  // kit_capability_explainer.dart: the row, the state and the offer.
  KitOverflowScene(
    const ['KitCapabilityExplainer'],
    'default',
    build: (_, c) {
      // The enable flow needs a handler to show its action.
      KitCapabilities.registerFlow(
        KitEnableFlows.voiceModelSetup,
        (context, request) async {},
      );
      return Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          const KitCapabilityExplainer.row(capability: 'voice.model'),
          const KitCapabilityExplainer.row(
            capability: 'flag:fileBrowsing+terminal',
            host: KitHost.codex,
            serverName: 'laptop in the office',
          ),
          const KitCapabilityExplainer.state(
            capability: 'voice.model',
            cost: ['About 160 MB', 'about 2 min'],
          ),
          KitCapabilityExplainer.offer(
            capability: 'voice.model',
            onNotNow: _noop,
          ),
        ],
      );
    },
  ),
  // kit_task_card.dart: the meta line wraps between its pieces, never
  // inside "12 min ago".
  KitOverflowScene(
    const ['KitTaskCard', 'KitPriorityGlyph'],
    'default',
    build: (_, c) => Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        KitTaskCard(
          title: c.t(
            'Fix the sync engine dropping queued messages after a reconnect',
            'إصلاح محرك المزامنة الذي يُسقط الرسائل بعد إعادة الاتصال',
          ),
          mark: KitTaskState.working,
          onOpen: _noop,
          meta: [
            KitTaskMeta(
              c.t('High', 'عالية'),
              priority: KitPriority.high,
              strong: true,
            ),
            KitTaskMeta(c.t('Bug', 'خلل'), icon: AppIconography.bug),
            const KitTaskMeta('fox'),
            KitTaskMeta(c.t('12 min ago', 'قبل 12 دقيقة')),
          ],
          action: KitAction(
            label: c.t('Move or change', 'نقل أو تغيير'),
            icon: AppIconography.swap,
            onPressed: _noop,
          ),
        ),
        KitTaskCard(
          title: c.t('Choose the release branch', 'اختيار فرع الإصدار'),
          mark: KitTaskState.needsYou,
          onOpen: _noop,
          meta: [
            KitTaskMeta(c.t('owl', 'بومة')),
            KitTaskMeta(c.t('1 h ago', 'قبل ساعة')),
          ],
          flag: KitTaskFlag(
            kind: KitTaskFlagKind.needsYou,
            label: c.t('Which branch to ship?', 'أي فرع يُشحن؟'),
          ),
        ),
      ],
    ),
  ),
  // kit_nav.dart: the dock, the rail and the sidebar by window; the
  // sidebar widens with larger text so its header and primary keep whole.
  KitOverflowScene(
    const ['KitNav', 'KitNavBar', 'KitNavRail'],
    'default',
    host: KitOverflowHost.fill,
    build: (_, c) => KitNav(
      destinations: [
        KitNavDestination(
          label: c.t('Work', 'العمل'),
          icon: AppIconography.workspace,
          pane: (_) => KitText(
            c.t(
              'Conversation 1: the quick brown fox jumps over.',
              'المحادثة 1: الثعلب البني السريع يقفز.',
            ),
          ),
        ),
        KitNavDestination(
          label: c.t('Inbox', 'الوارد'),
          icon: AppIconography.activity,
          needsYou: 3,
        ),
        KitNavDestination(
          label: c.t('Project', 'المشروع'),
          icon: AppIconography.files,
        ),
        KitNavDestination(
          label: c.t('Settings', 'الإعدادات'),
          icon: AppIconography.settings,
        ),
      ],
      selected: 0,
      onSelected: (_) {},
      sidebarHeader: KitShellControls(
        server: c.t('phone', 'الهاتف'),
        serverStatus: c.t('Connected', 'متصل'),
        serverTone: AppStatusTone.ok,
        onServer: _noop,
        project: 'opencode',
        onProject: _noop,
        onSearch: _noop,
        layout: KitShellControlsLayout.sidebar,
      ),
      sidebarPrimary: KitAction(
        label: c.t('New conversation', 'محادثة جديدة'),
        onPressed: _noop,
      ),
      child: const SizedBox.expand(),
    ),
  ),
];
