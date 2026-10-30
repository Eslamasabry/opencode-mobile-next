// Scenes for the G6 kit overflow matrix (docs/ux-system/revamp/STANDARDS.md
// §18, A11Y-2, LAY-4, KIT-24), read by test/text_scale_overflow_test.dart.
//
// The matrix finds the kit's parts itself, from the exports of
// lib/ui/kit/kit.dart, and fails when a part has no scene here or a scene
// names a part the kit no longer exports. A kit unit that adds a part adds
// one block at the end of [kitOverflowScenes] (append-only, like the other
// PROC-13 registries) and never edits the matrix. When the G4 manifest
// exposes the gallery scenes, the matrix reads those instead.
//
// A scene holds a part in realistic copy for each declared state, in
// English for left to right and Arabic for right to left.
import 'dart:async';
import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';

import 'kit_agent_cards_overflow_scenes.dart';
import 'kit_chat_overflow_scenes.dart';
import 'kit_chats_overflow_scenes.dart';
import 'kit_core_overflow_scenes.dart';
import 'kit_forms_overflow_scenes.dart';
import 'kit_overflow_chat_scenes.dart';
import 'kit_overflow_data_scenes.dart';
import 'kit_overflow_form_scenes.dart';
import 'kit_overflow_image_scenes.dart';
import 'kit_overflow_layout_scenes.dart';
import 'kit_overflow_team_scenes.dart';
import 'kit_overflow_usage_scenes.dart';
import 'kit_status_answer_overflow_scenes.dart';
import 'kit_welcome_overflow_scenes.dart';

part 'kit_overflow_scenes_core.dart';
part 'kit_overflow_scenes_groups.dart';

/// Where the matrix puts a scene.
enum KitOverflowHost {
  /// In a padded scrolling list, as rows and panels sit on a screen.
  list,

  /// As the whole body of the screen, for parts that fill it.
  fill,

  /// Opened from an empty screen through [KitOverflowScene.open].
  modal,
}

/// The words a scene shows, in the direction under test.
class KitSceneCopy {
  const KitSceneCopy({required this.rtl, this.stress});

  final bool rtl;

  /// The capacity gate's content injection (test/kit/kit_capacity_engine.dart):
  /// when set, every string a scene shows through [t] is replaced by what it
  /// returns (1,000 characters of prose, a 300-character token, CJK, emoji,
  /// huge numbers, nothing, or the pseudo-localized original). The overflow
  /// matrix leaves it null.
  final String Function(String text, {required bool rtl})? stress;

  /// English in left to right, Arabic in right to left.
  String t(String en, String ar) {
    final text = rtl ? ar : en;
    return stress == null ? text : stress!(text, rtl: rtl);
  }
}

/// One state of one part, as the overflow matrix pumps it.
class KitOverflowScene {
  const KitOverflowScene(
    this.parts,
    this.state, {
    this.build,
    this.open,
    this.host = KitOverflowHost.list,
    this.labelsOverflow = false,
  }) : assert((build == null) != (open == null));

  /// The manifest names this scene shows (a widget class or `showKit…`).
  final List<String> parts;

  /// The declared state (KIT-12) or `default`.
  final String state;
  final Widget Function(BuildContext context, KitSceneCopy copy)? build;
  final FutureOr<void> Function(BuildContext context, KitSceneCopy copy)? open;
  final KitOverflowHost host;

  /// For KitSegmented (KIT-24): the labels are too long for one line at
  /// text 2.0 on a phone, so the part must stack its KitChoiceRows.
  final bool labelsOverflow;

  String get id => '${parts.first}/$state';
}

void _noop() {}

List<KitAction> _tertiary(KitSceneCopy c) => [
  KitAction(label: c.t('Copy the address', 'نسخ العنوان'), onPressed: _noop),
  KitAction(label: c.t('Open settings', 'فتح الإعدادات'), onPressed: _noop),
];

Widget _row(KitSceneCopy c, {Widget? trailing, bool enabled = true}) => KitRow(
  leading: const KitRowIcon(AppIconography.terminal),
  title: c.t('Workstation on the office network', 'محطة العمل على شبكة المكتب'),
  supporting: TextSpan(
    text: c.t('Connected · last reply a minute ago', 'متصل · آخر رد قبل دقيقة'),
  ),
  trailing: trailing ?? const KitChevron(),
  enabled: enabled,
  onTap: _noop,
);

Future<void> _sheet(
  BuildContext context,
  KitSceneCopy c, {
  KitSheetHeight height = KitSheetHeight.content,
  bool loading = false,
  bool disabled = false,
}) => showKitSheet<void>(
  context,
  title: c.t('Language', 'اللغة'),
  subtitle: c.t('Words across the whole app', 'الكلمات في كل التطبيق'),
  height: height,
  loading: loading ? ValueNotifier(true) : null,
  body: (_) => Column(
    crossAxisAlignment: CrossAxisAlignment.stretch,
    children: [
      for (final (en, ar) in const [
        ('English', 'الإنجليزية'),
        ('Arabic', 'العربية'),
        ('German', 'الألمانية'),
      ])
        KitRow(
          leading: const KitRowIcon(AppIconography.info),
          title: c.t(en, ar),
          supporting: TextSpan(
            text: c.t('Used for menus and messages', 'للقوائم والرسائل'),
          ),
          onTap: _noop,
        ),
    ],
  ),
  primary: KitAction(
    label: c.t('Use English', 'استخدام العربية'),
    onPressed: disabled ? null : _noop,
  ),
  secondary: KitAction(
    label: c.t('Keep current', 'إبقاء الحالية'),
    onPressed: _noop,
  ),
  tertiary: [
    KitAction(label: c.t('More languages', 'لغات أخرى'), onPressed: _noop),
  ],
);

