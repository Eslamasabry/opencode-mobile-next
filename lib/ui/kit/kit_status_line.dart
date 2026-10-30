import 'dart:async';

import 'package:clock/clock.dart';
import 'package:flutter/material.dart';
import 'package:flutter/semantics.dart';

import '../../l10n/app_localizations.dart';
import '../app_theme.dart';
import 'kit_buttons.dart';
import 'kit_icon.dart';
import 'kit_icon_button.dart';
import 'kit_menu.dart';
import 'kit_since.dart';
import 'kit_text.dart';
import 'kit_tokens.dart';
import 'motion/kit_motion_parts.dart';
import 'motion/kit_reveal.dart';

/// Which condition a status is, in priority order, highest first (KIT-35;
/// `work` placed below the shell conditions and above updates, so a team's
/// Now line is never hidden by "Update ready", which STATE-19 calls the
/// lowest condition).
enum KitStatusKind {
  /// Offline, reconnecting, not answering.
  connection,

  /// Android stopped the app; background checks paused.
  appStopped,

  /// The phone is hot; the team is paused.
  heat,

  /// A risky switch is on for this screen (KitSwitchRow.risk). Never
  /// dismissible (SEC-9).
  riskySwitch,

  /// A working screen's own line: the team Now line, a live process.
  work,

  /// An app, code-push or server update is ready (STATE-19).
  update,

  /// Anything else a screen shows (a search summary, a counter).
  info,
}

/// One condition, produced by the one status source for its entity
/// (ARCH-8): the words are decided there, once; screens pass this object,
/// not strings, and render it with [KitStatusLine.of].
@immutable
class KitStatus {
  const KitStatus({
    required this.kind,
    required this.icon,
    required this.message,
    this.id,
    this.key,
    this.messageKey,
    this.supportingKey,
    this.dismissTooltip,
    this.tone = AppStatusTone.neutral,
    this.supporting,
    this.next,
    this.action,
    this.more = const [],
    this.since,
    this.onSlow = const [],
    this.onDismiss,
    this.quiet = false,
    this.semanticsLabel,
    this.countdownTo,
    this.countdownLine,
    this.onOpen,
  }) : assert(
         countdownTo == null || countdownLine != null,
         'KitStatus: a countdown needs countdownLine',
       ),
       assert(
         kind != KitStatusKind.riskySwitch || onDismiss == null,
         'A risky switch line is never dismissible: hiding it would hide a '
         'live risk (SEC-9). Offer its "Turn off" as the action instead.',
       );

  final KitStatusKind kind;

  /// Stable identity for dedupe and announcements: `connection:<profileId>`.
  final String? id;
  final Key? key;
  final Key? messageKey;
  final Key? supportingKey;
  final String? dismissTooltip;
  final IconData icon;

  /// "Reconnecting to laptop…"
  final String message;
  final AppStatusTone tone;
  final String? supporting;

  /// The Now line: "Working on the login fix · a reviewer checks it next ·
  /// about 6 min".
  final String? next;
  final KitAction? action;
  final List<KitAction> more;

  /// When the wait began; the line escalates after
  /// [KitMotion.escalateAfter].
  final DateTime? since;

  /// At most two ways out, offered once the wait turned slow.
  final List<KitAction> onSlow;
  final VoidCallback? onDismiss;

  /// A standing note, not a condition to act on: drawn as one small muted
  /// caption line with a quiet text action, no divider (the chat's
  /// "Auto-approve is on · Turn off").
  final bool quiet;

  /// What a screen reader says for a [quiet] line when its short words
  /// leave out detail (the time something happened); null: the message.
  final String? semanticsLabel;

  /// When something the app really scheduled happens (a retry timer): the
  /// supporting line becomes [countdownLine] for the time left, asked again
  /// once a second until it passes (then with [Duration.zero]). Wins over
  /// [supporting]. Never for a time the app only guesses.
  final DateTime? countdownTo;

  /// The supporting words for the time left until [countdownTo] ("Trying
  /// again in 12 s").
  final String Function(Duration left)? countdownLine;

  /// A [quiet] line's words open what is behind them (the connection's
  /// Details) when tapped; the line keeps no More menu then.
  final VoidCallback? onOpen;

  /// Lower is more important ([kind]'s index).
  int get priority => kind.index;

