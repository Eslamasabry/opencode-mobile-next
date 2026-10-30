import 'package:flutter/material.dart';

import '../../l10n/app_localizations.dart';
import 'kit_bidi.dart';
import 'kit_motion.dart';
import 'kit_row.dart';
import 'kit_since.dart';
import 'kit_task_mark.dart';
import 'kit_text.dart';
import 'kit_tokens.dart';
import 'motion/kit_motion_parts.dart' show KitSwap;

/// Why the app asks (AUTO-9; G38 wants exactly these three): the mark, the
/// span, the badge and the row all say the same reason in the same words
/// through [KitNeedsYou.reasonWord].
enum KitNeedsYouReason {
  /// Only the person can decide: a permission, a question or form, a gate,
  /// a merge.
  decision,

  /// Work cannot continue: credentials rejected, a limit, storage full, a
  /// step still failing.
  blocked,

  /// A first-time consent at the moment it becomes relevant.
  consent,
}

/// The one "needs you" marker (kit-v2.md §1.21, §4.5, §8.2; LOOK-24, AUTO-10,
/// AUTO-11, AUTO-12, AUTO-17, STATE-9, A11Y-3, G37, G38), in the same words
/// everywhere and counted once by the one attention source. Four builders:
///
/// - [mark]: a row's leading mark;
/// - [span]: the start of a row's supporting line;
/// - [badge]: the count on a tab, a rail destination, a header's switcher or
///   a server row;
/// - [row]: the pointing row that Work, Inbox and team lists use to open the
///   conversation at its [KitRequestCard].
///
/// Counting, deduplication, ordering and clearing belong to the attention
/// source (AUTO-11, AUTO-12; gate G38). This part only renders what it is
/// given.
abstract final class KitNeedsYou {
  /// A row's leading mark: exactly `KitTaskMark(state: KitTaskState.needsYou)`
  /// (§2.9), with the word "Needs you" in semantics.
  static Widget mark({Key? key}) =>
      KitTaskMark(key: key, state: KitTaskState.needsYou);

  /// The start of a row's supporting line, like `kitCurrentSpan`: "Needs
  /// you · ", or "{count} need you · " for `count > 1` (ICU plural). In the
  /// attention tone at label weight.
  static TextSpan span(BuildContext context, {int count = 1}) {
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    return TextSpan(
      text: l10n.kitNeedsYouSpan(count),
      style: KitText.styleOf(
        context,
        KitTextRole.label,
        tone: KitTextTone.attention,
      ),
    );
  }

  /// A count on a tab, a rail destination, a header's switcher or a server
  /// row. `count <= 0` returns [child] with no badge in the tree; `> 99`
  /// shows "99+". Semantics: the host's label gains ", {count} need you"
  /// (the badge itself is excluded, so the count is read once).
  static Widget badge({required int count, required Widget child, Key? key}) =>
      _KitNeedsYouBadge(key: key, count: count, child: child);

  /// The pointing row (LOOK-24, AUTO-17): Work, Inbox and team lists show a
  /// request as this row, which opens the conversation scrolled to its
  /// `KitRequestCard`. It never answers. Built on [KitRow].
  static Widget row({
    Key? key,
    required String title,
    required KitNeedsYouReason reason,
    required String ifIgnored,
    required VoidCallback onOpen,
    String? who,
    String? server,
    DateTime? since,
    Key? titleKey,
  }) {
    assert(
      ifIgnored.isNotEmpty,
      'KitNeedsYou.row: ifIgnored must say what happens if the person does '
      'not answer (G37) — it may not be empty',
    );
    return _KitNeedsYouRow(
      key: key,
      title: title,
      reason: reason,
      ifIgnored: ifIgnored,
      onOpen: onOpen,
      who: who,
      server: server,
      since: since,
      titleKey: titleKey,
    );
  }

  /// The words for [reason], for notification copy and the card header, so
  /// every surface says the same thing: "Needs your decision" / "Stuck:
  /// needs you" / "Needs your OK".
  static String reasonWord(BuildContext context, KitNeedsYouReason reason) {
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    return switch (reason) {
      KitNeedsYouReason.decision => l10n.kitNeedsYouReasonDecision,
      KitNeedsYouReason.blocked => l10n.kitNeedsYouReasonBlocked,
      KitNeedsYouReason.consent => l10n.kitNeedsYouReasonConsent,
    };
  }
}