Future<void> _confirm(
  BuildContext context,
  KitSceneCopy c, {
  KitConfirmKind kind = KitConfirmKind.neutral,
  bool full = false,
}) => showKitConfirm(
  context,
  title: c.t('Delete the conversation?', 'حذف المحادثة؟'),
  body: c.t(
    'It is removed from the server for everyone who can open it.',
    'تُزال من الخادم لكل من يستطيع فتحها.',
  ),
  confirmLabel: c.t('Delete conversation', 'حذف المحادثة'),
  kind: kind,
  consequences: full
      ? [
          c.t('Its 42 messages are gone', 'تُحذف رسائلها الـ42'),
          c.t('Shared links stop working', 'تتوقف الروابط المشاركة'),
        ]
      : const [],
  typedName: full ? 'release-notes-draft' : null,
  details: full
      ? const [
          KitTechnicalValue('Address', 'http://192.168.1.20:4096/session'),
          KitTechnicalValue('Branch', 'feature/very-long-branch-name-here'),
        ]
      : const [],
);

/// Every scene in the matrix, one block per part, appended at the end.
final kitOverflowScenes = <KitOverflowScene>[
  ..._coreOverflowScenes,
  ..._groupOverflowScenes,
  // September 27 additions: real states, shared with no golden runner.
  ...kitOverflowChatScenes,
  ...kitOverflowDataScenes,
  ...kitOverflowFormScenes,
  ...kitOverflowLayoutScenes,
  ...kitOverflowTeamScenes,
  ...kitOverflowTeamSpecScenes,
  ...kitOverflowImageScenes,
  ...kitOverflowUsageScenes,
  KitOverflowScene(
    const ['KitReach'],
    'default',
    build: (_, c) => KitReach(
      reach: 16,
      child: Builder(
        builder: (context) => KitReach.extend(
          by: KitReach.of(context),
          child: KitText(
            c.t(
              'Find latest finance hub project in the folder',
              'ابحث عن أحدث مشروع للمالية في المجلد',
            ),
          ),
        ),
      ),
    ),
  ),
  KitOverflowScene(
    const ['KitPresence'],
    'default',
    build: (_, c) => Row(
      children: [
        Flexible(
          child: KitPresence(
            show: true,
            child: KitText(
              c.t(
                'Find latest finance hub project in the folder',
                'ابحث عن أحدث مشروع للمالية في المجلد',
              ),
            ),
          ),
        ),
      ],
    ),
  ),
  KitOverflowScene(
    const ['KitContinuedLine'],
    'default',
    build: (_, c) => KitContinuedLine(
      text: c.t(
        'Continued in Claude Code: fix the login redirect on the finance hub',
        'تابعتها في Claude Code: إصلاح إعادة توجيه تسجيل الدخول في مركز المالية',
      ),
      onOpen: _noop,
    ),
  ),
  KitOverflowScene(
    const ['KitContinuedLine'],
    'disabled',
    build: (_, c) => KitContinuedLine(
      text: c.t('Continued in Claude Code', 'تابعتها في Claude Code'),
      onOpen: _noop,
      gone: true,
    ),
  ),
  KitOverflowScene(
    const ['KitHandoffRecord'],
    'default',
    build: (_, c) => KitHandoffRecord(
      sizeBytes: 48200,
      omittedMessages: 12,
      redacted: true,
      markdown: c.t(
        '# Conversation record\n\nFix the login redirect on the finance hub.',
        '# سجل المحادثة\n\nإصلاح إعادة توجيه تسجيل الدخول في مركز المالية.',
      ),
      path: '.opencode/handoff/finance-hub-login-redirect-record.md',
      initiallyExpanded: true,
    ),
  ),
  KitOverflowScene(
    const ['KitLiveBackground'],
    'default',
    build: (_, c) => SizedBox(
      height: 240,
      child: KitLiveBackground(
        // The background is a layer; the words on it scroll in the room it
        // gives them, as the caller's content does.
        child: SingleChildScrollView(
          child: Center(
            child: KitText(
              c.t(
                'Find latest finance hub project in the folder',
                'ابحث عن أحدث مشروع للمالية في المجلد',
              ),
            ),
          ),
        ),
      ),
    ),
  ),
  KitOverflowScene(
    const ['KitLiveLookPreview'],
    'default',
    build: (_, c) => KitLiveLookPreview(
      style: kitLiveStyleById('aurora'),
      intensity: KitLiveIntensity.normal,
      enabled: true,
      onTap: _noop,
    ),
  ),
  KitOverflowScene(
    const ['KitOneTimeCode'],
    'default',
    build: (_, c) => KitOneTimeCode(
      code: 'VPFI-KC2ZK-LONGER',
      label: c.t('Your code', 'رمزك'),
      copyLabel: c.t('Copy code', 'نسخ الرمز'),
    ),
  ),
  KitOverflowScene(
    const ['KitSegmented'],
    'labels-overflow',
    labelsOverflow: true,
    build: (_, c) => KitSegmented<String>(
      semanticsLabel: c.t('Instruction scope', 'نطاق التعليمات'),
      segments: [
        KitSegment(
          value: 'conversation',
          label: c.t('Only the current conversation', 'المحادثة الحالية فقط'),
        ),
        KitSegment(
          value: 'project',
          label: c.t(
            'Every conversation in this project',
            'كل المحادثات في هذا المشروع',
          ),
        ),
        KitSegment(
          value: 'server',
          label: c.t(
            'Every conversation on this server',
            'كل المحادثات على هذا الخادم',
          ),
        ),
      ],
      selected: 'conversation',
      onChanged: (_) {},
    ),
  ),
  // The two repair lanes exercised different states under some identical
  // part/state names. Keep both sets; name the incoming variants explicitly
  // rather than discard a scenario or permit duplicate manifest ids.
  ..._testsDScenes([
    ...kitChatOverflowScenes,
    ...kitCoreOverflowScenes,
    ...kitFormsOverflowScenes,
    ...kitChatsOverflowScenes,
    ...kitAgentCardsOverflowScenes,
    ...kitStatusAnswerOverflowScenes,
    ...kitWelcomeOverflowScenes,
    // Infrastructure parts introduced by the September 27 kit migration.
    for (final segments in <String, List<String>>{
      'root': [],
      'default': ['lib', 'screens'],
      'collapsed': ['lib', 'features', 'projects', 'screens'],
      'long': ['a-long-project-folder-name', 'a-long-screen-file-name'],
    }.entries)
      KitOverflowScene(
        const ['KitBreadcrumb'],
        segments.key,
        build: (_, c) => KitBreadcrumb(
          rootLabel: c.t('Project root', 'جذر المشروع'),
          segments: segments.value,
          onSelected: (_) {},
        ),
      ),
    KitOverflowScene(
      const ['KitGroupNote'],
      'default',
      build: (_, c) => KitGroupNote(
        message: c.t(
          'Two settings are not available on this server.',
          'إعدادان غير متاحين على هذا الخادم.',
        ),
        action: KitAction(label: c.t('Why', 'لماذا'), onPressed: _noop),
      ),
    ),
    for (final state in ['default', 'filled', 'error', 'disabled'])
      KitOverflowScene(
        const ['KitField'],
        state,
        build: (_, c) => _OverflowTextController(
          text: state == 'default' ? '' : 'release-notes',
          builder: (controller) => KitField(
            label: c.t('Project name', 'اسم المشروع'),
            controller: controller,
            helper: c.t(
              'Used to find this project later.',
              'للعثور على المشروع لاحقاً.',
            ),
            error: state == 'error'
                ? c.t(
                    'Choose a name that is not already used.',
                    'اختر اسماً غير مستخدم.',
                  )
                : null,
            enabled: state != 'disabled',
            disabledReason: state == 'disabled'
                ? c.t(
                    'Reconnect to rename the project.',
                    'أعد الاتصال لتغيير اسم المشروع.',
                  )
                : null,
          ),
        ),
      ),
    for (final state in ['default', 'filled', 'partial', 'disabled'])
      KitOverflowScene(
        const ['KitSearchField'],
        state,
        build: (_, c) => _OverflowTextController(
          text: state == 'default' ? '' : 'release',
          builder: (controller) => KitSearchField(
            controller: controller,
            label: c.t('Search conversations', 'ابحث في المحادثات'),
            onChanged: (_) {},
            resultCount: state == 'default' ? null : 12,
            partial: state == 'partial',
            enabled: state != 'disabled',
            disabledReason: state == 'disabled'
                ? c.t(
                    'Connect to search conversations.',
                    'اتصل للبحث في المحادثات.',
                  )
                : null,
          ),
        ),
      ),
    KitOverflowScene(
      const ['KitSearchNoMatch'],
      'no-match',
      build: (_, c) => KitSearchNoMatch(
        query: 'release-notes',
        what: c.t('conversations', 'المحادثات'),
        onClear: _noop,
      ),
    ),
    KitOverflowScene(
      const ['KitScrollArea', 'KitScrollbar', 'KitOwnScrollbar'],
      'scrollable',
      host: KitOverflowHost.fill,
      build: (_, c) => _OverflowScrollFrame(copy: c),
    ),
    for (final layout in KitShellControlsLayout.values)
      KitOverflowScene(
        const ['KitTopBar', 'KitShellControls'],
        layout.name,
        build: (_, c) => KitTopBar.shell(
          controls: KitShellControls(
            server: c.t('Office computer', 'حاسوب المكتب'),
            serverStatus: c.t('Reconnecting', 'جارٍ إعادة الاتصال'),
            onServer: _noop,
            onSearch: _noop,
            needsYou: 2,
            project: 'opencode',
            onProject: _noop,
            layout: layout,
          ),
        ),
      ),
    KitOverflowScene(
      const ['KitStatusScope', 'KitStatusLineSlot', 'KitStatusContribution'],
      'contributed',
      host: KitOverflowHost.fill,
      build: (_, c) => _OverflowStatusFrame(copy: c),
    ),
    KitOverflowScene(
      const ['KitTabStrip'],
      'counts-and-attention',
      build: (_, c) => KitTabStrip(
        selected: 1,
        onSelected: (_) {},
        tabs: [
          KitTab(label: c.t('Working', 'قيد العمل'), count: 12),
          KitTab(
            label: c.t('Needs your answer', 'بانتظار إجابتك'),
            count: 3,
            needsYou: 3,
          ),
          KitTab(label: c.t('Finished', 'مكتمل'), count: 48),
        ],
      ),
    ),
    for (final enabled in [true, false])
      KitOverflowScene(
        const ['KitTappable'],
        enabled ? 'enabled' : 'disabled',
        build: (_, c) => KitTappable(
          onTap: enabled ? _noop : null,
          disabledReason: enabled
              ? null
              : c.t('Reconnect first.', 'أعد الاتصال أولاً.'),
          child: KitText(c.t('Open project details', 'افتح تفاصيل المشروع')),
        ),
      ),
    KitOverflowScene(
      const ['KitInkLayer'],
      'at rest',
      build: (_, c) => KitInkLayer(
        shape: const RoundedRectangleBorder(),
        child: KitText(c.t('Open project details', 'افتح تفاصيل المشروع')),
      ),
    ),
    for (final kind in KitCodeKind.values)
      KitOverflowScene(
        const ['KitCodeBlock'],
        kind.name,
        build: (_, c) => KitCodeBlock(
          text:
              'flutter test test/project_settings_test.dart\nAll tests passed.',
          kind: kind,
          language: kind == KitCodeKind.code ? 'dart' : null,
          caption: c.t('Project checks', 'فحوصات المشروع'),
        ),
      ),
    // kit_sheet.dart (showKitFramedSheet, slice-P9.10): a body that draws
    // its own frame.
    KitOverflowScene(
      const ['showKitFramedSheet'],
      'default',
      host: KitOverflowHost.modal,
      open: (context, c) => showKitFramedSheet<void>(
        context,
        maxWidth: 720,
        useSafeArea: true,
        builder: (sheetContext) => KitSheet(
          title: c.t('Choose a project folder', 'اختر مجلد المشروع'),
          subtitle: c.t(
            'Claude Code works inside one folder of the Ubuntu on this phone.',
            'يعمل Claude Code داخل مجلد واحد في أوبونتو على هذا الهاتف.',
          ),
          handle: false,
          onClose: () => Navigator.of(sheetContext).pop(),
          primary: KitAction(
            label: c.t('Continue', 'متابعة'),
            onPressed: _noop,
          ),
          child: KitText(c.t('my-first-project', 'my-first-project')),
        ),
      ),
    ),
  ]),
  KitOverflowScene(
    const ['KitFloatingPanel'],
    'default',
    build: (_, c) => SizedBox(
      height: 400,
      child: KitFloatingPanel(
        onDismiss: _noop,
        child: KitText(
          c.t(
            'A small panel over the home screen',
            'لوحة صغيرة فوق الشاشة الرئيسية',
          ),
        ),
      ),
    ),
  ),
  KitOverflowScene(
    const ['KitSteerBox'],
    'default',
    build: (_, c) => KitSteerBox(
      title: c.t('Add to this turn', 'إضافة إلى هذا الدور'),
      controller: TextEditingController(
        text: c.t('Also add a test', 'أضف اختبارًا أيضًا'),
      ),
      hint: c.t('Write a message', 'اكتب رسالة'),
      sendLabel: c.t('Send', 'إرسال'),
      phase: KitSteerBoxPhase.ready,
      message: c.t(
        'The conversation changed. Your message is still here.',
        'تغيّرت المحادثة. رسالتك ما زالت هنا.',
      ),
      onSend: (_) {},
      onDictate: _noop,
      onClose: _noop,
    ),
  ),
  KitOverflowScene(
    const ['KitSteerBox'],
    'disabled',
    build: (_, c) => KitSteerBox(
      title: c.t('Add to this turn', 'إضافة إلى هذا الدور'),
      controller: TextEditingController(
        text: c.t('A draft that stays', 'مسودة باقية'),
      ),
      hint: c.t('Write a message', 'اكتب رسالة'),
      sendLabel: c.t('Send', 'إرسال'),
      phase: KitSteerBoxPhase.ready,
      blockedReason: c.t(
        'Waiting for conversation details…',
        'بانتظار تفاصيل المحادثة…',
      ),
      onSend: (_) {},
      onDictate: _noop,
      onClose: _noop,
    ),
  ),
  KitOverflowScene(
    const ['KitSteerBox'],
    'working',
    build: (_, c) => KitSteerBox(
      title: c.t('Add to this turn', 'إضافة إلى هذا الدور'),
      controller: TextEditingController(
        text: c.t('Also add a test', 'أضف اختبارًا أيضًا'),
      ),
      hint: c.t('Write a message', 'اكتب رسالة'),
      sendLabel: c.t('Send', 'إرسال'),
      phase: KitSteerBoxPhase.sending,
      onSend: (_) {},
      onDictate: _noop,
      onClose: _noop,
    ),
  ),
  KitOverflowScene(
    const ['KitSteerBox'],
    'answered',
    build: (_, c) => KitSteerBox(
      title: c.t('Add to this turn', 'إضافة إلى هذا الدور'),
      controller: TextEditingController(text: c.t('Done', 'تم')),
      hint: c.t('Write a message', 'اكتب رسالة'),
      sendLabel: c.t('Send', 'إرسال'),
      phase: KitSteerBoxPhase.sent,
      onSend: (_) {},
      onDictate: _noop,
      onClose: _noop,
    ),
  ),
  KitOverflowScene(
    const ['KitWidgetPreview'],
    'default',
    build: (_, c) => const KitWidgetPreview(
      size: KitWidgetPreviewSize.list,
      background: KitWidgetPreviewBackground.tinted,
      accent: Color(0xFF4187CD),
    ),
  ),
  KitOverflowScene(
    const ['KitWidgetStyleForm'],
    'default',
    build: (_, c) => KitWidgetStyleForm(
      previewSize: 'card',
      background: 'tinted',
      accent: 'blue',
      accentIds: const ['wallpaper', 'app', 'green', 'teal', 'blue', 'amber'],
      accentName: (id) => id,
      accentColor: (id) => Colors.primaries[id.length % 10],
      onPreviewSize: (_) {},
      onBackground: (_) {},
      onAccent: (_) {},
    ),
  ),
  // Immutable warning content: exercise both HTTP risk levels and both
  // certificate decisions through the actual sheet, including its actions.
  for (final privateNetwork in [false, true])
    KitOverflowScene(
      const ['KitServerTrustBody', 'showKitServerTrust'],
      privateNetwork ? 'private-http' : 'default',
      host: KitOverflowHost.modal,
      open: (context, _) => showKitServerTrust(
        context,
        origin: privateNetwork
            ? 'http://workstation.example-tailnet.ts.net:4098'
            : 'http://development-server.example.com:4098',
        privateNetwork: privateNetwork,
      ),
    ),
  for (final certificateChanged in [false, true])
    KitOverflowScene(
      const ['KitServerTrustBody', 'showKitServerTrust'],
      certificateChanged ? 'certificate-changed' : 'certificate',
      host: KitOverflowHost.modal,
      open: (context, _) => showKitServerTrust(
        context,
        origin: 'https://development-server.example.com:4098',
        fingerprintSha256:
            '0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef',
        certificateChanged: certificateChanged,
        validFrom: DateTime.utc(2026, 10, 15),
        validTo: DateTime.utc(2027, 10, 15),
      ),
    ),
  KitOverflowScene(
    const ['KitLinkScope'],
    'default',
    build: (_, c) => KitLinkScope(
      claim: (_, _) => false,
      child: KitText(
        c.t(
          'Links in this text go through the safe gate',
          'الروابط في هذا النص تمر عبر البوابة الآمنة',
        ),
      ),
    ),
  ),
  KitOverflowScene(
    const ['KitWebPreview'],
    'default',
    build: (_, c) => SizedBox(
      height: 400,
      child: KitWebPreview(
        origin: Uri.parse('http://127.0.0.1:5173/'),
        controller: KitWebPreviewController(),
        supported: false,
        onLeave: (_) {},
        unsupportedAction: KitAction(
          label: c.t('Open in the browser', 'فتح في المتصفح'),
          onPressed: _noop,
        ),
      ),
    ),
  ),
  // A very long address with no spaces, the case the sheet exists for.
  KitOverflowScene(
    const ['showKitTerminalLinkSheet'],
    'default',
    host: KitOverflowHost.modal,
    open: (context, _) => showKitTerminalLinkSheet(
      context,
      'https://claude.com/cai/oauth/authorize?code=true&client_id='
      '9d1c250a-e61b-44d9-88ed-5944d1962f5e&response_type=code&redirect_uri='
      'https%3A%2F%2Fplatform.claude.com%2Foauth%2Fcode%2Fcallback&scope='
      'org%3Acreate_api_key+user%3Aprofile+user%3Ainference',
    ),
  ),
  for (final open in [false, true])
    KitOverflowScene(
      const ['KitGuide'],
      open ? 'question' : 'default',
      host: KitOverflowHost.fill,
      build: (_, c) => KitGuide(
        items: [
          KitGuideItem(
            id: 'colours',
            label: c.t('Colours', 'الألوان'),
            value: c.t('Dark, Tokyo Night', 'داكن، طوكيو ليلًا'),
            next: true,
          ),
          KitGuideItem(
            id: 'style',
            label: c.t('Style', 'النمط'),
            value: c.t('Off in Saver', 'متوقف في التوفير'),
            held: true,
          ),
          KitGuideItem(
            id: 'comfort',
            label: c.t('Text & motion', 'النص والحركة'),
            value: c.t('Default, full motion', 'افتراضي، حركة كاملة'),
            done: true,
          ),
        ],
        selected: open ? 'colours' : null,
        onItem: (_) {},
        group: open
            ? KitGuideGroup(label: c.t('Colours', 'الألوان'), onBack: _noop)
            : null,
        question: open
            ? KitGuideQuestion(
                id: 'mode',
                title: c.t(
                  'Glow while a reply is written?',
                  'توهج أثناء كتابة الرد؟',
                ),
                changes: c.t(
                  'Buttons, links, the send key and the glow',
                  'الأزرار والروابط وزر الإرسال والتوهج',
                ),
                child: KitText(c.t('Pick one.', 'اختر واحدًا.')),
              )
            : null,
        home: KitText(
          c.t(
            'Start with Colours, the biggest change. Every choice saves as you go.',
            'ابدأ بالألوان، فهي التغيير الأكبر. يُحفظ كل اختيار فورًا.',
          ),
        ),
        next: KitGuideNext(
          count: c.t(
            '10 quick questions, all optional',
            '10 أسئلة سريعة، كلها اختيارية',
          ),
          label: c.t('Start with Colours', 'ابدأ بالألوان'),
          onPressed: _noop,
          ready: true,
        ),
      ),
    ),
  KitOverflowScene(
    const ['KitFineTunePanel'],
    'default',
    build: (_, c) => KitFineTunePanel(
      resetLabel: c.t(
        'Reset to Brutal defaults',
        'إعادة إعدادات جريء الافتراضية',
      ),
      onReset: () {},
      knobs: [
        KitFineTuneKnob<KitDensity>(
          title: c.t('Density', 'الكثافة'),
          hint: c.t(
            'Compact fits more on the screen.',
            'المضغوط يعرض المزيد على الشاشة.',
          ),
          defaultNote: c.t('Default: Comfortable', 'الافتراضي: مريح'),
          selected: KitDensity.compact,
          segments: [
            KitSegment(
              value: KitDensity.comfortable,
              label: c.t('Comfortable', 'مريح'),
            ),
            KitSegment(
              value: KitDensity.compact,
              label: c.t('Compact', 'مضغوط'),
            ),
          ],
          onChanged: (_) {},
        ),
      ],
    ),
  ),
  KitOverflowScene(
    const ['KitCarousel'],
    'default',
    build: (_, c) => KitCarousel(
      semanticsLabel: c.t('Themes', 'السمات'),
      itemCount: 4,
      itemHeight: KitOptionCard.themeHeight,
      itemBuilder: (context, i) => KitOptionCard.plain(
        icon: AppIconography.sparkle,
        label: c.t('Soft ring', 'حلقة ناعمة'),
        caption: c.t(
          'Drawings appear, nothing keeps moving',
          'تظهر الرسوم ولا يتحرك شيء',
        ),
        selected: i == 0,
        onPressed: _noop,
      ),
    ),
  ),
  KitOverflowScene(
    const ['KitAccentRow'],
    'default',
    build: (context, c) => KitAccentRow(
      semanticsLabel: c.t('Accent', 'لون التمييز'),
      matchLabel: c.t('Match theme', 'مثل السمة'),
      theme: KitTokens.of(context).roles,
      swatches: [
        for (final accent in appearanceAccents)
          KitAccentSwatch(id: accent.id, color: accent.dark, label: accent.id),
      ],
      selected: 'blue',
      onChanged: (_) {},
    ),
  ),
  KitOverflowScene(
    const ['KitStreamCaret'],
    'default',
    build: (_, c) => Text.rich(
      TextSpan(
        text: c.t('On it, grouping the options', 'جارٍ العمل، أجمع الخيارات'),
        children: const [WidgetSpan(child: KitStreamCaret())],
      ),
    ),
  ),
  for (final enabled in [true, false])
    KitOverflowScene(
      const ['KitOptionCard'],
      enabled ? 'default' : 'disabled',
      build: (context, c) => SizedBox(
        height: KitOptionCard.themeHeight + 40,
        child: enabled
            ? KitOptionCard.plain(
                icon: AppIconography.sparkle,
                label: c.t('Calm', 'هادئ'),
                caption: c.t(
                  'Drawings appear, nothing keeps moving and nothing '
                      'celebrates',
                  'تظهر الرسوم ولا يتحرك شيء ولا احتفالات',
                ),
                selected: true,
                onPressed: _noop,
              )
            : KitOptionCard.theme(
                roles: KitTokens.of(context).roles,
                label: c.t('Material You', 'مواد يو'),
                selected: false,
                onPressed: null,
                disabledReason: c.t(
                  'Not available on this phone',
                  'غير متاح على هذا الهاتف',
                ),
              ),
      ),
    ),
  for (final enabled in [true, false])
    KitOverflowScene(
      const ['KitStepSlider'],
      enabled ? 'default' : 'disabled',
      build: (_, c) => KitStepSlider<int>(
        semanticsLabel: c.t('Text size', 'حجم النص'),
        selected: 2,
        steps: [
          KitSliderStep(value: 1, label: c.t('Smallest', 'الأصغر')),
          KitSliderStep(value: 2, label: c.t('Small', 'صغير')),
          KitSliderStep(value: 3, label: c.t('Default', 'افتراضي')),
          KitSliderStep(value: 4, label: c.t('Large', 'كبير')),
        ],
        onChanged: enabled ? (_) {} : null,
      ),
    ),
  // KitChoiceTiles (vision model picker, 2026-10-20): each declared state.
  for (final state in const ['default', 'disabled', 'working'])
    KitOverflowScene(
      const ['KitChoiceTiles'],
      state,
      build: (_, c) => KitChoiceTiles<String>(
        semanticsLabel: c.t('Model', 'النموذج'),
        selected: 'default',
        busy: state == 'working' ? 'cheapest' : null,
        onSelected: (_) {},
        more: KitChoiceMore(
          label: c.t('All models (42)', 'كل النماذج (42)'),
          onTap: () {},
        ),
        choices: [
          for (final (value, title, name) in [
            ('default', c.t('Default', 'الافتراضي'), 'Claude Sonnet 5.5'),
            ('recent', c.t('Recently used', 'استُخدم مؤخرًا'), 'GPT-6 Sol'),
            ('cheapest', c.t('Cheapest', 'الأرخص'), 'Nemotron 3.5 Super'),
          ])
            KitChoice<String>(
              value: value,
              title: title,
              supporting: name,
              enabled: state != 'disabled',
              disabledReason: state == 'disabled'
                  ? c.t('Saving the choice…', 'جارٍ حفظ الاختيار…')
                  : null,
            ),
        ],
      ),
    ),
  // KitPageHeading (vision Files page, 2026-10-20): the switcher line
  // (default) and the plain line with no target (disabled).
  for (final state in const ['default', 'disabled'])
    KitOverflowScene(
      const ['KitPageHeading'],
      state,
      build: (_, c) => KitPageHeading(
        title: c.t('Files', 'الملفات'),
        line: c.t('neon-breaker / src', 'neon-breaker / src'),
        onLineTap: state == 'default' ? () {} : null,
        lineTapLabel: state == 'default'
            ? c.t(
                'Project neon-breaker. Switch project',
                'المشروع neon-breaker. تبديل المشروع',
              )
            : null,
      ),
    ),
  // KitWheel (the scheduling sheet's time wheel): two columns, the hour
  // and the minute, at every scale (the wheel caps its own growth).
  KitOverflowScene(
    const ['KitWheel'],
    'default',
    build: (_, c) => KitWheel(
      columns: [
        KitWheelColumn<int>(
          values: [for (var h = 1; h <= 12; h++) h],
          selected: 9,
          label: (h) => '$h',
          semanticsLabel: c.t('Hour', 'الساعة'),
          onChanged: (_) {},
        ),
        KitWheelColumn<String>(
          values: const ['00', '15', '30', '45'],
          selected: '00',
          label: (m) => m,
          semanticsLabel: c.t('Minute', 'الدقيقة'),
          onChanged: (_) {},
        ),
        KitWheelColumn<String>(
          values: [c.t('AM', 'ص'), c.t('PM', 'م')],
          selected: c.t('AM', 'ص'),
          label: (m) => m,
          semanticsLabel: c.t('Morning or evening', 'صباحًا أو مساءً'),
          onChanged: (_) {},
        ),
      ],
    ),
  ),
  KitOverflowScene(
    const ['KitTextHalo'],
    'default',
    build: (_, c) => KitTextHalo(
      ground: const Color(0xFF0B0C0E),
      child: KitText(
        c.t(
          'Find latest finance hub project in the folder',
          'ابحث عن أحدث مشروع للمالية في المجلد',
        ),
        role: KitTextRole.secondary,
        tone: KitTextTone.tertiary,
      ),
    ),
  ),
  KitOverflowScene(
    const ['KitTip', 'KitTipBubble'],
    'shown',
    build: (_, c) => Padding(
      padding: const EdgeInsets.only(top: 160),
      child: KitTip(
        message: c.t(
          'Hold to stop after this step',
          'اضغط مطولًا للتوقف بعد هذه الخطوة',
        ),
        visible: true,
        onDismissed: () {},
        child: const SizedBox.square(dimension: 48),
      ),
    ),
  ),
  KitOverflowScene(
    const ['KitWordText'],
    'default',
    build: (_, c) => KitWordText(
      c.t(
        'Ran the checkout tests on a clean cache and retry when the first '
            'run fails because the price refresh races the coupon check',
        'شغّل اختبارات الدفع على ذاكرة نظيفة وأعد المحاولة عندما يفشل '
            'التشغيل الأول لأن تحديث السعر يسبق فحص القسيمة',
      ),
      maxLines: 2,
      role: KitTextRole.secondary,
      tone: KitTextTone.secondary,
    ),
  ),
  KitOverflowScene(
    const ['KitErrorFallback'],
    'default',
    build: (_, c) => const KitErrorFallback(),
  ),
  // Impeccable layout pass 2026-10-28: KitScrollFade, KitLeadColumn.
  KitOverflowScene(
    const ['KitScrollFade'],
    'default',
    build: (_, c) => KitScrollFade(
      child: SingleChildScrollView(
        scrollDirection: Axis.horizontal,
        child: Row(
          children: [
            KitChip(label: c.t('Ask first', 'اسأل أولًا')),
            const SizedBox(width: 8),
            KitChip(label: c.t('Auto Accept', 'قبول تلقائي')),
          ],
        ),
      ),
    ),
  ),
  KitOverflowScene(
    const ['KitLeadColumn'],
    'default',
    build: (_, c) => KitLeadColumn.compact(
      child: KitNotice(
        message: c.t(
          'No reply came back from the model this time',
          'لم يصل أي رد من النموذج هذه المرة',
        ),
      ),
    ),
  ),
  // kit_frost.dart (FROST, 2026-10-30): the composer pane as glass.
  KitOverflowScene(
    const ['KitFrost'],
    'default',
    build: (context, c) => KitFrost(
      role: KitFrostRole.composer,
      strength: KitFrostStrength.balanced,
      shown: true,
      tint: KitTokens.of(context).roles.surface2,
      borderRadius: BorderRadius.circular(26),
      child: KitText(
        c.t(
          'Steer while it works on the focus ring tests',
          'وجّهه أثناء عمله على اختبارات حلقة التركيز',
        ),
      ),
    ),
  ),
];