  /// The same condition as one line: the words and More, with the action
  /// folded into More (first) and no supporting, Now or slow line. For a
  /// page about something else (the phone's own setup under a saved
  /// server's connection problem, B6): the truth stays on screen, its ways
  /// out one tap away, without pushing every step of the page down.
  KitStatus compact() {
    // A quiet line is one line already.
    if (quiet) return this;
    final action = this.action;
    return KitStatus(
      kind: kind,
      icon: icon,
      message: message,
      id: id,
      key: key,
      messageKey: messageKey,
      dismissTooltip: dismissTooltip,
      tone: tone,
      more: [?action, ...more],
      onDismiss: onDismiss,
      quiet: quiet,
      semanticsLabel: semanticsLabel,
    );
  }

  /// The one status to show among [statuses] (nulls ignored): the lowest
  /// [priority] value; ties keep the first. Used by `KitStatusLineSlot`.
  static KitStatus? highest(Iterable<KitStatus?> statuses) {
    KitStatus? best;
    for (final status in statuses) {
      if (status == null) continue;
      if (best == null || status.priority < best.priority) best = status;
    }
    return best;
  }
}

/// A condition on an otherwise working screen (design standard §5):
/// offline or reconnecting, Android stopped the app, heat, a risky switch
/// that is on, an update ready, or the team's Now line. One row: an icon,
/// the words, an optional action, and further actions behind a small menu.
/// At most one per window (`KitStatusLineSlot` picks it with
/// [KitStatus.highest]), not a card. It replaces every `MaterialBanner` and
/// the update and release snackbars.
///
/// States: condition, working, slow, now-line, error (+ disabled action).
///
/// - working (`tone: progress`): the icon in `accent`; no spinner. Give such
///   a line a [since], or its wait runs silently past 8 s (STATE-5).
/// - slow: [KitMotion.escalateAfter] after [since] the supporting line reads
///   "Still waiting after 8 s", and the first [onSlow] action takes the
///   action slot when it is empty (otherwise the [onSlow] actions go first
///   in More). Announced once.
/// - now-line: [next] under the message in `text2`; it unfolds when it first
///   appears and cross-fades when it changes.
/// - error (`tone: failure`): the neutral glyph in `text1` (LOOK-5); the
///   action is the fix ("Try again").
/// - disabled action: its `disabledReason` shows under the line; the line
///   itself is never disabled.
///
/// Accessibility: one live region, holding the message (and the slow
/// words once escalated). A change of the message or the escalation is
/// announced once; a new [supporting] or [next] (a time counting down) is
/// not re-announced.
///
/// Motion (§10): it fades and rises into place when it appears and again
/// when it becomes a different condition (its icon or tone changes); new
/// words for the same condition change in place. To have it fold away when
/// it goes, the host shows it through a [KitReveal]. Reduced motion:
/// instant. Haptics: none.
class KitStatusLine extends StatelessWidget {
  const KitStatusLine({
    super.key,
    required this.icon,
    required this.message,
    this.tone = AppStatusTone.neutral,
    this.action,
    this.more = const [],
    this.onDismiss,
    this.messageKey,
    this.dismissKey,
    this.dismissTooltip,
    this.controlsTogether = false,
    this.supporting,
    this.supportingKey,
    this.supportingSemanticsLabel,
    this.next,
    this.nextKey,
    this.since,
    this.onSlow = const [],
    this.moreKey,
    this.quiet = false,
    this.semanticsLabel,
    this.countdownTo,
    this.countdownLine,
    this.onOpen,
  }) : assert(
         countdownTo == null || countdownLine != null,
         'KitStatusLine: a countdown needs countdownLine',
       );

  /// See [KitStatus.onOpen].
  final VoidCallback? onOpen;

  /// See [KitStatus.countdownTo].
  final DateTime? countdownTo;

  /// See [KitStatus.countdownLine].
  final String Function(Duration left)? countdownLine;

  /// See [KitStatus.quiet].
  final bool quiet;

  /// See [KitStatus.semanticsLabel].
  final String? semanticsLabel;

