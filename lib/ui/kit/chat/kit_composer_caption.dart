part of 'kit_composer.dart';

/// What is written in the gap: the phase words with the time and a small
/// red stop; or "Didn't send", Retry and Details. Neutral words (text2;
/// a failure's text1 with a neutral glyph); red only for Stop. The words are the live region: the
/// label is the phase, so it announces phase changes and not the seconds.
class _EdgeCaption extends StatelessWidget {
  const _EdgeCaption({
    required this.data,
    required this.xCentre,
    required this.textHeight,
    required this.size,
  });

  final _EdgeData data;

  /// Where the words' x-height centre lies below the top of their box.
  final double xCentre;
  final double textHeight;
  final double size;

  @override
  Widget build(BuildContext context) {
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    final tokens = KitTokens.of(context);
    final failed = data.failure;
    final live = data.live;
    if (failed != null) {
      return Row(
        mainAxisSize: MainAxisSize.min,
        children: _failure(context, l10n, tokens, failed),
      );
    }
    if (live == null) return const SizedBox.shrink();
    final top = 24 - xCentre;
    final row = Padding(
      // 48 dp tall in all, with the words' x-height centre at its middle,
      // which is where the border line runs.
      padding: EdgeInsets.fromLTRB(
        _railEnd,
        top,
        _railEnd,
        _railHeight - top - textHeight,
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          _running(context, l10n, live),
          if (live.onStop != null) ...[
            const SizedBox(width: _stopGap),
            Padding(
              padding: EdgeInsets.only(top: xCentre - _stopGap),
              child: _square(tokens, _stopSide),
            ),
          ],
        ],
      ),
    );
    if (live.onStop == null) return row;
    return KitTappable(
      tappableKey: live.stopKey,
      label: l10n.kitTurnLiveStop,
      shape: KitShape.button,
      onTap: live.stopping ? null : live.onStop,
      disabledReason: live.stopping ? l10n.kitTurnLiveStopping : null,
      child: row,
    );
  }

  // The rail's hand-set measures, in logical pixels (48 dp tall in all).
  static const _railHeight = 48.0;
  static const _railEnd = 6.0;
  static const _stopGap = 4.0;
  static const _stopSide = 8.0;
  static const _stopRadius = 2.0;

  static Widget _square(KitTokens tokens, double side) => Container(
    width: side,
    height: side,
    decoration: BoxDecoration(
      color: tokens.roles.danger,
      borderRadius: BorderRadius.circular(_stopRadius),
    ),
  );

  Widget _text(String line, KitTextTone tone, {double? fontSize}) =>
      KitText.rich(
        TextSpan(
          text: line,
          style: TextStyle(
            fontWeight: FontWeight.w500,
            fontSize: fontSize ?? size,
          ),
        ),
        role: KitTextRole.caption,
        tone: tone,
        maxLines: 1,
        tabular: true,
      );

  Widget _words(String line, String spoken, String phase, KitTextTone tone) =>
      Semantics(
        liveRegion: true,
        label: spoken,
        child: ExcludeSemantics(
          child: AnimatedSwitcher(
            duration: KitMotion.quick,
            child: KeyedSubtree(key: ValueKey(phase), child: _text(line, tone)),
          ),
        ),
      );

  Widget _running(
    BuildContext context,
    AppLocalizations l10n,
    KitTurnLive live,
  ) {
    final activity = live.activity == KitTurnActivity.sending
        ? KitTurnActivity.thinking
        : live.activity;
    return KitSince(
      since: live.since,
      ticks: KitSinceTicks.seconds,
      builder: (context, status) {
        final slow = status.elapsed >= KitTurnLive.slowAfter;
        final words = switch (activity) {
          KitTurnActivity.waitingForServer when slow =>
            l10n.kitComposerPillNoAnswer,
          KitTurnActivity.waitingForServer => l10n.kitTurnLiveThinking,
          KitTurnActivity.waitingForModel when !slow =>
            l10n.kitTurnLiveThinking,
          _ => KitTurnLive.wordsFor(
            l10n,
            activity,
            status.elapsed,
            teamAlsoWorking: live.teamAlsoWorking,
          ),
        };
        var line = status.elapsed < KitTurnLive.showElapsedAfter
            ? l10n.kitTurnLiveNow(words)
            : l10n.kitTurnLiveFor(
                words,
                KitTurnLive.elapsedText(l10n, status.elapsed),
              );
        if (data.note != null) line = '$line · ${data.note}';
        return _words(
          line,
          data.note == null ? words : '$words. ${data.note}',
          words,
          KitTextTone.secondary,
        );
      },
    );
  }

  List<Widget> _failure(
    BuildContext context,
    AppLocalizations l10n,
    KitTokens tokens,
    KitComposerFailure failure,
  ) => [
    const SizedBox(width: _railEnd),
    // A failure is neutral (LOOK-5, B2): text1 words after the neutral
    // error glyph; only Stop on this edge is red.
    const KitIcon(
      AppIconography.error,
      size: KitIconSize.small,
      tone: KitTextTone.primary,
    ),
    const SizedBox(width: _stopGap),
    _words(failure.words, failure.words, failure.words, KitTextTone.primary),
    KitTappable(
      tappableKey: failure.retryKey,
      label: l10n.kitComposerRailRetry,
      shape: KitShape.button,
      onTap: failure.onRetry,
      child: SizedBox(
        height: tokens.minTarget - 8,
        child: Center(
          child: Padding(
            padding: EdgeInsets.symmetric(horizontal: tokens.space2),
            child: _text('· ${l10n.kitComposerRailRetry}', KitTextTone.primary),
          ),
        ),
      ),
    ),
    if (failure.onDetails != null)
      KitTappable(
        label: l10n.kitDetails,
        shape: KitShape.button,
        onTap: failure.onDetails,
        child: SizedBox(
          width: tokens.minTarget - 16,
          height: tokens.minTarget - 8,
          child: const Center(
            child: KitIcon(AppIconography.info, size: KitIconSize.small),
          ),
        ),
      ),
    const SizedBox(width: _railEnd),
  ];
}

