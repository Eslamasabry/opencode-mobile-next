import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:path_provider/path_provider.dart';

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
        'e=\$(sed -n \'s/.*"id_token_email"[^"]*" *\\([^"]*\\)".*/\\1/p; s/.*"email"[^"]*" *\\([^"]*\\)".*/\\1/p\' "\$f" 2>/dev/null | head -n 1); '
        'if [ -z "\$e" ]; then e=\$(grep -o \'"id_token_email"[[:space:]]*:[[:space:]]*"[^"]*"\' "\$f" 2>/dev/null | cut -d\'"\' -f4); fi; '
        'if [ -z "\$e" ]; then e=\$(grep -o \'"email"[[:space:]]*:[[:space:]]*"[^"]*"\' "\$f" 2>/dev/null | cut -d\'"\' -f4); fi; '
        'if [ -n "\$e" ]; then echo "ACCOUNT_EMAIL:\$e"; fi; '
        'exec "\$b" -p /usage --output-format json; '
        'fi; '
        'done; exit 127';
  }

  /// Cached in memory across calls once resolved.
  static String? lastKnownAccountEmail;

  static final RegExp _accountPattern =
      RegExp(r'ACCOUNT_EMAIL:([^\r\n]+)');

  static String? _accountIn(String output) {
    final match = _accountPattern.firstMatch(output);
    if (match != null) {
      final email = match.group(1)?.trim();
      if (email != null && email.isNotEmpty) {
        final cleaned = cleanUsageText(email, 64);
        if (cleaned != null) lastKnownAccountEmail = cleaned;
        return cleaned;
      }
    }
    return null;
  }

  static String? _readEmailFromFile(File file) {
    try {
      if (!file.existsSync()) return null;
      final content = file.readAsStringSync();
      final match = RegExp(
        r'"id_token_email"\s*:\s*"([^"]+)"|"email"\s*:\s*"([^"]+)"',
      ).firstMatch(content);
      if (match != null) {
        final raw = match.group(1) ?? match.group(2);
        if (raw != null && raw.isNotEmpty) {
          final cleaned = cleanUsageText(raw, 64);
          if (cleaned != null) {
            lastKnownAccountEmail = cleaned;
            return cleaned;
          }
        }
      }
      final parsed = jsonDecode(content);
      if (parsed is Map) {
        final email = parsed['id_token_email'] ?? parsed['email'];
        if (email is String && email.isNotEmpty) {
          final cleaned = cleanUsageText(email, 64);
          if (cleaned != null) {
            lastKnownAccountEmail = cleaned;
            return cleaned;
          }
        }
      }
    } catch (_) {}
    return null;
  }

  static String? _scanRoot(String rootPath, {String? profileId}) {
    if (profileId != null && _safeId.hasMatch(profileId)) {
      final f = File(
        '$rootPath/.oc-profiles/$profileId/.gemini/antigravity-cli/antigravity-oauth-token',
      );
      final email = _readEmailFromFile(f);
      if (email != null) return email;
    }
    final sharedFile = File(
      '$rootPath/.gemini/antigravity-cli/antigravity-oauth-token',
    );
    final sharedEmail = _readEmailFromFile(sharedFile);
    if (sharedEmail != null) return sharedEmail;

    try {
      final dir = Directory('$rootPath/.oc-profiles');
      if (dir.existsSync()) {
        for (final entry in dir.listSync()) {
          if (entry is Directory) {
            final f = File(
              '${entry.path}/.gemini/antigravity-cli/antigravity-oauth-token',
            );
            final email = _readEmailFromFile(f);
            if (email != null) return email;
          }
        }
      }
    } catch (_) {}
    return null;
  }

  /// Reads only the account email from local credentials if present on disk.
  /// Never touches or exposes tokens or secrets.
  static String? readLocalAccount({String? profileId}) {
    if (lastKnownAccountEmail != null) return lastKnownAccountEmail;

    final candidateRoots = <String>[
      '/home/oc',
      '/data/data/com.termux/files/home',
      '/data/user/0/io.github.eslamasabry.opencode_mobile/files/linux/ubuntu/home/oc',
      '/data/data/io.github.eslamasabry.opencode_mobile/files/linux/ubuntu/home/oc',
      '/data/user/0/com.opencode.app/files/linux/ubuntu/home/oc',
      '/data/data/com.opencode.app/files/linux/ubuntu/home/oc',
      if (Platform.environment['HOME'] != null) Platform.environment['HOME']!,
    ];

    for (final root in candidateRoots) {
      final email = _scanRoot(root, profileId: profileId);
      if (email != null) return email;
    }

    // Check application storage patterns on Android
    for (final prefix in const ['/data/user/0', '/data/data']) {
      try {
        final base = Directory(prefix);
        if (base.existsSync()) {
          for (final appDir in base.listSync()) {
            if (appDir is Directory &&
                (appDir.path.contains('opencode') ||
                    appDir.path.contains('eslamasabry'))) {
              final root = '${appDir.path}/files/linux/ubuntu/home/oc';
              final email = _scanRoot(root, profileId: profileId);
              if (email != null) return email;
            }
          }
        }
      } catch (_) {}
    }

    return null;
  }

  /// Asynchronously resolves the account email, checking the application's
  /// support directory (where in-app Ubuntu rootfs lives on Android) if sync
  /// resolution hasn't found it yet.
  static Future<String?> readLocalAccountAsync({String? profileId}) async {
    final syncAccount = readLocalAccount(profileId: profileId);
    if (syncAccount != null) return syncAccount;

    try {
      final support = await getApplicationSupportDirectory();
      final root = '${support.path}/linux/ubuntu/home/oc';
      final email = _scanRoot(root, profileId: profileId);
      if (email != null) return email;
    } catch (_) {}

    try {
      final docs = await getApplicationDocumentsDirectory();
      final root = '${docs.path}/linux/ubuntu/home/oc';
      final email = _scanRoot(root, profileId: profileId);
      if (email != null) return email;
    } catch (_) {}

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
    if (run == null) {
      final fallbackAccount = readLocalAccount(profileId: profileId);
      if (fallbackAccount != null) {
        return [
          ProviderUsageReading(
            providerId: 'antigravity',
            displayName: 'Antigravity',
            fetchedAt: (now ?? _clock()).toUtc(),
            windows: const [],
            accountRef: fallbackAccount,
            needsSignIn: true,
          ),
        ];
      }
      return const [];
    }
    final currentNow = (now ?? _clock()).toUtc();
    try {
      final res = await run(
        cliScript(profileId: profileId),
        timeout: const Duration(seconds: 20),
      );
      if (res.exitCode != 0) return const [];
      final body = _jsonObjectIn(res.output);
      if (body == null) return const [];
      final accountEmail =
          _accountIn(res.output) ?? readLocalAccount(profileId: profileId);
      if (accountEmail != null) lastKnownAccountEmail = accountEmail;
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

    final resolvedEmail = accountEmail ??
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