  /// Renders [status] from its entity's status source (ARCH-8). Its key
  /// defaults to `ValueKey('kit-status-<id or kind>')`, so a new condition is
  /// a new line (and a new announcement).
  factory KitStatusLine.of(KitStatus status, {Key? key, Key? messageKey}) =>
      KitStatusLine(
        key:
            key ??
            status.key ??
            ValueKey('kit-status-${status.id ?? status.kind.name}'),
        icon: status.icon,
        message: status.message,
        tone: status.tone,
        action: status.action,
        more: status.more,
        onDismiss: status.onDismiss,
        messageKey: messageKey ?? status.messageKey,
        supportingKey: status.supportingKey,
        dismissTooltip: status.dismissTooltip,
        supporting: status.supporting,
        next: status.next,
        since: status.since,
        onSlow: status.onSlow,
        quiet: status.quiet,
        semanticsLabel: status.semanticsLabel,
        countdownTo: status.countdownTo,
        countdownLine: status.countdownLine,
        onOpen: status.onOpen,
      );

  final IconData icon;
  final String message;
  final AppStatusTone tone;
  final KitAction? action;

  /// Other ways out, in a compact menu after [action].
  final List<KitAction> more;

  /// Only when dismissing changes nothing real (§5).
  final VoidCallback? onDismiss;
  final Key? messageKey;

  /// The close button's key and tooltip, when the caller names them.
  final Key? dismissKey;
  final String? dismissTooltip;

  /// When the line stacks (large text), keep the action, More and close on
  /// one row under the words instead of leaving close at the top: a
  /// height-limited slot that scrolls to its end then shows every control
  /// together (a one-time tip above the composer).
  final bool controlsTogether;

  /// Optional: one short muted sentence under [message], for what to do
  /// next ("Send it again") or a fact that belongs to the same condition
  /// ("2 drafts will send when connected"). Never the raw error: that goes
  /// behind a Details action.
  final String? supporting;
  final Key? supportingKey;

  /// What a screen reader says for [supporting] when its words alone would
  /// not (a bare link: "Shared conversation link …").
  final String? supportingSemanticsLabel;

  /// The Now line, under the message in `text2`: "Working on the login fix ·
  /// a reviewer checks it next · about 6 min".
  final String? next;
  final Key? nextKey;

  /// When the wait began, on this phone's clock. The line escalates
  /// [KitMotion.escalateAfter] later (see the class's slow state).
  final DateTime? since;

  /// At most two ways out after the escalation ("Retry", "Leave it
  /// running").
  final List<KitAction> onSlow;

  /// The More button's key; default `ValueKey('kit-status-more')`.
  final Key? moreKey;

  static const _defaultMoreKey = ValueKey('kit-status-more');
  static const _defaultDismissKey = ValueKey('kit-status-dismiss');

  @override
  Widget build(BuildContext context) {
    assert(
      onSlow.length <= 2,
      'KitStatusLine.onSlow offers at most two ways out (got '
      '${onSlow.length}).',
    );
    if (quiet) {
      return KitEntrance(
        trigger: (tone, icon),
        child: _countingQuietLine(context, action: action, more: more),
      );
    }
    return KitEntrance(
      trigger: (tone, icon),
      child: KitSince(
        since: since,
        builder: (context, wait) => LayoutBuilder(
          builder: (context, constraints) {
            final slow = wait.isSlow;
            final firstSlow = slow && onSlow.isNotEmpty ? onSlow.first : null;
            final effectiveAction = action ?? firstSlow;
            final effectiveMore = !slow
                ? more
                : action == null
                ? [...onSlow.skip(1), ...more]
                : [...onSlow, ...more];
            final stacked = _stacks(
              context,
              constraints.maxWidth,
              effectiveAction,
              effectiveMore,
            );
            // A countdown at large text: one quiet line ("Laptop isn't
            // answering · Trying again in 12 s · Try now") keeps the action
            // in sight where the stacked line would push it out of the
            // slot's room.
            if (stacked && countdownTo != null) {
              return _countingQuietLine(
                context,
                action: effectiveAction,
                more: effectiveMore,
              );
            }
            return _line(
              context,
              slow: slow,
              action: effectiveAction,
              more: effectiveMore,
              stacked: stacked,
            );
          },
        ),
      ),
    );
  }

