// KitStateView v2 (docs/ux-system/kit-api/KitStateView.md; kit-v2 §2.2,
// §4.9; design standard §3; STATE-1/2/3/5/12/13/20, LOOK-23, KIT-12,
// KIT-38, KIT-43, SEC-2, SEC-11).
import 'dart:async';

import 'package:flutter/material.dart';

import '../../l10n/app_localizations.dart';
import '../app_theme.dart';
import 'kit_bottom_inset.dart';
import 'kit_buttons.dart';
import 'kit_illustration.dart';
import 'kit_layout.dart';
import 'kit_max_width.dart';
import 'kit_notice.dart';
import 'kit_progress.dart';
import 'kit_since.dart';
import 'kit_status_answer.dart';
import 'kit_surface.dart';
import 'kit_surface_look.dart';
import 'kit_technical_value.dart';
import 'kit_text.dart';
import 'kit_tokens.dart';
import 'motion/kit_haptics.dart';
import 'motion/kit_motion_parts.dart';
import 'motion/kit_reveal.dart';

enum KitStateSize {
  /// Fills the body, centred vertically, with no card around it.
  page,

  /// Inside a list, the same slots at smaller type.
  inline,
}

/// Which constructor built a [KitStateView].
enum _Form { plain, missing, error, calm }

/// Every "not the normal content" moment (design standard §3): loading a
/// whole screen, empty, error, stopped or offline, blocked, a missing
/// capability. Fixed slots, in this order:
///
/// 1. [icon] in an icon tile (LOOK-23), its glyph tinted by [tone] through
///    the shared tone map (never a solid red block), or an [illustration]
///    (§10) where the moment deserves one;
/// 2. [title]: one line that says the state now, never contradicting the
///    progress ("Starting OpenCode…", not "stopped" while it starts);
/// 3. [body]: at most two short sentences, words for people in the app's
///    voice, never exception or transport text (`'$error'`,
///    `error.toString()`, an `ApiException` or `PlatformException`
///    message, an HTTP body): that goes to [details];
/// 4. [progress] (§4);
/// 5. actions in the one hierarchy (§2);
/// 6. the details fold ([KitDetailsFold]): [detailNotes], [detailValues],
///    the raw [details] text (redacted, mono) and [detailsChild], folded,
///    never above the actions.
///
/// Two optional places hold what a state is made of, without new slots in
/// that order: [content] sits between the progress and the actions (the
/// steps a progress is made of, a name field), and [footer] after
/// everything (the less common ways in, folded away).
///
/// Forms: the default constructor keeps its v1 behaviour exactly (a
/// `tone: failure` state shows only the actions it is given, KIT-43);
/// [KitStateView.error] adds the kit's error defaults (Copy details, and
/// Report a problem or the network fix, STATE-3); [KitStateView.missing]
/// explains a missing capability or offers to enable it.
///
/// Escalation: with [since] set, the state rebuilds once when the wait
/// reaches [KitMotion.escalateAfter] (through [KitSince], never a timer of
/// its own): the body becomes "Still waiting after 8 s", the [onSlow] ways
/// out unfold, and the live region announces it once. The title stays: a
/// slow wait is never called a failure.
///
/// Motion (§10): a state arrives with a short fade and rise when it first
/// shows and again whenever it becomes a different state (its icon, tone or
/// drawing changes); a new caption or progress inside the same state does
/// not replay it. Details unfold and fold. When a working state turns into
/// a finished one ([AppStatusTone.progress] to [AppStatusTone.ok]) while the
/// person watches, the phone gives a soft confirmation ([KitHaptics.done]).
///
/// States: loading, working, empty, error, missing, slow, finished
/// (+ disabled actions).
class KitStateView extends StatefulWidget {
  const KitStateView({
    super.key,
    required this.icon,
    required this.title,
    this.tone = AppStatusTone.neutral,
    this.body,
    this.progress,
    this.primary,
    this.secondary,
    this.tertiary = const [],
    this.details,
    this.detailNotes = const [],
    this.size = KitStateSize.page,
    this.titleKey,
    this.bodyKey,
    this.liveRegion = true,
    this.iconChild,
    this.content,
    this.footer,
    this.detailsChild,
    this.padding,
    this.illustration,
    this.illustrationAmbient = false,
    this.illustrationWidth,
    this.detailValues = const [],
    this.since,
    this.onSlow = const [],
    this.detailsKey,
  }) : capability = null,
       prerequisite = false,
       error = null,
       errorKind = null,
       retry = null,
       switchServer = null,
       reportSource = null,
       enableKey = null,
       copyDetailsKey = null,
       reportKey = null,
       countdownTo = null,
       countdownLine = null,
       header = null,
       _cost = const [],
       _form = _Form.plain;

