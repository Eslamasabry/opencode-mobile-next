// Gate G4 (docs/ux-system/revamp/STANDARDS.md §18): the kit manifest.
//
// A pure-Dart source scan, no widget pumping. It reads the exports of
// `lib/ui/kit/kit.dart` (following `show`/`hide` combinators, re-exports
// and `part` files, with either quote style) and also scans every `.dart`
// file under `lib/ui/kit/`, so a kit file that screens import by path is
// seen too. It collects every public widget class, every `KitScene`
// subclass and every top-level `showKit…` function. Comments are stripped
// before any code is read. Each part must then have what the rulebook asks
// for:
//
// - exported (KIT-14, NAME-1): reachable from `kit.dart`, not only by path.
// - name (NAME-1): class `Kit<Name>` in `lib/ui/kit/kit_<snake>.dart`
//   (chat parts in `lib/ui/kit/chat/`, scenes in `lib/ui/kit/scenes/`).
//   A part may share its file with the file's own part when that part's
//   frozen spec (`docs/ux-system/kit-api/<FilePart>.md`, FilePart being the
//   file name in PascalCase) declares `class <Name>` (KitAvatar and KitZoom
//   in kit_image.dart, KitSwap in motion/kit_motion_parts.dart). Such a
//   part's gallery and unit test are its file's.
// - states (KIT-12): a `States: …` line in its doc comment naming the states
//   it has from {loading, empty, error, disabled, working, answered}, for
//   example `/// States: loading, empty, error.` The part's own fields add
//   required states: a nullable `on…` callback needs `disabled`; a
//   `working`/`busy`/`sending` flag or an `onSubmit…`/`onSend…` callback
//   needs `working`; a Future, Stream, AsyncSnapshot or a List/Iterable/Map
//   of non-UI values (server data) needs loading, empty and error. A part
//   that needs none may write `/// States: none — <why, three words or
//   more>.`; a bare `States: none.` fails.
// - stateScenes (KIT-12, TEST-9): per declared state, dark and light
//   goldens at 412×915 in its gallery, named `<snake>_<state>…` in the
//   `name:` of a `kitGalleryShot` call or in a `matchesGoldenFile` literal
//   (the mode is a literal `dark`/`light` or `$mode` with both literals in
//   the file; 412×915 is the `size:` argument or `412x915` in the name).
// - gallery (TEST-9, TEST-14, LAY-4, KIT-32): `test/goldens/kit/
//   <snake>_golden_test.dart` with a golden at phone 412×915 and one at
//   1280×800, and a `…text2…` golden shot at `textScale: 2`; for a scene,
//   dark and light goldens (TEST-14). Owner decision (2026-09-27): Arabic
//   is dropped (no `…_ar_…` shot required) and galleries are required only
//   at those two sizes, not every `kitGallerySizes` size. Shots are read
//   from `kitGalleryShot(…)` and `kitGalleryPart(…)` calls and from any
//   call whose `name:` is a `kitGalleryName(…)` (a gallery's own helper).
// - test (TEST-15, NAME-1): `test/kit/<snake>_test.dart`.
// - docRow (KIT-14): a row in the `kit.dart` doc table naming `[<Name>]`.
// - motion (TEST-15, G8): named in the code of `test/kit_motion_test.dart`
//   (by class or by its `showKit…` opener), or that file calls
//   `readKitManifest(`.
// - keyboard (TEST-15, G14): modal parts and rows, likewise in
//   `test/kit/kit_keyboard_test.dart`.
// - overflow (TEST-15, G6): likewise in `test/text_scale_overflow_test.dart`.
// - openerReturn (KIT-11): a `showKit…` returns `Future<…>` (`showKitSheet`
//   `Future<T?>`, `showKitConfirm` `Future<bool>`, `showKitInputDialog`
//   `Future<String?>`); only `showKitUndo` returns `void`.
// - openerKey (KIT-10): a `showKit…` declares at least one optional
//   `Key? …Key` parameter.
// - harness (LAY-4, TEST-9): `kitGallerySizes` and `kitGalleryScaledSizes`
//   in the gallery harness both hold the two gallery sizes (412×915,
//   1280×800).
//
// InheritedWidget scopes (KitEffectsScope and the like) draw nothing, so
// they need only the exported, name, test and docRow checks. A class whose
// doc comment starts `Retired by kit-…` is a forwarding wrapper a unit moved
// into the kit unchanged (KIT-43, R12); the G2 ratchet counts its callers
// down, so it is not a part here. A KitScene
// (drawn by KitIllustration, TEST-14) needs exported, name (in
// `lib/ui/kit/scenes/`), a docRow and a gallery with dark and light shots.
//
// Parts that predate the gate sit in `kit_manifest_allowlist.json`
// (check -> subjects). The allowlist only shrinks:
// - a violation not on it fails;
// - an entry that no longer fails (stale) fails too, and the message
//   prints the smaller allowlist to commit; shrink it in place with
//     KIT_MANIFEST_WRITE=1 flutter test test/kit/kit_manifest_test.dart
//   (the pinned Flutter from AGENTS.md), which never adds an entry;
// - an entry outside [_creationAllowlist] (the allowlist when the gate was
//   made) and [_deferredAtKitMerge] (the gaps left when the last kit unit
//   merged, each with who closes it) fails, so a new part cannot be
//   added. Changing that ceiling is a gate change (KIT-44: a
//   `ratchet-tighten: G4 <check>` commit).
//
// The motion, keyboard, overflow and gallery-guideline gates (G8x, G14x,
// G6, G5) read the same manifest instead of hand lists:
//   import 'kit_manifest_test.dart' show readKitManifest, KitManifestPart;
// A consumer whose code calls `readKitManifest(` counts as covering every
// part for its check.
import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

/// The states a kit part may declare (KIT-12).
const kitManifestStates = <String>{
  'loading',
  'empty',
  'error',
  'disabled',
  'working',
  'answered',
};

/// The checks this gate runs; the allowlist's keys.
const kitManifestChecks = <String>[
  'exported',
  'name',
  'states',
  'stateScenes',
  'gallery',
  'test',
  'docRow',
  'motion',
  'keyboard',
  'overflow',
  'openerReturn',
  'openerKey',
  'harness',
];

const _kitLibrary = 'lib/ui/kit/kit.dart';
const _kitDirectory = 'lib/ui/kit';
const _allowlistPath = 'test/kit/kit_manifest_allowlist.json';
const _galleryHarness = 'test/goldens/kit/kit_gallery.dart';
const _motionTest = 'test/kit_motion_test.dart';
const _keyboardTest = 'test/kit/kit_keyboard_test.dart';
const _overflowTest = 'test/text_scale_overflow_test.dart';

/// The gallery sizes (owner decision 2026-09-27: phone and PC only) and
/// the TEST-9 scaled (text 2.0) sizes.
const _gallerySizes = ['412x915', '1280x800'];
const _test9ScaledSizes = ['412x915', '1280x800'];

/// Widgets whose subclasses draw nothing: plumbing, not drawn parts.
const _scopeBases = <String>{
  'InheritedWidget',
  'InheritedNotifier',
  'InheritedModel',
  'InheritedTheme',
  'ParentDataWidget',
};

/// Framework widget bases that may not be in the widget catalogue.
const _widgetBases = <String>{
  'Widget',
  'StatelessWidget',
  'StatefulWidget',
  'ProxyWidget',
  'RenderObjectWidget',
  'SingleChildRenderObjectWidget',
  'MultiChildRenderObjectWidget',
  'LeafRenderObjectWidget',
  'SlottedMultiChildRenderObjectWidget',
  'ImplicitlyAnimatedWidget',
  'AnimatedWidget',
  ..._scopeBases,
};

/// Nullable callbacks whose null is not a disabled state, per the part's
/// frozen spec: `Part.field` -> why.
const _nullNotDisabled = <String, String>{
  // KitChip.md: a plain chip has no action; a chip that cannot act now is
  // not shown (STATE-8), so there is no disabled chip.
  'KitChip.onPressed': 'a plain chip has no action',
  'KitChip.onRemove': 'only the removable kind has a remove target',
};

/// Parts whose text-2.0 golden waits on another unit, per their frozen
/// spec: part -> why. Remove the entry when that unit merges.
const _text2Pending = <String, String>{};

/// Element types of a List/Iterable/Map field that are UI, not server data.
const _uiElementTypes = <String>{
  'Widget',
  'String',
  'int',
  'double',
  'num',
  'bool',
  'IconData',
  'Color',
  'Offset',
  'Size',
  'Rect',
  'InlineSpan',
  'TextSpan',
  'Key',
  'Duration',
  'Animation',
  'Object',
  'dynamic',
  'TextInputFormatter',
  'BoxShadow',
  'Shadow',
  'Locale',
  'LogicalKeyboardKey',
  'SingleActivator',
};

