import 'dart:async';
import 'dart:math' as math;

import 'package:flutter/material.dart';

import '../../l10n/app_localizations.dart';
import '../app_theme.dart';
import 'kit_buttons.dart';
import 'kit_copy.dart';
import 'kit_icon_button.dart';
import 'kit_lead_column.dart';
import 'kit_max_width.dart';
import 'kit_redact.dart';
import 'kit_surface.dart';
import 'kit_surface_look.dart';
import 'kit_text.dart';
import 'kit_tokens.dart';
import 'motion/kit_reveal.dart';

/// Which of its forms a [KitNotice] draws.
enum _Form { plain, card, cost, offer, error }

/// A message that belongs to one part of a form or a list (design standard
/// §3, for a moment too small for a whole [KitStateView]): the verdict of a
/// connection test, a save that failed, a credential the app can no longer
/// read, a condition of one section, a one-time tip, a turn-on offer, or
/// the cost of an install.
///
/// A tinted icon, an optional [title], the [message], optional [notes],
/// and up to two [actions] under the words, as small tonal buttons (flat
/// polish 2026-10-26: bare words read as a caption). It sits on the
/// host's rails with no filled block and no card around it (§3: "never a
/// solid red block"; cards are for content, not for wrapping a message). It
/// is one live region, so a new verdict is read out once.
///
/// Forms (kit-KitNotice-v2): the plain notice in the tones neutral,
/// working ([AppStatusTone.progress]), ok, warning and error; the cost line
/// ([KitNotice.cost]); the one-sentence offer ([KitNotice.offer]); and the
/// error with the kit's error defaults ([KitNotice.error]).
///
/// The icon's colour follows LOOK-4 and LOOK-5: `text2` when neutral,
/// `accent` while working, `success` when ok, and `text1` for a warning or
/// a failure. Amber means only "needs you", which is `KitRequestCard` in
/// the conversation and the needs-you row in lists, never a notice.
///
/// Motion (§10): it fades and rises into place when it appears and again
/// when its tone changes (a verdict that turned). To have it fold away when
/// it goes, the host shows it through a [KitReveal].
///
/// States: error, working.
class KitNotice extends StatelessWidget {
  const KitNotice({
    super.key,
    required this.message,
    this.title,
    this.tone = AppStatusTone.neutral,
    this.icon,
    this.notes = const [],
    this.actions = const [],
    this.link,
    this.onDismiss,
    this.messageKey,
    this.liveRegion = true,
    this.dismissKey,
    this.dismissLabel,
  }) : card = false,
       caption = null,
       primary = null,
       secondary = null,
       costItems = null,
       error = null,
       errorKind = null,
       details = null,
       retry = null,
       switchServer = null,
       reportSource = null,
       copyDetailsKey = null,
       reportKey = null,
       _offer = null,
       _form = _Form.plain;

  /// Retired by kit-KitNotice-v2: use KitRequestCard in the conversation or
  /// KitNeedsYou.row in lists (LOOK-24).
  ///
  /// The needs-you card (visual language §5): on `attentionSurface` inside
  /// an `attentionLine` border with 22 dp corners, an icon tile, a
  /// [caption] in the tone ("Needs you · 40 s ago"), the [title] as a
  /// headline, the [message], and its answer buttons in place ([primary]
  /// and [secondary] side by side, [actions] as tertiary words). Amber
  /// always means "needs you", so [tone] is attention unless the card is a
  /// verdict of another kind.
  const KitNotice.card({
    super.key,
    required this.message,
    this.title,
    this.caption,
    this.tone = AppStatusTone.attention,
    this.icon,
    this.notes = const [],
    this.primary,
    this.secondary,
    this.actions = const [],
    this.onDismiss,
    this.messageKey,
    this.liveRegion = true,
  }) : card = true,
       dismissKey = null,
       dismissLabel = null,
       costItems = null,
       error = null,
       errorKind = null,
       details = null,
       retry = null,
       switchServer = null,
       reportSource = null,
       copyDetailsKey = null,
       reportKey = null,
       _offer = null,
       link = null,
       _form = _Form.card;

