import 'package:flutter/material.dart';
import 'package:intl/intl.dart';

import '../../../domain/usage_subscriptions.dart';
import '../../../l10n/app_localizations.dart';
import '../../kit/kit.dart';
import '../../widgets/provider_logo.dart';

AppLocalizations usageStrings(BuildContext context) =>
    lookupAppLocalizations(Localizations.localeOf(context));

/// The plain words Usage shows for a subscription, a connection and a time.
/// One place, so the card, the subscription page and the widget settings
/// never disagree on a sentence.
abstract final class UsageText {
  /// "Codex", "Claude", "GLM", "MiniMax"; any other provider keeps the name
  /// the reader gave it.
  static String provider(
    AppLocalizations l10n,
    String providerId,
    String displayName,
  ) => switch (providerId) {
    'codex' => l10n.quotaCodex,
    'claude' => l10n.quotaClaude,
    'glm' => l10n.quotaGlm,
    'minimax' => l10n.quotaMiniMax,
    _ => displayName.trim().isEmpty ? providerId : displayName.trim(),
  };

  /// Why a computer's usage check could not read, in plain words.
  static String computerProblem(
    AppLocalizations l10n,
    ComputerUsageProblem problem,
    String server,
  ) => switch (problem) {
    ComputerUsageProblem.nodeMissing ||
    ComputerUsageProblem.nodeTooOld => l10n.usageComputerNeedsNode(server),
    ComputerUsageProblem.serverTooOld => l10n.usageComputerServerTooOld(server),
    ComputerUsageProblem.unreachable => l10n.usageComputerOffline(server),
    ComputerUsageProblem.noAnswer => l10n.usageComputerNoAnswer(server),
  };

  static String subscriptionName(
    AppLocalizations l10n,
    UsageSubscription subscription,
  ) => provider(l10n, subscription.providerId, subscription.displayName);

  /// The models.dev logo slug for a plan provider id. Usage ids are not
  /// always the catalog's ("codex" is OpenAI's mark).
  static String logoId(String providerId) => switch (providerId) {
    'codex' => 'openai',
    'glm' => 'zai',
    'claude' => 'anthropic',
    'antigravity_claude' || 'antigravity_3p' => 'anthropic',
    'antigravity' => 'google',
    'copilot' => 'github-copilot',
    'kimi' => 'moonshotai',
    'grok' => 'xai',
    _ => providerId,
  };

  /// The provider's mark, by its catalog slug.
  static Widget logo(String providerId) => ProviderLogo(logoId(providerId));

  /// Plans whose sign-in is a key, so "not signed in" reads "No key added".
  static bool isKeyProvider(String providerId) =>
      providerId == 'glm' || providerId == 'minimax';

  /// The name the rest of the app uses: In-app Ubuntu by its server name,
  /// a saved server by its saved name, and "This phone" only for the phone's
  /// agent host.
  static String connection(AppLocalizations l10n, UsageConnection connection) =>
      switch (connection.kind) {
        UsageConnectionKind.phoneAgents => l10n.usageThisPhone,
        UsageConnectionKind.inAppUbuntu => l10n.phoneServerCardTitle,
        _ =>
          connection.id == UsageConnection.phoneId || connection.name.isEmpty
              ? l10n.usageThisPhone
              : connection.name,
      };

  /// "plus" -> "Plus"; null when the plan is unknown.
  static String? plan(String? label) {
    final text = label?.trim();
    if (text == null || text.isEmpty) return null;
    return toBeginningOfSentenceCase(text);
  }

  static String windowTitle(AppLocalizations l10n, UsageWindow window) =>
      switch (window.kind) {
        UsageWindowKind.fiveHour => l10n.usageWindowFiveHour,
        UsageWindowKind.weekly => l10n.usageWindowWeek,
        UsageWindowKind.monthly => l10n.usageWindowMonth,
        UsageWindowKind.other => _otherTitle(l10n, window),
      };

  /// A window with no known length: the tools window by its id, else the
  /// label (or id) cleaned to title case, never a raw lower-case id.
  static String _otherTitle(AppLocalizations l10n, UsageWindow window) {
    final id = window.id.trim().toLowerCase();
    if (id == 'mcp' || id.startsWith('mcp.') || id.startsWith('mcp_')) {
      return l10n.usageWindowTools;
    }
    final raw = window.label.trim().isEmpty ? window.id : window.label;
    final words = raw
        .replaceAll(RegExp(r'[_.\-]+'), ' ')
        .split(RegExp(r'\s+'))
        .where((w) => w.isNotEmpty)
        .map((w) => toBeginningOfSentenceCase(w.toLowerCase()));
    final cleaned = words.join(' ');
    return cleaned.isEmpty ? l10n.usageWindowOther : cleaned;
  }

  /// "18% left".
  static String left(AppLocalizations l10n, double leftPercent) =>
      l10n.usageLeft(leftPercent.round());

