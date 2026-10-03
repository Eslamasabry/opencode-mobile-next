/// The one sheet frame and the one confirmation (docs/ux-system/kit-v2.md
/// §1.1, §1.2, §4.1, §4.2, §4.7, §8.2).
///
/// Both adapt to the window (§8.1, [KitLayout]): a bottom sheet on a phone,
/// a capped bottom sheet on a medium window, a centred panel (or an
/// end-side sheet for a full-height sheet) on a tablet in landscape, a PC
/// or the web. Esc closes the top one and obeys the same draft and unsaved
/// input rules as a swipe down.
library;

import 'dart:async';
import 'dart:math' as math;

import 'package:flutter/foundation.dart';
import 'package:flutter/gestures.dart';
import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter/services.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../../l10n/app_localizations.dart';
import '../app_theme.dart';
import '../widgets/request_routes.dart';
import 'kit_buttons.dart';
import 'kit_icon_button.dart';
import 'kit_layout.dart';
import 'kit_menu.dart';
import 'kit_motion.dart';
import 'kit_notice.dart';
import 'kit_progress.dart';
import 'kit_technical_value.dart';
import 'kit_text.dart';
import 'kit_tokens.dart';
import 'motion/kit_haptics.dart';
import 'motion/kit_reveal.dart';

part 'kit_confirm_sheet.dart';
part 'kit_consequences.dart';
part 'kit_sheet_frame.dart';
part 'kit_sheet_parts.dart';
part 'kit_sheet_modal.dart';

AppLocalizations _l10n(BuildContext context) =>
    lookupAppLocalizations(Localizations.localeOf(context));

/// How tall a [showKitSheet] opens.
enum KitSheetHeight {
  /// As tall as its content needs, up to 90 % of the window.
  content,

  /// Half the window: a list the person scrolls.
  half,

  /// Nearly the whole window; an end-side sheet on a wide window (§8.2).
  full,
}

/// The tone of a [showKitSheet] header's icon tile (§5 Sheets). [attention]
/// is used only by `showKitRequestSheet` (LOOK-4, LOOK-24); nothing outside
/// `lib/ui/kit/` reads it (G17).
enum KitSheetTone { neutral, attention }

extension on KitSheetTone {
  _KitTileTone get _tileTone => switch (this) {
    KitSheetTone.neutral => _KitTileTone.neutral,
    KitSheetTone.attention => _KitTileTone.attention,
  };
}

/// Typed input kept across dismissal, per target and server profile
/// (§1.1 data safety, gate G10). Its key is `oc.draft.<target>.<profileId>`,
/// so `ProfileStore.profileScopedPreferenceKeys` sweeps it when the profile
/// is deleted.
///
/// A sheet opened with a draft restores the saved text into [controller]
/// (when the field is empty), saves every change, and lets swipe, back,
/// Esc and close dismiss it silently: nothing is lost, so nothing is asked.
/// The caller calls [clear] once the input is used (sent, saved).
///
/// The caller owns [controller] and disposes it after the sheet closes.
@immutable
class KitDraft {
  const KitDraft({
    required this.target,
    required this.profileId,
    required this.controller,
    this.prefs,
  });

  /// What the text is for, stable across launches: `note.<sessionId>`.
  final String target;
  final String profileId;
  final TextEditingController controller;

  /// The store; defaults to [SharedPreferences.getInstance].
  final SharedPreferences? prefs;

  /// The [profileId] of a draft that belongs to the app, not to a server:
  /// Report a problem, whose page opens with or without a server. Its key
  /// (`oc.draft.<target>.app`) is the same from every entry point, no
  /// profile's deletion sweep removes it, and the page clears it once the
  /// report is sent. Every target that uses it is listed, with its reason,
  /// in `test/kit/kit_draft_manifest_test.dart` (G10); any other draft
  /// takes the server profile's id.
  static const appWide = 'app';

  static String keyFor(String target, String profileId) {
    assert(target.isNotEmpty && profileId.isNotEmpty);
    return 'oc.draft.$target.$profileId';
  }

  String get key => keyFor(target, profileId);

  Future<SharedPreferences> _store() async =>
      prefs ?? await SharedPreferences.getInstance();

  /// Puts the saved text back into an empty [controller].
  Future<void> restore() async {
    final saved = (await _store()).getString(key);
    if (saved != null && saved.isNotEmpty && controller.text.isEmpty) {
      controller.text = saved;
    }
  }

  /// Saves what [controller] holds now (an empty field removes the key).
  Future<void> save() async {
    final store = await _store();
    final text = controller.text;
    if (text.isEmpty) {
      await store.remove(key);
    } else {
      await store.setString(key, text);
    }
  }

