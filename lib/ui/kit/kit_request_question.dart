part of 'kit_request_card.dart';

/// How a waiting card is drawn.
enum KitRequestLook {
  /// The needs-you card every list and sheet uses (LOOK-24).
  standard,

  /// The conversation's question (vision 2026-10-20, "Chat with a
  /// question"): a calm `surface1` card with the attention line and an
  /// attention edge at its start, "● Needs you · 20 s" counting while it
  /// waits, a permission's ask at the title size, what it will run or touch
  /// in one bordered mono block with Details inside it, Reject and the
  /// primary under the thumb, and the named standing grant centred under
  /// them.
  question,
}

/// One thing a request will run or touch, as one mono line of the card's
/// block: a path or a command, left to right in any language, with the
/// size of its change when the request carries one ("+4 −1").
@immutable
class KitRequestTarget {
  const KitRequestTarget(this.text, {this.change, this.file = false});

  /// The path or command, exactly as the request names it.
  final String text;

  /// [text] is a file's path: the line leads with the file glyph.
  final bool file;

  /// The change's size in words ("+4 −1"); null when the request has none.
  final String? change;
}

/// The question look's drawing ([KitRequestLook.question]).
extension _KitRequestQuestionLook on _KitRequestCardState {
  // ── The question look (vision 2026-10-20) ──────────────────────────────

  /// What the block shows: the [KitRequestCard.targets], else the summary
  /// as one target.
  List<KitRequestTarget> get _targets {
    if (widget.targets.isNotEmpty) return widget.targets;
    final summary = widget.summary;
    return summary == null || summary.trim().isEmpty
        ? const []
        : [KitRequestTarget(summary)];
  }

