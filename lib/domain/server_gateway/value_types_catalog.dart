import '../../api/models.dart';

class ExperimentalServerCapabilities {
  final bool backgroundSubagents;

  const ExperimentalServerCapabilities({required this.backgroundSubagents});
}

/// Runtime permission on v1; the v2 session protocol supports both kinds.
enum BackgroundWorkSupport { unavailable, subagents, subagentsAndShells }

/// v2 acknowledges the request with 204, which can also mean an idle no-op.
/// Only v1's true response confirms that something was promoted.
enum BackgroundWorkResult { promoted, unchanged, requested }

class CodingToolInfo {
  final String id;
  final String description;
  final Object? parameters;

  const CodingToolInfo({
    required this.id,
    required this.description,
    required this.parameters,
  });
}

class ChatDefaults {
  final ModelRef? model;
  final String? agent;

  const ChatDefaults({this.model, this.agent});
}

class McpServerInfo {
  final String name;
  final String status;
  final String? error;

  const McpServerInfo({required this.name, required this.status, this.error});
}

enum McpServerKind { remote, local }

enum McpConfigScope { project, global, runtimeLocation }

class McpServerDraft {
  final String name;
  final McpServerKind kind;
  final String? url;
  final List<String> command;
  final String? cwd;
  final Map<String, String> headers;
  final Map<String, String> environment;
  final bool detectOAuth;
  final int? timeoutMs;

  const McpServerDraft({
    required this.name,
    required this.kind,
    this.url,
    this.command = const [],
    this.cwd,
    this.headers = const {},
    this.environment = const {},
    this.detectOAuth = true,
    this.timeoutMs,
  });

  String get normalizedName => name.trim();

  Map<String, Object?> toConfigJson() {
    final serverName = normalizedName;
    if (serverName.isEmpty || serverName.contains(RegExp(r'[\r\n]'))) {
      throw const ProductException('Enter a valid MCP server name');
    }
    final timeout = timeoutMs;
    if (timeout != null && timeout <= 0) {
      throw const ProductException('MCP timeout must be greater than zero');
    }
    switch (kind) {
      case McpServerKind.remote:
        final value = url?.trim() ?? '';
        final uri = Uri.tryParse(value);
        if (uri == null ||
            !uri.hasScheme ||
            !uri.hasAuthority ||
            (uri.scheme != 'https' && uri.scheme != 'http') ||
            uri.userInfo.isNotEmpty) {
          throw const ProductException(
            'Enter an HTTP or HTTPS MCP server URL without credentials',
          );
        }
        _validatePairs(headers, 'HTTP header');
        return {
          'type': 'remote',
          'url': uri.toString(),
          if (headers.isNotEmpty) 'headers': Map.of(headers),
          if (!detectOAuth) 'oauth': false,
          'timeout': ?timeout,
        };
      case McpServerKind.local:
        final parts = command.map((part) => part.trim()).toList();
        if (parts.isEmpty || parts.any((part) => part.isEmpty)) {
          throw const ProductException(
            'Enter the local command and each argument on its own line',
          );
        }
        _validatePairs(environment, 'environment variable');
        return {
          'type': 'local',
          'command': parts,
          if (cwd?.trim().isNotEmpty == true) 'cwd': cwd!.trim(),
          if (environment.isNotEmpty) 'environment': Map.of(environment),
          'timeout': ?timeout,
        };
    }
  }

  static void _validatePairs(Map<String, String> values, String label) {
    for (final entry in values.entries) {
      if (entry.key.trim().isEmpty ||
          entry.key.contains(RegExp(r'[\r\n=]')) ||
          entry.value.contains(RegExp(r'[\r\n]'))) {
        throw ProductException('Enter a valid $label name and value');
      }
    }
  }
}

class McpResourceInfo {
  final String name;
  final String server;
  final String uri;
  final String? description;
  final String? mimeType;

  const McpResourceInfo({
    required this.name,
    required this.server,
    required this.uri,
    this.description,
    this.mimeType,
  });
}

class IntegrationInfo {
  final String id;
  final String name;
  final List<IntegrationMethodInfo> methods;
  final List<IntegrationConnectionInfo> connections;
  final int connectionCount;

  const IntegrationInfo({
    required this.id,
    required this.name,
    required this.methods,
    this.connections = const [],
    required this.connectionCount,
  });

  List<String> get credentialIDs => connections
      .where((connection) => connection.type == 'credential')
      .map((connection) => connection.id)
      .whereType<String>()
      .where((id) => id.isNotEmpty)
      .toList(growable: false);

