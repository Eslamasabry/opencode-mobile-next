import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/l10n/app_localizations_en.dart';
import 'package:opencode_mobile/ui/widgets/relative_time.dart';
import 'package:opencode_mobile/ui/widgets/session_title.dart';

// The full Arabic corpus is integrated centrally. This small delegate proves
// helper output follows the supplied locale without translating server content.
class _ArabicHelperStrings extends AppLocalizationsEn {
  @override
  String get workspaceNewSession => 'جلسة جديدة';
  @override
  String get e7WorkspaceJustNow => 'الآن';
  @override
  String e7WorkspaceMinutesAgo(int count) => 'قبل $count د';
  @override
  String e7WorkspaceHoursAgo(int count) => 'قبل $count س';
  @override
  String e7WorkspaceDaysAgo(int count) => 'قبل $count ي';
}

void main() {
  final l10n = _ArabicHelperStrings();
  test('session placeholders localize while real titles remain intact', () {
    expect(presentedSessionTitle(null, l10n: l10n), 'جلسة جديدة');
    expect(
      presentedSessionTitle(
        Session(id: 's', title: 'New session - 2026-09-10T00:00:00Z'),
        l10n: l10n,
        fallback: 'جلسة بلا عنوان',
      ),
      'جلسة جديدة',
    );
    expect(
      presentedSessionTitle(
        Session(id: 's', title: 'Investigate checkout'),
        l10n: l10n,
      ),
      'Investigate checkout',
    );
    expect(
      presentedSessionTitle(null, l10n: l10n, fallback: 'جلسة بلا عنوان'),
      'جلسة بلا عنوان',
    );
  });
  test('relative time follows supplied locale', () {
    final now = DateTime(2026, 9, 10, 12);
    String age(Duration elapsed) => relativeTimeLabel(
      now.subtract(elapsed).millisecondsSinceEpoch,
      now: now,
      l10n: l10n,
    );
    expect(age(const Duration(seconds: 20)), 'الآن');
    expect(age(const Duration(minutes: 5)), 'قبل 5 د');
    expect(age(const Duration(hours: 3)), 'قبل 3 س');
    expect(age(const Duration(days: 2)), 'قبل 2 ي');
  });
}