/// The allowlist when the gate was made (2026-09-26): the ceiling it may
/// only shrink from. An allowlist entry outside this set fails.
const _creationAllowlist = <String, List<String>>{
  'exported': [
    'KitFoldersOpenScene',
    'ServersLinkScene',
    'ServersWelcomeScene',
    'SetupPhoneScene',
    'SetupReadyScene',
    'SetupStepsScene',
    'SetupUnpluggedScene',
    'StatesFolderScene',
    'StatesSearchScene',
    'StatesSheetScene',
    'StatesTerminalScene',
    'StatesTrayScene',
    'StatesUnpluggedScene',
    'StatesWorkingScene',
    'TeamBoardScene',
    'TeamDiscoverRelayScene',
    'TeamDiscoverTeaserScene',
    'TeamIdleScene',
    'TeamMergedScene',
    'TeamNudgeScene',
    'TeamPlanningScene',
    'TeamRestScene',
    'TeamWakingScene',
    'TerminalKeyBar',
  ],
  'name': [
    'KitActionBlock',
    'KitAnimatedRows',
    'KitButton',
    'KitChevron',
    'KitEffectsScope',
    'KitEntrance',
    'KitExpandRow',
    'KitFoldersOpenScene',
    'KitGlass',
    'KitInset',
    'KitLoadingBar',
    'KitPortalScene',
    'KitProgressView',
    'KitRefresh',
    'KitReveal',
    // Visual language merge (ddcb6bc7), before this gate: kit-KitRow-v2
    // owns kit_row.dart, whose API spec (docs/ux-system/kit-api/KitRow.md)
    // keeps both here; remove once that unit settles NAME-1 for them.
    'KitRowGroup',
    'KitRowIcon',
    'KitRowMenu',
    'KitRowValue',
    'KitSkeletonRows',
    'KitSwitchRow',
    'KitTabSwitcher',
    'LoadingList',
    'ProductEmptyState',
    'ProductErrorState',
    'ProductInlineEmpty',
    'SectionLabel',
    'ServersLinkScene',
    'ServersWelcomeScene',
    'SetupPhoneScene',
    'SetupReadyScene',
    'SetupStepsScene',
    'SetupUnpluggedScene',
    'StatesFolderScene',
    'StatesSearchScene',
    'StatesSheetScene',
    'StatesTerminalScene',
    'StatesTrayScene',
    'StatesUnpluggedScene',
    'StatesWorkingScene',
    'TeamBoardScene',
    'TeamDiscoverRelayScene',
    'TeamDiscoverTeaserScene',
    'TeamIdleScene',
    'TeamMergedScene',
    'TeamNudgeScene',
    'TeamPlanningScene',
    'TeamRestScene',
    'TeamWakingScene',
    'TerminalKeyBar',
  ],
  'states': [
    'KitActionBlock',
    'KitActionStack',
    'KitAnimatedRows',
    'KitAskLine',
    'KitButton',
    'KitChevron',
    'KitConfirmSheet',
    'KitEntrance',
    'KitExpandRow',
    'KitGlass',
    'KitIconButton',
    'KitIllustration',
    'KitInset',
    'KitLoadingBar',
    'KitNotice',
    'KitPanel',
    'KitProgressView',
    'KitRefresh',
    'KitRequestCard',
    'KitReveal',
    'KitRow',
    'KitRowIcon',
    'KitRowMenu',
    'KitScreen',
    'KitSecretField',
    'KitSheet',
    'KitSkeletonRows',
    'KitSkeletonTranscript',
    'KitStateView',
    'KitStatusLine',
    'KitStatusMark',
    'KitSwitchRow',
    'KitTabSwitcher',
    'KitTaskMark',
    'LoadingList',
    'ProductEmptyState',
    'ProductErrorState',
    'ProductInlineEmpty',
    'SectionLabel',
    'TerminalKeyBar',
  ],
  'gallery': [
    'KitActionBlock',
    'KitActionStack',
    'KitAnimatedRows',
    'KitAskLine',
    'KitButton',
    'KitChevron',
    'KitEntrance',
    'KitExpandRow',
    'KitFoldersOpenScene',
    'KitGlass',
    'KitIconButton',
    'KitIllustration',
    'KitInset',
    'KitLoadingBar',
    'KitNotice',
    'KitPanel',
    'KitPortalScene',
    'KitProgressView',
    'KitRefresh',
    'KitRequestCard',
    'KitReveal',
    'KitRow',
    // Visual language merge (ddcb6bc7), before this gate; shown today in
    // kit_foundation_golden_test.dart. kit-KitRow-v2 gives them
    // kit_row_golden_test.dart (KitRow.md open question 3).
    'KitRowGroup',
    'KitRowIcon',
    'KitRowMenu',
    'KitRowValue',
    'KitScreen',
    'KitSecretField',
    'KitSkeletonRows',
    'KitSkeletonTranscript',
    'KitStateView',
    'KitStatusLine',
    'KitStatusMark',
    'KitSwitchRow',
    'KitTabSwitcher',
    'KitTaskMark',
    // Visual language merge (ddcb6bc7), before this gate; kit-KitText-v2
    // writes test/goldens/kit/kit_text_golden_test.dart.
    'KitText',
    'LoadingList',
    'ProductEmptyState',
    'ProductErrorState',
    'ProductInlineEmpty',
    'SectionLabel',
    'ServersLinkScene',
    'ServersWelcomeScene',
    'SetupPhoneScene',
    'SetupReadyScene',
    'SetupStepsScene',
    'SetupUnpluggedScene',
    'StatesFolderScene',
    'StatesSearchScene',
    'StatesSheetScene',
    'StatesTerminalScene',
    'StatesTrayScene',
    'StatesUnpluggedScene',
    'StatesWorkingScene',
    'TeamBoardScene',
    'TeamDiscoverRelayScene',
    'TeamDiscoverTeaserScene',
    'TeamIdleScene',
    'TeamMergedScene',
    'TeamNudgeScene',
    'TeamPlanningScene',
    'TeamRestScene',
    'TeamWakingScene',
    'TerminalKeyBar',
  ],
  'test': [
    'KitActionBlock',
    'KitActionStack',
    'KitAnimatedRows',
    'KitAskLine',
    'KitButton',
    'KitChevron',
    'KitEffectsScope',
    'KitEntrance',
    'KitExpandRow',
    'KitGlass',
    'KitIconButton',
    'KitIllustration',
    'KitInset',
    'KitLoadingBar',
    'KitNotice',
    'KitPanel',
    'KitProgressView',
    'KitRefresh',
    'KitRequestCard',
    'KitReveal',
    'KitRow',
    'KitRowIcon',
    'KitRowMenu',
    'KitScreen',
    'KitSkeletonRows',
    'KitSkeletonTranscript',
    'KitStateView',
    'KitStatusLine',
    'KitStatusMark',
    'KitSwitchRow',
    'KitTabSwitcher',
    'KitTaskMark',
    'LoadingList',
    'ProductEmptyState',
    'ProductErrorState',
    'ProductInlineEmpty',
    'SectionLabel',
    'TerminalKeyBar',
  ],
  'docRow': [
    'KitAnimatedRows',
    'KitEntrance',
    'KitFoldersOpenScene',
    'KitIconButton',
    'KitInset',
    'KitProgressView',
    'KitRefresh',
    'KitReveal',
    'KitSecretField',
    'KitTabSwitcher',
    'LoadingList',
    'ProductEmptyState',
    'ProductErrorState',
    'ProductInlineEmpty',
    'ServersLinkScene',
    'ServersWelcomeScene',
    'SetupPhoneScene',
    'SetupReadyScene',
    'SetupStepsScene',
    'SetupUnpluggedScene',
    'StatesFolderScene',
    'StatesSearchScene',
    'StatesSheetScene',
    'StatesTerminalScene',
    'StatesTrayScene',
    'StatesUnpluggedScene',
    'StatesWorkingScene',
    'TeamBoardScene',
    'TeamDiscoverRelayScene',
    'TeamDiscoverTeaserScene',
    'TeamIdleScene',
    'TeamMergedScene',
    'TeamNudgeScene',
    'TeamPlanningScene',
    'TeamRestScene',
    'TeamWakingScene',
    'TerminalKeyBar',
  ],
  'motion': [
    'KitActionBlock',
    'KitActionStack',
    'KitAnimatedRows',
    'KitAskLine',
    'KitButton',
    'KitChevron',
    'KitEntrance',
    'KitExpandRow',
    'KitGlass',
    'KitIconButton',
    'KitIllustration',
    'KitInset',
    'KitLoadingBar',
    'KitNotice',
    'KitPanel',
    'KitProgressView',
    'KitRefresh',
    'KitRequestCard',
    'KitReveal',
    'KitRow',
    'KitRowIcon',
    'KitRowMenu',
    'KitScreen',
    'KitSecretField',
    'KitSkeletonRows',
    'KitSkeletonTranscript',
    'KitStateView',
    'KitStatusLine',
    'KitSwitchRow',
    'KitTabSwitcher',
    'KitTaskMark',
    'LoadingList',
    'ProductEmptyState',
    'ProductErrorState',
    'ProductInlineEmpty',
    'SectionLabel',
    'TerminalKeyBar',
  ],
  'keyboard': ['KitExpandRow', 'KitRow', 'KitSwitchRow'],
  'overflow': [
    'KitActionBlock',
    'KitActionStack',
    'KitAnimatedRows',
    'KitAskLine',
    'KitButton',
    'KitChevron',
    'KitConfirmSheet',
    'KitEntrance',
    'KitExpandRow',
    'KitGlass',
    'KitIconButton',
    'KitIllustration',
    'KitInset',
    'KitLoadingBar',
    'KitNotice',
    'KitPanel',
    'KitProgressView',
    'KitRefresh',
    'KitRequestCard',
    'KitReveal',
    'KitRow',
    'KitRowIcon',
    'KitRowMenu',
    'KitScreen',
    'KitSecretField',
    'KitSheet',
    'KitSkeletonRows',
    'KitSkeletonTranscript',
    'KitStateView',
    'KitStatusLine',
    'KitStatusMark',
    'KitSwitchRow',
    'KitTabSwitcher',
    'KitTaskMark',
    'LoadingList',
    'ProductEmptyState',
    'ProductErrorState',
    'ProductInlineEmpty',
    'SectionLabel',
    'TerminalKeyBar',
  ],
  'harness': ['kitGallerySizes 915x412'],
};

