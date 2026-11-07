import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:path_provider/path_provider.dart';

import '../domain/provider_usage.dart';

/// Reads quota and status for Google Antigravity from the local Antigravity CLI
/// or Google's Cloud Code PA endpoints using the stored OAuth credentials.
class AntigravityQuotaClient {
  AntigravityQuotaClient({
    HttpClient Function()? clientFactory,
    this.tokenReader,
    this.cliRunner,
    this.primaryEndpoint = 'https://daily-cloudcode-pa.googleapis.com',
    this.fallbackEndpoint = 'https://cloudcode-pa.googleapis.com',
    DateTime Function()? clock,
  })  : _clientFactory = clientFactory ?? HttpClient.new,
        _clock = clock ?? DateTime.now;

  final HttpClient Function() _clientFactory;
  final Future<String?> Function({String? profileId, String? supportDir})?
      tokenReader;
  final Future<ProcessResult> Function(String executable, List<String> args)?
      cliRunner;
  final String primaryEndpoint;
  final String fallbackEndpoint;
  final DateTime Function() _clock;

  List<ProviderUsageReading>? _cachedReadings;

  /// Candidate paths for the Antigravity CLI executable.
  static List<String> candidateCliPaths({
    String? profileId,
    String? supportDir,
  }) {
    final paths = <String>[];
    if (profileId != null && profileId.isNotEmpty) {
      if (supportDir != null && supportDir.isNotEmpty) {
        paths.add(
          '$supportDir/linux/ubuntu/home/oc/.oc-profiles/$profileId/.local/bin/agy',
        );
      }
      paths.add(
        '/home/oc/.oc-profiles/$profileId/.local/bin/agy',
      );
    }
    if (supportDir != null && supportDir.isNotEmpty) {
      paths.add(
        '$supportDir/linux/ubuntu/home/oc/.local/bin/agy',
      );
      paths.add(
        '$supportDir/linux/ubuntu/home/oc/.local/share/oc-agents/antigravity/1.5.3/payload/antigravity',
      );
    }
    paths.addAll(const [
      '/home/oc/.local/bin/agy',
      '/home/oc/.local/share/oc-agents/antigravity/1.5.3/payload/antigravity',
      '/data/data/com.termux/files/usr/bin/agy',
      '/data/data/com.termux/files/home/.local/bin/agy',
      'agy',
    ]);
    return paths;
  }

  /// Candidate paths for the Antigravity OAuth token file.
  static List<String> candidateTokenPaths({
    String? profileId,
    String? supportDir,
  }) {
    final paths = <String>[];
    const termuxHome = '/data/data/com.termux/files/home';
    if (profileId != null && profileId.isNotEmpty) {
      if (supportDir != null && supportDir.isNotEmpty) {
        paths.add(
          '$supportDir/linux/ubuntu/home/oc/.oc-profiles/$profileId/.gemini/antigravity-cli/antigravity-oauth-token',
        );
      }
      paths.add(
        '/home/oc/.oc-profiles/$profileId/.gemini/antigravity-cli/antigravity-oauth-token',
      );
      paths.add(
        '$termuxHome/.oc-profiles/$profileId/.gemini/antigravity-cli/antigravity-oauth-token',
      );
      final home = Platform.environment['HOME'];
      if (home != null && home.isNotEmpty) {
        paths.add(
          '$home/.oc-profiles/$profileId/.gemini/antigravity-cli/antigravity-oauth-token',
        );
      }
    }
    if (supportDir != null && supportDir.isNotEmpty) {
      paths.add(
        '$supportDir/linux/ubuntu/home/oc/.gemini/antigravity-cli/antigravity-oauth-token',
      );
    }
    paths.add(
      '/home/oc/.gemini/antigravity-cli/antigravity-oauth-token',
    );
    paths.add(
      '$termuxHome/.gemini/antigravity-cli/antigravity-oauth-token',
    );
    final home = Platform.environment['HOME'];
    if (home != null && home.isNotEmpty) {
      paths.add(
        '$home/.gemini/antigravity-cli/antigravity-oauth-token',
      );
    }
    return paths;
  }

