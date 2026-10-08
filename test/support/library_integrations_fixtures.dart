import 'dart:async';

import 'package:flutter/material.dart';
import 'package:opencode_mobile/api/mcp_oauth.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/api/sse.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/screens/library_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

class IntegrationsRepository implements ProductRepository {
  List<McpServerInfo> servers = const [];
  List<McpResourceInfo> resources = const [];
  List<IntegrationInfo> integrations = const [];
  Object? serverError;
  Object? resourceError;
  Object? integrationError;
  Object? providerDisconnectError;
  Object? providerRefreshError;
  McpAuthLaunch mcpAuthLaunch = McpAuthLaunch(
    authorizationUrl: Uri.parse(
      'https://mcp-auth.example.com/authorize?redirect_uri='
      'http%3A%2F%2F127.0.0.1%3A19876%2Fmcp%2Foauth%2Fcallback',
    ),
    oauthState: 'mcp-state-1',
  );
  IntegrationAuthLaunch oauthLaunch = const IntegrationAuthLaunch(
    attemptID: 'attempt-1',
    url: 'https://provider-auth.example.com/authorize',
    instructions: '',
    mode: IntegrationAuthMode.auto,
  );
  IntegrationAuthStatus oauthStatus = const IntegrationAuthStatus(
    state: IntegrationAuthState.pending,
  );
  Completer<IntegrationAuthStatus>? oauthStatusCompleter;
  Map<String, String>? oauthInputs;
  int oauthCalls = 0;
  int oauthStatusCalls = 0;
  int oauthCompleteCalls = 0;
  int oauthCancelCalls = 0;
  int providerRefreshCalls = 0;
  int providerDisconnectCalls = 0;
  int mcpConnectCalls = 0;
  int mcpCompleteCalls = 0;
  int mcpCancelCalls = 0;
  String? mcpCompletionCode;
  IntegrationInfo? disconnectedIntegration;
  String? oauthCompletionCode;

  @override
  void setLocation({String? directory, String? workspace}) {}

  @override
  Future<List<McpServerInfo>> listMcpServers() async {
    if (serverError case final error?) throw error;
    return servers;
  }

  @override
  Future<List<McpResourceInfo>> listMcpResources() async {
    if (resourceError case final error?) throw error;
    return resources;
  }

  @override
  Future<List<IntegrationInfo>> listIntegrations() async {
    if (integrationError case final error?) throw error;
    return integrations;
  }

  @override
  Future<McpAuthLaunch> startMcpAuthentication(String name) async =>
      mcpAuthLaunch;

  @override
  Future<McpServerInfo> completeMcpAuthentication(
    String name,
    String code,
  ) async {
    mcpCompleteCalls += 1;
    mcpCompletionCode = code;
    servers = [McpServerInfo(name: name, status: 'connected')];
    return servers.single;
  }

  @override
  Future<void> cancelMcpAuthentication(String name) async {
    mcpCancelCalls += 1;
  }

  @override
  Future<void> connectMcp(String name) async {
    mcpConnectCalls += 1;
  }

  final mcpDisconnected = <String>[];

  @override
  Future<void> disconnectMcp(String name) async {
    mcpDisconnected.add(name);
    servers = [McpServerInfo(name: name, status: 'disabled')];
  }

  final savedKeys = <String>[];

  @override
  Future<void> connectIntegrationKey(
    String id,
    String key, {
    String? label,
  }) async {
    savedKeys.add(id);
  }

  @override
  Future<IntegrationAuthLaunch> startIntegrationOAuth(
    String id,
    String methodID, {
    Map<String, String> inputs = const {},
    String? label,
  }) async {
    oauthCalls++;
    oauthInputs = Map.of(inputs);
    return oauthLaunch;
  }

  @override
  Future<IntegrationAuthStatus> integrationOAuthStatus(String attemptID) async {
    oauthStatusCalls++;
    final completer = oauthStatusCompleter;
    if (completer != null) return completer.future;
    return oauthStatus;
  }

  @override
  Future<void> completeIntegrationOAuth(
    String attemptID, {
    String? code,
  }) async {
    oauthCompleteCalls++;
    oauthCompletionCode = code;
  }

  @override
  Future<void> cancelIntegrationOAuth(String attemptID) async {
    oauthCancelCalls++;
  }

  @override
  Future<void> refreshProviderRuntime() async {
    providerRefreshCalls++;
    if (providerRefreshError case final error?) throw error;
  }

  @override
  Future<void> disconnectIntegration(IntegrationInfo integration) async {
    providerDisconnectCalls++;
    disconnectedIntegration = integration;
    if (providerDisconnectError case final error?) throw error;
    integrations = [
      for (final current in integrations)
        if (current.id != integration.id)
          current
        else
          IntegrationInfo(
            id: current.id,
            name: current.name,
            methods: current.methods,
            connections: current.connections
                .where(
                  (connection) =>
                      connection.type != 'credential' &&
                      connection.type != 'runtime',
                )
                .toList(),
            connectionCount: current.connections
                .where(
                  (connection) =>
                      connection.type != 'credential' &&
                      connection.type != 'runtime',
                )
                .length,
          ),
    ];
  }

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

Future<ConnectionController> integrationsController(
  ProductRepository repository,
) async {
  SharedPreferences.setMockInitialValues({});
  final preferences = await SharedPreferences.getInstance();
  final store = ProfileStore(prefs: preferences);
  await store.upsert(
    ServerProfile(
      id: 'integrations-test',
      name: 'Test server',
      baseUrl: 'https://integrations.example',
    ),
  );
  await store.setActiveId('integrations-test');
  return ConnectionController(store)
    ..repository = repository
    ..status = StreamStatus.connected;
}

Widget app(
  ConnectionController controller, {
  Future<bool> Function(Uri destination)? authorizationLauncher,
  double textScale = 1,
}) => MaterialApp(
  // Kit parts read AppLocalizations.of.
  localizationsDelegates: AppLocalizations.localizationsDelegates,
  supportedLocales: AppLocalizations.supportedLocales,
  home: Builder(
    builder: (context) => MediaQuery(
      data: MediaQuery.of(
        context,
      ).copyWith(textScaler: TextScaler.linear(textScale)),
      child: IntegrationsScreen(
        controller: controller,
        authorizationLauncher: authorizationLauncher,
      ),
    ),
  ),
);
