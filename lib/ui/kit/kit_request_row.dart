part of 'kit_request_card.dart';

/// [KitRequestCard.asRow]: the request as one row of the Inbox: the place
/// line with its amber dot (and how long it has waited, "waiting 4 min",
/// when [KitRequestCard.since] is given), the question, the exact command,
/// and the answers at the row's end (Reject quiet, Allow once filled unless the
/// caller asked for secondary answers), or one Open.
extension _KitRequestRow on _KitRequestCardState {
  Widget _rowAsk(BuildContext context) {
    final waiting = widget.phase == KitRequestPhase.waiting;
    final place = widget._place;
    // Counted on whole minutes from when this phone first showed it.
    final since = waiting && place != null ? widget.since : null;
    if (since == null) return _rowAskAt(context, null);
    return KitSince(
      since: since,
      ticks: KitSinceTicks.minutes,
      builder: (context, status) => _rowAskAt(context, status.elapsed),
    );
  }

  /// [_rowAsk] with the wait so far, [age], on the place line.
  Widget _rowAskAt(BuildContext context, Duration? age) {
    final tokens = KitTokens.of(context);
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    final decide = widget.answers as KitRequestDecide?;
    final summary = widget.summary;
    final waiting = widget.phase == KitRequestPhase.waiting;
    final server = widget.server;
    final place = widget._place;
    final label = [
      widget.title,
      KitNeedsYou.reasonWord(context, widget.reason!),
      if (place != null && place.isNotEmpty)
        KitBidi.auto(place)
      else if (server == null || server.isEmpty)
        KitBidi.auto(widget.who!)
      else
        l10n.kitNeedsYouWhoOnServer(
          KitBidi.auto(widget.who!),
          KitBidi.auto(server),
        ),
      if (age != null) l10n.kitNeedsYouWaitingSpoken(age.inMinutes),
      if (summary != null && summary.isNotEmpty) summary,
      widget.ifIgnored!,
    ].join(', ');
    final Widget? answers = widget.phase == KitRequestPhase.sending
        ? _answerLine(context)
        : decide == null
        ? null
        : _rowDecide(context, decide);
    return Semantics(
      key: const ValueKey('kit-request-row'),
      container: true,
      explicitChildNodes: true,
      label: label,
      child: Focus(
        focusNode: _focus,
        onKeyEvent: _onKey,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            // Read once when it appears (A11Y-3).
            Semantics(
              container: true,
              liveRegion: true,
              label: widget.announcement,
              child: _rowHead(
                context,
                place == null || age == null
                    ? place
                    // The age never breaks mid-phrase ("waiting 4 / min").
                    : '$place · '
                          '${l10n.kitNeedsYouWaiting(KitSince.ageLabel(context, age)).replaceAll(' ', '\u00A0')}',
                open: answers == null,
              ),
            ),
            if (summary != null && summary.isNotEmpty)
              _summary(context, summary),
            if (waiting && widget.receipt != null)
              Padding(
                padding: EdgeInsetsDirectional.only(top: tokens.space3),
                child: widget.receipt!,
              ),
            if (answers != null)
              Padding(
                padding: EdgeInsetsDirectional.only(top: tokens.space3),
                child: answers,
              ),
          ],
        ),
      ),
    );
  }

  /// The question, under the Inbox's place line when there is one; with
  /// [KitRequestCard._onOpen] both are one target that opens the
  /// conversation. A request answered in its conversation keeps its Open at
  /// the end of the question's line (under it from 1.3x text), so the row
  /// stays one line pair.
  Widget _rowHead(BuildContext context, String? place, {bool open = false}) {
    final tokens = KitTokens.of(context);
    Widget title = KitText(
      widget.title,
      key: widget.titleKey,
      role: place == null ? KitTextRole.rowTitle : KitTextRole.headline,
      tone: KitTextTone.primary,
    );
    if (open) {
      // Open at the end of the question's line; under it from 1.3x text.
      final large = MediaQuery.textScalerOf(context).scale(10) >= 13;
      final button = _rowOpen(context);
      title = large
          ? Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                title,
                Padding(
                  padding: EdgeInsetsDirectional.only(top: tokens.space3),
                  child: Align(
                    alignment: AlignmentDirectional.centerEnd,
                    child: button,
                  ),
                ),
              ],
            )
          : Row(
              children: [
                Expanded(child: title),
                SizedBox(width: tokens.space3),
                button,
              ],
            );
    }
    final head = place == null
        ? title
        : Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              _rowPlace(context, place),
              SizedBox(height: tokens.space1),
              title,
            ],
          );
    final onOpen = widget._onOpen;
    if (onOpen == null) return ExcludeSemantics(child: head);
    // One target, one spoken name ("Open Fix flaky checkout test"): the
    // visible Open inside it does the same thing.
    return Semantics(
      container: true,
      child: KitTappable(
        onTap: onOpen,
        label: widget._openLabel,
        shape: KitShape.button,
        child: head,
      ),
    );
  }

  /// The amber needs-you dot and where the request waits, one quiet line.
  /// The caller isolates each name in it (COPY-30); the line keeps the
  /// page's direction, so the server leads on the reading side.
  Widget _rowPlace(BuildContext context, String place, {bool waiting = true}) {
    final tokens = KitTokens.of(context);
    // The dot grows with the words, up to half again its size.
    final grow = MediaQuery.textScalerOf(context).scale(10).clamp(10, 15) / 10;
    final dot = KitTokens.needsYouDotSize * grow;
    final style = KitText.styleOf(context, KitTextRole.secondary);
    final line = MediaQuery.textScalerOf(
      context,
    ).scale((style.fontSize ?? 14) * (style.height ?? 1.4));
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        // Centred on the first line of words, however many lines follow.
        SizedBox(
          height: line,
          child: Center(
            child: SizedBox.square(
              dimension: dot,
              child: DecoratedBox(
                decoration: BoxDecoration(
                  // Amber only while it waits on the person.
                  color: waiting ? tokens.roles.attention : tokens.roles.text3,
                  shape: BoxShape.circle,
                ),
              ),
            ),
          ),
        ),
        SizedBox(width: tokens.space2),
        Expanded(
          child: KitText(
            place,
            role: KitTextRole.secondary,
            tone: KitTextTone.secondary,
            maxLines: 2,
            overflow: TextOverflow.ellipsis,
          ),
        ),
      ],
    );
  }

  /// A request answered in its conversation: its one "Open".
  Widget _rowOpen(BuildContext context) {
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    return KitButton(
      role: KitButtonRole.secondary,
      key: widget._openKey,
      label: l10n.kitRequestOpen,
      expand: false,
      maxLines: 1,
      // Opening is not an answer: no once-only guard, no send haptic.
      onPressed: widget._onOpen,
    );
  }

  /// Reject and Allow once at the row's end, the main answer last (nearest
  /// the thumb); they wrap under each other rather than squeeze at large
  /// text.
  Widget _rowDecide(BuildContext context, KitRequestDecide answers) {
    final tokens = KitTokens.of(context);
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    final onAllow = answers.onAllow;
    final onReject = answers.onReject;
    final reason = answers.disabledReason;
    final buttons = Wrap(
      alignment: WrapAlignment.end,
      crossAxisAlignment: WrapCrossAlignment.center,
      spacing: tokens.space2,
      runSpacing: tokens.space2,
      children: [
        KitButton.tertiary(
          key: answers.rejectKey,
          label: answers.rejectLabel ?? l10n.kitRequestReject,
          shortcut: 'D',
          onPressed: onReject == null
              ? null
              : () {
                  if (_mayAnswer()) onReject();
                },
        ),
        KitButton(
          role: answers.secondary
              ? KitButtonRole.secondary
              : KitButtonRole.primary,
          key: answers.allowKey,
          label: answers.allowLabel ?? l10n.kitRequestAllowOnce,
          shortcut: 'A',
          expand: false,
          maxLines: 1,
          onPressed: onAllow == null
              ? null
              : () {
                  if (_mayAnswer()) onAllow();
                },
        ),
      ],
    );
    if (reason == null || (onAllow != null && onReject != null)) {
      return buttons;
    }
    return Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        buttons,
        Padding(
          padding: EdgeInsetsDirectional.only(top: tokens.space2),
          child: KitText(
            reason,
            role: KitTextRole.secondary,
            tone: KitTextTone.secondary,
          ),
        ),
      ],
    );
  }

  /// Answered, answered elsewhere or expired: the command (or the question)
  /// and its outcome on one quiet line, with Undo while the answer is held.
  Widget _rowAnswered(BuildContext context) {
    final tokens = KitTokens.of(context);
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    final summary = widget.summary;
    final what = summary != null && summary.isNotEmpty
        ? KitText(
            summary,
            role: KitTextRole.mono,
            tone: KitTextTone.secondary,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
          )
        : KitText(
            widget.title,
            key: widget.titleKey,
            role: KitTextRole.secondary,
            tone: KitTextTone.secondary,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
          );
    // At the start of the row's words, not centred in the cross-fade. The
    // Inbox keeps the place line above it: which request was answered.
    final place = widget._place;
    final outcome = Wrap(
      crossAxisAlignment: WrapCrossAlignment.center,
      spacing: tokens.space2,
      runSpacing: tokens.space1,
      children: [
        what,
        if (widget.phase == KitRequestPhase.expired)
          Semantics(
            container: true,
            liveRegion: true,
            child: KitText(
              l10n.kitRequestExpired,
              role: KitTextRole.secondary,
              tone: KitTextTone.secondary,
            ),
          )
        else
          widget.receipt!,
      ],
    );
    return Align(
      key: const ValueKey('kit-request-row-answered'),
      alignment: AlignmentDirectional.centerStart,
      child: place == null
          ? outcome
          : Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                _rowPlace(context, place, waiting: false),
                SizedBox(height: tokens.space2),
                outcome,
              ],
            ),
    );
  }
}
