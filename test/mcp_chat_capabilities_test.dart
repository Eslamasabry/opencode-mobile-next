import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api2/gateway_mappers.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';

void main() {
  test('unqualified gateways do not promise chat connector actions', () {
    const capabilities = ServerCapabilities();

    expect(capabilities.mcpChatConnect, isFalse);
    expect(capabilities.mcpChatOAuth, isFalse);
    expect(capabilities.mcpChatToolRefresh, isFalse);
  });

  test('OpenCode 1 can connect and refresh chat connectors with OAuth', () {
    const capabilities = ServerCapabilities.allV1;

    expect(capabilities.mcpRuntimeAdds, isTrue);
    expect(capabilities.mcpChatConnect, isTrue);
    expect(capabilities.mcpChatOAuth, isTrue);
    expect(capabilities.mcpChatToolRefresh, isTrue);
  });

  test('OpenCode 2 supports runtime chat connectors without chat OAuth', () {
    const capabilities = api2ServerCapabilities;

    expect(capabilities.mcpRuntimeAdds, isTrue);
    expect(capabilities.mcpChatConnect, isTrue);
    expect(capabilities.mcpChatOAuth, isFalse);
    expect(capabilities.mcpChatToolRefresh, isFalse);
  });

  test('managed Agent cards preserve connector capability decisions', () {
    const capabilities = ServerCapabilities(
      mcpChatConnect: true,
      mcpChatOAuth: false,
      mcpChatToolRefresh: true,
    );

    for (final genUi in [true, false]) {
      final changed = capabilities.withGenUi(genUi);
      expect(changed.genUi, genUi);
      expect(changed.mcpChatConnect, isTrue);
      expect(changed.mcpChatOAuth, isFalse);
      expect(changed.mcpChatToolRefresh, isTrue);
    }

    final withOAuth = const ServerCapabilities(
      mcpChatOAuth: true,
    ).withGenUi(true);
    expect(withOAuth.mcpChatConnect, isFalse);
    expect(withOAuth.mcpChatOAuth, isTrue);
    expect(withOAuth.mcpChatToolRefresh, isFalse);
  });
}
