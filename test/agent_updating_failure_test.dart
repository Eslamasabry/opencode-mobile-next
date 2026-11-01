// The owner's phone (APK 4360) showed "The Paseo daemon could not complete
// this action. / The phone refused new agent work: Agent setup is in
// progress... (phone check running) / Paseo unavailable". Plain words with a
// way forward now; the technical text stays under Details.
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/product_failure.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/l10n/app_localizations_en.dart';
import 'package:opencode_mobile/paseo/transport.dart';

void main() {
  const reason =
      'The phone refused new agent work: Agent setup is in progress. Wait '
      'for it to finish and try again. (phone check running)';

  test('the refusal is its own plain category, directly and wrapped', () {
    final direct = PaseoFailure(PaseoFailureKind.unavailable, reason: reason);
    expect(
      ProductFailure.from(direct).category,
      ProductFailureCategory.agentUpdating,
    );
    final wrapped = ProductException(
      'Could not start this chat. Refresh before trying again.',
      cause: direct,
    );
    expect(
      ProductFailure.from(wrapped).category,
      ProductFailureCategory.agentUpdating,
    );
  });

  test('an ordinary unavailable daemon is still a computer problem', () {
    expect(
      ProductFailure.from(PaseoFailure(PaseoFailureKind.unavailable)).category,
      ProductFailureCategory.computer,
    );
  });

  test('the words are plain and keep the message; the daemon text is only '
      'in Details', () {
    final l10n = AppLocalizationsEn();
    final copy = l10n.productErrorAgentUpdating;
    for (final raw in ['Paseo', 'daemon', 'phone check', 'unavailable']) {
      expect(copy, isNot(contains(raw)));
    }
    expect(copy, contains('Try again'));
    expect(copy, contains('kept'));
    final details = ProductFailure.from(
      PaseoFailure(PaseoFailureKind.unavailable, reason: reason),
    ).technicalDetails;
    expect(details, contains('phone check running'));
  });
}
