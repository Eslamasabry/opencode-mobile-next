import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/ui/widgets/connect_methods.dart';

void main() {
  const browser = IntegrationMethodInfo(
    type: 'oauth',
    id: 'b',
    label: 'ChatGPT Pro/Plus (browser)',
  );
  const headless = IntegrationMethodInfo(
    type: 'oauth',
    id: 'h',
    label: 'ChatGPT Pro/Plus (headless)',
  );
  const key = IntegrationMethodInfo(
    type: 'key',
    label: 'Manually enter API Key',
  );

  test('headless comes first, browser last, key in between', () {
    final ordered = orderConnectMethods(const [browser, key, headless]);
    expect(ordered.map((m) => m.label).toList(), [
      headless.label,
      key.label,
      browser.label,
    ]);
  });

  test('hints say where each flow finishes', () {
    expect(connectMethodHint(browser), contains('paste the callback URL'));
    expect(connectMethodHint(headless), contains('Works from a phone'));
    expect(connectMethodHint(key), 'Paste an API key');
    expect(connectMethodNeedsServerBrowser(browser), isTrue);
    expect(connectMethodNeedsServerBrowser(headless), isFalse);
  });
}