/// [who] and [server] joined for the row's supporting line and semantics,
/// each bidi-isolated (COPY-30): "{who} on {server}", or whichever of the
/// two is given alone. `null` when neither is given.
String? _kitNeedsYouWhoOnServer(
  AppLocalizations l10n, {
  String? who,
  String? server,
}) {
  final w = who == null ? null : KitBidi.auto(who);
  final s = server == null ? null : KitBidi.auto(server);
  if (w != null && s != null) return l10n.kitNeedsYouWhoOnServer(w, s);
  return s ?? w;
}

/// [KitNeedsYou.row]'s widget: rebuilds the "waiting …" words once a minute
/// through [KitSince], so the row's age never goes stale while it sits on
/// screen (KitSince.md; no escalation happens here).
class _KitNeedsYouRow extends StatelessWidget {
  const _KitNeedsYouRow({
    super.key,
    required this.title,
    required this.reason,
    required this.ifIgnored,
    required this.onOpen,
    this.who,
    this.server,
    this.since,
    this.titleKey,
  });

  final String title;
  final KitNeedsYouReason reason;
  final String ifIgnored;
  final VoidCallback onOpen;
  final String? who;
  final String? server;
  final DateTime? since;
  final Key? titleKey;

  @override
  Widget build(BuildContext context) {
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    final reasonWord = KitNeedsYou.reasonWord(context, reason);
    final whoOnServer = _kitNeedsYouWhoOnServer(l10n, who: who, server: server);
    return KitSince(
      since: since,
      ticks: KitSinceTicks.minutes,
      builder: (context, status) {
        final minutes = status.elapsed.inMinutes;
        // Visible: the kit's abbreviated age ("waiting 4 min"); spoken: the
        // unabbreviated words ("waiting 4 minutes", KitNeedsYou.md
        // Accessibility), so a screen reader never says "min".
        final waiting = since == null
            ? null
            : l10n.kitNeedsYouWaiting(
                KitSince.ageLabel(context, status.elapsed),
              );
        final waitingSpoken = since == null
            ? null
            : l10n.kitNeedsYouWaitingSpoken(minutes);
        final label = [
          title,
          reasonWord,
          ?whoOnServer,
          ?waitingSpoken,
          ifIgnored,
        ].join(', ');
        // Every run joins with the one " · " separator, so an abbreviated age
        // never runs into a full stop ("4 min. Voice…").
        final supporting = TextSpan(
          children: [
            TextSpan(
              text: '$reasonWord · ',
              style: KitText.styleOf(
                context,
                KitTextRole.label,
                tone: KitTextTone.attention,
              ),
            ),
            if (whoOnServer != null) TextSpan(text: '$whoOnServer · '),
            if (waiting != null) TextSpan(text: '$waiting · '),
            TextSpan(text: ifIgnored),
          ],
        );
        return Semantics(
          button: true,
          label: label,
          excludeSemantics: true,
          onTap: onOpen,
          child: KitRow(
            leading: KitNeedsYou.mark(),
            title: title,
            titleKey: titleKey,
            titleMaxLines: 2,
            supporting: supporting,
            supportingMaxLines: 2,
            onTap: onOpen,
          ),
        );
      },
    );
  }
}

/// [KitNeedsYou.badge]'s widget: a cross-fade on [KitMotion.quick] between
/// two shown counts, or a fade on [KitMotion.standard] appearing from or
/// disappearing to none (MOT-2: never a scale). [child]'s own semantics merge
/// with a hidden suffix so the count is read once, on the host (§1.21).
///
/// At `count <= 0` with no fade-out running, the build is [child] alone (in a
/// [KeyedSubtree] with a [GlobalKey], which adds no render object and no
/// semantics node, so the host's state survives the badge coming and going):
/// the wrapper exists only while a badge is shown or fading out.
class _KitNeedsYouBadge extends StatefulWidget {
  const _KitNeedsYouBadge({
    super.key,
    required this.count,
    required this.child,
  });

  final int count;
  final Widget child;