  /// The quiet form: a small icon, the words in the secondary caption style,
  /// and the action as an underlined caption word whose padding makes the
  /// 48 dp target. No divider, no row height of its own. With [onDismiss] a
  /// small close ends the row.
  /// The quiet form, its words followed by the countdown when there is one
  /// (asked again once a second; the live region keeps the plain message).
  Widget _countingQuietLine(
    BuildContext context, {
    required KitAction? action,
    required List<KitAction> more,
  }) {
    final until = countdownTo;
    final line = countdownLine;
    if (until == null || line == null) {
      return _quietLine(context, action: action, more: more);
    }
    return _Ticker(
      until: until,
      builder: (context, left) => _quietLine(
        context,
        action: action,
        more: more,
        words: '$message · ${line(left)}',
      ),
    );
  }

  Widget _quietLine(
    BuildContext context, {
    required KitAction? action,
    List<KitAction> more = const [],
    String? words,
  }) {
    final roles = ThemeRoles.resolve(Theme.of(context));
    final tokens = KitTokens.of(context);
    final style = KitText.styleOf(context, KitTextRole.secondary);
    final message = words ?? this.message;
    final moreMenu = _moreMenu(context, more);
    final dismiss = onDismiss;
    final color = KitText.toneColor(roles, KitTextTone.secondary);
    final scaler = MediaQuery.textScalerOf(context);
    final direction = Directionality.of(context);
    double widthOf(String text) {
      final painter = TextPainter(
        text: TextSpan(text: text, style: style),
        textDirection: direction,
        textScaler: scaler,
        maxLines: 1,
      )..layout();
      final width = painter.width;
      painter.dispose();
      return width;
    }

    return Semantics(
      container: true,
      child: Padding(
        padding: EdgeInsetsDirectional.only(
          start: tokens.gutter,
          end: tokens.gutter - tokens.space2,
        ),
        child: LayoutBuilder(
          builder: (context, constraints) {
            // Whether the words and the action share one line. When they do
            // not, the action drops under the words without its "·" (a dot
            // never starts a line), and the icon stays by the first line.
            final room =
                constraints.maxWidth -
                14 -
                tokens.space2 -
                (dismiss == null ? 0 : tokens.minTarget) -
                (moreMenu == null ? 0 : tokens.minTarget);
            final oneLine =
                action == null ||
                widthOf(message) +
                        tokens.space2 +
                        widthOf('·') +
                        2 * tokens.space2 +
                        widthOf(action.label) <=
                    room;
            // Wrapped: the icon sits level with the first line of words.
            final firstLine =
                (style.fontSize ?? 14) *
                (style.height ?? 1.3) *
                scaler.scale(1);
            final iconTop = oneLine
                ? 0.0
                : ((firstLine - 14) / 2).clamp(0.0, tokens.minTarget);
            final lead = <Widget>[
              Padding(
                padding: EdgeInsetsDirectional.only(top: iconTop),
                child: Icon(icon, size: 14, color: color),
              ),
              SizedBox(width: tokens.space2),
              // The action follows the words and drops to its own line when
              // they do not fit together (large text), never overflowing.
              Expanded(
                child: Wrap(
                  crossAxisAlignment: WrapCrossAlignment.center,
                  children: [
                    Semantics(
                      liveRegion: true,
                      label: semanticsLabel ?? this.message,
                      onTap: onOpen,
                      excludeSemantics: true,
                      child: _openable(
                        Text(
                          message,
                          key: messageKey,
                          // Counting seconds keep their width.
                          style: words == null
                              ? style
                              : style.copyWith(
                                  fontFeatures: const [
                                    FontFeature.tabularFigures(),
                                  ],
                                ),
                        ),
                      ),
                    ),
                    if (action != null && oneLine)
                      ExcludeSemantics(
                        child: Padding(
                          padding: EdgeInsetsDirectional.only(
                            start: tokens.space2,
                          ),
                          child: Text('·', style: style),
                        ),
                      ),
                    if (action != null)
                      Semantics(
                        button: true,
                        label: action.label,
                        excludeSemantics: true,
                        onTap: action.onPressed,
                        child: Material(
                          type: MaterialType.transparency,
                          child: InkResponse(
                            key: action.key,
                            onTap: action.onPressed,
                            containedInkWell: true,
                            highlightShape: BoxShape.rectangle,
                            child: ConstrainedBox(
                              constraints: BoxConstraints(
                                minHeight: tokens.minTarget,
                              ),
                              child: Padding(
                                padding: EdgeInsets.symmetric(
                                  horizontal: tokens.space2,
                                ),
                                child: Align(
                                  widthFactor: 1,
                                  child: Text(
                                    action.label,
                                    style: style.copyWith(
                                      decoration: TextDecoration.underline,
                                      decorationColor: color,
                                    ),
                                  ),
                                ),
                              ),
                            ),
                          ),
                        ),
                      ),
                  ],
                ),
              ),
            ];
            return Row(
              crossAxisAlignment: oneLine
                  ? CrossAxisAlignment.center
                  : CrossAxisAlignment.start,
              children: [
                ...lead,
                // The other ways out, the same menu the full line uses.
                ?moreMenu,
                // A note that may be put away: one small close at the end, the
                // same button the full line uses.
                if (dismiss != null)
                  KitIconButton(
                    key: dismissKey ?? _defaultDismissKey,
                    icon: AppIconography.close,
                    size: 20,
                    tooltip:
                        dismissTooltip ??
                        lookupAppLocalizations(
                          Localizations.localeOf(context),
                        ).kitSheetDismiss,
                    onPressed: dismiss,
                  ),
              ],
            );
          },
        ),
      ),
    );
  }