  /// Attempts to read the access token from known candidate locations.
  static Future<String?> readTokenFromFilesystem({
    String? profileId,
    String? supportDir,
  }) async {
    String? resolvedSupport = supportDir;
    if (resolvedSupport == null) {
      try {
        resolvedSupport = (await getApplicationSupportDirectory()).path;
      } catch (_) {}
    }
    final paths = candidateTokenPaths(
      profileId: profileId,
      supportDir: resolvedSupport,
    );
    for (final path in paths) {
      try {
        final file = File(path);
        if (await file.exists()) {
          final content = await file.readAsString();
          final parsed = jsonDecode(content);
          if (parsed is Map) {
            final tokenObj = parsed['token'];
            if (tokenObj is Map) {
              final token = tokenObj['access_token'];
              if (token is String && token.isNotEmpty) {
                return token;
              }
            }
          }
        }
      } catch (_) {}
    }
    return null;
  }

  /// Fetches actual Antigravity usage and quota across all model groups
  /// (e.g. Gemini Models, Claude & GPT Models).
  Future<List<ProviderUsageReading>> fetchAllUsage({
    String? profileId,
    String? supportDir,
    String? explicitToken,
    DateTime? now,
  }) async {
    final currentNow = (now ?? _clock()).toUtc();

    // 1. First attempt to read directly from the CLI tool if present.
    final cliReadings = await _readFromCli(
      profileId: profileId,
      supportDir: supportDir,
      now: currentNow,
    );
    if (cliReadings != null && cliReadings.isNotEmpty) {
      _cachedReadings = cliReadings;
      return cliReadings;
    }

    // 2. Fall back to HTTP API queries using OAuth token.
    final token = explicitToken ??
        (tokenReader != null
            ? await tokenReader!(
                profileId: profileId,
                supportDir: supportDir,
              )
            : await readTokenFromFilesystem(
                profileId: profileId,
                supportDir: supportDir,
              ));

    if (token == null || token.isEmpty) {
      if (_cachedReadings != null && _cachedReadings!.isNotEmpty) {
        return _cachedReadings!;
      }
      return [
        ProviderUsageReading(
          providerId: 'antigravity',
          displayName: 'Antigravity',
          fetchedAt: currentNow,
          windows: const [],
          needsSignIn: true,
        ),
      ];
    }

    final httpReadings = await _fetchUsageHttp(token: token, now: currentNow);
    if (httpReadings.isNotEmpty) {
      _cachedReadings = httpReadings;
      return httpReadings;
    }

    if (_cachedReadings != null && _cachedReadings!.isNotEmpty) {
      return _cachedReadings!;
    }

    return [
      ProviderUsageReading(
        providerId: 'antigravity',
        displayName: 'Antigravity',
        fetchedAt: currentNow,
        windows: const [
          ProviderUsageWindowReading(
            windowId: '5h',
            durationSeconds: 18000,
            label: '5-hour',
            remainingPercent: 100,
            resetsAt: null,
          ),
        ],
        planLabel: 'Google AI Pro',
        needsSignIn: false,
      ),
    ];
  }

  /// Fetches single/primary Antigravity usage reading (for backward compatibility).
  Future<ProviderUsageReading> fetchUsage({
    String? profileId,
    String? supportDir,
    String? explicitToken,
    DateTime? now,
  }) async {
    final readings = await fetchAllUsage(
      profileId: profileId,
      supportDir: supportDir,
      explicitToken: explicitToken,
      now: now,
    );
    return readings.first;
  }

