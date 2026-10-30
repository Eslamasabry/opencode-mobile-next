import 'dart:math' as math;
import 'dart:ui' show PathMetric;

import 'package:clock/clock.dart';
import 'package:flutter/material.dart';
import 'package:intl/intl.dart' show DateFormat;

import '../../l10n/app_localizations.dart';
import '../app_iconography.dart';
import 'kit_bidi.dart';
import 'kit_buttons.dart';
import 'kit_max_width.dart';
import 'kit_surface_look.dart';
import 'kit_tappable.dart';
import 'kit_text.dart';
import 'kit_tokens.dart';

/// How a fact came to be known (FactsBook provenance, spec 60 section 5).
enum KitProvenance {
  /// The person said it: a solid border, a check, "You told it".
  told,

  /// Read from a source: a plain border, a tag, "From <source>".
  seen,

  /// Inferred: a dashed border, a spark, "It guessed".
  guessed,

  /// Worked out from other facts: a plain border, "Worked out".
  calculated,
}

/// One remembered fact, or one search hit, with where it came from and when
/// (docs/ux-system/kit-api/KitFactRow.md; FactsBook D25 and G7).
///
/// The text, then one line that always holds the provenance word and the
/// date: "You told it · Sep 12", "From a shared message · Oct 3", "It guessed
/// · Oct 9". The border says the same thing in a second way (solid, plain,
/// dashed) but never alone: the word is always there. A guessed fact that
/// waits for approval says so and can carry its two [answers] ("Yes, keep
/// it", "Not right") under the line. A hit passes a [title] (the thing it
/// belongs to) and an [open] action.
///
/// In a right-to-left layout the amounts, card endings, times and addresses
/// inside [text] are isolated left to right, so "••42" and "[AMOUNT] CUR"
/// never reorder inside Arabic words.
///
/// States: none — a fact that exists; its provenance is its look, and press
/// and focus come from [KitTappable] when [open] is given.
class KitFactRow extends StatelessWidget {
  const KitFactRow({
    super.key,
    required this.text,
    required this.provenance,
    required this.observedAt,
    this.source,
    this.title,
    this.waitingForOk = false,
    this.answers = const [],
    this.open,
    this.semanticsLabel,
    this.tappableKey,
  });

  /// The fact, as the node holds it.
  final String text;

  final KitProvenance provenance;

  /// When it was observed or told.
  final DateTime observedAt;

  /// The source in plain words ("a shared message"); null for what the
  /// person told directly.
  final String? source;

  /// A hit's thing ("Dentist"), above the text.
  final String? title;

  /// A guessed fact waiting for the person's OK.
  final bool waitingForOk;

  /// Up to two answers under the line; drawn as buttons.
  final List<KitAction> answers;

  /// Opens the thing a hit belongs to; null is a row you read.
  final VoidCallback? open;

  /// Overrides the spoken sentence; null builds it from the parts.
  final String? semanticsLabel;
  final Key? tappableKey;

  /// "Oct 3" this year, "Oct 3, 2025" otherwise, in the person's language.
  static String dateOf(BuildContext context, DateTime at) {
    final locale = Localizations.localeOf(context).toString();
    final local = at.toLocal();
    final format = local.year == clock.now().year
        ? DateFormat.MMMd(locale)
        : DateFormat.yMMMd(locale);
    return format.format(local);
  }

  /// The provenance line: "You told it · Oct 3", "From a shared message ·
  /// Oct 3", "It guessed · Oct 9 · waiting for your OK".
  static String lineOf(
    BuildContext context, {
    required KitProvenance provenance,
    required DateTime observedAt,
    String? source,
    bool waitingForOk = false,
  }) {
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    final date = dateOf(context, observedAt);
    final from = source == null || source.isEmpty
        ? null
        : _named(context, source);
    final base = switch (provenance) {
      KitProvenance.told => l10n.kitFactToldLine(date),
      KitProvenance.seen =>
        from == null
            ? l10n.kitFactSeenNoSourceLine(date)
            : l10n.kitFactSeenLine(from, date),
      KitProvenance.guessed =>
        from == null
            ? l10n.kitFactGuessedLine(date)
            : l10n.kitFactGuessedFromLine(date, from),
      KitProvenance.calculated => l10n.kitFactCalculatedLine(date),
    };
    return waitingForOk ? l10n.kitFactWaitingLine(base) : base;
  }

  /// A name from the data, isolated for mixed text only in a right-to-left
  /// layout (left-to-right text reads as written).
  static String _named(BuildContext context, String value) =>
      Directionality.of(context) == TextDirection.rtl
      ? KitBidi.auto(value)
      : value;

  /// A run of Latin letters, digits and the marks amounts, card endings,
  /// times and addresses use, with single spaces between its words ("[Bank
  /// A] Visa ••42", "[AMOUNT] CUR", "10:00", "billing@provider-a.example"),
  /// not ending in a full stop or a comma.
  static final _valueRuns = RegExp(
    r'(?:[A-Za-z0-9_\[\]•@+\-/%$]|[.,:](?=[A-Za-z0-9_\[\]•]))+'
    r'(?: (?:[A-Za-z0-9_\[\]•@+\-/%$]|[.,:](?=[A-Za-z0-9_\[\]•]))+)*',
  );

  /// [text] with its amounts, card endings, times, addresses and Latin names
  /// isolated left to right (bidi-safe values, G20). Only a right-to-left layout
  /// needs the marks, so [rtl] false returns [text] as written.
  static String isolateValues(String text, {required bool rtl}) {
    if (!rtl) return text;
    return text.replaceAllMapped(_valueRuns, (match) => KitBidi.ltr(match[0]!));
  }