Iterable<KitOverflowScene> _testsDScenes(List<KitOverflowScene> scenes) =>
    scenes.map(
      (scene) => KitOverflowScene(
        scene.parts,
        'tests-d-${scene.state}',
        build: scene.build,
        open: scene.open,
        host: scene.host,
        labelsOverflow: scene.labelsOverflow,
      ),
    );

/// A valid 1x1 opaque PNG (the fixture kit_image_test.dart uses).
const _onePixelPng = <int>[
  0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
  0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
  0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01, 0x08, 0x02, 0x00, 0x00, 0x00,
  0x90, 0x77, 0x53, 0xDE,
  0x00, 0x00, 0x00, 0x0C, 0x49, 0x44, 0x41, 0x54,
  0x08, 0xD7, 0x63, 0xF8, 0xCF, 0xC0, 0x00, 0x00, 0x03, 0x01, 0x01, 0x00,
  0x18, 0xDD, 0x8D, 0xB0,
  0x00, 0x00, 0x00, 0x00, 0x49, 0x45, 0x4E, 0x44, 0xAE, 0x42, 0x60, 0x82, //
];

/// Owns the editing state for one matrix mount and releases it on teardown.
class _OverflowTextController extends StatefulWidget {
  const _OverflowTextController({required this.text, required this.builder});
  final String text;
  final Widget Function(TextEditingController) builder;
  @override
  State<_OverflowTextController> createState() =>
      _OverflowTextControllerState();
}

