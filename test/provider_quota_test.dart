import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/provider_quota.dart';

/// Handwritten normalized extension data. Never copied from an auth store or a
/// provider response. Shared by the adapter/controller tests, not stored on disk.
Map<String, dynamic> providerQuotaFixture({
  int fetchedAtMs = 1000000,
  QuotaProvider provider = QuotaProvider.codex,
}) => <String, dynamic>{
  'schemaVersion': 1,
  'provider': provider.name,
  'source': provider == QuotaProvider.codex ? 'codex.wham' : 'claude.oauth',
  'status': 'ok',
  'freshness': 'fresh',
  'fetchedAtMs': fetchedAtMs,
  'expiresAtMs': fetchedAtMs + 60000,
  'account': <String, dynamic>{
    'ref': 'a' * 64,
    'status': provider == QuotaProvider.codex ? 'matched' : 'sourceBound',
    if (provider == QuotaProvider.codex) 'plan': 'plus',
  },
  'ordinaryUsageAllowed': provider == QuotaProvider.codex ? true : null,
  'windows': <Map<String, dynamic>>[
    <String, dynamic>{
      'id': 'primary',
      'status': 'reported',
      'usedPercent': 25.5,
      'durationSeconds': 18000,
      'resetsAtMs': fetchedAtMs + 300000,
    },
    <String, dynamic>{'id': 'secondary', 'status': 'missing'},
  ],
};

Map<String, dynamic> _window(Map<String, dynamic> fixture) =>
    (fixture['windows'] as List).first as Map<String, dynamic>;