const _byChat = 'slice-P3.5 (chat chain) holds lib/ui/kit/chat/**';
const _toAssignName =
    'coordinator to assign: kit_status_slot.dart needs a KitStatusSlot.md '
    'spec naming its classes, or one file per part';
const _toAssignStates =
    'coordinator to assign: map the spec states onto the KIT-12 words, then '
    'add their gallery scenes';
const _toAssignScenes =
    'coordinator to assign: add the declared states as 412x915 scenes in its '
    'gallery';
const _toAssignGallery =
    'coordinator to assign: add the missing gallery or its missing shots';
const _toAssignTest =
    'coordinator to assign: add its test/kit/<snake>_test.dart';
const _toAssignKeyboard =
    'coordinator to assign: cover it in test/kit/kit_keyboard_test.dart (G14)';

/// Gaps the kit still had when its last unit merged (2026-09-27, unit
/// kit-gates-manifest): check -> part -> who closes it. They extend the
/// ceiling above so the gate passes today, and shrink like the rest of the
/// allowlist: fix the part, rerun with KIT_MANIFEST_WRITE=1, then delete its
/// line here (a line whose part left the allowlist fails).
const _deferredAtKitMerge = <String, Map<String, String>>{
  'name': {
    'KitStatusContribution': _toAssignName,
    'KitStatusLineSlot': _toAssignName,
    'KitStatusScope': _toAssignName,
  },
  'states': {
    'KitAgentStrip': _byChat,
    'KitBoardLane': _toAssignStates,
    'KitBoardLanes': _toAssignStates,
    'KitCapabilityExplainer': _toAssignStates,
    'KitChecklist': _toAssignStates,
    'KitChoiceList': _toAssignStates,
    'KitChoiceRow': _toAssignStates,
    'KitCodeBlock': _toAssignStates,
    'KitComposer': _byChat,
    'KitComposerChips': _byChat,
    'KitComposerStatusStrip': _byChat,
    'KitContextRegion': _toAssignStates,
    'KitDetailsFold': _toAssignStates,
    'KitDiffView': _toAssignStates,
    'KitLogPanel': _toAssignStates,
    'KitMarkdown': _byChat,
    'KitMessage': _byChat,
    'KitNav': _toAssignStates,
    'KitPickerRow': _toAssignStates,
    'KitProgressRow': _toAssignStates,
    'KitQueuedMessage': _byChat,
    'KitReceipt': _toAssignStates,
    'KitSearchField': _toAssignStates,
    'KitSearchNoMatch': _toAssignStates,
    'KitShellControls': _toAssignStates,
    'KitTappable': _toAssignStates,
    'KitTaskCard': _toAssignStates,
    'KitToolRow': _byChat,
    'KitTopBar': _toAssignStates,
    'KitTurn': _byChat,
    'KitViewer': _toAssignStates,
    'KitWorkGraph': _toAssignStates,
    'KitWorkLine': _byChat,
  },
  'stateScenes': {
    'KitComposerChips': _byChat,
    'KitDateTimeRow': _toAssignScenes,
    'KitField': _toAssignScenes,
  },
  'gallery': {
    'KitAgentStrip': _byChat,
    'KitComposerStatusStrip': _byChat,
    'KitContextRegion': _toAssignGallery,
    'KitMessage': _byChat,
    'KitOwnScrollbar': _toAssignGallery,
    'KitQueuedMessage': _byChat,
    'KitScrollArea': _toAssignGallery,
    'KitScrollbar': _toAssignGallery,
    'KitStatusContribution': _toAssignGallery,
    'KitStatusLineSlot': _toAssignGallery,
  },
  'test': {
    'KitComposerStatusStrip': _byChat,
    'KitStatusContribution': _toAssignTest,
    'KitStatusLineSlot': _toAssignTest,
    'KitStatusScope': _toAssignTest,
  },
  'keyboard': {
    'KitChoiceList': _toAssignKeyboard,
    'KitChoiceRow': _toAssignKeyboard,
    'KitDateTimeRow': _toAssignKeyboard,
    'KitDetailsFold': _toAssignKeyboard,
    'KitDiffView': _toAssignKeyboard,
    'KitPickerRow': _toAssignKeyboard,
    'KitProgressRow': _toAssignKeyboard,
    'KitToolRow': _byChat,
    'KitViewer': _toAssignKeyboard,
  },
};

/// A drawn widget part, a scope widget that draws nothing, or a drawn
/// [KitScene] (painted by KitIllustration; TEST-14).
enum KitManifestKind { part, scope, scene }

/// One public widget class or KitScene: exported by `kit.dart` or declared
/// under `lib/ui/kit/`.
class KitManifestPart {
  const KitManifestPart({
    required this.name,
    required this.file,
    required this.kind,
    required this.exported,
    required this.states,
    required this.statesProblem,
    required this.requiredStates,
    required this.openers,
  });

  final String name;

  /// The file (relative to the package root) that declares the class.
  final String file;
  final KitManifestKind kind;

  /// Whether `kit.dart` exports it; false for a kit file that screens
  /// import by path.
  final bool exported;

  /// The states its doc comment declares (KIT-12); empty for a reasoned
  /// `States: none — …`; null when it declares none or the line is
  /// malformed ([statesProblem] says which).
  final List<String>? states;
  final String? statesProblem;

  /// The states its fields require (KIT-12 second sentence): state -> why.
  final Map<String, String> requiredStates;

  /// The `showKit…` functions declared in the same file: the part is modal.
  final List<String> openers;

  String get snake => kitSnake(name);

  /// The snake of the file that declares it: `kit_image` for KitAvatar.
  String get fileSnake =>
      file.substring(file.lastIndexOf('/') + 1).replaceAll('.dart', '');

  /// Whether it shares [file] with that file's own part because the file
  /// part's frozen spec declares it there (NAME-1 exception, see above).
  bool get coLocated {
    if (fileSnake == snake) return false;
    final spec = File('docs/ux-system/kit-api/${_pascal(fileSnake)}.md');
    return spec.existsSync() &&
        RegExp('\\bclass\\s+$name\\b').hasMatch(spec.readAsStringSync());
  }

  /// The snake its gallery and unit test are named after: its own, or
  /// (co-located, with no files of its own) its file's.
  String get homeSnake =>
      coLocated &&
          !File('test/kit/${snake}_test.dart').existsSync() &&
          !File('test/goldens/kit/${snake}_golden_test.dart').existsSync()
      ? fileSnake
      : snake;
  bool get isModal => openers.isNotEmpty;
  bool get isRow => name.endsWith('Row');

  /// The names a hand-listed consumer test may use for this part.
  List<String> get aliases => [name, ...openers];

  @override
  String toString() => '$name ($file)';
}

/// One top-level `showKit…` function under `lib/ui/kit/` or exported by
/// `kit.dart`.
class KitManifestOpener {
  const KitManifestOpener({
    required this.name,
    required this.file,
    required this.exported,
    required this.returnType,
    required this.parameters,
  });

  final String name;
  final String file;
  final bool exported;

  /// The declared return type, '' when it declares none.
  final String returnType;

  /// The source between the parameter list's parentheses.
  final String parameters;