  /// Forgets the saved text: the input was used.
  Future<void> clear() async => (await _store()).remove(key);
}

/// Opens [body] in the one sheet frame (§1.1): a handle, a header (title,
/// optional subtitle, close), a scrolling body and pinned actions.
///
/// It adapts to the window (§8.2): a bottom sheet on a compact window, a
/// bottom sheet capped at 640 dp on a medium one, a centred panel of up to
/// 560 dp on an expanded or large one, where [KitSheetHeight.full] becomes
/// an end-side sheet of 400–480 dp. A short window (a phone in landscape)
/// keeps the bottom sheet.
///
/// An action closes the sheet with `Navigator.pop(context, result)` from
/// the caller's context (the sheet is the top route of that navigator) or
/// with [KitSheet.close] from inside [body].
///
/// Data safety: with a [draft], swipe down, back, Esc and close keep the
/// text silently. With only [dirty], the frame owns the swipe (on its
/// handle and header) and catches back and Esc, and asks the discard
/// question inside the sheet (§4.7: never a sheet on a sheet). A
/// [showKitConfirm] raised from inside [body] also replaces the content in
/// place and adds no route. [dismissible] is false only while an
/// irreversible step runs, and the body says so.
///
/// With [routes], the sheet closes itself when its request is answered
/// elsewhere.
///
/// A pinned primary that changes while the sheet is open (Send enabling
/// once something is typed, Submit once an answer is chosen) comes from
/// [primaryListenable]: the frame redraws its pinned block whenever the
/// listenable changes, and the block stays pinned (KIT-17). While the
/// listenable holds null, [primary] is shown (null: no primary).
/// [secondaryListenable] does the same for the secondary, so a form whose
/// actions change as it works ("Test and turn on", then "Cancel test",
/// then "Save anyway") keeps both answers pinned.
///
/// One close control (owner rule: nothing shown twice): when the secondary
/// is a plain dismiss ("Cancel", "Not now"), pass [secondaryDismisses] and
/// the header draws no close button while a secondary is shown. Esc, back,
/// the handle and a swipe still close the sheet.
///
/// [footer] is a short line of settings that go with the primary (the
/// thinking level and agent beside "Use for this conversation"): it is
/// pinned with the actions, above them, so it stays in reach while the
/// body scrolls. It is at most one line of chips and brings its own
/// [KitTokens.space2] below it (nothing when it has nothing to show); a
/// longer choice opens from it as a menu, never a dialog over the sheet.
///
/// A long list is given as [itemCount] and [itemBuilder] instead of [body]:
/// the frame builds only the rows in view (a virtualised list), where a
/// [body] is laid out whole inside the frame's scroll view. Pass exactly
/// one of [body] and [itemBuilder].
Future<T?> showKitSheet<T>(
  BuildContext context, {
  required String title,
  WidgetBuilder? body,
  int? itemCount,
  IndexedWidgetBuilder? itemBuilder,
  String? subtitle,
  IconData? icon,
  KitSheetTone tone = KitSheetTone.neutral,
  KitSheetHeight height = KitSheetHeight.content,
  KitAction? primary,
  ValueListenable<KitAction?>? primaryListenable,
  KitAction? secondary,
  ValueListenable<KitAction?>? secondaryListenable,
  bool secondaryDismisses = false,
  List<KitAction> tertiary = const [],
  WidgetBuilder? footer,
  ValueListenable<bool>? dirty,
  KitDraft? draft,
  ValueListenable<bool>? loading,
  RequestRoutes? routes,
  bool dismissible = true,
  Key? sheetKey,
}) async {
  assert(
    (body == null) != (itemBuilder == null),
    'showKitSheet: pass a body or an itemBuilder, not both',
  );
  assert(
    itemBuilder == null || itemCount != null,
    'showKitSheet: an itemBuilder needs its itemCount',
  );
  if (routes?.isPending == false) return null;
  final window = KitLayout.modalWindowOf(context);
  final shape = !window.isWide
      ? _KitModalShape.bottom
      : height == KitSheetHeight.full
      ? _KitModalShape.side
      : _KitModalShape.panel;
  // A sheet guarding unsaved input owns its swipe (the route's own drag
  // would dismiss it without asking).
  final ownsDrag = dirty != null && draft == null;
  return _presentKitModal<T>(
    context,
    shape: shape,
    maxWidth: shape == _KitModalShape.bottom
        ? KitLayout.sheetMaxWidth
        : KitLayout.dialogPanelWidth,
    dismissible: dismissible,
    enableDrag: !ownsDrag,
    builder: (sheetContext) {
      routes?.own(ModalRoute.of(sheetContext));
      return _KitSheetHost(
        title: title,
        subtitle: subtitle,
        icon: icon,
        tone: tone,
        body: body,
        itemCount: itemCount,
        itemBuilder: itemBuilder,
        height: height,
        shape: shape,
        primary: primary,
        primaryListenable: primaryListenable,
        secondary: secondary,
        secondaryListenable: secondaryListenable,
        secondaryDismisses: secondaryDismisses,
        tertiary: tertiary,
        footer: footer,
        dirty: dirty,
        draft: draft,
        loading: loading,
        dismissible: dismissible,
        sheetKey: sheetKey,
      );
    },
  );
}

