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

import 'kit_overflow_chat_scenes.dart';
import 'kit_overflow_data_scenes.dart';
import 'kit_overflow_form_scenes.dart';
import 'kit_overflow_layout_scenes.dart';
import 'kit_overflow_team_scenes.dart';
import 'kit_chat_overflow_scenes.dart';
import 'kit_core_overflow_scenes.dart';
import 'kit_forms_overflow_scenes.dart';
import 'kit_chats_overflow_scenes.dart';
import 'kit_agent_cards_overflow_scenes.dart';

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
  const KitSceneCopy({required this.rtl});

  final bool rtl;

  /// English in left to right, Arabic in right to left.
  String t(String en, String ar) => rtl ? ar : en;
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