  /// A capability the page needs is missing (map `whenMissing` modes
  /// *explains* and *offers-enable*). [why] is the body and says where the
  /// capability is available (STATE-12); [enable] is the primary; [cost]
  /// shows as [KitNotice.cost] above it (KIT-37). It looks nothing up: the
  /// registry lives in `KitCapabilityExplainer`, which builds this.
  ///
  /// [prerequisite] ("finish X first") requires [enable] (STATE-13).
  const KitStateView.missing({
    super.key,
    required String this.capability,
    required this.title,
    required String why,
    KitAction? enable,
    List<String> cost = const [],
    this.prerequisite = false,
    this.icon = AppIconography.locked,
    this.size = KitStateSize.inline,
    this.titleKey,
    this.bodyKey,
    this.enableKey,
  }) : assert(
         !prerequisite || enable != null,
         'KitStateView.missing: a prerequisite needs an enable action '
         '(STATE-13, G37): never a dead end.',
       ),
       body = why,
       primary = enable,
       _cost = cost,
       tone = AppStatusTone.neutral,
       progress = null,
       secondary = null,
       tertiary = const [],
       details = null,
       detailNotes = const [],
       liveRegion = true,
       iconChild = null,
       content = null,
       footer = null,
       detailsChild = null,
       padding = null,
       illustration = null,
       illustrationAmbient = false,
       illustrationWidth = null,
       detailValues = const [],
       since = null,
       onSlow = const [],
       detailsKey = null,
       error = null,
       errorKind = null,
       retry = null,
       switchServer = null,
       reportSource = null,
       copyDetailsKey = null,
       reportKey = null,
       countdownTo = null,
       countdownLine = null,
       header = null,
       _form = _Form.missing;

  /// An error with the kit's defaults (STATE-3, C26). [error] is only
  /// classified ([KitErrorKind.of], or [errorKind] when the caller knows
  /// better) and never shown. [details] is the raw technical text: it goes
  /// to the fold, to Copy details and to Report, always redacted.
  ///
  /// - network: [retry] is the primary, [switchServer] the secondary, and
  ///   "Copy details" a tertiary when [details] is set. No Report: the fix
  ///   comes first.
  /// - other: [retry] is the primary when given; "Copy details" when
  ///   [details] is set, and "Report a problem" when
  ///   [KitReportHook.available] (one tap here, the handler's preview makes
  ///   two, P8.3).
  const KitStateView.error({
    super.key,
    required this.title,
    this.body,
    this.error,
    this.errorKind,
    this.details,
    this.detailNotes = const [],
    this.detailValues = const [],
    this.retry,
    this.secondary,
    this.switchServer,
    this.reportSource,
    this.icon = AppIconography.error,
    this.size = KitStateSize.page,
    this.since,
    this.onSlow = const [],
    this.titleKey,
    this.bodyKey,
    this.copyDetailsKey,
    this.reportKey,
    this.detailsKey,
  }) : tone = AppStatusTone.failure,
       progress = null,
       primary = null,
       tertiary = const [],
       liveRegion = true,
       iconChild = null,
       content = null,
       footer = null,
       detailsChild = null,
       padding = null,
       illustration = null,
       illustrationAmbient = false,
       illustrationWidth = null,
       capability = null,
       prerequisite = false,
       enableKey = null,
       countdownTo = null,
       countdownLine = null,
       header = null,
       _cost = const [],
       _form = _Form.error;