  /// The quiet words as a tap target for [onOpen], when there is one.
  Widget _openable(Widget words) {
    final open = onOpen;
    if (open == null) return words;
    return Material(
      type: MaterialType.transparency,
      child: InkResponse(
        key: const ValueKey('kit-status-open'),
        onTap: open,
        containedInkWell: true,
        highlightShape: BoxShape.rectangle,
        child: words,
      ),
    );
  }

  /// The small More menu holding [more], or null when there is none.
  Widget? _moreMenu(BuildContext context, List<KitAction> more) {
    if (more.isEmpty) return null;
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    return Builder(
      builder: (anchor) => KitIconButton(
        key: moreKey ?? _defaultMoreKey,
        icon: AppIconography.more,
        size: 20,
        tooltip: l10n.kitMore,
        onPressed: () => showKitMenu(
          anchor,
          items: [
            for (final item in more)
              KitMenuItem(
                key: item.key,
                label: item.label,
                onSelected: () => item.onPressed?.call(),
                enabled: item.onPressed != null,
                destructive: item.destructive,
                disabledReason: item.disabledReason,
              ),
          ],
        ),
      ),
    );
  }

  /// The action moves under the words when both do not fit on one line
  /// (long words, large text), instead of squeezing the words.
  bool _stacks(
    BuildContext context,
    double width,
    KitAction? action,
    List<KitAction> more,
  ) {
    if (action == null) return false;
    final tokens = KitTokens.of(context);
    final scaler = MediaQuery.textScalerOf(context);
    double measure(String text, TextStyle style) {
      final painter = TextPainter(
        text: TextSpan(text: text, style: style),
        textDirection: Directionality.of(context),
        textScaler: scaler,
        maxLines: 1,
      )..layout();
      final result = painter.width;
      painter.dispose();
      return result;
    }

    final frame =
        tokens.gutter +
        tokens.iconSize(context, tokens.smallIconSize) +
        tokens.space3 +
        tokens.space1 +
        (more.isEmpty ? 0 : tokens.minTarget) +
        (onDismiss == null ? 0 : tokens.minTarget);
    final actionWidth =
        measure(action.label, KitText.styleOf(context, KitTextRole.button)) +
        2 * tokens.space2 +
        tokens.space1;
    final messageWidth = measure(
      message,
      KitText.styleOf(context, KitTextRole.body),
    );
    return messageWidth + actionWidth + frame > width;
  }

  Widget _icon(BuildContext context, KitTokens tokens, ThemeRoles roles) {
    final family = icon.fontFamily;
    if (family != null && family.startsWith('AppPhosphor')) {
      return KitIcon.status(tone, icon: icon);
    }
    // Compatibility for a glyph outside the app's set (a caller not yet on
    // AppIconography): KitIcon rejects it, so it is drawn plainly in the
    // neutral tone until the caller moves to an AppIconography verb.
    return Icon(
      icon,
      size: tokens.smallIconSize,
      color: KitText.toneColor(roles, KitTextTone.secondary),
    );
  }