/// Opens [builder] as a bottom sheet when the body draws its own [KitSheet]
/// frame (with `handle: false`): a sheet whose frame depends on state only
/// its body holds, such as a fixed share of the window's height, a
/// full-height list with its own search, or a header that changes with a
/// tab (the folder browser, the command launcher, the timeline). Anything
/// else uses [showKitSheet].
///
/// The route is the theme's bottom sheet (`bottomSheetTheme`: `surface2`,
/// the sheet radius, the scrim and the drag handle the frame leaves out),
/// scroll-controlled so the body sets its own height, and capped at
/// [maxWidth] when given. [useSafeArea] keeps it below the status bar.
/// [sheetKey] keys the body. It returns what the body pops with, or null
/// when dismissed; `Navigator.pop(context, result)` from the caller's
/// context or [KitSheet.close] from inside the body closes it.
///
/// Added by slice-P9.10 so the last raw `showModalBottomSheet(` calls
/// outside the kit (G1) come through the kit without changing how they
/// look.
Future<T?> showKitFramedSheet<T>(
  BuildContext context, {
  required WidgetBuilder builder,
  double? maxWidth,
  bool useSafeArea = false,
  Key? sheetKey,
}) => showModalBottomSheet<T>(
  context: context,
  isScrollControlled: true,
  useSafeArea: useSafeArea,
  // The frame is drawn with `handle: false`: the route draws the one
  // handle, as the theme does.
  showDragHandle: true,
  // Reduced motion (MOT-7): no slide; otherwise the route's own.
  sheetAnimationStyle: KitMotion.reduced(context)
      ? AnimationStyle.noAnimation
      : null,
  constraints: maxWidth == null ? null : BoxConstraints(maxWidth: maxWidth),
  builder: (sheetContext) =>
      KeyedSubtree(key: sheetKey, child: builder(sheetContext)),
);

/// The one sheet frame (§1.1), drawn by [showKitSheet]; also used on its
/// own for goldens and for a full-screen variant on tablets.
///
/// A handle (bottom sheets only), a header with the title, an optional
/// muted subtitle and the close button at the end, the one loading bar,
/// the scrolling [child], and the pinned action block. At 200 % text the
/// title and subtitle wrap (never truncated) and the actions stay pinned
/// while the body scrolls; when a fixed header would take more than half
/// of the room the pinned actions leave (large text, the keyboard open, a
/// short window), the header scrolls away with the body. The frame never
/// overflows.
///
/// [KitSheet.list] draws a long list the same way, building only the rows
/// in view.
class KitSheet extends StatelessWidget {
  const KitSheet({
    super.key,
    required this.title,
    required this.child,
    this.subtitle,
    this.icon,
    this.tone = KitSheetTone.neutral,
    this.primary,
    this.secondary,
    this.tertiary = const [],
    this.footer,
    this.leading,
    this.menu = const [],
    this.menuLabel,
    this.headerLine,
    this.bar = false,
    this.step,
    this.onClose,
    this.loading = false,
    this.handle = true,
    this.fill = false,
    this.onPullDown,
    this.dismissKeyboardOnDrag = false,
    this.showClose = true,
  }) : itemCount = null,
       itemBuilder = null;

  /// The frame with a virtualised list for a body: only the rows in view
  /// are built, so a list of thousands opens as fast as a list of three.
  const KitSheet.list({
    super.key,
    required this.title,
    required int this.itemCount,
    required IndexedWidgetBuilder this.itemBuilder,
    this.subtitle,
    this.icon,
    this.tone = KitSheetTone.neutral,
    this.primary,
    this.secondary,
    this.tertiary = const [],
    this.footer,
    this.leading,
    this.menu = const [],
    this.menuLabel,
    this.headerLine,
    this.bar = false,
    this.step,
    this.onClose,
    this.loading = false,
    this.handle = true,
    this.fill = false,
    this.onPullDown,
    this.dismissKeyboardOnDrag = false,
    this.showClose = true,
  }) : child = const SizedBox.shrink();