  /// The clock today, "Fri 9:00 AM" within the week, a date after that.
  static String when(BuildContext context, DateTime at, DateTime now) {
    final local = at.toLocal();
    final today = now.toLocal();
    final clock = KitTime.clock(context, local);
    if (local.year == today.year &&
        local.month == today.month &&
        local.day == today.day) {
      return clock;
    }
    final locale = Localizations.localeOf(context).toLanguageTag();
    if (local.isAfter(today) &&
        local.difference(today) < const Duration(days: 6)) {
      return '${DateFormat.E(locale).format(local)} $clock';
    }
    return KitTime.moment(context, local, now: now);
  }

  /// "1 h 25 min", "40 min", "2 d 3 h".
  static String duration(AppLocalizations l10n, Duration value) {
    final minutes = value.inMinutes < 1 ? 1 : value.inMinutes;
    if (minutes < 60) return l10n.usageDurationMinutes(minutes);
    final hours = minutes ~/ 60;
    if (hours < 24) {
      final rest = minutes % 60;
      return rest == 0
          ? l10n.usageDurationHours(hours)
          : l10n.usageDurationHoursMinutes(hours, rest);
    }
    final days = hours ~/ 24;
    final restHours = hours % 24;
    return restHours == 0
        ? l10n.usageDurationDays(days)
        : l10n.usageDurationDaysHours(days, restHours);
  }

  /// "Resets at 14:10, in 1 h 25 min" for a window, or what already
  /// happened when the time has passed.
  static String resetsDetail(
    BuildContext context,
    UsageWindow window,
    DateTime now,
  ) {
    final l10n = usageStrings(context);
    final at = window.resetsAt;
    if (at == null) return '';
    if (window.resetPassed(now)) return l10n.usageResetPassed;
    return l10n.usageResetsIn(
      when(context, at, now),
      duration(l10n, at.difference(now)),
    );
  }

  /// The windows a card draws: the 5-hour and weekly limit together
  /// wherever both are reported, else the first one or two reported.
  static List<UsageWindow> cardWindows(UsageSubscription subscription) {
    final both = [?subscription.fiveHour, ?subscription.weekly];
    if (both.isNotEmpty) return both;
    return subscription.windows.take(2).toList();
  }

  /// The least left across a plan's drawn windows, in percent; 101 when it
  /// draws none, so a plan with no numbers sorts last.
  static double tightestLeft(UsageSubscription subscription) {
    final windows = cardWindows(subscription);
    if (windows.isEmpty) return 101;
    return windows.map((w) => w.leftPercent).reduce((a, b) => a < b ? a : b);
  }

  /// What a plan is ordered by on the Plans page: the least left among its
  /// windows that still have something. A window that ran out refills on its
  /// own, so a used-up plan sits by what its other windows hold; a plan with
  /// nothing at all left sorts first, one with no numbers last.
  static double leastLeftToOrderBy(UsageSubscription subscription) {
    final windows = cardWindows(subscription);
    if (windows.isEmpty) return 101;
    final lefts = [
      for (final w in windows)
        if (w.leftPercent > 0) w.leftPercent,
    ];
    return lefts.isEmpty ? 0 : lefts.reduce((a, b) => a < b ? a : b);
  }

  /// The one small summary at the end of a plan block's header: the
  /// tightest window's "12% left", or "Back at 1:25 PM" for a used-up plan.
  /// Null when the plan draws no window.
  static String? planSummary(
    BuildContext context,
    AppLocalizations l10n,
    UsageSubscription subscription,
    DateTime now,
  ) {
    if (cardWindows(subscription).isEmpty) return null;
    if (subscription.state == SubscriptionState.exhausted) {
      final blocked = [
        for (final w in subscription.windows)
          if (w.usedPercent >= 100 && w.resetsAt != null) w.resetsAt!,
      ]..sort();
      return blocked.isEmpty
          ? l10n.usageLeft(0)
          : l10n.usageBackAt(when(context, blocked.last, now));
    }
    return l10n.usageLeft(tightestLeft(subscription).round());
  }

  /// "12% · 14:10" for one window line of a plan block: what is left and
  /// when it resets. The block's summary says "left"; a screen reader gets
  /// [windowValue].
  static String windowShort(
    BuildContext context,
    UsageWindow window,
    DateTime now,
  ) {
    final at = window.resetsAt;
    final left = '${window.leftPercent.round()}%';
    if (at == null || window.resetPassed(now)) return left;
    return '$left · ${when(context, at, now)}';
  }

  /// "18% left · 14:10" for one window of a card.
  static String windowValue(
    BuildContext context,
    UsageWindow window,
    DateTime now,
  ) {
    final l10n = usageStrings(context);
    final at = window.resetsAt;
    final left = l10n.usageLeft(window.leftPercent.round());
    if (at == null || window.resetPassed(now)) return left;
    return '$left · ${when(context, at, now)}';
  }

