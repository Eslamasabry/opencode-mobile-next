import 'dart:async';
import 'dart:convert';

import '../domain/provider_usage.dart';

/// Runs one shell [script] inside the app's built-in Linux and answers its exit
/// code and output (BuiltinLinux.run in production).
typedef AntigravityLinuxRunner =
    Future<({int exitCode, String output})> Function(
      String script, {
      Duration timeout,
    });

/// Reads quota for Google Antigravity by asking the Antigravity CLI itself
/// (`agy -p /usage --output-format json`) inside built-in Linux, the same
/// command a person runs in a terminal. The app never reads the CLI's
/// credential file and never calls Google's endpoints on the CLI's behalf: the
/// CLI stays the only client that uses its sign-in.
class AntigravityQuotaClient {
  AntigravityQuotaClient({this.runner, DateTime Function()? clock})
    : _clock = clock ?? DateTime.now;

  /// Null when built-in Linux is not available: then there is nothing to read.
  final AntigravityLinuxRunner? runner;
  final DateTime Function() _clock;

  List<ProviderUsageReading>? _cachedReadings;

  /// The last good readings (shown while a new read is under way).
  List<ProviderUsageReading>? get cachedReadings => _cachedReadings;

  /// Where the CLI can live inside built-in Linux: the profile's own home
  /// first (agents installed per profile), then the shared `oc` home.
  static List<String> candidateCliPaths({String? profileId}) => [
    if (profileId != null && _safeId.hasMatch(profileId))
      '/home/oc/.oc-profiles/$profileId/.local/bin/agy',
    '/home/oc/.local/bin/agy',
  ];

  static final RegExp _safeId = RegExp(r'^[A-Za-z0-9_-]{1,64}$');

  /// The script run inside built-in Linux: the first CLI that exists runs
  /// with its own HOME, so it finds its own sign-in; 127 when none is
  /// installed. Paths are fixed strings (the profile id is checked above).
  static String cliScript({String? profileId}) {
    final candidates = candidateCliPaths(profileId: profileId).join(' ');
    final pId = (profileId != null && _safeId.hasMatch(profileId))
        ? profileId
        : '';
    return 'for b in $candidates; do '
        'if [ -x "\$b" ]; then '
        'h="\${b%/.local/bin/agy}"; '
        'if [ -n "$pId" ] && [ -d "/home/oc/.oc-profiles/$pId" ]; then h="/home/oc/.oc-profiles/$pId"; '
        'elif [ ! -f "\$h/.gemini/antigravity-cli/antigravity-oauth-token" ]; then '
        'for o in /home/oc/.oc-profiles/*/.gemini/antigravity-cli/antigravity-oauth-token; do '
        'if [ -f "\$o" ]; then h="\${o%/.gemini/antigravity-cli/antigravity-oauth-token}"; break; fi; '
        'done; fi; '
        'export HOME="\$h"; '
        'f="\$HOME/.gemini/antigravity-cli/antigravity-oauth-token"; '
        'e=\$(grep -o \'"id_token_email"[[:space:]]*:[[:space:]]*"[^"]*"\' "\$f" 2>/dev/null | head -n 1 | cut -d\'"\' -f4); '
        'if [ -z "\$e" ]; then e=\$(grep -o \'"email"[[:space:]]*:[[:space:]]*"[^"]*"\' "\$f" 2>/dev/null | head -n 1 | cut -d\'"\' -f4); fi; '
        'case "\$e" in *[!A-Za-z0-9.@_+-]*) e="";; esac; '
        'if [ "\${#e}" -gt $maxAccountLength ]; then e=""; fi; '
        'if [ -n "\$e" ]; then echo "ACCOUNT_EMAIL:\$e"; fi; '
        'exec "\$b" -p /usage --output-format json; '
        'fi; '
        'done; exit 127';
  }

  static const int maxAccountLength = 64;

  static final RegExp _accountLine = RegExp(
    r'^ACCOUNT_EMAIL:(.*)$',
    multiLine: true,
  );
  static final RegExp _emailShape = RegExp(
    r'^[A-Za-z0-9._%+-]{1,64}@[A-Za-z0-9-]+(\.[A-Za-z0-9-]+)+$',
  );

  /// The account email printed by the probe script as `ACCOUNT_EMAIL:<email>`,
  /// or null. Only a plain email-shaped value of at most 64 characters is
  /// accepted; anything else in the output is ignored.
  static String? parseProbeAccount(String output) {
    for (final match in _accountLine.allMatches(output)) {
      final value = (match.group(1) ?? '').trim();
      if (value.length <= maxAccountLength && _emailShape.hasMatch(value)) {
        return value;
      }
    }
    return null;
  }

  /// The CLI's quota readings, or an empty list when the CLI is missing, not
  /// signed in, too slow, or answers something unreadable. Never invents
  /// numbers: an empty list means "this provider reports no limits now".
  Future<List<ProviderUsageReading>> fetchAllUsage({
    String? profileId,
    DateTime? now,
  }) async {
    final run = runner;
    if (run == null) return const [];
    final currentNow = (now ?? _clock()).toUtc();
    try {
      final res = await run(
        cliScript(profileId: profileId),
        timeout: const Duration(seconds: 20),
      );
      if (res.exitCode != 0) return const [];
      final body = _jsonObjectIn(res.output);
      if (body == null) return const [];
      final accountEmail = parseProbeAccount(res.output);
      final readings = parseCliJson(
        body,
        currentNow: currentNow,
        accountEmail: accountEmail,
      );
      if (readings.isNotEmpty) _cachedReadings = readings;
      return readings;
    } catch (_) {
      return const [];
    }
  }