  /// The cost of an install or a turn-on, stated before its primary
  /// (KIT-37): "About 208 MB · about 4 min · uses battery while it
  /// installs". The [items] come from the component registry or the
  /// caller, each already in words (sizes and units wrapped in `KitBidi.ltr`
  /// by the caller, COPY-30); the kit joins them with " · ". Not a live
  /// region: it is static text beside a button. [message] is empty.
  const KitNotice.cost(
    List<String> items, {
    super.key,
    this.title,
    this.messageKey,
  }) : costItems = items,
       message = '',
       tone = AppStatusTone.neutral,
       icon = null,
       notes = const [],
       actions = const [],
       onDismiss = null,
       liveRegion = false,
       dismissKey = null,
       dismissLabel = null,
       card = false,
       caption = null,
       primary = null,
       secondary = null,
       error = null,
       errorKind = null,
       details = null,
       retry = null,
       switchServer = null,
       reportSource = null,
       copyDetailsKey = null,
       reportKey = null,
       _offer = null,
       link = null,
       _form = _Form.cost;

  /// One sentence, one action, one close: a one-time tip or a turn-on offer
  /// ("{server} also runs an AI team. Turn it on?"). The sentence wraps
  /// whole; when it needs more than one line, the close stays at the end
  /// of the sentence's first line and the action moves under the sentence,
  /// starting at the sentence's text inset. "Not now" memory is the
  /// caller's (COPY-20).
  const KitNotice.offer({
    super.key,
    required this.message,
    required KitAction action,
    required VoidCallback this.onDismiss,
    this.icon,
    this.messageKey,
    this.dismissKey,
    this.dismissLabel,
  }) : _offer = action,
       title = null,
       tone = AppStatusTone.neutral,
       notes = const [],
       actions = const [],
       liveRegion = true,
       card = false,
       caption = null,
       primary = null,
       secondary = null,
       costItems = null,
       error = null,
       errorKind = null,
       details = null,
       retry = null,
       switchServer = null,
       reportSource = null,
       copyDetailsKey = null,
       reportKey = null,
       link = null,
       _form = _Form.offer;

  /// A failure about one part (STATE-20: "a sheet that fetches: an inline
  /// KitNotice error with Try again"), with the kit's error defaults
  /// (STATE-3): Copy details, a trailing icon, whenever [details] is given;
  /// for a network failure the fix ([retry], [switchServer]); otherwise
  /// [retry] and "Report a problem" when [KitReportHook.available].
  ///
  /// [message] says what failed in words; [error] is only classified
  /// ([KitErrorKind.of], or [errorKind] when the caller knows better) and
  /// never shown. [details] is the raw technical text: never shown here,
  /// copied and reported only after [KitReportHook.redact].
  const KitNotice.error({
    super.key,
    required this.message,
    this.title,
    this.error,
    this.errorKind,
    this.details,
    this.retry,
    this.switchServer,
    this.reportSource,
    this.messageKey,
    this.copyDetailsKey,
    this.reportKey,
  }) : tone = AppStatusTone.failure,
       icon = null,
       notes = const [],
       actions = const [],
       onDismiss = null,
       liveRegion = true,
       dismissKey = null,
       dismissLabel = null,
       card = false,
       caption = null,
       primary = null,
       secondary = null,
       costItems = null,
       _offer = null,
       link = null,
       _form = _Form.error;

  /// Drawn as the needs-you card ([KitNotice.card]).
  final bool card;

  /// The card's small line above the title, in the tone.
  final String? caption;

  /// The card's answer buttons, in place.
  final KitAction? primary;
  final KitAction? secondary;

  final String? title;

  /// Words for people, not exception text: never `'$error'`,
  /// `error.toString()`, an `ApiException` or `PlatformException` message
  /// or an HTTP body. The raw failure goes to [details] (the error form),
  /// copied and reported only after redaction.
  final String message;
  final AppStatusTone tone;

  /// Defaults to the tone's glyph: a check for ok, an error mark for a
  /// failure, a warning for attention, otherwise an info mark (a light bulb
  /// for an offer).
  final IconData? icon;

  /// Further plain lines after the message, each its own line, muted
  /// (what a verdict implies, where to look next).
  final List<String> notes;

  /// Tertiary, start-aligned, at most two shown.
  final List<KitAction> actions;

  /// One quiet text link after [actions] ("Details"): a way to look, never
  /// the fix, so it does not stand beside the fix as a second button.
  final KitAction? link;

  /// Only when dismissing changes nothing real.
  final VoidCallback? onDismiss;
  final Key? messageKey;
  final bool liveRegion;

  /// The close button's key (default `kit-notice-dismiss`) and label
  /// (default "Dismiss").
  final Key? dismissKey;
  final String? dismissLabel;