void main() {
  test(
    'Claude source-bound snapshots do not claim independently matched account identity',
    () {
      final snapshot = ProviderQuotaSnapshot.fromJson(
        providerQuotaFixture(provider: QuotaProvider.claude),
      );
      expect(snapshot.provider, QuotaProvider.claude);
      expect(snapshot.account.status, QuotaAccountStatus.sourceBound);
      expect(snapshot.account.plan, isNull);
      expect(snapshot.canShowWindows, isTrue);
      expect(snapshot.ordinaryUsageAllowed, isNull);
      expect(quotaPathFor(QuotaProvider.claude), '/ocmn/quota/v1/claude');
      expect(quotaPathFor(QuotaProvider.codex), providerQuotaPath);
    },
  );

  test(
    'provider/source mismatches and invented Claude eligibility are rejected',
    () {
      for (final change in [
        (Map<String, dynamic> value) => value['source'] = 'codex.wham',
        (Map<String, dynamic> value) => value['ordinaryUsageAllowed'] = true,
        (Map<String, dynamic> value) => (value['account'] as Map).remove('ref'),
        (Map<String, dynamic> value) => value['status'] = 'authRequired',
      ]) {
        final value = providerQuotaFixture(provider: QuotaProvider.claude);
        change(value);
        expect(
          () => ProviderQuotaSnapshot.fromJson(value),
          throwsFormatException,
        );
      }
      final codex = providerQuotaFixture();
      (codex['account'] as Map)['status'] = 'sourceBound';
      expect(
        () => ProviderQuotaSnapshot.fromJson(codex),
        throwsFormatException,
      );
    },
  );

  test('the route is explicitly separate from upstream OpenCode APIs', () {
    expect(providerQuotaPath, '/ocmn/quota/v1');
  });

  test('parses reported and missing windows without invented allowances', () {
    final snapshot = ProviderQuotaSnapshot.fromJson(providerQuotaFixture());
    expect(snapshot.status, ProviderQuotaStatus.ok);
    expect(snapshot.freshness, QuotaFreshness.fresh);
    expect(snapshot.account.status, QuotaAccountStatus.matched);
    expect(snapshot.account.plan, 'plus');
    expect(snapshot.account.ref == 'a' * 64, isTrue);
    expect(snapshot.ordinaryUsageAllowed, isTrue);
    expect(snapshot.windows.first.usedPercent, 25.5);
    expect(snapshot.windows.first.remainingPercent, 74.5);
    expect(snapshot.windows.first.durationSeconds, 18000);
    expect(snapshot.windows.first.resetsAt!.isUtc, isTrue);
    expect(snapshot.windows.last.status, QuotaWindowStatus.missing);
    expect(snapshot.windows.last.usedPercent, isNull);
    expect(snapshot.windows.last.remainingPercent, isNull);
    expect(snapshot.windows.last.durationSeconds, isNull);
    expect(snapshot.windows.last.resetsAt, isNull);
    expect(snapshot.fetchedAt.isUtc, isTrue);
    expect(snapshot.expiresAt.millisecondsSinceEpoch, 1060000);
  });

  test('optional ordinary usage remains unknown, and false stays false', () {
    for (final allowed in [null, false, true]) {
      final value = providerQuotaFixture()..['ordinaryUsageAllowed'] = allowed;
      final parsed = ProviderQuotaSnapshot.fromJson(value);
      expect(parsed.ordinaryUsageAllowed, allowed);
    }
    final value = providerQuotaFixture()..remove('ordinaryUsageAllowed');
    expect(ProviderQuotaSnapshot.fromJson(value).ordinaryUsageAllowed, isNull);
  });

  test('zero and fully used are real values, not missing windows', () {
    for (final percent in [0, 100]) {
      final value = providerQuotaFixture();
      _window(value)['usedPercent'] = percent;
      final window = ProviderQuotaSnapshot.fromJson(value).windows.first;
      expect(window.usedPercent, percent.toDouble());
      expect(window.remainingPercent, (100 - percent).toDouble());
    }
  });

  test('expiry is inclusive; a passed reset never replenishes a window', () {
    final value = ProviderQuotaSnapshot.fromJson(providerQuotaFixture());
    expect(
      value.isStale(value.expiresAt.subtract(const Duration(milliseconds: 1))),
      isFalse,
    );
    expect(value.isStale(value.expiresAt), isTrue);
    expect(value.isStale(value.windows.first.resetsAt!), isTrue);
    expect(value.windows.first.usedPercent, 25.5);
    expect(value.windows.first.remainingPercent, 74.5);
    final stale = ProviderQuotaSnapshot.fromJson(
      providerQuotaFixture()..['freshness'] = 'stale',
    );
    expect(stale.isStale(stale.fetchedAt), isTrue);
  });

  test(
    'snapshot is detached from mutable input and its windows are immutable',
    () {
      final value = providerQuotaFixture();
      final snapshot = ProviderQuotaSnapshot.fromJson(value);
      _window(value)['usedPercent'] = 99;
      (value['account'] as Map)['plan'] = 'pro';
      (value['windows'] as List).clear();
      expect(snapshot.windows, hasLength(2));
      expect(snapshot.windows.first.usedPercent, 25.5);
      expect(snapshot.account.plan, 'plus');
      expect(() => snapshot.windows.clear(), throwsUnsupportedError);
    },
  );

  test(
    'accepts each declared no-measurement status without making up quotas',
    () {
      for (final status in ProviderQuotaStatus.values) {
        for (final accountStatus in QuotaAccountStatus.values.where(
          (value) => value != QuotaAccountStatus.sourceBound,
        )) {
          final value = providerQuotaFixture()
            ..['status'] = status.name
            ..['freshness'] = 'none'
            ..['ordinaryUsageAllowed'] = null
            ..['windows'] = <Object>[]
            ..['account'] = <String, dynamic>{
              'status': accountStatus.name,
              if (accountStatus == QuotaAccountStatus.matched) 'ref': 'b' * 64,
            };
          final snapshot = ProviderQuotaSnapshot.fromJson(value);
          expect(snapshot.status, status);
          expect(snapshot.account.status, accountStatus);
          expect(snapshot.windows, isEmpty);
          expect(snapshot.ordinaryUsageAllowed, isNull);
        }
      }
    },
  );

  test(
    'no error status or unverified/mismatched account may carry measurements',
    () {
      for (final status in ProviderQuotaStatus.values.where(
        (v) => v != ProviderQuotaStatus.ok,
      )) {
        final value = providerQuotaFixture()..['status'] = status.name;
        expect(
          () => ProviderQuotaSnapshot.fromJson(value),
          throwsFormatException,
        );
        value['windows'] = <Object>[];
        expect(
          () => ProviderQuotaSnapshot.fromJson(value),
          throwsFormatException,
        );
      }
      for (final accountStatus in [
        QuotaAccountStatus.unverified,
        QuotaAccountStatus.mismatch,
      ]) {
        final value = providerQuotaFixture();
        (value['account'] as Map)['status'] = accountStatus.name;
        expect(
          () => ProviderQuotaSnapshot.fromJson(value),
          throwsFormatException,
        );
        value['windows'] = <Object>[];
        expect(
          () => ProviderQuotaSnapshot.fromJson(value),
          throwsFormatException,
        );
      }
    },
  );

  test(
    'all known plan labels are accepted; arbitrary plan copy is discarded',
    () {
      for (final plan in [
        'free',
        'go',
        'plus',
        'pro',
        'team',
        'business',
        'enterprise',
        'edu',
      ]) {
        final value = providerQuotaFixture();
        (value['account'] as Map)['plan'] = plan;
        expect(ProviderQuotaSnapshot.fromJson(value).account.plan, plan);
      }
      for (final plan in [
        null,
        12,
        {},
        'unknown-plan',
        'https://untrusted.example',
        'fixture-private-copy',
      ]) {
        final value = providerQuotaFixture();
        (value['account'] as Map)['plan'] = plan;
        expect(ProviderQuotaSnapshot.fromJson(value).account.plan, isNull);
      }
    },
  );

  test(
    'rejects wrong version/provider/source, missing keys, and non-objects',
    () {
      for (final value in [null, [], 'text', 1, true]) {
        expect(
          () => ProviderQuotaSnapshot.fromJson(value),
          throwsFormatException,
        );
      }
      for (final key in [
        'schemaVersion',
        'provider',
        'source',
        'status',
        'freshness',
        'fetchedAtMs',
        'expiresAtMs',
        'account',
        'windows',
      ]) {
        final value = providerQuotaFixture()..remove(key);
        expect(
          () => ProviderQuotaSnapshot.fromJson(value),
          throwsFormatException,
          reason: key,
        );
      }
      for (final entry in <String, Object>{
        'schemaVersion': 2,
        'provider': 'different-provider',
        'source': 'upstream-opencode',
        'status': 'unknown',
        'freshness': 'unknown',
        'account': [],
        'windows': {},
        'ordinaryUsageAllowed': 1,
      }.entries) {
        final value = providerQuotaFixture()..[entry.key] = entry.value;
        expect(
          () => ProviderQuotaSnapshot.fromJson(value),
          throwsFormatException,
          reason: entry.key,
        );
      }
    },
  );

  test('timestamp bounds and ordering are strict, including window resets', () {
    for (final timestamp in [
      -1,
      8640000000000001,
      1.5,
      '100',
      double.nan,
      double.infinity,
    ]) {
      for (final key in ['fetchedAtMs', 'expiresAtMs']) {
        final value = providerQuotaFixture()..[key] = timestamp;
        expect(
          () => ProviderQuotaSnapshot.fromJson(value),
          throwsFormatException,
        );
      }
      final value = providerQuotaFixture();
      _window(value)['resetsAtMs'] = timestamp;
      expect(
        () => ProviderQuotaSnapshot.fromJson(value),
        throwsFormatException,
      );
    }
    final reversed = providerQuotaFixture()..['expiresAtMs'] = 999999;
    expect(
      () => ProviderQuotaSnapshot.fromJson(reversed),
      throwsFormatException,
    );
    final bounds = providerQuotaFixture(fetchedAtMs: 0)
      ..['expiresAtMs'] = 8640000000000000;
    expect(
      ProviderQuotaSnapshot.fromJson(bounds).fetchedAt.millisecondsSinceEpoch,
      0,
    );
  });

  test(
    'account references must be opaque lower-case hex; matched needs one',
    () {
      for (final ref in [
        null,
        '',
        'a' * 63,
        'a' * 65,
        'A' * 64,
        'g' * 64,
        'person@example.test',
        123,
      ]) {
        final value = providerQuotaFixture();
        (value['account'] as Map)['ref'] = ref;
        expect(
          () => ProviderQuotaSnapshot.fromJson(value),
          throwsFormatException,
        );
      }
      final value = providerQuotaFixture();
      (value['account'] as Map)['status'] = 'other';
      expect(
        () => ProviderQuotaSnapshot.fromJson(value),
        throwsFormatException,
      );
    },
  );

  test(
    'rejects non-finite/out-of-range percentages and status contradictions',
    () {
      for (final percent in [
        null,
        -0.1,
        100.1,
        double.nan,
        double.infinity,
        double.negativeInfinity,
        '25',
        true,
      ]) {
        final value = providerQuotaFixture();
        _window(value)['usedPercent'] = percent;
        expect(
          () => ProviderQuotaSnapshot.fromJson(value),
          throwsFormatException,
        );
      }
      final missing = providerQuotaFixture();
      _window(missing)['status'] = 'missing';
      expect(
        () => ProviderQuotaSnapshot.fromJson(missing),
        throwsFormatException,
      );
      final unknown = providerQuotaFixture();
      _window(unknown)['status'] = 'other';
      expect(
        () => ProviderQuotaSnapshot.fromJson(unknown),
        throwsFormatException,
      );
    },
  );

  test('duration and window identifiers are bounded and unique', () {
    for (final duration in [0, -1, 315360001, 1.5, '18000', false]) {
      final value = providerQuotaFixture();
      _window(value)['durationSeconds'] = duration;
      expect(
        () => ProviderQuotaSnapshot.fromJson(value),
        throwsFormatException,
      );
    }
    for (final id in [
      null,
      '',
      'a' * 65,
      'with space',
      'https://host',
      '<b>',
      1,
    ]) {
      final value = providerQuotaFixture();
      _window(value)['id'] = id;
      expect(
        () => ProviderQuotaSnapshot.fromJson(value),
        throwsFormatException,
      );
    }
    final duplicate = providerQuotaFixture();
    (duplicate['windows'] as List).add(
      Map<String, dynamic>.of(_window(duplicate)),
    );
    expect(
      () => ProviderQuotaSnapshot.fromJson(duplicate),
      throwsFormatException,
    );
    final malformed = providerQuotaFixture()..['windows'] = [null];
    expect(
      () => ProviderQuotaSnapshot.fromJson(malformed),
      throwsFormatException,
    );
    for (final count in [64, 65]) {
      final value = providerQuotaFixture()
        ..['windows'] = <Map<String, dynamic>>[
          for (var index = 0; index < count; index++)
            <String, dynamic>{'id': 'window/$index', 'status': 'missing'},
        ];
      if (count == 64) {
        expect(ProviderQuotaSnapshot.fromJson(value).windows, hasLength(64));
      } else {
        expect(
          () => ProviderQuotaSnapshot.fromJson(value),
          throwsFormatException,
        );
      }
    }
  });

  test(
    'diagnostic strings and parse failures do not echo input or account refs',
    () {
      final value = providerQuotaFixture()
        ..['ignored'] = 'fixture-private-copy';
      final snapshot = ProviderQuotaSnapshot.fromJson(value);
      expect(snapshot.toString(), 'ProviderQuotaSnapshot(ok, 2 windows)');
      expect(snapshot.account.toString(), 'ProviderQuotaAccount(matched)');
      for (final kind in QuotaFailureKind.values) {
        expect(
          ProviderQuotaFailure(kind).toString(),
          'ProviderQuotaFailure(${kind.name})',
        );
      }
      try {
        ProviderQuotaSnapshot.fromJson('fixture-private-copy');
        fail('Expected a sanitized parser failure');
      } on FormatException catch (error) {
        expect(error.source, isNull);
        expect(error.toString().contains('fixture-private-copy'), isFalse);
      }
    },
  );
}