  /// Whether it declares an optional `Key? …Key` parameter (KIT-10).
  bool get hasOptionalKey => RegExp(
    r'\bKey\?\s+\w*Key\b',
  ).hasMatch(parameters.replaceAll(RegExp(r'required\s+Key\?\s+\w+'), ''));
}

class KitManifest {
  const KitManifest({
    required this.parts,
    required this.openers,
    required this.unresolved,
    required this.problems,
  });

  final List<KitManifestPart> parts;
  final List<KitManifestOpener> openers;

  /// Names in a `show` combinator that no declaration matched.
  final List<String> unresolved;

  /// Declarations the scan found but could not read (fail loudly).
  final List<String> problems;

  Iterable<KitManifestPart> get drawn =>
      parts.where((p) => p.kind == KitManifestKind.part);
}

/// `kit_motion_parts` -> `KitMotionParts`.
String _pascal(String snake) => [
  for (final w in snake.split('_'))
    if (w.isNotEmpty) '${w[0].toUpperCase()}${w.substring(1)}',
].join();

/// `KitConfirmSheet` -> `kit_confirm_sheet`.
String kitSnake(String name) => name
    .replaceAllMapped(RegExp(r'([a-z0-9])([A-Z])'), (m) => '${m[1]}_${m[2]}')
    .replaceAllMapped(RegExp(r'([A-Z])([A-Z][a-z])'), (m) => '${m[1]}_${m[2]}')
    .toLowerCase();

/// A Dart source with its comments blanked ([code]) and, in [shape], its
/// string literals blanked too (quotes of the outermost literal kept). All
/// three keep every offset and newline of the file.
class KitSource {
  KitSource._(this.text, this.code, this.shape);

  factory KitSource.read(String path) =>
      KitSource.parse(File(path).readAsStringSync());

  factory KitSource.parse(String s) {
    final n = s.length;
    final code = s.split('');
    final shape = s.split('');
    void blank(int i, List<String> out) {
      if (s[i] != '\n') out[i] = ' ';
    }

    void hide(int i) {
      if (s[i] != '\n') shape[i] = '_';
    }

    // Frames: a string literal (quote, raw) or an interpolation (depth).
    final quotes = <String?>[];
    final raws = <bool>[];
    final depths = <int>[];
    var i = 0;
    while (i < n) {
      final c = s[i];
      final inString = quotes.isNotEmpty && quotes.last != null;
      if (inString) {
        final quote = quotes.last!;
        if (!raws.last && c == r'\' && i + 1 < n) {
          hide(i);
          hide(i + 1);
          i += 2;
          continue;
        }
        if (!raws.last && s.startsWith(r'${', i)) {
          hide(i);
          hide(i + 1);
          quotes.add(null);
          raws.add(false);
          depths.add(0);
          i += 2;
          continue;
        }
        if (s.startsWith(quote, i)) {
          quotes.removeLast();
          raws.removeLast();
          depths.removeLast();
          if (quotes.isNotEmpty) {
            for (var k = 0; k < quote.length; k++) {
              hide(i + k);
            }
          }
          i += quote.length;
          continue;
        }
        hide(i);
        i++;
        continue;
      }
      final nested = quotes.isNotEmpty; // inside an interpolation
      if (s.startsWith('//', i)) {
        while (i < n && s[i] != '\n') {
          blank(i, code);
          blank(i, shape);
          i++;
        }
        continue;
      }
      if (s.startsWith('/*', i)) {
        var depth = 0;
        do {
          if (s.startsWith('/*', i)) {
            depth++;
            blank(i, code);
            blank(i, shape);
            blank(i + 1, code);
            blank(i + 1, shape);
            i += 2;
          } else if (s.startsWith('*/', i)) {
            depth--;
            blank(i, code);
            blank(i, shape);
            blank(i + 1, code);
            blank(i + 1, shape);
            i += 2;
          } else {
            blank(i, code);
            blank(i, shape);
            i++;
          }
        } while (depth > 0 && i < n);
        continue;
      }
      final rawStart =
          c == 'r' &&
          i + 1 < n &&
          (s[i + 1] == "'" || s[i + 1] == '"') &&
          (i == 0 || !_identChar.hasMatch(s[i - 1]));
      if (c == "'" || c == '"' || rawStart) {
        final start = rawStart ? i + 1 : i;
        final q = s[start];
        final quote = s.startsWith('$q$q$q', start) ? '$q$q$q' : q;
        final end = start + quote.length;
        if (nested) {
          for (var k = i; k < end; k++) {
            hide(k);
          }
        }
        quotes.add(quote);
        raws.add(rawStart);
        depths.add(0);
        i = end;
        continue;
      }
      if (nested) {
        if (c == '{') depths[depths.length - 1]++;
        if (c == '}') {
          if (depths.last == 0) {
            quotes.removeLast();
            raws.removeLast();
            depths.removeLast();
          } else {
            depths[depths.length - 1]--;
          }
        }
        hide(i);
      }
      i++;
    }
    return KitSource._(s, code.join(), shape.join());
  }

  /// The raw text (comments included; the doc table lives in comments).
  final String text;

  /// Comments blanked.
  final String code;

  /// Comments and string literals blanked: brackets here are real.
  final String shape;

  /// The index of the bracket closing the one at [open], or -1.
  int close(int open) {
    const pairs = {'(': ')', '[': ']', '{': '}'};
    final stack = <String>[];
    for (var i = open; i < shape.length; i++) {
      final c = shape[i];
      if (pairs.containsKey(c)) stack.add(pairs[c]!);
      if (pairs.containsValue(c)) {
        if (stack.isEmpty || stack.removeLast() != c) return -1;
        if (stack.isEmpty) return i;
      }
    }
    return -1;
  }

  /// The bracket depth at each offset, and where the top-level declaration
  /// holding it starts (just after the previous top-level `;` or `}`).
  late final (List<int>, List<int>) _structure = () {
    final depthAt = List<int>.filled(shape.length + 1, 0);
    final boundary = List<int>.filled(shape.length + 1, 0);
    var depth = 0;
    var last = 0;
    for (var i = 0; i < shape.length; i++) {
      depthAt[i] = depth;
      boundary[i] = last;
      final c = shape[i];
      if (c == '(' || c == '[' || c == '{') depth++;
      if (c == ')' || c == ']' || c == '}') depth--;
      if (depth == 0 && (c == ';' || c == '}')) last = i + 1;
    }
    depthAt[shape.length] = depth;
    boundary[shape.length] = last;
    return (depthAt, boundary);
  }();

  /// Whether offset [at] is outside every bracket.
  bool topLevel(int at) => _structure.$1[at] == 0;

  /// The code of the top-level declaration before offset [at], with
  /// annotations removed: a function's return type and modifiers.
  String headBefore(int at) => code
      .substring(_structure.$2[at], at)
      .replaceAll(RegExp(r'@[\w.]+(?:\s*\([^()]*\))?'), ' ');

  /// The top-level (depth 0) comma-separated pieces of the code between
  /// [open] and its closing bracket.
  List<String> arguments(int open) {
    final end = close(open);
    if (end < 0) return const [];
    final out = <String>[];
    var depth = 0;
    var from = open + 1;
    for (var i = open + 1; i < end; i++) {
      final c = shape[i];
      if (c == '(' || c == '[' || c == '{') depth++;
      if (c == ')' || c == ']' || c == '}') depth--;
      if (c == ',' && depth == 0) {
        out.add(code.substring(from, i));
        from = i + 1;
      }
    }
    out.add(code.substring(from, end));
    return [
      for (final a in out)
        if (a.trim().isNotEmpty) a.trim(),
    ];
  }
}

final _identChar = RegExp(r'[\w$]');

final _sources = <String, KitSource>{};
KitSource _source(String path) =>
    _sources.putIfAbsent(path, () => KitSource.read(path));

String _normalize(String path) {
  final out = <String>[];
  for (final segment in path.split('/')) {
    if (segment == '..') {
      out.removeLast();
    } else if (segment != '.' && segment.isNotEmpty) {
      out.add(segment);
    }
  }
  return out.join('/');
}

String _resolve(String from, String uri) {
  if (uri.startsWith('package:opencode_mobile/')) {
    return 'lib/${uri.substring('package:opencode_mobile/'.length)}';
  }
  final dir = from.substring(0, from.lastIndexOf('/'));
  return _normalize('$dir/$uri');
}

/// `export`/`part` directives, with either quote style (read from code, so
/// a commented-out directive does not count).
final _directive = RegExp(
  r'''^(export|part)\s+(['"])([^'"]+)\2\s*([^;]*);''',
  multiLine: true,
);