  /// The calm empty or offline state (vision 2026-10-20, "Both, toned
  /// down"): no icon tile and no drawing, one big calm line ([title], set
  /// as [KitStatusAnswer] at [KitAnswerScale.calm]), one plain next step
  /// ([body]) under it, and the main action at the bottom within thumb
  /// reach. Nothing in it is amber or red: an empty list and a link that
  /// retries by itself need nobody.
  ///
  /// - page (the default): fills the height it is given. The words sit
  ///   centred in the room above the actions; [primary] (solid mint, full
  ///   width) and at most two [tertiary] text actions, centred, end the
  ///   page above the dock and the gesture bar; [footer] (one quiet line)
  ///   goes last. Without actions the words are centred. With unbounded
  ///   height (inside a list) it stacks with the state's vertical padding.
  /// - inline: inside a list (a search with no result), centred, with the
  ///   first [tertiary] as one quiet text action under the words and no
  ///   primary.
  ///
  /// [countdownTo] and [countdownLine] make [body] a live countdown to
  /// something the app really scheduled ("Trying again in 12 s").
  ///
  /// Motion: the words arrive with the state's fade and rise and
  /// cross-fade when they change ([KitStatusAnswer]); instant under reduced
  /// motion.
  const KitStateView.calm({
    super.key,
    required this.title,
    this.body,
    this.primary,
    this.tertiary = const [],
    this.footer,
    this.size = KitStateSize.page,
    this.titleKey,
    this.bodyKey,
    this.countdownTo,
    this.countdownLine,
    this.header,
    this.icon = AppIconography.info,
  }) : assert(
         countdownTo == null || countdownLine != null,
         'KitStateView.calm: a countdown needs countdownLine',
       ),
       tone = AppStatusTone.neutral,
       progress = null,
       secondary = null,
       details = null,
       detailNotes = const [],
       liveRegion = true,
       iconChild = null,
       content = null,
       detailsChild = null,
       padding = null,
       illustration = null,
       illustrationAmbient = false,
       illustrationWidth = null,
       detailValues = const [],
       since = null,
       onSlow = const [],
       detailsKey = null,
       capability = null,
       prerequisite = false,
       error = null,
       errorKind = null,
       retry = null,
       switchServer = null,
       reportSource = null,
       enableKey = null,
       copyDetailsKey = null,
       reportKey = null,
       _cost = const [],
       _form = _Form.calm;

  /// What the state is about, for its key and its arrival (a calm state
  /// draws no icon).
  final IconData icon;
  final AppStatusTone tone;
  final String title;

  /// [KitStateView.calm]: the body counts down to this moment.
  final DateTime? countdownTo;

  /// [KitStateView.calm]: the body's words for the time left.
  final String Function(Duration left)? countdownLine;

  /// [KitStateView.calm] page: what the screen says above the state (its
  /// quiet notes), at the top of the same scroll, full width; the words
  /// are centred in the room left between it and the actions.
  final Widget? header;

  /// Words, not exception text (see the class notes): the raw failure goes
  /// to [details], folded and redacted.
  final String? body;
  final KitProgress? progress;
  final KitAction? primary;
  final KitAction? secondary;
  final List<KitAction> tertiary;

  /// Raw technical text: the fold's mono block, redacted.
  final String? details;

  /// Plain lines shown first when the fold opens (what to check).
  final List<String> detailNotes;
  final KitStateSize size;
  final Key? titleKey;
  final Key? bodyKey;

  /// Announce the state when it changes (a page state usually should).
  final bool liveRegion;