  /// The JSON object in the CLI's output (it may print a banner first).
  static Map<String, dynamic>? _jsonObjectIn(String output) {
    final start = output.indexOf('{');
    final end = output.lastIndexOf('}');
    if (start < 0 || end <= start) return null;
    try {
      final parsed = jsonDecode(output.substring(start, end + 1));
      return parsed is Map<String, dynamic> ? parsed : null;
    } catch (_) {
      return null;
    }
  }

  static List<ProviderUsageReading> parseCliJson(
    Map<String, dynamic> data, {
    DateTime? currentNow,
    String? planLabel,
    String? accountEmail,
  }) {
    final now = currentNow ?? DateTime.now().toUtc();
    final readings = <ProviderUsageReading>[];
    final command = data['command'];
    if (command is! Map) return readings;
    final cmdData = command['data'];
    if (cmdData is! Map) return readings;
    final groups = cmdData['groups'];
    if (groups is! List) return readings;

    final resolvedEmail =
        accountEmail ??
        cleanUsageText(
          cmdData['account'] ??
              cmdData['email'] ??
              cmdData['user'] ??
              command['account'] ??
              command['email'] ??
              data['account'] ??
              data['email'],
          64,
        );

    for (final group in groups) {
      if (group is! Map) continue;
      final groupName = (group['name'] as String?)?.trim() ?? '';
      final isGemini = groupName.toLowerCase().contains('gemini');
      final isClaudeOrGpt =
          groupName.toLowerCase().contains('claude') ||
          groupName.toLowerCase().contains('gpt') ||
          groupName.toLowerCase().contains('3p');

      final String providerId;
      final String displayName;
      if (isGemini) {
        providerId = 'antigravity';
        displayName = groups.length > 1
            ? 'Antigravity (Gemini Models)'
            : 'Antigravity';
      } else if (isClaudeOrGpt) {
        providerId = 'antigravity_claude';
        displayName = 'Antigravity (Claude and GPT models)';
      } else {
        final slug = groupName.toLowerCase().replaceAll(
          RegExp(r'[^a-z0-9]'),
          '_',
        );
        providerId = 'antigravity_$slug';
        displayName = 'Antigravity ($groupName)';
      }

      final buckets = group['buckets'];
      final windows = <ProviderUsageWindowReading>[];
      if (buckets is List) {
        for (final b in buckets) {
          if (b is! Map) continue;
          final win = b['window'] as String?;
          final name = b['name'] as String?;
          final disabled = b['disabled'] == true;
          final fraction = b['remaining_fraction'] ?? b['remainingFraction'];
          if (fraction is! num || !fraction.isFinite) continue;

          // If a bucket is disabled (e.g. 5h limit disabled because weekly limit was reached),
          // capacity is effectively 0%.
          final remainingPercent = disabled
              ? 0
              : (fraction * 100).clamp(0, 100).round();
          final resetTimeStr = (b['reset_time'] ?? b['resetTime']) as String?;
          final resetsAt = resetTimeStr != null
              ? DateTime.tryParse(resetTimeStr)?.toUtc()
              : null;

          final int? durationSeconds;
          final String windowId;
          final String label;

          if (win == '5h') {
            durationSeconds = 18000;
            windowId = '5h';
            label = '5-hour';
          } else if (win == 'weekly') {
            durationSeconds = 604800;
            windowId = 'weekly';
            label = 'Weekly';
          } else {
            durationSeconds = null;
            windowId = b['id'] is String ? b['id'] as String : 'window';
            label = name ?? 'Quota';
          }

          windows.add(
            ProviderUsageWindowReading(
              windowId: windowId,
              durationSeconds: durationSeconds,
              label: label,
              remainingPercent: remainingPercent,
              resetsAt: resetsAt,
            ),
          );
        }
      }

      windows.sort((a, b) {
        final aPriority = a.durationSeconds == 18000
            ? 0
            : (a.durationSeconds == 604800 ? 1 : 2);
        final bPriority = b.durationSeconds == 18000
            ? 0
            : (b.durationSeconds == 604800 ? 1 : 2);
        return aPriority.compareTo(bPriority);
      });

      if (windows.isNotEmpty) {
        readings.add(
          ProviderUsageReading(
            providerId: providerId,
            displayName: displayName,
            fetchedAt: now,
            windows: windows,
            planLabel: planLabel ?? 'Google AI Pro',
            accountRef: resolvedEmail,
            needsSignIn: false,
          ),
        );
      }
    }

    readings.sort((a, b) {
      final aPriority = a.providerId == 'antigravity' ? 0 : 1;
      final bPriority = b.providerId == 'antigravity' ? 0 : 1;
      return aPriority.compareTo(bPriority);
    });

    return readings;
  }

  /// Parses HTTP API responses into multiple distinct provider readings.
}