Set<String> _names(String combinator, String keyword) {
  final m = RegExp(
    '\\b$keyword\\s+([\\w\\s,]+?)(?=\\s+(?:show|hide)\\b|\$)',
  ).firstMatch(combinator.trim());
  if (m == null) return {};
  return m[1]!
      .split(',')
      .map((s) => s.trim())
      .where((s) => s.isNotEmpty)
      .toSet();
}

class _Decl {
  _Decl(this.name, this.file, this.line, this.kind, {this.offset = 0});
  final String name;
  final String file;
  final int line;
  final String kind; // class, function, other
  final int offset;
}

final _classHeader = RegExp(
  r'^(?:(?:abstract|base|final|interface|sealed|mixin)\s+)*class\s+(\w+)',
  multiLine: true,
);
final _otherHeader = RegExp(
  r'^(?:enum|mixin|typedef|extension\s+type)\s+(\w+)',
  multiLine: true,
);

/// A name followed by optional type parameters and `(`; filtered to
/// top-level declarations by [_topLevelFunctions].
final _callLike = RegExp(
  r'(?<![\w$.@])([A-Za-z_$][\w$]*)\s*'
  r'(?:<(?:[^<>()]|<(?:[^<>()]|<[^<>()]*>)*>)*>)?\s*\(',
);

int _lineOf(String source, int offset) =>
    '\n'.allMatches(source.substring(0, offset)).length;

/// The top-level function declarations of [source]: name -> (offset of
/// the name, offset of its `(`, the text before the name back to the
/// previous top-level `;` or `}`).
List<(String, int, int, String)> _topLevelFunctions(KitSource source) {
  final out = <(String, int, int, String)>[];
  for (final m in _callLike.allMatches(source.shape)) {
    if (!source.topLevel(m.start)) continue;
    final name = m[1]!;
    if (const {'Function', 'if', 'for', 'while', 'switch'}.contains(name)) {
      continue;
    }
    final head = source.headBefore(m.start);
    if (_notDeclaration(head)) continue;
    out.add((name, m.start, m.end - 1, head.trim()));
  }
  return out;
}

/// Whether the code before a top-level name shows it is not being
/// declared (an initializer, an expression body, a directive).
bool _notDeclaration(String head) =>
    head.contains('=') ||
    RegExp(r'^\s*(?:typedef|import|export|part|library)\b').hasMatch(head);

/// The files of a library: the file and its `part`s.
List<String> _unitsOf(String path) {
  final code = _source(path).code;
  return [
    path,
    for (final m in _directive.allMatches(code))
      if (m[1] == 'part') _resolve(path, m[3]!),
  ];
}

List<_Decl> _declarationsIn(String file) {
  final source = _source(file);
  return [
    for (final m in _classHeader.allMatches(source.shape))
      _Decl(
        m[1]!,
        file,
        _lineOf(source.text, m.start),
        'class',
        offset: m.start,
      ),
    for (final m in _otherHeader.allMatches(source.shape))
      _Decl(m[1]!, file, _lineOf(source.text, m.start), 'other'),
    for (final (name, at, _, _) in _topLevelFunctions(source))
      _Decl(name, file, _lineOf(source.text, at), 'function', offset: at),
  ];
}

/// Every public declaration [path] exports, through re-exports.
void _collectExports(
  String path,
  Set<String>? show,
  Set<String> hide,
  Map<String, _Decl> out,
  Set<String> wanted,
  Set<String> visiting,
) {
  if (!visiting.add('$path|$show|$hide')) return;
  bool visible(String name) =>
      !name.startsWith('_') &&
      (show == null || show.contains(name)) &&
      !hide.contains(name);
  for (final unit in _unitsOf(path)) {
    for (final decl in _declarationsIn(unit)) {
      if (visible(decl.name)) out.putIfAbsent(decl.name, () => decl);
    }
  }
  for (final m in _directive.allMatches(_source(path).code)) {
    if (m[1] != 'export') continue;
    final innerShow = _names(m[4]!, 'show');
    final innerHide = _names(m[4]!, 'hide');
    Set<String>? nextShow = innerShow.isEmpty ? show : innerShow;
    if (show != null && innerShow.isNotEmpty) {
      nextShow = innerShow.intersection(show);
    }
    wanted.addAll(innerShow);
    _collectExports(
      _resolve(path, m[3]!),
      nextShow,
      {...hide, ...innerHide},
      out,
      wanted,
      visiting,
    );
  }
}