class _OverflowTextControllerState extends State<_OverflowTextController> {
  late final controller = TextEditingController(text: widget.text);
  @override
  void dispose() {
    controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => widget.builder(controller);
}

class _OverflowScrollFrame extends StatefulWidget {
  const _OverflowScrollFrame({required this.copy});
  final KitSceneCopy copy;
  @override
  State<_OverflowScrollFrame> createState() => _OverflowScrollFrameState();
}

class _OverflowScrollFrameState extends State<_OverflowScrollFrame> {
  final controller = ScrollController();
  @override
  void dispose() {
    controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => KitScrollArea(
    builder: (_) => KitScrollbar(
      controller: controller,
      child: ListView.builder(
        controller: controller,
        itemCount: 30,
        itemBuilder: (_, i) =>
            KitRow(title: widget.copy.t('Conversation $i', 'المحادثة $i')),
      ),
    ),
  );
}

class _OverflowStatusFrame extends StatefulWidget {
  const _OverflowStatusFrame({required this.copy});
  final KitSceneCopy copy;
  @override
  State<_OverflowStatusFrame> createState() => _OverflowStatusFrameState();
}

class _OverflowStatusFrameState extends State<_OverflowStatusFrame> {
  final conditions = ValueNotifier<List<KitStatus>>(const []);
  @override
  void dispose() {
    conditions.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => KitStatusScope(
    conditions: conditions,
    child: KitStatusLineSlot(
      child: KitStatusContribution(
        status: KitStatus(
          kind: KitStatusKind.info,
          icon: AppIconography.info,
          message: widget.copy.t(
            'Your settings were saved on this phone.',
            'حُفظت إعداداتك على هذا الهاتف.',
          ),
        ),
        child: ListView(
          children: [
            KitText(widget.copy.t('Project settings', 'إعدادات المشروع')),
          ],
        ),
      ),
    ),
  );
}