  Widget _line(
    BuildContext context, {
    required bool slow,
    required KitAction? action,
    required List<KitAction> more,
    required bool stacked,
  }) {
    final theme = Theme.of(context);
    final roles = ThemeRoles.resolve(theme);
    final tokens = KitTokens.of(context);
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    final bodyStyle = KitText.styleOf(context, KitTextRole.body);
    final secondaryStyle = KitText.styleOf(context, KitTextRole.secondary);
    final dismiss = onDismiss;
    final moreMenu = _moreMenu(context, more);
    final dismissButton = dismiss == null
        ? null
        : KitIconButton(
            key: dismissKey ?? _defaultDismissKey,
            icon: AppIconography.close,
            size: 20,
            tooltip: dismissTooltip ?? l10n.kitSheetDismiss,
            onPressed: dismiss,
          );
    final slowLabel = KitSince.slowLabel(context);
    final announced = slow ? '$message\n$slowLabel' : message;
    final supporting = this.supporting;
    final countdownTo = this.countdownTo;
    final countdownLine = this.countdownLine;
    final Widget? supportingText = countdownTo != null && countdownLine != null
        ? KeyedSubtree(
            key: const ValueKey('kit-status-countdown'),
            child: _Countdown(
              until: countdownTo,
              line: countdownLine,
              textKey: supportingKey,
              style: secondaryStyle,
            ),
          )
        : slow
        ? KeyedSubtree(
            key: const ValueKey('kit-status-slow'),
            // Already said by the live region above.
            child: ExcludeSemantics(
              child: Text(
                slowLabel,
                key: const ValueKey('kit-status-slow-text'),
                style: secondaryStyle,
              ),
            ),
          )
        : supporting == null
        ? null
        : KeyedSubtree(
            key: const ValueKey('kit-status-supporting'),
            child: Semantics(
              label: supportingSemanticsLabel,
              excludeSemantics: supportingSemanticsLabel != null,
              child: Text(
                supporting,
                key: supportingKey,
                style: secondaryStyle,
              ),
            ),
          );
    final next = this.next;
    final disabledReason = action == null || action.enabled
        ? null
        : action.disabledReason;
    final actionButton = action == null
        ? null
        : KitButton.fromAction(action, role: KitButtonRole.tertiary);
    // The icon lines up with the message's first line when the line stacks.
    final firstLine =
        MediaQuery.textScalerOf(context).scale(bodyStyle.fontSize ?? 16) *
        (bodyStyle.height ?? 1);
    // Stacked with the controls apart, More (and Dismiss) sit beside the
    // message while the action sits under it, inset towards the start: by
    // geometry alone a screen reader would read the action first (it
    // starts further left in the band More spans). Read the top row, then
    // the action under it (A11Y-4). The wrappers are always there (empty
    // otherwise) so a change of layout keeps the same elements.
    final ordered = stacked && !controlsTogether;
    Widget inOrder(double order, Widget child) => Semantics(
      container: ordered,
      sortKey: ordered ? OrdinalSortKey(order) : null,
      child: child,
    );
    return Semantics(
      container: true,
      child: DecoratedBox(
        decoration: BoxDecoration(
          border: Border(
            bottom: BorderSide(
              color: roles.hairline,
              width: KitTokens.hairlineWidth(context),
            ),
          ),
        ),
        child: ConstrainedBox(
          constraints: const BoxConstraints(
            minHeight: KitTokens.statusLineMinHeight,
          ),
          child: Padding(
            padding: EdgeInsetsDirectional.only(
              start: tokens.gutter,
              // A lone text action's label ends on the 16 dp gutter (the
              // button's own padding makes up the rest); More and Dismiss
              // are icon targets and keep their tighter inset.
              end:
                  !stacked &&
                      actionButton != null &&
                      moreMenu == null &&
                      dismissButton == null
                  ? tokens.gutter - tokens.space2
                  : tokens.space1,
            ),
            child: Row(
              crossAxisAlignment: stacked
                  ? CrossAxisAlignment.start
                  : CrossAxisAlignment.center,
              children: [
                Padding(
                  padding: stacked
                      ? EdgeInsetsDirectional.only(top: tokens.space2)
                      : EdgeInsetsDirectional.zero,
                  child: SizedBox(
                    height: stacked ? firstLine : null,
                    child: Center(child: _icon(context, tokens, roles)),
                  ),
                ),
                SizedBox(width: tokens.space3),
                Expanded(
                  child: Padding(
                    padding: EdgeInsetsDirectional.symmetric(
                      vertical: tokens.space2,
                    ),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Semantics(
                          container: true,
                          sortKey: ordered ? const OrdinalSortKey(0) : null,
                          liveRegion: true,
                          label: announced,
                          excludeSemantics: true,
                          child: Text(
                            message,
                            key: messageKey,
                            style: bodyStyle,
                          ),
                        ),
                        if (supportingText != null)
                          Padding(
                            padding: EdgeInsetsDirectional.only(
                              top: tokens.space1,
                            ),
                            child: KitSwap(
                              alignment: AlignmentDirectional.topStart,
                              child: supportingText,
                            ),
                          ),
                        KitReveal(
                          child: next == null
                              ? null
                              : Padding(
                                  padding: EdgeInsetsDirectional.only(
                                    top: tokens.space1,
                                  ),
                                  child: KitSwap(
                                    pace: KitPace.standard,
                                    alignment: AlignmentDirectional.topStart,
                                    child: KeyedSubtree(
                                      key: ValueKey(next),
                                      child: Text(
                                        next,
                                        key: nextKey,
                                        style: secondaryStyle,
                                      ),
                                    ),
                                  ),
                                ),
                        ),
                        // At large text the action moves under the words
                        // instead of squeezing them.
                        if (stacked && controlsTogether)
                          Row(
                            children: [
                              Expanded(
                                child: actionButton == null
                                    ? const SizedBox.shrink()
                                    : KitInset(child: actionButton),
                              ),
                              ?moreMenu,
                              ?dismissButton,
                            ],
                          )
                        else if (stacked && actionButton != null)
                          inOrder(3, KitInset(child: actionButton)),
                        if (disabledReason != null)
                          Padding(
                            padding: EdgeInsetsDirectional.only(
                              top: tokens.space1,
                            ),
                            child: Text(disabledReason, style: secondaryStyle),
                          ),
                      ],
                    ),
                  ),
                ),
                if (!stacked && actionButton != null) actionButton,
                if (!(stacked && controlsTogether)) ...[
                  if (moreMenu != null) inOrder(1, moreMenu),
                  if (dismissButton != null) inOrder(2, dismissButton),
                ],
              ],
            ),
          ),
        ),
      ),
    );
  }
}

