//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union008.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'mcp_remote_config.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class McpRemoteConfig {
  /// Returns a new [McpRemoteConfig] instance.
  McpRemoteConfig({
    required this.type,

    required this.url,

    this.enabled,

    this.headers,

    this.oauth,

    this.timeout,
  });

  /// Type of MCP server connection
  @JsonKey(
    name: r'type',
    required: true,
    includeIfNull: false,
    unknownEnumValue: McpRemoteConfigTypeEnum.unknownDefaultOpenApi,
  )
  final McpRemoteConfigTypeEnum type;

  /// URL of the remote MCP server
  @JsonKey(name: r'url', required: true, includeIfNull: false)
  final String url;

  @JsonKey(name: r'enabled', required: false, includeIfNull: false)
  final bool? enabled;

  @JsonKey(name: r'headers', required: false, includeIfNull: false)
  final Map<String, String>? headers;

  @JsonKey(name: r'oauth', required: false, includeIfNull: false)
  final OpencodeSdkRawUnion008? oauth;

  @JsonKey(name: r'timeout', required: false, includeIfNull: false)
  final int? timeout;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is McpRemoteConfig &&
            runtimeType == other.runtimeType &&
            equals(
              [type, url, enabled, headers, oauth, timeout],
              [
                other.type,
                other.url,
                other.enabled,
                other.headers,
                other.oauth,
                other.timeout,
              ],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([type, url, enabled, headers, oauth, timeout]);

  factory McpRemoteConfig.fromJson(Map<String, dynamic> json) =>
      _$McpRemoteConfigFromJson(json);

  Map<String, dynamic> toJson() => _$McpRemoteConfigToJson(this);

  String toString() {
    return toJson().toString();
  }
}

/// Type of MCP server connection
enum McpRemoteConfigTypeEnum {
  /// Type of MCP server connection
  @JsonValue(r'remote')
  remote(r'remote'),

  /// Type of MCP server connection
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const McpRemoteConfigTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