  /// Drawn inside the icon tile instead of [icon] (a check that draws
  /// itself in); [icon] still names the state.
  final Widget? iconChild;

  /// Between the progress and the actions: what the state is made of.
  final Widget? content;

  /// After the actions and the details: the less common ways on.
  final Widget? footer;

  /// Shown last in the fold when it opens, after [detailNotes],
  /// [detailValues] and [details]; for technical content that is not one
  /// string (a log view).
  final Widget? detailsChild;

  /// Overrides the inline size's padding, for a host already on the rails.
  final EdgeInsetsGeometry? padding;

  /// A drawing (design standard §10) in place of the icon tile; [icon]
  /// still names the state.
  final KitScene? illustration;

  /// Keep [illustration] moving after its entrance: only for a state the
  /// person waits in (connecting, installing).
  final bool illustrationAmbient;

  /// The drawing's width when the default (140 dp on a page, 88 inline)
  /// does not suit: a scene with several figures on a screen with room.
  final double? illustrationWidth;

  /// Labelled technical values for the fold (addresses, ids, paths).
  final List<KitTechnicalValue> detailValues;

  /// When the wait began (the last progress event for a working state);
  /// the state escalates at [KitMotion.escalateAfter]. Null never escalates.
  final DateTime? since;

  /// At most two ways out, shown as tertiary actions after the escalation
  /// ("Try again", "Restart"). "Leave it running" is doing nothing, so it
  /// is a real action only when the caller can move the wait away.
  final List<KitAction> onSlow;

  /// The fold's toggle; null is `ValueKey('kit-state-details')` (TEST-5).
  final Key? detailsKey;

  /// [KitStateView.missing]: the capability id ("voice.model").
  final String? capability;

  /// [KitStateView.missing]: "finish X first"; [primary] is then required.
  final bool prerequisite;

  /// [KitStateView.error]: classified, never shown.
  final Object? error;
  final KitErrorKind? errorKind;

  /// [KitStateView.error]: "Try again", the primary.
  final KitAction? retry;

  /// [KitStateView.error]: "Switch server", the secondary for a network
  /// failure only.
  final KitAction? switchServer;

  /// [KitStateView.error]: the page or part id sent with a report
  /// ("saved-permissions").
  final String? reportSource;

  /// [KitStateView.missing]: overrides the enable action's key.
  final Key? enableKey;

  /// [KitStateView.error]: "Copy details"; null is
  /// `ValueKey('kit-state-copy-details')`.
  final Key? copyDetailsKey;

  /// [KitStateView.error]: "Report a problem"; null is
  /// `ValueKey('kit-state-report')`.
  final Key? reportKey;

  final List<String> _cost;
  final _Form _form;

  @override
  State<KitStateView> createState() => _KitStateViewState();
}

/// How far above the middle of its room a calm page sets its words, as a
/// share of the height it is given.
const double _calmLift = .1;

class _KitStateViewState extends State<KitStateView> {
  @override
  void initState() {
    super.initState();
    _check();
  }

  @override
  void didUpdateWidget(KitStateView old) {
    super.didUpdateWidget(old);
    _check();
    if (old.tone == AppStatusTone.progress && widget.tone == AppStatusTone.ok) {
      KitHaptics.done(context);
    }
  }

  void _check() {
    assert(
      widget.onSlow.length <= 2,
      'KitStateView.onSlow: at most two ways out (KitStateView.md).',
    );
  }

  /// The shared tone map (README D12, LOOK-4, LOOK-5, LOOK-6): attention
  /// and failure paint in `text1`; amber is for needs-you only.
  static Color _tintFor(ThemeRoles roles, AppStatusTone tone) => switch (tone) {
    AppStatusTone.neutral => roles.text2,
    AppStatusTone.progress => roles.accent,
    AppStatusTone.ok => roles.success,
    AppStatusTone.attention || AppStatusTone.failure => roles.text1,
  };