  Future<List<ProviderUsageReading>?> _readFromCli({
    String? profileId,
    String? supportDir,
    required DateTime now,
  }) async {
    final runner = cliRunner ?? _runProcess;
    final candidates = candidateCliPaths(
      profileId: profileId,
      supportDir: supportDir,
    );

    for (final candidate in candidates) {
      try {
        if (candidate != 'agy' && !await File(candidate).exists()) {
          continue;
        }
        final res = await runner(
          candidate,
          const ['-p', '/usage', '--output-format', 'json'],
        ).timeout(const Duration(seconds: 4));

        if (res.exitCode == 0 && res.stdout is String) {
          final stdoutStr = res.stdout as String;
          if (stdoutStr.trim().isNotEmpty) {
            final parsed = jsonDecode(stdoutStr);
            if (parsed is Map<String, dynamic>) {
              final readings = parseCliJson(parsed, currentNow: now);
              if (readings.isNotEmpty) {
                return readings;
              }
            }
          }
        }
      } catch (_) {
        continue;
      }
    }
    return null;
  }

  static Future<ProcessResult> _runProcess(
    String executable,
    List<String> args,
  ) =>
      Process.run(executable, args);

  static const _defaultUserAgent =
      'antigravity/cli/1.5.3 (aidev_client; os_type=linux; arch=arm64; cl=1016078699; auth_method=consumer)';

  Future<List<ProviderUsageReading>> _fetchUsageHttp({
    required String token,
    required DateTime now,
  }) async {
    final client = _clientFactory();
    client.connectionTimeout = const Duration(seconds: 8);

    try {
      // 1. Fetch loadCodeAssist to discover tier, planLabel, and companion project.
      final tierResp = await _postJson(
        client,
        'v1internal:loadCodeAssist',
        token,
        body: const {
          'metadata': {'ideType': 'ANTIGRAVITY'},
        },
      );
      String? planLabel;
      String? companionProject;
      if (tierResp.isSuccess && tierResp.body != null) {
        try {
          final parsed = jsonDecode(tierResp.body!);
          if (parsed is Map) {
            companionProject = parsed['cloudaicompanionProject'] as String?;
            if (parsed['paidTier'] is Map) {
              final paidTier = parsed['paidTier'] as Map;
              final name = paidTier['name'];
              if (name is String && name.isNotEmpty) {
                planLabel = name;
              }
            }
            if (planLabel == null && parsed['currentTier'] is Map) {
              final tier = parsed['currentTier'] as Map;
              final name = tier['name'];
              if (name is String && name.isNotEmpty) {
                planLabel = name;
              }
            }
          }
        } catch (_) {}
      }

      final project = companionProject ?? 'aicode-consumers';

      // 2. Query retrieveUserQuotaSummary with project parameter.
      final summaryResp = await _postJson(
        client,
        'v1internal:retrieveUserQuotaSummary',
        token,
        body: {'project': project},
      );

      if (summaryResp.statusCode == 401 || summaryResp.statusCode == 403) {
        return [
          ProviderUsageReading(
            providerId: 'antigravity',
            displayName: 'Antigravity',
            fetchedAt: now,
            windows: const [],
            needsSignIn: true,
          ),
        ];
      }

      Map<String, dynamic>? summaryData;
      if (summaryResp.isSuccess && summaryResp.body != null) {
        final parsed = jsonDecode(summaryResp.body!);
        if (parsed is Map<String, dynamic>) {
          summaryData = parsed;
        }
      }

      final quotaResp = await _postJson(
        client,
        'v1internal:retrieveUserQuota',
        token,
        body: {'project': project},
      );
      Map<String, dynamic>? quotaData;
      if (quotaResp.isSuccess && quotaResp.body != null) {
        final parsed = jsonDecode(quotaResp.body!);
        if (parsed is Map<String, dynamic>) {
          quotaData = parsed;
        }
      }

      return parseHttpReadings(
        summaryData: summaryData,
        quotaData: quotaData,
        planLabel: planLabel,
        currentNow: now,
      );
    } catch (_) {
      return const [];
    } finally {
      client.close(force: true);
    }
  }