  Widget _questionCard(BuildContext context) {
    final tokens = KitTokens.of(context);
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    final reasonWord = KitNeedsYou.reasonWord(context, widget.reason!);
    final who = KitBidi.auto(widget.who!);
    final server = widget.server;
    final whoOnServer = server == null || server.isEmpty
        ? who
        : l10n.kitNeedsYouWhoOnServer(who, KitBidi.auto(server));
    final detail = widget.detail;
    final waiting = widget.phase == KitRequestPhase.waiting;
    final answers = widget.answers;
    final sheetPrimary =
        waiting &&
        (answers is KitRequestInSheet || answers is KitRequestChooseMany);
    final targets = _targets;
    // Details belongs to the thing it explains: inside the block.
    final detailsInBlock =
        targets.isNotEmpty && widget.onDetails != null && !sheetPrimary;

    final (inScroll, fixed) = _answers(context);
    final tertiary = _quietLine(
      context,
      centred: true,
      withDetails: !detailsInBlock,
    );
    final large = MediaQuery.textScalerOf(context).scale(10) >= 20;
    // The ask reads at the title size; from 1.3 text, the headline.
    final bigTitle = MediaQuery.textScalerOf(context).scale(10) < 13;

    return KitSince(
      since: widget.since,
      ticks: KitSinceTicks.minutes,
      builder: (context, status) {
        final ageSpoken = widget.since == null
            ? null
            : l10n.kitNeedsYouWaitingSpoken(status.elapsed.inMinutes);
        final label = [
          widget.title,
          reasonWord,
          whoOnServer,
          ?ageSpoken,
          if (detail != null && detail.isNotEmpty) detail,
          widget.ifIgnored!,
        ].join(', ');
        final promote =
            (widget.kind == KitRequestKind.question ||
                widget.kind == KitRequestKind.choice) &&
            !widget.detailMarkdown &&
            detail != null &&
            detail.isNotEmpty;
        final header = Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          mainAxisSize: MainAxisSize.min,
          children: [
            _waitingCaption(context, waiting: waiting),
            SizedBox(height: tokens.space2),
            KitText(
              promote ? detail : widget.title,
              key: widget.titleKey,
              // A question short enough to read at a glance takes the
              // title size too; a long one stays the headline.
              role:
                  bigTitle &&
                      (!promote ||
                          (detail.length <= 64 && !detail.contains('\n')))
                  ? KitTextRole.title
                  : KitTextRole.headline,
              tone: KitTextTone.primary,
            ),
            if (!promote && detail != null && detail.isNotEmpty)
              Padding(
                padding: EdgeInsetsDirectional.only(top: tokens.space1),
                child: widget.detailMarkdown
                    ? _BoundedMarkdown(detail)
                    : KitText(
                        detail,
                        role: KitTextRole.secondary,
                        tone: KitTextTone.secondary,
                      ),
              ),
          ],
        );
        final words = Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Semantics(
              container: true,
              liveRegion: true,
              label: widget.announcement,
              child: ExcludeSemantics(child: header),
            ),
            if (targets.isNotEmpty)
              Padding(
                padding: EdgeInsetsDirectional.only(top: tokens.space4),
                child: _targetBlock(
                  context,
                  targets,
                  details: detailsInBlock ? widget.onDetails : null,
                ),
              ),
            if (waiting && widget.receipt != null)
              Padding(
                padding: EdgeInsetsDirectional.only(top: tokens.space3),
                child: widget.receipt!,
              ),
            if (inScroll != null)
              Padding(
                padding: EdgeInsetsDirectional.only(top: tokens.space3),
                child: inScroll,
              ),
            if (large && tertiary != null)
              Padding(
                padding: EdgeInsetsDirectional.only(top: tokens.space2),
                child: tertiary,
              ),
          ],
        );
        return Semantics(
          container: true,
          explicitChildNodes: true,
          label: label,
          child: _frame(
            context,
            attention: true,
            edge: true,
            key: const ValueKey('kit-request-card'),
            endsWithTap: !large && tertiary != null,
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                _scrollWhenLarge(context, words),
                if (fixed != null)
                  Padding(
                    padding: EdgeInsetsDirectional.only(top: tokens.space4),
                    child: fixed,
                  ),
                if (!large && tertiary != null)
                  Padding(
                    padding: EdgeInsetsDirectional.only(top: tokens.space1),
                    child: tertiary,
                  ),
              ],
            ),
          ),
        );
      },
    );
  }

  /// "● Needs you · 20 s": the attention dot and word while it waits, and
  /// how long it has waited on this phone, counting seconds for the first
  /// minute and minutes after; how many more wait behind it.
  Widget _waitingCaption(BuildContext context, {required bool waiting}) {
    final tokens = KitTokens.of(context);
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    final more = widget.queued > 0
        ? l10n
              .chatRequestMoreWaiting(widget.queued)
              .replaceFirst(RegExp(r'[.。]$'), '')
        : null;
    Widget line(Duration? age) {
      final text = [
        l10n.kitRequestNeedsYouLabel,
        if (age != null) KitSince.shortAge(context, age),
        ?more,
      ].join(' · ');
      return kitRequestNeedsYouTag(
        context,
        text: text,
        waiting: waiting,
        role: KitTextRole.label,
        tabular: true,
        question: true,
        leading: ExcludeSemantics(
          child: SizedBox.square(
            dimension: KitTokens.needsYouDotSize,
            child: DecoratedBox(
              decoration: ShapeDecoration(
                color: tokens.roles.attention,
                shape: const CircleBorder(),
              ),
            ),
          ),
        ),
      );
    }

    if (widget.since == null || !waiting) return line(null);
    return KitSince(
      since: widget.since,
      ticks: KitSinceTicks.secondsThenMinutes,
      builder: (context, status) => line(status.elapsed),
    );
  }

  /// What it will run or touch, in one bordered mono block: a command
  /// wraps (three lines at most), a path keeps its root and its name; each
  /// with its change's size; past [KitRequestCard.maxTargetsInPlace] a
  /// "+{n} more" line; [details] as the block's own link.
  Widget _targetBlock(
    BuildContext context,
    List<KitRequestTarget> targets, {
    VoidCallback? details,
  }) {
    final tokens = KitTokens.of(context);
    final roles = tokens.roles;
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    final single = targets.length == 1;
    final shown = targets.take(KitRequestCard.maxTargetsInPlace).toList();
    final hidden = targets.length - shown.length;
    Widget detailsLink() => KitTappable(
      key: widget.detailsKey,
      onTap: details,
      label: l10n.kitDetails,
      child: Padding(
        padding: EdgeInsetsDirectional.symmetric(horizontal: tokens.space1),
        child: Text(
          l10n.kitDetails,
          style:
              KitText.styleOf(
                context,
                KitTextRole.secondary,
                tone: KitTextTone.accent,
              ).copyWith(
                decoration: TextDecoration.underline,
                decorationColor: roles.accent,
              ),
        ),
      ),
    );
    final line = KitSurfaceLook.divider(
      context,
      color: roles.hairline,
      width: KitTokens.hairlineWidth(context),
    );
    final divider = Divider(
      height: line.width,
      thickness: line.width,
      color: line.color,
    );
    // From 1.3 text the size and Details take a line of their own under
    // the path, so a long path and its link never squeeze each other.
    final large = MediaQuery.textScalerOf(context).scale(10) >= 13;
    // Signal: a shell command is printed after its prompt ("\$ sleep 150").
    final prompt =
        KitTrack.on(context) && widget.icon == AppIconography.terminal;
    Widget row(KitRequestTarget target, {bool withDetails = false}) {
      final change = target.change;
      final shown = prompt && !target.file ? '\$ ${target.text}' : target.text;
      final text = Padding(
        padding: EdgeInsets.symmetric(vertical: tokens.space3),
        child: Semantics(
          container: true,
          label: change == null ? target.text : '${target.text}, $change',
          textDirection: TextDirection.ltr,
          child: ExcludeSemantics(
            child: single
                ? KitText.mono(shown, tone: KitTextTone.primary, maxLines: 3)
                : KitText.mono(
                    shown,
                    tone: KitTextTone.primary,
                    cut: KitMonoCut.middle,
                  ),
          ),
        ),
      );
      final size = change == null
          ? null
          : ExcludeSemantics(
              child: KitText(
                change,
                role: KitTextRole.secondary,
                tone: KitTextTone.secondary,
                tabular: true,
              ),
            );
      final lead = <Widget>[
        if (target.file) ...[
          ExcludeSemantics(
            child: Icon(
              AppIconography.file,
              size: tokens.smallIconSize,
              color: roles.text2,
            ),
          ),
          SizedBox(width: tokens.space3),
        ],
      ];
      // With Details in it the row is its link's height; a list row is
      // the line and a little air.
      final padding = EdgeInsetsDirectional.only(
        start: tokens.space3,
        end: withDetails ? tokens.space1 : tokens.space3,
      );
      if (large) {
        return Padding(
          padding: padding,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            mainAxisSize: MainAxisSize.min,
            children: [
              Row(
                children: [
                  ...lead,
                  Expanded(child: text),
                ],
              ),
              if (size != null || withDetails)
                Wrap(
                  alignment: size == null
                      ? WrapAlignment.end
                      : WrapAlignment.spaceBetween,
                  crossAxisAlignment: WrapCrossAlignment.center,
                  spacing: tokens.space3,
                  children: [?size, if (withDetails) detailsLink()],
                ),
            ],
          ),
        );
      }
      return Padding(
        padding: padding,
        child: Row(
          children: [
            ...lead,
            Expanded(child: text),
            if (size != null) ...[SizedBox(width: tokens.space3), size],
            if (withDetails) ...[SizedBox(width: tokens.space2), detailsLink()],
          ],
        ),
      );
    }

    final footer = single || (hidden <= 0 && details == null)
        ? null
        : Padding(
            padding: EdgeInsetsDirectional.only(
              start: tokens.space3,
              end: tokens.space2,
            ),
            // A Wrap: at large text Details drops under "+2 more".
            child: Wrap(
              alignment: hidden > 0
                  ? WrapAlignment.spaceBetween
                  : WrapAlignment.end,
              crossAxisAlignment: WrapCrossAlignment.center,
              spacing: tokens.space3,
              children: [
                if (hidden > 0)
                  Padding(
                    padding: EdgeInsets.symmetric(vertical: tokens.space3),
                    child: KitText(
                      l10n.kitRequestMoreTargets(hidden),
                      role: KitTextRole.secondary,
                      tone: KitTextTone.secondary,
                    ),
                  ),
                if (details != null) detailsLink(),
              ],
            ),
          );
    // The paths are code, like the permission card's command: the style's
    // code edge keeps them visible where the ground is the card's face too.
    return kitCodeSurface(
      context,
      fill: roles.ground,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(tokens.codeRadius),
        side: BorderSide(
          color: roles.hairline,
          width: KitTokens.hairlineWidth(context),
        ),
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          for (final (index, target) in shown.indexed) ...[
            if (index > 0) divider,
            row(target, withDetails: single && details != null),
          ],
          if (footer != null) ...[divider, footer],
        ],
      ),
    );
  }
}