  /// The place in the person's words, at most four words.
  final String title;
  final String? subtitle;

  /// The header's icon tile; null draws no tile.
  final IconData? icon;
  final KitSheetTone tone;

  /// The body; it scrolls inside the frame, so it is never its own
  /// scroll view or Scaffold.
  final Widget child;

  /// A [KitSheet.list] body: how many rows, and each row. Null for [child].
  final int? itemCount;
  final IndexedWidgetBuilder? itemBuilder;
  final KitAction? primary;
  final KitAction? secondary;
  final List<KitAction> tertiary;

  /// Pinned above the actions: a short line of settings that go with the
  /// primary (see [showKitSheet]).
  final Widget? footer;

  /// The header's first control, in place of the close button: a back
  /// chevron that goes up one folder or back one step, or Close (X) at the
  /// first step. Its label is read aloud and shown as the tooltip. Null
  /// keeps the close button at the end.
  final KitAction? leading;

  /// The header's overflow (a "more" button at the end, replacing the close
  /// button): a [KitMenuItem] per setting or route, so a rare choice never
  /// takes a row of the body.
  final List<KitMenuItem> menu;

  /// The overflow button's name; defaults to "More actions".
  final String? menuLabel;

  /// One quiet line under the title, such as a place menu ("This phone
  /// \u2304") that switches what the sheet shows. Replaces [subtitle].
  final Widget? headerLine;

  /// Pins [secondary] (a text button, start) and [primary] (the main
  /// button, end, ellipsized) as one bottom bar, the Move-to-a-folder
  /// layout, instead of the stacked action block. [tertiary] is not shown.
  final bool bar;

  /// Names the step the frame shows. When it changes the whole frame (the
  /// header, the body and the pinned block) cross-fades in place, so a
  /// second step replaces the first inside the one sheet and never opens
  /// another sheet or dialog. Reduced motion swaps at once.
  final Object? step;

  /// Null hides the close button (while an irreversible step runs).
  final VoidCallback? onClose;

  /// False leaves the close button out while [onClose] still closes from
  /// the handle: the secondary is the one close control (nothing shown
  /// twice).
  final bool showClose;
  final bool loading;

  /// The drag handle on top (bottom sheets).
  final bool handle;

  /// Fill the height the frame is given ([KitSheetHeight.half], `full`)
  /// instead of shrinking to the content.
  final bool fill;

  /// A swipe down on the handle or header, where the frame owns the drag
  /// (a sheet guarding unsaved input).
  final VoidCallback? onPullDown;

  /// A drag on the body closes the keyboard (a searchable sheet: the
  /// search field above, results below).
  final bool dismissKeyboardOnDrag;

  /// Closes the sheet [context] is inside with [result]: the person chose
  /// an action, so nothing is asked.
  static void close<T>(BuildContext context, [T? result]) =>
      Navigator.of(context).pop(result);

  @override
  Widget build(BuildContext context) {
    final frame = _buildFrame(context);
    if (step == null) return frame;
    return AnimatedSwitcher(
      duration: KitMotion.reduced(context) ? Duration.zero : KitMotion.quick,
      layoutBuilder: (current, previous) => Stack(
        alignment: AlignmentDirectional.topStart,
        children: [...previous, ?current],
      ),
      child: KeyedSubtree(key: ValueKey(step), child: frame),
    );
  }