  /// The primary, secondary and tertiary actions: the caller's, or the
  /// error defaults.
  (KitAction?, KitAction?, List<KitAction>) _actions(AppLocalizations l10n) {
    final w = widget;
    switch (w._form) {
      case _Form.plain || _Form.calm:
        return (w.primary, w.secondary, w.tertiary);
      case _Form.missing:
        final enable = w.primary;
        final key = w.enableKey;
        final primary = enable == null || key == null || !_plain(enable)
            ? enable
            : KitAction(
                key: key,
                label: enable.label,
                onPressed: enable.onPressed,
                icon: enable.icon,
                destructive: enable.destructive,
                working: enable.working,
                disabledReason: enable.disabledReason,
                shortcut: enable.shortcut,
              );
        return (primary, null, const []);
      case _Form.error:
        final details = w.details;
        final copy = details == null
            ? null
            : KitAction.copy(
                key:
                    w.copyDetailsKey ??
                    const ValueKey('kit-state-copy-details'),
                label: l10n.kitCopyDetails,
                text: () => KitReportHook.redact(details),
              );
        final network =
            (w.errorKind ?? KitErrorKind.of(w.error)) == KitErrorKind.network;
        if (network) return (w.retry, w.secondary ?? w.switchServer, [?copy]);
        return (
          w.retry,
          w.secondary,
          [
            ?copy,
            if (KitReportHook.available)
              KitAction(
                key: w.reportKey ?? const ValueKey('kit-state-report'),
                label: l10n.kitReportProblem,
                onPressed: () => unawaited(_report()),
              ),
          ],
        );
    }
  }

  static bool _plain(KitAction action) => action.copyText == null;

  Future<void> _report() {
    final details = widget.details;
    return KitReportHook.report(
      context,
      KitReport(
        title: widget.title,
        details: details == null ? null : KitReportHook.redact(details),
        source: widget.reportSource,
        errorType: widget.error?.runtimeType.toString(),
      ),
    );
  }

  @override
  Widget build(BuildContext context) => KitMaxWidth.cap(
    maxWidth: KitLayout.stateMaxWidth,
    // A page's state sits in the middle; an inline one starts at the
    // gutter, where the words around it do.
    alignment: widget.size == KitStateSize.page
        ? AlignmentDirectional.center
        : AlignmentDirectional.centerStart,
    child: _buildContent(context),
  );

  Widget _buildContent(BuildContext context) {
    if (widget._form == _Form.calm) return _buildCalm(context);
    final since = widget.since;
    if (since == null) return _build(context, slow: false);
    return KitSince(
      since: since,
      builder: (context, status) => _build(context, slow: status.isSlow),
    );
  }