  bool get hasEnvironmentConnection =>
      connections.any((connection) => connection.type == 'env');
}

class IntegrationConnectionInfo {
  final String type;
  final String? id;
  final String label;

  const IntegrationConnectionInfo({
    required this.type,
    this.id,
    required this.label,
  });
}

class IntegrationMethodInfo {
  final String type;
  final String? id;
  final String label;
  final List<Map<String, dynamic>> prompts;
  final List<String> environmentNames;

  const IntegrationMethodInfo({
    required this.type,
    this.id,
    required this.label,
    this.prompts = const [],
    this.environmentNames = const [],
  });
}

class IntegrationAuthLaunch {
  final String attemptID;
  final String url;
  final String instructions;
  final IntegrationAuthMode mode;
  final int? expiresAt;

  const IntegrationAuthLaunch({
    required this.attemptID,
    required this.url,
    required this.instructions,
    required this.mode,
    this.expiresAt,
  });
}

enum IntegrationAuthMode { auto, code }

enum IntegrationAuthState { pending, complete, failed, expired }

class IntegrationAuthStatus {
  final IntegrationAuthState state;
  final String? message;
  final int? expiresAt;

  const IntegrationAuthStatus({
    required this.state,
    this.message,
    this.expiresAt,
  });
}

class CommandInfo {
  final String name;
  final String? description;
  final String? agent;
  final bool subtask;

  const CommandInfo({
    required this.name,
    this.description,
    this.agent,
    required this.subtask,
  });
}

class SkillInfo {
  /// Protocol identifier; display names are not necessarily valid selectors.
  final String? id;
  final String name;
  final String? description;
  final String location;
  final String content;
  final bool slashCommand;

  const SkillInfo({
    this.id,
    required this.name,
    this.description,
    required this.location,
    required this.content,
    required this.slashCommand,
  });
}

class ReferenceInfo {
  final String name;
  final String path;
  final String? description;

  const ReferenceInfo({
    required this.name,
    required this.path,
    this.description,
  });
}

class QuestionChoice {
  final String label;
  final String description;

  const QuestionChoice({required this.label, required this.description});
}

class QuestionPrompt {
  final String title;
  final String question;
  final bool multiple;
  final bool custom;
  final List<QuestionChoice> choices;

  const QuestionPrompt({
    required this.title,
    required this.question,
    required this.multiple,
    required this.custom,
    required this.choices,
  });
}

class PendingQuestion {
  final String id;
  final String sessionID;
  final List<QuestionPrompt> prompts;

  const PendingQuestion({
    required this.id,
    required this.sessionID,
    required this.prompts,
  });

  factory PendingQuestion.fromJson(Map<String, dynamic> json) {
    final raw = json['questions'];
    return PendingQuestion(
      id: (json['id'] ?? json['requestID'] ?? '').toString(),
      sessionID: (json['sessionID'] ?? '').toString(),
      prompts: raw is List
          ? raw.whereType<Map>().map((item) {
              final value = Map<String, dynamic>.from(item);
              final options = value['options'];
              return QuestionPrompt(
                title: (value['header'] ?? 'Question').toString(),
                question: (value['question'] ?? '').toString(),
                multiple: value['multiple'] == true,
                custom: value['custom'] != false,
                choices: options is List
                    ? options.whereType<Map>().map((option) {
                        final choice = Map<String, dynamic>.from(option);
                        return QuestionChoice(
                          label: (choice['label'] ?? '').toString(),
                          description: (choice['description'] ?? '').toString(),
                        );
                      }).toList()
                    : const [],
              );
            }).toList()
          : const [],
    );
  }
}

abstract class TerminalChannel {
  Stream<String> get output;
  int? get cursor => null;
  void write(String value);
  Future<void> close();
}

abstract interface class LocationAwareProductRepository {
  int get locationRevision;
}

class ProductException implements Exception {
  final String message;
  final Object? cause;

  const ProductException(this.message, {this.cause});

  @override
  String toString() => message;
}

/// A provider runtime refresh refused because replies are still running.
///
/// Refreshing an OpenCode 1 provider runtime disposes the server instance,
/// which aborts every reply running in it. Nothing was disposed; retry once
/// [runningReplies] have finished, or leave the reload to the automatic
/// retry that runs when the server goes idle.
class ProviderRuntimeBusyException extends ProductException {
  final int runningReplies;

  const ProviderRuntimeBusyException(this.runningReplies)
    : super('Providers reload after the running replies finish');
}