  Widget _buildFrame(BuildContext context) {
    final tokens = KitTokens.of(context);
    final l10n = _l10n(context);
    final subtitle = this.subtitle;
    final footer = this.footer;
    final hasActions =
        primary != null ||
        secondary != null ||
        (!bar && tertiary.isNotEmpty) ||
        footer != null;
    Widget top = Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        if (handle) _KitHandle(onDismiss: onClose),
        Padding(
          // The handle's own height is the air above the header.
          padding: EdgeInsetsDirectional.only(
            start: leading != null ? tokens.space1 : tokens.rail,
            top: handle ? EdgeInsets.zero.top : tokens.space3,
            end: tokens.space2,
            bottom: tokens.space1,
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            mainAxisSize: MainAxisSize.min,
            children: [
              if (icon case final icon?) ...[
                KeyedSubtree(
                  key: const ValueKey('kit-sheet-icon'),
                  child: _KitIconTile(icon: icon, tone: tone._tileTone),
                ),
                SizedBox(height: tokens.space3),
              ],
              Row(
                crossAxisAlignment: leading != null
                    ? CrossAxisAlignment.center
                    : CrossAxisAlignment.start,
                children: [
                  if (leading case final lead?)
                    KitIconButton(
                      key: lead.key ?? const ValueKey('kit-sheet-leading'),
                      icon: lead.icon ?? AppIconography.back,
                      label: lead.label,
                      onPressed: lead.onPressed,
                    ),
                  Expanded(
                    child: Padding(
                      padding: leading != null
                          ? EdgeInsetsDirectional.only(
                              start: tokens.space1,
                              end: tokens.space2,
                            )
                          : EdgeInsetsDirectional.only(
                              top: tokens.space3,
                              end: tokens.space2,
                            ),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          Semantics(
                            header: true,
                            namesRoute: true,
                            child: KitText(title, role: KitTextRole.title),
                          ),
                          if (headerLine != null) ...[
                            SizedBox(height: tokens.space1 / 2),
                            headerLine!,
                          ] else if (subtitle != null) ...[
                            SizedBox(height: tokens.space1 / 2),
                            KitText(subtitle, role: KitTextRole.secondary),
                          ],
                        ],
                      ),
                    ),
                  ),
                  if (menu.isNotEmpty)
                    Builder(
                      builder: (anchor) => KitIconButton(
                        key: const ValueKey('kit-sheet-menu'),
                        icon: AppIconography.more,
                        tooltip: menuLabel ?? l10n.kitTopBarMore,
                        onPressed: () => showKitMenu(
                          anchor,
                          items: menu,
                          semanticsLabel: menuLabel ?? l10n.kitTopBarMore,
                        ),
                      ),
                    )
                  else if (onClose case final close?
                      when showClose && leading == null)
                    KitIconButton(
                      key: const ValueKey('kit-sheet-close'),
                      icon: AppIconography.close,
                      label: l10n.kitSheetClose,
                      onPressed: close,
                    ),
                ],
              ),
            ],
          ),
        ),
      ],
    );
    if (onPullDown case final pull?) {
      top = _PullDown(onPullDown: pull, child: top);
    }
    final header = Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        top,
        KitLoadingBar(loading: loading, label: l10n.kitSheetLoading),
      ],
    );
    // The body scrolls inside the frame. Its first sliver is the header's
    // place when a short window (or 200 % text with the keyboard open)
    // leaves no room to keep the header fixed: the header then scrolls away
    // with the body ("a short window lets the header scroll with the body").
    final bodyPadding = EdgeInsetsDirectional.fromSTEB(
      tokens.rail,
      tokens.space2,
      tokens.rail,
      hasActions ? tokens.space2 : tokens.rail,
    );
    final itemBuilder = this.itemBuilder;
    final scroll = CustomScrollView(
      shrinkWrap: !fill,
      keyboardDismissBehavior: dismissKeyboardOnDrag
          ? ScrollViewKeyboardDismissBehavior.onDrag
          : null,
      slivers: [
        const _KitHeaderSpacer(),
        if (itemBuilder != null)
          SliverPadding(
            padding: bodyPadding,
            sliver: SliverList.builder(
              itemCount: itemCount,
              itemBuilder: itemBuilder,
            ),
          )
        else
          SliverToBoxAdapter(
            child: Padding(padding: bodyPadding, child: child),
          ),
      ],
    );
    return _KitSheetFrame(
      fill: fill,
      // What the body keeps at least before the header gives up its fixed
      // place and before the pinned block is cut: two touch targets.
      reserve: tokens.minTarget * 2,
      children: [
        header,
        scroll,
        if (hasActions)
          Padding(
            key: const ValueKey('kit-sheet-actions'),
            padding: EdgeInsetsDirectional.fromSTEB(
              tokens.rail,
              tokens.space2,
              tokens.rail,
              tokens.space4,
            ),
            child: bar
                ? _KitSheetBar(primary: primary, secondary: secondary)
                : footer == null
                ? KitActionBlock(
                    primary: primary,
                    secondary: secondary,
                    tertiary: tertiary,
                  )
                : Column(
                    mainAxisSize: MainAxisSize.min,
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      // The footer brings its own space below it, so an
                      // empty one takes no room.
                      KeyedSubtree(
                        key: const ValueKey('kit-sheet-footer'),
                        child: footer,
                      ),
                      KitActionBlock(
                        primary: primary,
                        secondary: secondary,
                        tertiary: tertiary,
                      ),
                    ],
                  ),
          ),
      ],
    );
  }
}