class _Circle extends StatelessWidget {
  const _Circle({
    super.key,
    required this.kind,
    required this.label,
    required this.onTap,
    this.tappableKey,
    this.shortcut,
    this.working = false,
    this.disabledReason,
  });

  final _CircleKind kind;
  final String label;
  final VoidCallback? onTap;
  final Key? tappableKey;
  final String? shortcut;
  final bool working;
  final String? disabledReason;

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final roles = tokens.roles;
    final stop = kind == _CircleKind.stop;
    final disabled = kind == _CircleKind.sendDisabled;
    // Stop is the destructive variant: the same red as a destructive kit
    // button, so ending a run reads as ending something.
    final fill = stop
        ? roles.dangerFill
        : disabled
        ? roles.surface3
        : roles.accent;
    final ink = stop
        ? roles.onDangerFill
        : disabled
        ? roles.text3
        : roles.onAccent;
    final rtl = Directionality.of(context) == TextDirection.rtl;
    final still = KitMotion.reduced(context);

    Widget glyph;
    if (working) {
      glyph = still
          ? Icon(
              AppIconography.statusDot,
              size: KitIconSize.small.logical,
              color: ink,
            )
          : SizedBox.square(
              dimension: KitIconSize.small.logical,
              child: CircularProgressIndicator(strokeWidth: 2, color: ink),
            );
    } else if (stop) {
      const side = KitTokens.composerStopSquare;
      glyph = SizedBox.square(
        key: const ValueKey('kit-composer-stop-square'),
        dimension: side,
        child: DecoratedBox(
          decoration: BoxDecoration(
            color: ink,
            borderRadius: BorderRadius.circular(side / 4),
          ),
        ),
      );
    } else {
      final icon = switch (kind) {
        _CircleKind.mic || _CircleKind.listen => AppIconography.mic,
        _CircleKind.voiceDone => AppIconography.check,
        _ => AppIconography.send,
      };
      glyph = Icon(icon, size: KitIconSize.small.logical, color: ink);
      // The send glyph points the way the words go (LAY-8); the mic does
      // not mirror.
      if (rtl && icon == AppIconography.send) {
        glyph = Transform.flip(flipX: true, child: glyph);
      }
    }

    return KitTappable(
      tappableKey: tappableKey,
      shape: KitShape.circle,
      label: label,
      tooltip: label,
      shortcut: shortcut,
      disabledReason: onTap == null ? (disabledReason ?? label) : null,
      onTap: onTap,
      child: SizedBox.square(
        dimension: tokens.minTarget,
        child: Center(
          child: SizedBox.square(
            dimension: KitTokens.composerActionSize,
            child: DecoratedBox(
              key: ValueKey('kit-composer-circle-${kind.name}'),
              decoration: BoxDecoration(color: fill, shape: BoxShape.circle),
              child: Center(child: glyph),
            ),
          ),
        ),
      ),
    );
  }
}