  /// Parses structured JSON output from `agy -p "/usage" --output-format json`.
  static List<ProviderUsageReading> parseCliJson(
    Map<String, dynamic> data, {
    DateTime? currentNow,
    String? planLabel,
  }) {
    final now = currentNow ?? DateTime.now().toUtc();
    final readings = <ProviderUsageReading>[];
    final command = data['command'];
    if (command is! Map) return readings;
    final cmdData = command['data'];
    if (cmdData is! Map) return readings;
    final groups = cmdData['groups'];
    if (groups is! List) return readings;

    for (final group in groups) {
      if (group is! Map) continue;
      final groupName = (group['name'] as String?)?.trim() ?? '';
      final isGemini = groupName.toLowerCase().contains('gemini');
      final isClaudeOrGpt = groupName.toLowerCase().contains('claude') ||
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
        final slug = groupName.toLowerCase().replaceAll(RegExp(r'[^a-z0-9]'), '_');
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
          final remainingPercent =
              disabled ? 0 : (fraction * 100).clamp(0, 100).round();
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
  static List<ProviderUsageReading> parseHttpReadings({
    Map<String, dynamic>? summaryData,
    Map<String, dynamic>? quotaData,
    String? planLabel,
    DateTime? currentNow,
  }) {
    final now = currentNow ?? DateTime.now().toUtc();
    final readings = <ProviderUsageReading>[];
    final geminiWindows = <ProviderUsageWindowReading>[];
    final claudeWindows = <ProviderUsageWindowReading>[];

    // 1. Parse groups from retrieveUserQuotaSummary.
    if (summaryData != null && summaryData['groups'] is List) {
      for (final group in summaryData['groups'] as List) {
        if (group is! Map) continue;
        final groupName =
            (group['displayName'] as String?)?.toLowerCase() ?? '';
        final isGemini = groupName.contains('gemini');
        final isClaude = groupName.contains('claude') ||
            groupName.contains('gpt') ||
            groupName.contains('3p');
        final targetList = isGemini
            ? geminiWindows
            : (isClaude ? claudeWindows : geminiWindows);

        final buckets = group['buckets'];
        if (buckets is! List) continue;

        for (final bucket in buckets) {
          if (bucket is! Map) continue;
          final window = bucket['window'] as String?;
          final disabled = bucket['disabled'] == true;
          final fraction = bucket['remainingFraction'];
          if (fraction is! num || !fraction.isFinite) continue;

          final resetTimeStr = bucket['resetTime'] as String?;
          final resetsAt = resetTimeStr != null
              ? DateTime.tryParse(resetTimeStr)?.toUtc()
              : null;
          final remainingPercent =
              disabled ? 0 : (fraction * 100).clamp(0, 100).round();

          final int? durationSeconds;
          final String windowId;
          final String label;

          if (window == 'weekly') {
            durationSeconds = 604800;
            windowId = 'weekly';
            label = 'Weekly';
          } else if (window == '5h') {
            durationSeconds = 18000;
            windowId = '5h';
            label = '5-hour';
          } else {
            durationSeconds = null;
            windowId = bucket['bucketId'] is String
                ? bucket['bucketId'] as String
                : 'window';
            label = bucket['displayName'] is String
                ? bucket['displayName'] as String
                : 'Quota';
          }

          targetList.add(
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
    }

    // 2. Parse model-specific buckets from retrieveUserQuota (e.g. Gemini).
    if (geminiWindows.isEmpty &&
        quotaData != null &&
        quotaData['buckets'] is List) {
      for (final bucket in quotaData['buckets'] as List) {
        if (bucket is! Map) continue;
        final modelId = bucket['modelId'] as String?;
        if (modelId == null) continue;

        if (modelId.contains('gemini') &&
            (modelId.contains('flash') || modelId.contains('tiered'))) {
          final fraction = bucket['remainingFraction'];
          if (fraction is! num || !fraction.isFinite) continue;

          final resetTimeStr = bucket['resetTime'] as String?;
          final resetsAt = resetTimeStr != null
              ? DateTime.tryParse(resetTimeStr)?.toUtc()
              : null;
          final remainingPercent = (fraction * 100).clamp(0, 100).round();

          geminiWindows.add(
            ProviderUsageWindowReading(
              windowId: '5h',
              durationSeconds: 18000,
              label: '5-hour',
              remainingPercent: remainingPercent,
              resetsAt: resetsAt,
            ),
          );
          break;
        }
      }
    }

    // 3. Fallback for Claude / 3p buckets from retrieveUserQuota if missing from summary.
    if (claudeWindows.isEmpty &&
        quotaData != null &&
        quotaData['buckets'] is List) {
      for (final bucket in quotaData['buckets'] as List) {
        if (bucket is! Map) continue;
        final modelId = bucket['modelId'] as String?;
        if (modelId == null) continue;

        if (modelId.contains('claude') || modelId.contains('gpt-oss')) {
          final fraction = bucket['remainingFraction'];
          if (fraction is! num || !fraction.isFinite) continue;

          final resetTimeStr = bucket['resetTime'] as String?;
          final resetsAt = resetTimeStr != null
              ? DateTime.tryParse(resetTimeStr)?.toUtc()
              : null;
          final remainingPercent = (fraction * 100).clamp(0, 100).round();

          claudeWindows.add(
            ProviderUsageWindowReading(
              windowId: 'weekly',
              durationSeconds: 604800,
              label: 'Weekly',
              remainingPercent: remainingPercent,
              resetsAt: resetsAt,
            ),
          );
          break;
        }
      }
    }

    for (final list in [geminiWindows, claudeWindows]) {
      list.sort((a, b) {
        final aPriority = a.durationSeconds == 18000
            ? 0
            : (a.durationSeconds == 604800 ? 1 : 2);
        final bPriority = b.durationSeconds == 18000
            ? 0
            : (b.durationSeconds == 604800 ? 1 : 2);
        return aPriority.compareTo(bPriority);
      });
    }

    if (geminiWindows.isNotEmpty) {
      readings.add(
        ProviderUsageReading(
          providerId: 'antigravity',
          displayName: claudeWindows.isNotEmpty
              ? 'Antigravity (Gemini Models)'
              : 'Antigravity',
          fetchedAt: now,
          windows: geminiWindows,
          planLabel: planLabel ?? 'Google AI Pro',
          needsSignIn: false,
        ),
      );
    }

    if (claudeWindows.isNotEmpty) {
      readings.add(
        ProviderUsageReading(
          providerId: 'antigravity_claude',
          displayName: 'Antigravity (Claude and GPT models)',
          fetchedAt: now,
          windows: claudeWindows,
          planLabel: planLabel ?? 'Google AI Pro',
          needsSignIn: false,
        ),
      );
    }

    return readings;
  }

  /// Parses single list of windows (backward compatibility helper).
  static List<ProviderUsageWindowReading> parseWindows({
    Map<String, dynamic>? summaryData,
    Map<String, dynamic>? quotaData,
  }) {
    final readings = parseHttpReadings(
      summaryData: summaryData,
      quotaData: quotaData,
    );
    if (readings.isEmpty) return const [];
    return readings.expand((r) => r.windows).toList();
  }

  Future<_ApiResponse> _postJson(
    HttpClient client,
    String path,
    String token, {
    Map<String, dynamic>? body,
  }) async {
    final payload = utf8.encode(jsonEncode(body ?? const {}));
    for (final base in [primaryEndpoint, fallbackEndpoint]) {
      try {
        final uri = Uri.parse('$base/$path');
        final request = await client.postUrl(uri);
        request.headers.set('Authorization', 'Bearer $token');
        request.headers.set('Content-Type', 'application/json');
        request.headers.set('User-Agent', _defaultUserAgent);
        request.add(payload);

        final response =
            await request.close().timeout(const Duration(seconds: 8));
        final body = await response.transform(utf8.decoder).join();
        return _ApiResponse(statusCode: response.statusCode, body: body);
      } on TimeoutException {
        continue;
      } on SocketException {
        continue;
      } catch (_) {
        continue;
      }
    }
    return const _ApiResponse(statusCode: 0, body: null);
  }
}

class _ApiResponse {
  const _ApiResponse({required this.statusCode, required this.body});
  final int statusCode;
  final String? body;
  bool get isSuccess => statusCode >= 200 && statusCode < 300;
}