/// Every `.dart` file under `lib/ui/kit/`, sorted.
List<String> _kitFiles() => [
  for (final entity in Directory(_kitDirectory).listSync(recursive: true))
    if (entity is File && entity.path.endsWith('.dart'))
      entity.path.replaceAll(r'\', '/'),
]..sort();

/// Superclass of every class declared under lib/.
Map<String, String> _superclasses() {
  final supers = <String, String>{};
  final header = RegExp(
    r'^(?:(?:abstract|base|final|interface|sealed|mixin)\s+)*class\s+(\w+)'
    r'(?:\s*<[^{]*?>)?\s+extends\s+(\w+)',
    multiLine: true,
  );
  for (final entity in Directory('lib').listSync(recursive: true)) {
    if (entity is! File || !entity.path.endsWith('.dart')) continue;
    for (final m in header.allMatches(_source(entity.path).shape)) {
      supers.putIfAbsent(m[1]!, () => m[2]!);
    }
  }
  return supers;
}

Set<String> _frameworkWidgets() {
  final json =
      jsonDecode(
            File('test/kit_ratchet_flutter_widgets.json').readAsStringSync(),
          )
          as Map<String, Object?>;
  return (json['widgets']! as Map<String, Object?>).keys.toSet();
}

/// The doc comment above line [line] of [file] (annotations skipped).
List<String> _docAbove(String file, int line) {
  final lines = _source(file).text.split('\n');
  final doc = <String>[];
  for (var i = line - 1; i >= 0; i--) {
    final text = lines[i].trim();
    if (text.startsWith('@')) continue;
    if (!text.startsWith('///')) break;
    doc.insert(0, text.substring(3).trim());
  }
  return doc;
}

(List<String>?, String?) _statesFrom(List<String> doc) {
  final lines = doc.where((l) => l.startsWith('States:')).toList();
  if (lines.isEmpty) return (null, 'no "States: …" line in its doc comment');
  if (lines.length > 1) return (null, 'more than one "States:" line');
  final body = lines.single.substring('States:'.length).trim();
  final none = RegExp(r'^none\b\s*[—–:;,(-]*\s*(.*)$').firstMatch(body);
  if (none != null) {
    final reason = none[1]!.replaceAll(RegExp(r'[.)]+$'), '').trim();
    if (reason.split(RegExp(r'\s+')).where((w) => w.isNotEmpty).length < 3) {
      return (
        null,
        '"States: none" without a reason; write '
            '"States: none — <why it has no states>."',
      );
    }
    return (const [], null);
  }
  final states = body
      .replaceAll(RegExp(r'\.$'), '')
      .split(',')
      .map((s) => s.trim())
      .toList();
  final unknown = states.where((s) => !kitManifestStates.contains(s));
  if (unknown.isNotEmpty) {
    return (null, 'unknown states ${unknown.join(', ')}');
  }
  return (states, null);
}

/// The fields (`final <Type> <name>;`) of the class body at [offset].
Map<String, String> _fieldsOf(KitSource source, int offset) {
  final open = source.shape.indexOf('{', offset);
  if (open < 0) return const {};
  final end = source.close(open);
  if (end < 0) return const {};
  final inner = KitSource.parse(source.text.substring(open + 1, end));
  return {
    for (final m in RegExp(
      r'\bfinal\s+([^;=]+?)\s+(\w+)\s*;',
    ).allMatches(inner.shape))
      if (inner.topLevel(m.start))
        m[2]!: inner.code
            .substring(m.start, m.end)
            .replaceFirst(RegExp(r'^final\s+'), '')
            .replaceFirst(RegExp(r'\s+\w+\s*;$'), '')
            .replaceAll(RegExp(r'\s+'), ' ')
            .trim(),
  };
}

/// KIT-12's second sentence, read from the part's fields: state -> why.
Map<String, String> _requiredStates(Map<String, String> fields) {
  final out = <String, String>{};
  for (final MapEntry(key: name, value: type) in fields.entries) {
    // An optional close affordance is absent when null, not disabled; an
    // event the part reports (KitSince's onEscalated) is not something the
    // person can do, so null does not disable anything either.
    final closes =
        name == 'onDismiss' || name == 'onClose' || name == 'onEscalated';
    if (RegExp(r'^on[A-Z]').hasMatch(name) && type.endsWith('?') && !closes) {
      out.putIfAbsent('disabled', () => 'nullable callback $name');
    }
    if (RegExp(
          r'^(?:working|busy|sending|submitting|isWorking|isBusy|isSending)$',
        ).hasMatch(name) ||
        RegExp(r'^on(?:Submit|Send)').hasMatch(name)) {
      out.putIfAbsent('working', () => 'sends through $name');
    }
    if (type.contains('Function(')) continue; // a callback, not data
    final data = RegExp(
      r'^(?:Future|Stream|AsyncSnapshot|ValueListenable<(?:List|Iterable))\b',
    ).hasMatch(type);
    final collection = RegExp(
      r'^(?:List|Iterable|Map)<\s*(?:[\w<>?, ]+,\s*)?(\w+)',
    ).firstMatch(type);
    final element = collection?[1];
    final serverList =
        element != null &&
        !_uiElementTypes.contains(element) &&
        !element.startsWith('Kit') &&
        !element.endsWith('Widget');
    if (data || serverList) {
      for (final state in ['loading', 'empty', 'error']) {
        out.putIfAbsent(state, () => 'shows server data $name ($type)');
      }
    }
  }
  return out;
}

/// Reads the kit manifest: `lib/ui/kit/kit.dart`'s exports plus every
/// widget, scene and opener declared under `lib/ui/kit/`.
/// [includeRetired] lets regression gates retain coverage of exported
/// forwarding widgets; G4 itself keeps excluding them per KIT-43.
KitManifest readKitManifest({bool includeRetired = false}) {
  final exportedDecls = <String, _Decl>{};
  final wanted = <String>{};
  _collectExports(_kitLibrary, null, {}, exportedDecls, wanted, {});
  final decls = <String, (_Decl, bool)>{
    for (final d in exportedDecls.values) d.name: (d, true),
  };
  for (final file in _kitFiles()) {
    for (final d in _declarationsIn(file)) {
      if (d.name.startsWith('_')) continue;
      decls.putIfAbsent(d.name, () => (d, false));
    }
  }
  final supers = _superclasses();
  final framework = _frameworkWidgets();
  final problems = <String>[];

  List<String> chain(String name) {
    final out = <String>[name];
    final seen = <String>{name};
    var current = name;
    for (
      var next = supers[current];
      next != null && seen.add(next);
      next = supers[current]
    ) {
      out.add(next);
      current = next;
    }
    return out;
  }

  bool isWidget(List<String> chain) =>
      chain.length > 1 &&
      (chain.skip(1).any(_widgetBases.contains) ||
          framework.contains(chain.last));

  final openers = <KitManifestOpener>[];
  for (final (decl, exported) in decls.values) {
    if (decl.kind != 'function' || !decl.name.startsWith('showKit')) continue;
    final source = _source(decl.file);
    final found = _topLevelFunctions(
      source,
    ).where((f) => f.$1 == decl.name).firstOrNull;
    final end = found == null ? -1 : source.close(found.$3);
    if (found == null || end < 0) {
      problems.add(
        '${decl.file}:${decl.line + 1} ${decl.name}: '
        'cannot read its return type and parameter list',
      );
      continue;
    }
    openers.add(
      KitManifestOpener(
        name: decl.name,
        file: decl.file,
        exported: exported,
        returnType: found.$4.replaceAll(RegExp(r'\s+'), ' '),
        parameters: source.code.substring(found.$3 + 1, end),
      ),
    );
  }
  // Every top-level `showKit…` in a kit file must have been read above.
  for (final file in _kitFiles()) {
    final source = _source(file);
    for (final m in RegExp(
      r'(?<![\w$.@])(showKit\w+)\b',
    ).allMatches(source.shape)) {
      if (!source.topLevel(m.start)) continue;
      if (_notDeclaration(source.headBefore(m.start))) continue;
      if (!openers.any((o) => o.name == m[1] && o.file == file) &&
          !problems.any((p) => p.contains(' ${m[1]}:'))) {
        problems.add(
          '$file:${_lineOf(source.text, m.start) + 1} ${m[1]}: '
          'a top-level showKit… the scan could not read',
        );
      }
    }
  }
  openers.sort((a, b) => a.name.compareTo(b.name));

  final parts = <KitManifestPart>[];
  for (final (decl, exported) in decls.values) {
    if (decl.kind != 'class') continue;
    final supersOf = chain(decl.name);
    final isScene = supersOf.skip(1).contains('KitScene');
    if (!isScene && !isWidget(supersOf)) continue;
    final doc = _docAbove(decl.file, decl.line);
    if (!includeRetired &&
        doc.isNotEmpty &&
        doc.first.startsWith('Retired by kit-')) {
      continue;
    }
    final (states, problem) = _statesFrom(doc);
    parts.add(
      KitManifestPart(
        name: decl.name,
        file: decl.file,
        kind: isScene
            ? KitManifestKind.scene
            : supersOf.any(_scopeBases.contains)
            ? KitManifestKind.scope
            : KitManifestKind.part,
        exported: exported,
        states: states,
        statesProblem: problem,
        requiredStates: _requiredStates(
          Map.of(_fieldsOf(_source(decl.file), decl.offset))..removeWhere(
            (field, _) => _nullNotDisabled.containsKey('${decl.name}.$field'),
          ),
        ),
        openers: [
          for (final o in openers)
            if (o.file == decl.file) o.name,
        ],
      ),
    );
  }
  parts.sort((a, b) => a.name.compareTo(b.name));

  return KitManifest(
    parts: parts,
    openers: openers,
    unresolved: (wanted.difference(exportedDecls.keys.toSet()).toList()
      ..sort()),
    problems: problems,
  );
}

/// One golden a gallery file records.
/// The golden name of a `name:` argument: a string literal, or a
/// `kitGalleryName('<shot>', size, …)` call (G23's naming helper), read as
/// `<shot>[_ar][_text2]_$size_$mode`.
String? _galleryName(String? expression) {
  if (expression == null) return null;
  final literal = _Gallery._literal(expression);
  if (literal != null) return literal;
  final call = RegExp(
    r'''^kitGalleryName\(\s*(['"])((?:[\w$]|\$\{[^}]*\})+)\1([\s\S]*)\)$''',
  ).firstMatch(expression.trim());
  if (call == null) return null;
  final rest = call[3]!;
  return [
    call[2]!,
    if (RegExp(r'\bar:\s*true\b').hasMatch(rest)) 'ar',
    if (RegExp(r'\btext2:\s*true\b').hasMatch(rest)) 'text2',
    r'$size',
    r'$mode',
  ].join('_');
}

class _Shot {
  _Shot(this.name, this.arguments, {this.sizeArgument});

  /// The golden name's literal source, quotes and `.png` removed.
  final String name;

  /// The named arguments of its gallery call ({} for a
  /// `matchesGoldenFile`).
  final Map<String, String> arguments;

  /// The `size:` argument, else the size passed to its `kitGalleryName`.
  final String? sizeArgument;
}

/// The size [kitGalleryName]'s call in [expression] was given (its second
/// positional argument), or null.
String? _galleryNameSize(KitSource source, int at, String? expression) {
  if (expression == null || !expression.startsWith('kitGalleryName')) {
    return null;
  }
  final open = source.code.indexOf('(', at);
  if (open < 0) return null;
  final args = source.arguments(open);
  return args.length > 1 && !args[1].contains(':') ? args[1] : null;
}

/// The `WxH` sizes a list expression holds: `Size(w, h)` literals, the
/// gallery harness lists, and named `<Size>[…]` lists declared in [code].
Set<String> _sizesIn(String expression, String code, [int depth = 0]) {
  final out = <String>{
    for (final m in RegExp(
      r'Size\(\s*(\d+)(?:\.0)?\s*,\s*(\d+)(?:\.0)?\s*\)',
    ).allMatches(expression))
      '${m[1]}x${m[2]}',
  };
  if (RegExp(r'\bkitGallerySizes\b').hasMatch(expression)) {
    out.addAll(_harnessSizes('kitGallerySizes'));
  }
  if (RegExp(r'\bkitGalleryScaledSizes\b').hasMatch(expression)) {
    out.addAll(_harnessSizes('kitGalleryScaledSizes'));
  }
  if (depth < 2) {
    for (final m in RegExp(r'\b(_?[a-z]\w*)\b').allMatches(expression)) {
      final list = RegExp(
        '\\b${RegExp.escape(m[1]!)}\\s*=\\s*(?:const\\s+)?(?:<Size>)?\\[([^\\]]*)\\]',
      ).firstMatch(code);
      if (list != null) out.addAll(_sizesIn(list[1]!, code, depth + 1));
    }
  }
  return out;
}

/// The sizes of a `const <name> = <Size>[…]` list in the gallery harness.
List<String> _harnessSizes(String name) {
  final m = RegExp(
    'const\\s+$name\\s*=\\s*<Size>\\[([^\\]]*)\\]',
  ).firstMatch(_source(_galleryHarness).code);
  if (m == null) return const [];
  return [
    for (final s in RegExp(
      r'Size\(\s*(\d+)\s*,\s*(\d+)\s*\)',
    ).allMatches(m[1]!))
      '${s[1]}x${s[2]}',
  ];
}

/// The goldens of a gallery file, read from its code.
class _Gallery {
  _Gallery(this.source) {
    final code = source.code;
    // kitGalleryShot, kitGalleryPart, and a gallery's own helper whose
    // `name:` is a kitGalleryName(…) call.
    for (final m in RegExp(r'(?<![\w$.])(\w+)\s*\(').allMatches(code)) {
      final callee = m[1]!;
      if (callee == 'kitGalleryName') continue;
      final gallery = callee == 'kitGalleryShot' || callee == 'kitGalleryPart';
      final close = source.close(m.end - 1);
      if (close < 0) continue;
      if (!gallery &&
          !RegExp(
            r'\bname\s*:\s*kitGalleryName\s*\(',
          ).hasMatch(code.substring(m.end, close))) {
        continue;
      }
      final named = <String, String>{};
      final at = <String, int>{};
      final open = m.end - 1;
      final end = source.close(open);
      if (end < 0) continue;
      var depth = 0;
      var from = open + 1;
      for (var i = open + 1; i <= end; i++) {
        final c = source.shape[i];
        if (i == end || (c == ',' && depth == 0)) {
          final a = code.substring(from, i);
          final n = RegExp(r'^\s*(\w+)\s*:\s*([\s\S]*)$').firstMatch(a);
          if (n != null) {
            named[n[1]!] = n[2]!.trim();
            at[n[1]!] = from + a.indexOf(n[2]!);
          }
          from = i + 1;
          continue;
        }
        if (c == '(' || c == '[' || c == '{') depth++;
        if (c == ')' || c == ']' || c == '}') depth--;
      }
      final name = _galleryName(named['name']);
      if (name == null) continue;
      if (!gallery && !named['name']!.startsWith('kitGalleryName')) continue;
      shots.add(
        _Shot(
          name,
          named,
          sizeArgument:
              named['size'] ??
              _galleryNameSize(source, at['name'] ?? 0, named['name']),
        ),
      );
    }
    for (final m in RegExp(r'\bmatchesGoldenFile\s*\(').allMatches(code)) {
      final args = source.arguments(m.end - 1);
      final name = args.isEmpty ? null : _literal(args.first);
      if (name != null) {
        shots.add(
          _Shot(
            name.replaceAll(RegExp(r'\.png$'), '').split('/').last,
            const {},
          ),
        );
      }
    }
    final literal = RegExp(r'''(['"])(dark|light)\1''');
    final modes = {for (final m in literal.allMatches(code)) m[2]!};
    _modeVariable = modes.containsAll(['dark', 'light']);
  }

  final KitSource source;
  final shots = <_Shot>[];
  late final bool _modeVariable;

  static String? _literal(String? expression) {
    if (expression == null) return null;
    final m = RegExp(r'''^(['"])(.*)\1$''').firstMatch(expression.trim());
    return m?[2];
  }

  /// The modes a golden name covers.
  Set<String> modesOf(_Shot shot) => {
    if (shot.name.contains('dark')) 'dark',
    if (shot.name.contains('light')) 'light',
    if (_modeVariable && RegExp(r'\$\{?mode\b').hasMatch(shot.name)) ...[
      'dark',
      'light',
    ],
  };

  /// The `WxH` sizes [shot] is recorded at: from its name, a `Size(…)`
  /// literal, a variable set to one, or the list a `for` loop over the
  /// variable walks.
  Set<String> sizesOf(_Shot shot) {
    final named = RegExp(r'(\d+)x(\d+)').firstMatch(shot.name);
    if (named != null) return {'${named[1]}x${named[2]}'};
    final size = shot.sizeArgument;
    if (size == null) return const {};
    final code = source.code;
    final literal = _sizesIn(size, code, 2);
    if (literal.isNotEmpty) return literal;
    if (!RegExp(r'^\w+$').hasMatch(size)) return const {};
    final out = <String>{};
    for (final m in RegExp(
      '\\b$size\\s*=\\s*(?:const\\s+)?(Size\\([^()]*\\))',
    ).allMatches(code)) {
      out.addAll(_sizesIn(m[1]!, code, 2));
    }
    for (final m in RegExp(
      '\\bfor\\s*\\(\\s*(?:final|var|const)?\\s*(?:Size\\s+)?$size\\s+in\\b',
    ).allMatches(code)) {
      final open = code.indexOf('(', m.start);
      final end = source.close(open);
      if (end < 0) continue;
      out.addAll(_sizesIn(code.substring(m.end, end), code));
    }
    return out;
  }

  /// Whether [shot] is at 412×915.
  bool atPhone(_Shot shot) => sizesOf(shot).contains('412x915');

  /// Whether some shot is at [size] (`WxH`).
  bool covers(String size) => shots.any((s) => sizesOf(s).contains(size));

  bool uses(String identifier) =>
      RegExp('\\b$identifier\\b').hasMatch(source.code);

  bool get hasText2 => shots.any(
    (s) =>
        s.name.contains('text2') &&
        (RegExp(r'^2(?:\.0)?$').hasMatch(s.arguments['textScale'] ?? '') ||
            (s.arguments.isEmpty &&
                RegExp(
                  r'TextScaler\.linear\(\s*2(?:\.0)?\s*\)',
                ).hasMatch(source.code))),
  );

  /// The modes of the 412×915 goldens named `<snake>_<state>…`.
  Set<String> stateModes(String snake, String state) {
    final prefix = RegExp('^${RegExp.escape('${snake}_$state')}(?:\$|_|\\\$)');
    return {
      for (final s in shots)
        if (prefix.hasMatch(s.name) && atPhone(s)) ...modesOf(s),
    };
  }

  Set<String> get allModes => {for (final s in shots) ...modesOf(s)};
}

/// check -> subject -> why it fails.
Map<String, Map<String, String>> kitManifestViolations(KitManifest manifest) {
  final out = {for (final c in kitManifestChecks) c: <String, String>{}};
  final docRows = _source(
    _kitLibrary,
  ).text.split('\n').where((l) => l.startsWith('/// |')).join('\n');
  bool inDocTable(String name) => docRows.contains('[$name]');

  String? consumer(String path) =>
      File(path).existsSync() ? _source(path).code : null;

  final motion = consumer(_motionTest);
  final keyboard = consumer(_keyboardTest);
  final overflow = consumer(_overflowTest);
  bool covers(String? code, KitManifestPart part) =>
      code != null &&
      (RegExp(r'\breadKitManifest\s*\(').hasMatch(code) ||
          part.aliases.any(
            (a) => RegExp('\\b${RegExp.escape(a)}\\b').hasMatch(code),
          ));

  for (final part in manifest.parts) {
    final snake = part.snake;
    final home = part.homeSnake;
    if (!part.exported) {
      out['exported']![part.name] =
          '${part.file} is not reachable from $_kitLibrary';
    }
    final allowedFiles = part.kind == KitManifestKind.scene
        ? ['lib/ui/kit/scenes/$snake.dart']
        : [
            'lib/ui/kit/$snake.dart',
            'lib/ui/kit/chat/$snake.dart',
            'lib/ui/kit/team/$snake.dart',
          ];
    if (!part.name.startsWith('Kit')) {
      out['name']![part.name] = 'not named Kit<Name> (${part.file})';
    } else if (!allowedFiles.contains(part.file) && !part.coLocated) {
      out['name']![part.name] =
          'declared in ${part.file}, not ${allowedFiles.join(' or ')}';
    }
    if (!inDocTable(part.name)) {
      out['docRow']![part.name] = 'no [${part.name}] row in the kit.dart table';
    }
    final galleryPath = 'test/goldens/kit/${home}_golden_test.dart';
    final gallery = File(galleryPath).existsSync()
        ? _Gallery(_source(galleryPath))
        : null;
    if (part.kind == KitManifestKind.scene) {
      // TEST-14: dark and light goldens of the finished frame.
      if (gallery == null) {
        out['gallery']![part.name] = 'no $galleryPath';
      } else {
        final missing = {'dark', 'light'}.difference(gallery.allModes);
        if (missing.isNotEmpty) {
          out['gallery']![part.name] =
              '$galleryPath has no ${missing.join(' or ')} golden';
        }
      }
      continue;
    }
    final unitTest = 'test/kit/${home}_test.dart';
    if (!File(unitTest).existsSync()) {
      out['test']![part.name] = 'no $unitTest';
    }
    if (part.kind == KitManifestKind.scope) continue;

    if (part.statesProblem case final problem?) {
      out['states']![part.name] = problem;
    } else {
      final declared = part.states!;
      final missing = [
        for (final MapEntry(key: state, value: why)
            in part.requiredStates.entries)
          if (!declared.contains(state)) '$state ($why)',
      ];
      if (missing.isNotEmpty) {
        out['states']![part.name] =
            'declares ${declared.isEmpty ? 'none' : declared.join(', ')} '
            'but needs ${missing.join(', ')}';
      }
    }
    if (gallery == null) {
      out['gallery']![part.name] = 'no $galleryPath';
    } else {
      final missing = [
        for (final size in _gallerySizes)
          if (!gallery.covers(size)) 'a $size golden',
        if (!gallery.hasText2 && !_text2Pending.containsKey(part.name))
          'a …text2… golden at textScale: 2',
      ];
      if (missing.isNotEmpty) {
        out['gallery']![part.name] = '$galleryPath lacks ${missing.join(', ')}';
      }
    }
    final missingScenes = [
      for (final state in part.states ?? const <String>[])
        for (final mode in ['dark', 'light'])
          if (gallery == null ||
              !{
                ...gallery.stateModes(snake, state),
                if (home != snake)
                  ...gallery.stateModes(
                    '${home}_${snake.replaceFirst('kit_', '')}',
                    state,
                  ),
              }.contains(mode))
            '${snake}_${state}_…$mode',
    ];
    if (missingScenes.isNotEmpty) {
      out['stateScenes']![part.name] =
          'no 412x915 golden ${missingScenes.join(', ')}';
    }
    if (!covers(motion, part)) {
      out['motion']![part.name] = 'not in $_motionTest';
    }
    if ((part.isModal || part.isRow) && !covers(keyboard, part)) {
      out['keyboard']![part.name] = 'not in $_keyboardTest';
    }
    if (!covers(overflow, part)) {
      out['overflow']![part.name] = 'not in $_overflowTest';
    }
  }

  const exactReturns = {
    'showKitSheet': 'Future<T?>',
    'showKitConfirm': 'Future<bool>',
    'showKitInputDialog': 'Future<String?>',
    'showKitUndo': 'void',
  };
  for (final opener in manifest.openers) {
    if (!opener.exported) {
      out['exported']![opener.name] =
          '${opener.file} is not reachable from $_kitLibrary';
    }
    final type = opener.returnType.replaceAll(RegExp(r'\s+'), '');
    final exact = exactReturns[opener.name];
    if (exact != null ? type != exact : !type.startsWith('Future<')) {
      out['openerReturn']![opener.name] =
          '${type.isEmpty ? 'declares no return type' : 'returns ${opener.returnType}'}, '
          'expected ${exact ?? 'Future<…>'}';
    }
    if (opener.name != 'showKitUndo' && !opener.hasOptionalKey) {
      out['openerKey']![opener.name] = 'no optional Key? …Key parameter';
    }
    if (!inDocTable(opener.name)) {
      out['docRow']![opener.name] =
          'no [${opener.name}] row in the kit.dart table';
    }
  }

  final gallerySizes = _harnessSizes('kitGallerySizes');
  for (final size in _gallerySizes) {
    if (!gallerySizes.contains(size)) {
      out['harness']!['kitGallerySizes $size'] =
          '$_galleryHarness kitGallerySizes lacks the gallery size $size';
    }
  }
  final scaledSizes = _harnessSizes('kitGalleryScaledSizes');
  for (final size in _test9ScaledSizes) {
    if (!scaledSizes.contains(size)) {
      out['harness']!['kitGalleryScaledSizes $size'] =
          '$_galleryHarness kitGalleryScaledSizes lacks the TEST-9 size $size';
    }
  }
  return out;
}

Map<String, List<String>> _readAllowlist(Map<String, Object?> json) => {
  for (final MapEntry(:key, :value) in json.entries)
    if (!key.startsWith('_'))
      key: [for (final v in value! as List<Object?>) v! as String],
};

String _encodeAllowlist(Map<String, List<String>> allowlist, String about) {
  final ordered = <String, Object>{'_about': about};
  for (final check in kitManifestChecks) {
    final names = [...?allowlist[check]]..sort();
    if (names.isNotEmpty) ordered[check] = names;
  }
  return '${const JsonEncoder.withIndent('  ').convert(ordered)}\n';
}

void main() {
  final manifest = readKitManifest();

  test('G4: every deferral names who closes it', () {
    for (final MapEntry(key: check, value: parts)
        in _deferredAtKitMerge.entries) {
      expect(kitManifestChecks, contains(check));
      for (final MapEntry(key: part, value: owner) in parts.entries) {
        expect(owner.length, greaterThan(10), reason: '$check · $part');
      }
    }
  });

  test('G4: the manifest reads the kit library and every kit file', () {
    final names = {for (final p in manifest.parts) p.name};
    // Loud failures if the scan silently finds nothing (a vacuous pass).
    expect(names, containsAll(['KitSheet', 'KitConfirmSheet', 'KitRow']));
    expect(
      manifest.parts.where((p) => !p.exported).map((p) => p.name),
      containsAll(['StatesSheetScene', 'TerminalKeyBar']),
      reason: 'kit files imported by path must be in the manifest',
    );
    expect(
      manifest.openers.map((o) => o.name),
      containsAll(['showKitSheet', 'showKitConfirm']),
    );
    expect(
      manifest.parts.firstWhere((p) => p.name == 'KitSheet').openers,
      contains('showKitSheet'),
    );
    expect(
      manifest.unresolved,
      isEmpty,
      reason: 'kit.dart `show` names no declaration was found for',
    );
    expect(
      manifest.problems,
      isEmpty,
      reason: 'kit declarations the scan could not read',
    );
  });

  test('G4: every kit part and opener meets the manifest '
      '(allowlist only shrinks)', () {
    final raw =
        jsonDecode(File(_allowlistPath).readAsStringSync())
            as Map<String, Object?>;
    final unknownChecks = raw.keys
        .where((k) => !k.startsWith('_') && !kitManifestChecks.contains(k))
        .toList();
    expect(
      unknownChecks,
      isEmpty,
      reason: '$_allowlistPath names checks this gate does not run',
    );
    final allowlist = _readAllowlist(raw);
    final violations = kitManifestViolations(manifest);

    final grown = <String>[];
    final fresh = <String>[];
    final shrunk = <String, List<String>>{};
    final stale = <String>[];
    for (final check in kitManifestChecks) {
      final allowed = allowlist[check] ?? const <String>[];
      final ceiling = [
        ...?_creationAllowlist[check],
        ...?_deferredAtKitMerge[check]?.keys,
      ];
      final failing = violations[check]!;
      for (final subject in allowed) {
        if (!ceiling.contains(subject)) grown.add('$check · $subject');
      }
      for (final MapEntry(key: subject, value: why) in failing.entries) {
        if (!allowed.contains(subject) || !ceiling.contains(subject)) {
          fresh.add('$check · $subject: $why');
        }
      }
      shrunk[check] = [
        for (final subject in allowed)
          if (failing.containsKey(subject) && ceiling.contains(subject))
            subject,
      ];
      for (final subject in allowed) {
        if (!failing.containsKey(subject)) stale.add('$check · $subject');
      }
    }

    final lingering = [
      for (final MapEntry(key: check, value: parts)
          in _deferredAtKitMerge.entries)
        for (final part in parts.keys)
          if (!(allowlist[check] ?? const <String>[]).contains(part))
            '$check · $part',
    ];
    expect(
      lingering,
      isEmpty,
      reason:
          'these deferrals are closed: delete their lines from '
          '_deferredAtKitMerge in test/kit/kit_manifest_test.dart',
    );

    expect(
      grown,
      isEmpty,
      reason:
          '$_allowlistPath grew past the allowlist the gate was made with; '
          'fix the part instead (KIT-12, TEST-15; §18.2 "only shrinks")',
    );

    if (fresh.isNotEmpty) {
      fail(
        '${fresh.length} kit manifest violation(s) break STANDARDS.md G4 '
        '(NAME-1, KIT-10–KIT-14, TEST-9, TEST-14, TEST-15). Fix the part; '
        'the allowlist only shrinks:\n'
        '${fresh.map((f) => '  - $f').join('\n')}',
      );
    }

    if (stale.isNotEmpty) {
      final about = raw['_about'] as String? ?? '';
      final encoded = _encodeAllowlist(shrunk, about);
      if (Platform.environment['KIT_MANIFEST_WRITE'] == '1') {
        File(_allowlistPath).writeAsStringSync(encoded);
        stdout.writeln(
          'G4: wrote the smaller $_allowlistPath without:\n'
          '${stale.map((s) => '  - $s').join('\n')}',
        );
      } else {
        fail(
          'G4: ${stale.length} allowlist '
          '${stale.length == 1 ? 'entry now passes' : 'entries now pass'}; '
          'commit the smaller $_allowlistPath (or rerun with '
          'KIT_MANIFEST_WRITE=1):\n'
          '${stale.map((s) => '  - $s').join('\n')}\n$encoded',
        );
      }
    }
  });
}