/// Rebuilds [builder] once a second with the time left until [until], then
/// once more at zero.
class _Ticker extends StatefulWidget {
  const _Ticker({required this.until, required this.builder});

  final DateTime until;
  final Widget Function(BuildContext context, Duration left) builder;

  @override
  State<_Ticker> createState() => _TickerState();
}

class _TickerState extends State<_Ticker> {
  Timer? _tick;

  @override
  void initState() {
    super.initState();
    _schedule();
  }

  @override
  void didUpdateWidget(_Ticker oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.until != widget.until) _schedule();
  }

  void _schedule() {
    _tick?.cancel();
    _tick = null;
    if (!widget.until.isAfter(clock.now())) return;
    _tick = Timer.periodic(const Duration(seconds: 1), (_) {
      if (!mounted) return;
      setState(() {});
      if (!widget.until.isAfter(clock.now())) {
        _tick?.cancel();
        _tick = null;
      }
    });
  }

  @override
  void dispose() {
    _tick?.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final left = widget.until.difference(clock.now());
    return widget.builder(context, left.isNegative ? Duration.zero : left);
  }
}

/// The supporting line of a status with a countdown: asked for its words
/// once a second until [until] passes, then once more at zero. Not a live
/// region: the seconds are read on demand, never announced each tick.
class _Countdown extends StatelessWidget {
  const _Countdown({
    required this.until,
    required this.line,
    required this.style,
    this.textKey,
  });

  final DateTime until;
  final String Function(Duration left) line;
  final TextStyle style;
  final Key? textKey;

  @override
  Widget build(BuildContext context) => _Ticker(
    until: until,
    builder: (context, left) => Text(
      line(left),
      key: textKey,
      style: style.copyWith(fontFeatures: const [FontFeature.tabularFigures()]),
    ),
  );
}