  @override
  State<_KitNeedsYouBadge> createState() => _KitNeedsYouBadgeState();
}

class _KitNeedsYouBadgeState extends State<_KitNeedsYouBadge>
    with SingleTickerProviderStateMixin {
  /// A third of the pill's height past the child's top-end corner.
  static const double _offset = KitTokens.badgeOffset;

  final GlobalKey _childKey = GlobalKey();

  /// Appearing (forward) and disappearing (reverse) on [KitMotion.standard].
  late final AnimationController _presence = AnimationController(
    vsync: this,
    duration: KitMotion.standard,
    value: widget.count > 0 ? 1 : 0,
  )..addStatusListener(_onPresenceStatus);

  late final CurvedAnimation _opacity = CurvedAnimation(
    parent: _presence,
    curve: KitMotion.enter,
    reverseCurve: KitMotion.exit,
  );

  /// The last positive count, shown while the badge fades out.
  late int _shownCount = widget.count;

  bool _reduced = false;

  void _onPresenceStatus(AnimationStatus status) {
    // Fade-out done: rebuild to [child] alone.
    if (status.isDismissed && mounted) setState(() {});
  }

  @override
  void didUpdateWidget(covariant _KitNeedsYouBadge oldWidget) {
    super.didUpdateWidget(oldWidget);
    final shown = widget.count > 0;
    if (shown) _shownCount = widget.count;
    if ((oldWidget.count > 0) == shown) return;
    if (_reduced) {
      _presence.value = shown ? 1 : 0;
    } else if (shown) {
      _presence.forward();
    } else {
      _presence.reverse();
    }
  }

  @override
  void dispose() {
    _opacity.dispose();
    _presence.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    _reduced = KitMotion.reduced(context);
    final child = KeyedSubtree(key: _childKey, child: widget.child);
    final count = widget.count;
    if (count <= 0 && _presence.isDismissed) return child;
    final text = _shownCount > 99 ? '99+' : '$_shownCount';
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    return MergeSemantics(
      child: Stack(
        clipBehavior: Clip.none,
        children: [
          child,
          PositionedDirectional(
            top: -_offset,
            end: -_offset,
            child: ExcludeSemantics(
              child: FadeTransition(
                opacity: _opacity,
                // The pill stays; its number rolls to the next one.
                child: _KitNeedsYouPill(
                  text: text,
                  shown: _shownCount > 99 ? 100 : _shownCount,
                ),
              ),
            ),
          ),
          if (count > 0)
            Semantics(
              label: l10n.kitNeedsYouBadgeSuffix(count),
              child: const SizedBox.shrink(),
            ),
        ],
      ),
    );
  }
}

/// The badge's own pill (LOOK-19 "chips and pills"; at least
/// `KitTokens.badgeHeight` / `badgeMinWidth` = 18 and growing with its
/// clamped text, `KitShape.pill`; A11Y-8's named text-scale clamp,
/// `KitTokens.badgeTextScaleMax`).
class _KitNeedsYouPill extends StatelessWidget {
  const _KitNeedsYouPill({required this.text, required this.shown});

  final String text;

  /// The count [text] says (100 for "99+"), so the number rolls only when
  /// what it says changes.
  final int shown;

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final scaler = MediaQuery.textScalerOf(
      context,
    ).clamp(maxScaleFactor: KitTokens.badgeTextScaleMax);
    final style = KitText.styleOf(context, KitTextRole.caption).copyWith(
      color: tokens.roles.onAttentionFill,
      fontFeatures: const [FontFeature.tabularFigures()],
    );
    return MediaQuery(
      data: MediaQuery.of(context).copyWith(textScaler: scaler),
      child: Container(
        constraints: const BoxConstraints(
          minWidth: KitTokens.badgeMinWidth,
          minHeight: KitTokens.badgeHeight,
        ),
        padding: EdgeInsets.symmetric(horizontal: tokens.space1),
        alignment: Alignment.center,
        decoration: ShapeDecoration(
          color: tokens.roles.attentionFill,
          shape: tokens.shapeOf(KitShape.pill),
        ),
        child: KitSwap.count(
          count: shown,
          child: Text(text, style: style, textScaler: scaler),
        ),
      ),
    );
  }
}