  /// The cost line's items ([KitNotice.cost]).
  final List<String>? costItems;

  /// What failed ([KitNotice.error]): classified, never shown.
  final Object? error;

  /// Overrides the classification of [error].
  final KitErrorKind? errorKind;

  /// Raw technical text: copied and reported after redaction, never shown.
  final String? details;

  /// "Try again".
  final KitAction? retry;

  /// "Switch server", offered for a network failure only.
  final KitAction? switchServer;

  /// Where a report says it came from: the page or part id
  /// ("files-viewer").
  final String? reportSource;

  final Key? copyDetailsKey;
  final Key? reportKey;

  final KitAction? _offer;
  final _Form _form;

  static IconData _iconFor(AppStatusTone tone) => switch (tone) {
    AppStatusTone.ok => AppIconography.checkCircle,
    AppStatusTone.failure => AppIconography.error,
    AppStatusTone.attention => AppIconography.warning,
    _ => AppIconography.info,
  };

  /// The tone map (README D12, LOOK-4, LOOK-5): attention and failure keep
  /// their glyphs but paint in `text1`; amber is for needs-you only and
  /// never red.
  static Color _tintFor(ThemeRoles roles, AppStatusTone tone) => switch (tone) {
    AppStatusTone.neutral => roles.text2,
    AppStatusTone.progress => roles.accent,
    AppStatusTone.ok => roles.success,
    AppStatusTone.attention || AppStatusTone.failure => roles.text1,
  };

  @override
  Widget build(BuildContext context) =>
      KitMaxWidth.reading(child: _buildContent(context));

  Widget _buildContent(BuildContext context) => switch (_form) {
    _Form.card => _buildCard(context),
    _Form.cost => _buildCost(context),
    _Form.plain || _Form.offer || _Form.error => _buildNotice(context),
  };

  /// The height of the first line of [role] at the person's text size, so
  /// the leading icon centres on it.
  static double _lineHeight(BuildContext context, KitTextRole role) {
    final style = KitText.styleFor(role);
    final size = style.fontSize ?? 16;
    return MediaQuery.textScalerOf(context).scale(size) * (style.height ?? 1);
  }