  Widget _buildCalm(BuildContext context) {
    final tokens = KitTokens.of(context);
    final w = widget;
    final page = w.size == KitStateSize.page;
    final tertiary = w.tertiary.take(2).toList();
    final answer = KitStatusAnswer(
      answer: w.title,
      scale: page ? KitAnswerScale.calm : KitAnswerScale.compact,
      line: w.body,
      countdownTo: w.countdownTo,
      countdownLine: w.countdownLine,
      answerKey: w.titleKey,
      lineKey: w.bodyKey,
    );
    Widget quiet() => Wrap(
      alignment: WrapAlignment.center,
      spacing: tokens.space2,
      children: [
        for (final action in tertiary)
          KitButton.fromAction(
            action,
            role: KitButtonRole.tertiary,
            expand: false,
          ),
      ],
    );
    if (!page) {
      return Padding(
        padding: EdgeInsetsDirectional.symmetric(vertical: tokens.space6),
        child: KitEntrance(
          trigger: w.icon,
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              answer,
              if (tertiary.isNotEmpty) ...[
                SizedBox(height: tokens.space3),
                quiet(),
              ],
            ],
          ),
        ),
      );
    }
    final primary = w.primary;
    final footer = w.footer;
    final hasBottom = primary != null || tertiary.isNotEmpty || footer != null;
    final bottom = !hasBottom
        ? null
        : Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              if (primary != null)
                KitButton.fromAction(primary, role: KitButtonRole.primary),
              if (tertiary.isNotEmpty) ...[
                if (primary != null) SizedBox(height: tokens.space2),
                quiet(),
              ],
              if (footer != null) ...[SizedBox(height: tokens.space2), footer],
            ],
          );
    return LayoutBuilder(
      builder: (context, constraints) {
        final bounded = constraints.hasBoundedHeight;
        // The words in the middle of the room above the actions, the
        // actions at the bottom: spaceBetween over the height the state is
        // given (a minimum, so large text still scrolls).
        final width = constraints.maxWidth;
        final side = width.isFinite
            ? ((width - KitLayout.stateMaxWidth) / 2).clamp(
                tokens.gutter,
                double.infinity,
              )
            : tokens.gutter;
        Widget railed(Widget child) => Padding(
          padding: EdgeInsetsDirectional.symmetric(horizontal: side),
          child: child,
        );
        final header = w.header;
        final spread = bounded && (bottom != null || header != null);
        // Optical centre: a little above the middle of the room, where the
        // eye expects the middle to be.
        final lift = spread ? constraints.maxHeight * _calmLift : 0.0;
        // Above the dock and the gesture bar, like a list's end.
        final end =
            tokens.space4 + (bounded ? KitBottomInset.of(context).bottom : 0.0);
        // The words in the middle of the room between the screen's notes
        // and the actions, the actions at the bottom: spaceBetween over the
        // height the state is given (a minimum, so large text still
        // scrolls).
        final column = Column(
          mainAxisAlignment: spread
              ? MainAxisAlignment.spaceBetween
              : MainAxisAlignment.center,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            if (header != null) header else if (spread) const SizedBox.shrink(),
            Padding(
              padding: EdgeInsetsDirectional.only(
                top: tokens.space5,
                bottom: tokens.space5 + lift,
              ),
              child: railed(KitEntrance(trigger: w.icon, child: answer)),
            ),
            if (bottom != null)
              railed(KitEntrance(trigger: w.icon, child: bottom))
            else if (spread)
              const SizedBox.shrink(),
          ],
        );
        return SingleChildScrollView(
          physics: const AlwaysScrollableScrollPhysics(),
          child: ConstrainedBox(
            constraints: BoxConstraints(
              minHeight: bounded ? constraints.maxHeight : 0,
            ),
            child: Padding(
              padding: EdgeInsetsDirectional.only(bottom: end),
              child: column,
            ),
          ),
        );
      },
    );
  }

  Widget _build(BuildContext context, {required bool slow}) {
    final tokens = KitTokens.of(context);
    final roles = tokens.roles;
    final page = widget.size == KitStateSize.page;
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    final tint = _tintFor(roles, widget.tone);
    final (primary, secondary, tertiary) = _actions(l10n);
    final actions = KitActionBlock(
      primary: primary,
      secondary: secondary,
      tertiary: tertiary,
    );
    final fold = KitDetailsFold(
      values: widget.detailValues,
      notes: widget.detailNotes,
      text: widget.details,
      foldKey: widget.detailsKey ?? const ValueKey('kit-state-details'),
      textKey: const ValueKey('kit-state-details-text'),
      child: widget.detailsChild,
    );
    final body = slow ? KitSince.slowLabel(context) : widget.body;
    final Widget? bodyText = body == null
        ? null
        : KitText(
            body,
            key: widget.bodyKey,
            role: page ? KitTextRole.body : KitTextRole.secondary,
            tone: KitTextTone.secondary,
          );
    final content = widget._cost.isEmpty
        ? widget.content
        : KitNotice.cost(widget._cost);
    final tile = page ? tokens.markSize : tokens.iconTileSize;
    final gap = page ? tokens.space5 : tokens.space3;
    final column = Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Align(
          alignment: AlignmentDirectional.centerStart,
          child: widget.illustration != null
              ? KitIllustration(
                  key: const ValueKey('kit-state-illustration'),
                  scene: widget.illustration!,
                  width: widget.illustrationWidth ?? (page ? 140 : 88),
                  ambient: widget.illustrationAmbient,
                )
              // The tile is made for the person's Style like every other
              // tile (KitSurface.tile's control surface).
              : kitSurfaceMaterial(
                  context,
                  level: KitSurfaceLevel.surface3,
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(
                      page ? tokens.markRadius : tokens.iconTileRadius,
                    ),
                  ),
                  fill: roles.surface3,
                  role: KitSurfaceRole.control,
                  clip: false,
                  child: SizedBox.square(
                    key: const ValueKey('kit-state-icon'),
                    dimension: tile,
                    child: Center(
                      child:
                          widget.iconChild ??
                          Icon(
                            widget.icon,
                            size: page
                                ? tokens.markIconSize
                                : tokens.smallIconSize,
                            color: tint,
                          ),
                    ),
                  ),
                ),
        ),
        SizedBox(height: gap),
        KitText(
          widget.title,
          key: widget.titleKey,
          role: page ? KitTextRole.title : KitTextRole.headline,
        ),
        if (bodyText != null) ...[
          SizedBox(height: page ? tokens.space2 : tokens.space1),
          if (widget.since == null)
            bodyText
          else
            KitSwap(
              alignment: AlignmentDirectional.centerStart,
              child: KeyedSubtree(
                key: ValueKey(slow ? 'kit-state-slow' : 'kit-state-body'),
                child: bodyText,
              ),
            ),
        ],
        if (widget.progress case final progress?) ...[
          SizedBox(height: gap),
          KitProgressView(progress: progress),
        ],
        if (content != null) ...[SizedBox(height: gap), content],
        if (!actions.isEmpty) ...[
          SizedBox(height: page ? tokens.space6 : tokens.space3),
          actions,
        ],
        if (widget.onSlow.isNotEmpty)
          KitReveal(
            child: !slow
                ? null
                : Padding(
                    key: const ValueKey('kit-state-on-slow'),
                    padding: EdgeInsetsDirectional.only(
                      top: page ? tokens.space3 : tokens.space2,
                    ),
                    child: KitActionBlock(tertiary: widget.onSlow),
                  ),
          ),
        if (!fold.isEmpty) ...[
          SizedBox(height: page ? tokens.space3 : tokens.space1),
          fold,
        ],
        if (widget.footer case final footer?) ...[
          SizedBox(height: page ? tokens.space4 : tokens.space2),
          footer,
        ],
      ],
    );
    final announced = Semantics(
      container: true,
      liveRegion: widget.liveRegion,
      child: KitEntrance(
        trigger: (widget.icon, widget.tone, widget.illustration?.runtimeType),
        child: column,
      ),
    );
    if (!page) {
      return Padding(
        padding:
            widget.padding ??
            EdgeInsetsDirectional.fromSTEB(
              tokens.gutter,
              tokens.gutter,
              tokens.gutter,
              tokens.space2,
            ),
        child: announced,
      );
    }
    return LayoutBuilder(
      builder: (context, constraints) => SingleChildScrollView(
        physics: const AlwaysScrollableScrollPhysics(),
        child: ConstrainedBox(
          constraints: BoxConstraints(
            minHeight: constraints.hasBoundedHeight ? constraints.maxHeight : 0,
          ),
          child: Center(
            child: Padding(
              padding: EdgeInsetsDirectional.symmetric(
                horizontal: tokens.gutter,
                vertical: KitTokens.stateVerticalPadding,
              ),
              child: ConstrainedBox(
                constraints: const BoxConstraints(
                  maxWidth: KitLayout.stateMaxWidth,
                ),
                child: announced,
              ),
            ),
          ),
        ),
      ),
    );
  }
}
