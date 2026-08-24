//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'mcp_local_config.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class McpLocalConfig {
  /// Returns a new [McpLocalConfig] instance.
  McpLocalConfig({
    required this.type,

    required this.command,

    this.cwd,

    this.environment,

    this.enabled,

    this.timeout,
  });

  /// Type of MCP server connection
  @JsonKey(
    name: r'type',
    required: true,
    includeIfNull: false,
    unknownEnumValue: McpLocalConfigTypeEnum.unknownDefaultOpenApi,
  )
  final McpLocalConfigTypeEnum type;

  /// Command and arguments to run the MCP server
  @JsonKey(name: r'command', required: true, includeIfNull: false)
  final List<String> command;

  @JsonKey(name: r'cwd', required: false, includeIfNull: false)
  final String? cwd;

  @JsonKey(name: r'environment', required: false, includeIfNull: false)
  final Map<String, String>? environment;

  @JsonKey(name: r'enabled', required: false, includeIfNull: false)
  final bool? enabled;

  @JsonKey(name: r'timeout', required: false, includeIfNull: false)
  final int? timeout;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is McpLocalConfig &&
            runtimeType == other.runtimeType &&
            equals(
              [type, command, cwd, environment, enabled, timeout],
              [
                other.type,
                other.command,
                other.cwd,
                other.environment,
                other.enabled,
                other.timeout,
              ],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([type, command, cwd, environment, enabled, timeout]);

  factory McpLocalConfig.fromJson(Map<String, dynamic> json) =>
      _$McpLocalConfigFromJson(json);

  Map<String, dynamic> toJson() => _$McpLocalConfigToJson(this);

  String toString() {
    return toJson().toString();
  }
}

/// Type of MCP server connection
enum McpLocalConfigTypeEnum {
  /// Type of MCP server connection
  @JsonValue(r'local')
  local(r'local'),

  /// Type of MCP server connection
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const McpLocalConfigTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