  Widget _buildCost(BuildContext context) {
    final tokens = KitTokens.of(context);
    final title = this.title;
    return KitEntrance(
      trigger: _form,
      child: Semantics(
        container: true,
        child: Padding(
          padding: EdgeInsetsDirectional.symmetric(vertical: tokens.space1),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            mainAxisSize: MainAxisSize.min,
            children: [
              if (title != null) ...[
                KitText(title, role: KitTextRole.rowTitle),
                SizedBox(height: tokens.space1 / 2),
              ],
              KitText(
                (costItems ?? const []).join(' · '),
                key: messageKey,
                role: KitTextRole.secondary,
              ),
            ],
          ),
        ),
      ),
    );
  }

  /// A notice's acts under its words: small tonal buttons (the platform's
  /// filled-tonal button), each a visible button rather than bare words that
  /// read as a caption, neutral so green stays the page's one main act and
  /// every theme keeps their words legible. Their edge starts where the
  /// words do.
  static Widget _actionsRow(
    KitTokens tokens,
    List<KitAction> shown, {
    KitAction? link,
  }) => Wrap(
    spacing: tokens.space2,
    runSpacing: tokens.space2,
    crossAxisAlignment: WrapCrossAlignment.center,
    children: [
      for (final action in shown)
        KitButton.fromAction(
          action,
          role: KitButtonRole.secondary,
          expand: false,
        ),
      if (link != null)
        KitButton.fromAction(link, role: KitButtonRole.tertiary),
    ],
  );

  /// The words shown as the notice's buttons: the caller's, the offer's one,
  /// or the error defaults.
  List<KitAction> _shownActions(BuildContext context, AppLocalizations l10n) {
    final offer = _offer;
    if (offer != null) return [offer];
    if (_form != _Form.error) return actions.take(2).toList();
    final network =
        (errorKind ?? KitErrorKind.of(error)) == KitErrorKind.network;
    return [
      ?retry,
      if (network)
        ?switchServer
      else if (KitReportHook.available)
        KitAction(
          key: reportKey,
          label: l10n.kitReportProblem,
          onPressed: () => unawaited(_report(context)),
        ),
    ];
  }

  Future<void> _report(BuildContext context) {
    final details = this.details;
    return KitReportHook.report(
      context,
      KitReport(
        title: title ?? message,
        details: details == null ? null : KitReportHook.redact(details),
        source: reportSource,
        errorType: error?.runtimeType.toString(),
      ),
    );
  }

  /// Copy details and the close button, at the end.
  List<Widget> _trailing(BuildContext context, AppLocalizations l10n) {
    final tokens = KitTokens.of(context);
    final details = this.details;
    final dismiss = onDismiss;
    final copy = _form == _Form.error && details != null
        ? KitIconButton(
            key: copyDetailsKey,
            icon: AppIconography.copy,
            label: l10n.kitCopyDetails,
            size: tokens.smallIconSize,
            onPressed: () =>
                unawaited(KitCopy.copy(context, KitReportHook.redact(details))),
          )
        : null;
    final close = dismiss == null
        ? null
        : KitIconButton(
            key: dismissKey ?? const ValueKey('kit-notice-dismiss'),
            icon: AppIconography.close,
            label: dismissLabel ?? l10n.kitSheetDismiss,
            size: tokens.smallIconSize,
            onPressed: dismiss,
          );
    return [
      ?copy,
      if (copy != null && close != null) SizedBox(width: tokens.space2),
      ?close,
    ];
  }

  Widget _buildNotice(BuildContext context) {
    final tokens = KitTokens.of(context);
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    final offer = _form == _Form.offer;
    final glyph = icon ?? (offer ? AppIconography.idea : _iconFor(tone));
    final tint = _tintFor(tokens.roles, tone);
    final title = this.title;
    final messageRole = title == null
        ? KitTextRole.body
        : KitTextRole.secondary;
    final iconSize = tokens.iconSize(context, tokens.smallIconSize);
    final firstLine = _lineHeight(
      context,
      title == null ? messageRole : KitTextRole.rowTitle,
    );
    // The leading column (KitLeadColumn): the icon centred in a tile-wide
    // slot, so the words start on a list row's text rail; in the
    // conversation, the bare glyph's width (its compact rail).
    final mark = SizedBox(
      width: math.max(iconSize, KitLeadColumn.slotOf(context)),
      height: math.max(firstLine, iconSize),
      child: Center(
        child: Icon(glyph, size: iconSize, color: tint),
      ),
    );
    final text = KitText(message, key: messageKey, role: messageRole);
    final shown = _shownActions(context, l10n);
    final trailing = _trailing(context, l10n);

    Widget body;
    if (offer) {
      body = LayoutBuilder(
        builder: (context, constraints) => _offerLine(
          context,
          stacked: _offerStacks(context, constraints.maxWidth, shown.single),
          firstLine: firstLine,
          mark: mark,
          text: text,
          action: shown.single,
          trailing: trailing,
        ),
      );
    } else {
      body = Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          mark,
          SizedBox(width: tokens.space3),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              mainAxisSize: MainAxisSize.min,
              children: [
                if (title != null) ...[
                  KitText(title, role: KitTextRole.rowTitle),
                  SizedBox(height: tokens.space1 / 2),
                ],
                text,
                for (final note in notes) ...[
                  SizedBox(height: tokens.space1),
                  KitText(note, role: KitTextRole.secondary),
                ],
                if (shown.isNotEmpty || link != null)
                  SizedBox(height: tokens.space2),
                if (shown.isNotEmpty || link != null)
                  _actionsRow(tokens, shown, link: link),
              ],
            ),
          ),
          ...trailing,
        ],
      );
    }
    return KitEntrance(
      trigger: (tone, glyph),
      child: Semantics(
        container: true,
        liveRegion: liveRegion,
        child: Padding(
          padding: EdgeInsetsDirectional.symmetric(vertical: tokens.space1),
          child: body,
        ),
      ),
    );
  }

  /// The offer's action moves under the sentence when the sentence, the
  /// action and the close do not fit on one line (long words, large text).
  bool _offerStacks(BuildContext context, double width, KitAction action) {
    if (!width.isFinite) return false;
    final tokens = KitTokens.of(context);
    final scaler = MediaQuery.textScalerOf(context);
    final direction = Directionality.of(context);
    double measure(String text, TextStyle style) {
      final painter = TextPainter(
        text: TextSpan(text: text, style: style),
        textDirection: direction,
        textScaler: scaler,
        maxLines: 1,
      )..layout();
      final result = painter.width;
      painter.dispose();
      return result;
    }

    final fixedWidth =
        math.max(
          tokens.iconSize(context, tokens.smallIconSize),
          KitLeadColumn.slotOf(context),
        ) +
        tokens.space3 +
        (onDismiss == null ? 0 : tokens.minTarget);
    final actionWidth = math.max(
      measure(action.label, KitText.styleOf(context, KitTextRole.button)) +
          2 * KitButton.tertiaryInset +
          tokens.space1,
      tokens.minTarget,
    );
    final messageWidth = measure(
      message,
      KitText.styleOf(context, KitTextRole.body),
    );
    return messageWidth + actionWidth + fixedWidth > width;
  }

  Widget _offerLine(
    BuildContext context, {
    required bool stacked,
    required double firstLine,
    required Widget mark,
    required Widget text,
    required KitAction action,
    required List<Widget> trailing,
  }) {
    final tokens = KitTokens.of(context);
    final button = KitButton.fromAction(action, role: KitButtonRole.tertiary);
    if (!stacked) {
      return Row(
        children: [
          mark,
          SizedBox(width: tokens.space3),
          Expanded(child: text),
          button,
          ...trailing,
        ],
      );
    }
    // The close ends the sentence's first line: the sentence and its mark
    // drop so that line is centred on the close's target, where it sits in
    // the one-line form. The action starts at the sentence's text inset.
    final drop = trailing.isEmpty
        ? 0.0
        : math.max(0.0, ((tokens.minTarget - firstLine) / 2).floorToDouble());
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Padding(
          padding: EdgeInsetsDirectional.only(top: drop),
          child: mark,
        ),
        SizedBox(width: tokens.space3),
        Expanded(
          child: Padding(
            padding: EdgeInsetsDirectional.only(top: drop),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              mainAxisSize: MainAxisSize.min,
              children: [
                text,
                KitInset(child: button),
              ],
            ),
          ),
        ),
        ...trailing,
      ],
    );
  }

  Widget _buildCard(BuildContext context) {
    final theme = Theme.of(context);
    final tokens = KitTokens.of(context);
    final roles = tokens.roles;
    final attention = tone == AppStatusTone.attention;
    final tint = tone == AppStatusTone.neutral
        ? roles.text2
        : AppTheme.statusColor(theme, tone);
    final surface = attention
        ? roles.attentionSurface
        : tint.withValues(alpha: .08);
    final line = attention ? roles.attentionLine : tint.withValues(alpha: .30);
    final title = this.title;
    final caption = this.caption;
    final dismiss = onDismiss;
    final primary = this.primary;
    final secondary = this.secondary;
    final shown = actions.take(2).toList();
    final tileSize = tokens.markSize - 8;
    return KitEntrance(
      trigger: (tone, icon),
      child: Semantics(
        container: true,
        liveRegion: liveRegion,
        child: kitSurfaceBox(
          style: KitSurfaceLook.styleOf(context),
          // The tone's tint stays the card's colour in every style.
          decoration: KitSurfaceLook.decorate(
            context,
            solid: BoxDecoration(
              color: Color.alphaBlend(surface, roles.surface1),
              borderRadius: BorderRadius.circular(tokens.cardRadius),
              border: Border.all(
                color: line,
                width: KitTokens.hairlineWidth(context),
              ),
            ),
            color: Color.alphaBlend(surface, roles.surface1),
          ),
          child: Padding(
            padding: EdgeInsets.all(tokens.space4),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              mainAxisSize: MainAxisSize.min,
              children: [
                Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    // The tone's tile, made for the person's Style like
                    // every other tile; its tint stays its own fill.
                    kitSurfaceMaterial(
                      context,
                      level: KitSurfaceLevel.surface3,
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(
                          tokens.markRadius - 2,
                        ),
                      ),
                      fill: tint.withValues(alpha: .16),
                      role: KitSurfaceRole.control,
                      color: tint.withValues(alpha: .16),
                      clip: false,
                      child: SizedBox.square(
                        dimension: tileSize,
                        child: Center(
                          child: Icon(
                            icon ?? _iconFor(tone),
                            size: tokens.smallIconSize,
                            color: tint,
                          ),
                        ),
                      ),
                    ),
                    SizedBox(width: tokens.space3),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          if (caption != null) ...[
                            Text(
                              caption,
                              style: tokens.cardCaption.copyWith(color: tint),
                            ),
                            SizedBox(height: tokens.space1),
                          ],
                          if (title != null) ...[
                            Text(title, style: tokens.cardTitle),
                            SizedBox(height: tokens.space1 / 2),
                          ],
                          Text(
                            message,
                            key: messageKey,
                            style: title == null
                                ? tokens.rowTitle
                                : tokens.rowSupporting,
                          ),
                          for (final note in notes) ...[
                            SizedBox(height: tokens.space1),
                            Text(note, style: tokens.rowSupporting),
                          ],
                          if (shown.isNotEmpty) ...[
                            SizedBox(height: tokens.space2),
                            _actionsRow(tokens, shown),
                          ],
                        ],
                      ),
                    ),
                    if (dismiss != null)
                      IconButton(
                        key: const ValueKey('kit-notice-dismiss'),
                        tooltip: lookupAppLocalizations(
                          Localizations.localeOf(context),
                        ).workspaceDismissNotice,
                        onPressed: dismiss,
                        visualDensity: VisualDensity.compact,
                        icon: Icon(
                          AppIconography.close,
                          size: tokens.smallIconSize,
                        ),
                      ),
                  ],
                ),
                if (primary != null || secondary != null) ...[
                  SizedBox(height: tokens.space4),
                  Row(
                    children: [
                      if (secondary != null)
                        Expanded(
                          flex: 3,
                          child: KitButton.fromAction(
                            secondary,
                            role: KitButtonRole.secondary,
                          ),
                        ),
                      if (primary != null && secondary != null)
                        SizedBox(width: tokens.space3),
                      if (primary != null)
                        Expanded(
                          flex: secondary == null ? 1 : 4,
                          child: KitButton.fromAction(
                            primary,
                            role: KitButtonRole.primary,
                          ),
                        ),
                    ],
                  ),
                ],
              ],
            ),
          ),
        ),
      ),
    );
  }
}