  @override
  Widget build(BuildContext context) =>
      KitMaxWidth.list(child: _buildContent(context));

  Widget _buildContent(BuildContext context) {
    final tokens = KitTokens.of(context);
    final roles = tokens.roles;
    final rtl = Directionality.of(context) == TextDirection.rtl;
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    final word = switch (provenance) {
      KitProvenance.told => l10n.kitFactToldWord,
      KitProvenance.seen => l10n.kitFactSeenWord,
      KitProvenance.guessed => l10n.kitFactGuessedWord,
      KitProvenance.calculated => l10n.kitFactCalculatedWord,
    };
    final icon = switch (provenance) {
      KitProvenance.told => AppIconography.check,
      KitProvenance.seen => AppIconography.bookmark,
      KitProvenance.guessed => AppIconography.sparkle,
      KitProvenance.calculated => AppIconography.function,
    };
    final line = lineOf(
      context,
      provenance: provenance,
      observedAt: observedAt,
      source: source,
      waitingForOk: waitingForOk,
    );
    final shown = isolateValues(text, rtl: rtl);
    final heading = title;
    final words = semanticsLabel ?? [?heading, text, word, line].join('. ');
    final radius = tokens.panelCornerRadius;
    final surfaces = KitSurfaceLook.styleOf(context);
    final border = switch (provenance) {
      KitProvenance.told => _Border.solid(roles.accent, 1.5),
      KitProvenance.guessed => _Border.dashed(roles.text2, 1.5),
      // A style draws its own edge: the plain hairline goes.
      KitProvenance.seen || KitProvenance.calculated => _Border.solid(
        KitSurfaceLook.isPlain(context) ? roles.hairline : Colors.transparent,
        1,
      ),
    };
    final card = CustomPaint(
      foregroundPainter: _FactBorderPainter(border, radius),
      child: kitSurfaceBox(
        style: surfaces,
        decoration: KitSurfaceLook.decorate(
          context,
          solid: BoxDecoration(
            color: tokens.fillOf(KitSurfaceLevel.surface1),
            borderRadius: BorderRadius.circular(radius),
          ),
        ),
        child: Padding(
          padding: EdgeInsetsDirectional.fromSTEB(
            tokens.space4,
            tokens.space3,
            tokens.space4,
            tokens.space3,
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            mainAxisSize: MainAxisSize.min,
            spacing: tokens.space1,
            children: [
              if (heading != null)
                KitText(
                  _named(context, heading),
                  role: KitTextRole.rowTitle,
                  maxLines: 2,
                  overflow: TextOverflow.ellipsis,
                ),
              KitText(shown, role: KitTextRole.body),
              Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                spacing: tokens.space2,
                children: [
                  Padding(
                    padding: EdgeInsets.zero,
                    child: Icon(
                      icon,
                      size: tokens.smallIconSize,
                      color: roles.text2,
                    ),
                  ),
                  Expanded(
                    child: KitText(
                      line,
                      role: KitTextRole.caption,
                      tone: KitTextTone.secondary,
                    ),
                  ),
                ],
              ),
              if (answers.isNotEmpty)
                Padding(
                  padding: EdgeInsets.only(top: tokens.space2),
                  child: Wrap(
                    spacing: tokens.space2,
                    runSpacing: tokens.space2,
                    children: [
                      for (final answer in answers.take(2))
                        KitButton.fromAction(
                          answer,
                          role: KitButtonRole.secondary,
                          expand: false,
                        ),
                    ],
                  ),
                ),
            ],
          ),
        ),
      ),
    );
    final padded = Padding(
      padding: EdgeInsetsDirectional.symmetric(
        horizontal: tokens.gutter,
        vertical: tokens.space1,
      ),
      child: card,
    );
    final tap = open;
    if (tap == null) {
      return Semantics(
        container: true,
        label: words,
        child: ExcludeSemantics(child: padded),
      );
    }
    return KitTappable(
      tappableKey: tappableKey,
      onTap: tap,
      label: words,
      child: ExcludeSemantics(child: padded),
    );
  }
}

class _Border {
  const _Border.solid(this.color, this.width) : dashed = false;
  const _Border.dashed(this.color, this.width) : dashed = true;
  final Color color;
  final double width;
  final bool dashed;
}

/// Draws the provenance border over the card: solid, or dashed for a guess.
class _FactBorderPainter extends CustomPainter {
  const _FactBorderPainter(this.border, this.radius);

  final _Border border;
  final double radius;

  @override
  void paint(Canvas canvas, Size size) {
    final inset = border.width / 2;
    final rect = RRect.fromRectAndRadius(
      Rect.fromLTWH(
        inset,
        inset,
        size.width - border.width,
        size.height - border.width,
      ),
      Radius.circular(math.max(0, radius - inset)),
    );
    final paint = Paint()
      ..color = border.color
      ..style = PaintingStyle.stroke
      ..strokeWidth = border.width;
    if (!border.dashed) {
      canvas.drawRRect(rect, paint);
      return;
    }
    const dash = 6.0;
    const gap = 4.0;
    final path = Path()..addRRect(rect);
    for (final PathMetric metric in path.computeMetrics()) {
      var at = 0.0;
      while (at < metric.length) {
        final end = math.min(at + dash, metric.length);
        canvas.drawPath(metric.extractPath(at, end), paint);
        at += dash + gap;
      }
    }
  }

  @override
  bool shouldRepaint(_FactBorderPainter old) =>
      old.border.color != border.color ||
      old.border.width != border.width ||
      old.border.dashed != border.dashed ||
      old.radius != radius;
}