  /// A plan with numbers (or one that reads and reports no limits) is a
  /// card; the rest need setup.
  static bool hasNumbers(UsageSubscription s) =>
      s.windows.isNotEmpty || s.state == SubscriptionState.noLimits;

  /// The one muted line of a row under "Needs setup".
  static String setupLine(
    AppLocalizations l10n,
    UsageSnapshot snapshot,
    UsageSubscription s,
  ) {
    final several = snapshot.hasSeveralConnections;
    final where = s.fixConnection ?? s.readFrom;
    final named = several && where != null ? connection(l10n, where) : null;
    return switch (s.state) {
      SubscriptionState.cantReadYet =>
        named == null
            ? l10n.usageSetupSignedIn
            : l10n.usageSetupSignedInOn(named),
      SubscriptionState.signInExpired =>
        named == null ? l10n.usageSetupRanOut : l10n.usageSetupRanOutOn(named),
      SubscriptionState.notSignedIn =>
        isKeyProvider(s.providerId)
            ? l10n.usageNoKey
            : l10n.usageNotSignedInOne,
      _ => l10n.usageSetupCouldntRead,
    };
  }

  /// The sentence under a card's name when the state, not the numbers, is
  /// the news. Null when the bars say it all.
  static String? stateMessage(
    AppLocalizations l10n,
    UsageSnapshot snapshot,
    UsageSubscription subscription,
    DateTime now,
  ) {
    final several = snapshot.hasSeveralConnections;
    final where = subscription.fixConnection ?? subscription.readFrom;
    final provider = subscriptionName(l10n, subscription);
    switch (subscription.state) {
      case SubscriptionState.cantReadYet:
        return several && where != null
            ? l10n.usageCantReadBody(connection(l10n, where))
            : l10n.usageCantReadBodyOne;
      case SubscriptionState.signInExpired:
        return several && where != null
            ? l10n.usageSignInRanOut(provider, connection(l10n, where))
            : l10n.usageSignInRanOutOne(provider);
      case SubscriptionState.notSignedIn:
        if (isKeyProvider(subscription.providerId)) return l10n.usageNoKey;
        return several
            ? l10n.usageNotSignedInAnywhere
            : l10n.usageNotSignedInOne;
      case SubscriptionState.readerBroken:
        return l10n.usageReaderBroken(provider);
      case SubscriptionState.noLimits:
        return l10n.usageNoLimits;
      case SubscriptionState.offlineStale:
        final fetched = subscription.fetchedAt;
        final age = fetched == null
            ? null
            : duration(l10n, now.difference(fetched));
        final from = subscription.readFrom;
        if (age == null) return l10n.usagePillOffline;
        return several && from != null
            ? l10n.usageOfflineStale(connection(l10n, from), age)
            : l10n.usageOfflineStaleOne(age);
      case SubscriptionState.ok:
      case SubscriptionState.nearLimit:
      case SubscriptionState.exhausted:
      case SubscriptionState.apiKeyOnly:
        return null;
    }
  }

  /// The one reset line of a card: "5-hour resets 14:10 · week resets Fri",
  /// "Out until 14:10", or what happened while the app was closed.
  static String? resetLine(
    BuildContext context,
    UsageSubscription subscription,
    DateTime now,
  ) {
    final l10n = usageStrings(context);
    final windows = cardWindows(subscription);
    if (windows.isEmpty) return null;
    if (subscription.state == SubscriptionState.exhausted) {
      final blocked = [
        for (final w in windows)
          if (w.usedPercent >= 100 && w.resetsAt != null) w.resetsAt!,
      ]..sort();
      if (blocked.isNotEmpty) {
        return l10n.usageOutUntil(when(context, blocked.last, now));
      }
    }
    if (subscription.resetSinceLastReading) {
      final passed = [
        for (final w in subscription.windows)
          if (w.resetPassed(now)) w.resetsAt!,
      ]..sort();
      if (passed.isNotEmpty) {
        return l10n.usageResetSince(when(context, passed.first, now));
      }
    }
    final parts = <String>[];
    for (final w in windows) {
      final at = w.resetsAt;
      if (at == null || w.resetPassed(now)) continue;
      final time = when(context, at, now);
      parts.add(switch (w.kind) {
        UsageWindowKind.fiveHour => l10n.usageFiveHourResets(time),
        UsageWindowKind.weekly => l10n.usageWeekResets(time),
        _ => l10n.usageResetsAt(time),
      });
    }
    if (parts.isEmpty) return null;
    return parts.join(' · ');
  }

  /// "Checked 20 min ago" when the provider is limiting checks (the check is
  /// newer than the numbers).
  static String? checkedLine(
    AppLocalizations l10n,
    UsageSubscription subscription,
    DateTime now,
  ) {
    final fetched = subscription.fetchedAt;
    final checked = subscription.checkedAt;
    if (fetched == null || checked == null) return null;
    if (checked.difference(fetched) < const Duration(minutes: 10)) return null;
    return l10n.usageCheckedAgo(duration(l10n, now.difference(checked)));
  }
}