/// Network or not: decides between "Report a problem" and the network fix
/// (STATE-3).
enum KitErrorKind {
  /// Unreachable, timed out, refused, reset, DNS.
  network,
  other;

  /// The IO types that mean the server could not be reached, matched by
  /// name so the kit imports no `dart:io` (web builds keep compiling).
  static const Set<String> _networkTypes = {
    'SocketException',
    'HandshakeException',
    'HttpException',
    'ClientException',
    'WebSocketException',
    'WebSocketChannelException',
  };

  /// [TimeoutException] and the IO types above are [network]; anything
  /// else, and null, is [other].
  static KitErrorKind of(Object? error) {
    if (error == null) return other;
    if (error is TimeoutException) return network;
    return _networkTypes.contains(error.runtimeType.toString())
        ? network
        : other;
  }
}

/// What "Report a problem" sends to the app's report flow. Built by the kit
/// from a state view or notice; [details] is already redacted, and it never
/// carries `error.toString()` (it may hold secrets), only the type's name.
class KitReport {
  const KitReport({
    required this.title,
    this.details,
    this.source,
    this.errorType,
    this.log,
  });

  /// The state's title: "Couldn't load files".
  final String title;

  /// Redacted technical text.
  final String? details;

  /// The page or part id: "files-viewer".
  final String? source;

  /// `error.runtimeType.toString()`, no message text.
  final String? errorType;

  /// The failed job's log excerpt (P8.4), already redacted and bounded:
  /// the report shows it in a KitLogPanel and attaches it. Empty when the
  /// job kept no log (the report says so); null when the report is not
  /// about a job.
  final String? log;
}

typedef KitReportHandler =
    Future<void> Function(BuildContext context, KitReport report);

/// The one app-wide seam for "Report a problem" (C26). The kit imports no
/// feedback code; the app sets [handler] at start-up, a flow that previews
/// exactly what is sent and offers Copy and Share first (SEC-11). Until it
/// does, no kit part offers "Report a problem" (STATE-13: never a dead
/// button).
abstract final class KitReportHook {
  static KitReportHandler? handler;

  static bool get available => handler != null;

  /// Calls [handler] with [report]; a no-op when none is set.
  static Future<void> report(BuildContext context, KitReport report) async {
    final handler = KitReportHook.handler;
    if (handler == null) return;
    await handler(context, report);
  }

  /// The one redaction the kit applies before copying or reporting details:
  /// [KitRedact.text], the same masking [KitCopy.copy] applies (SEC-2).
  static String redact(String text) => KitRedact.text(text);
}
