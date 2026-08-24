//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'mcp_o_auth_config.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class McpOAuthConfig {
  /// Returns a new [McpOAuthConfig] instance.
  McpOAuthConfig({
    this.clientId,

    this.clientSecret,

    this.scope,

    this.callbackPort,

    this.redirectUri,
  });

  @JsonKey(name: r'clientId', required: false, includeIfNull: false)
  final String? clientId;

  @JsonKey(name: r'clientSecret', required: false, includeIfNull: false)
  final String? clientSecret;

  @JsonKey(name: r'scope', required: false, includeIfNull: false)
  final String? scope;

  // minimum: 1
  // maximum: 65535
  @JsonKey(name: r'callbackPort', required: false, includeIfNull: false)
  final int? callbackPort;

  @JsonKey(name: r'redirectUri', required: false, includeIfNull: false)
  final String? redirectUri;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is McpOAuthConfig &&
            runtimeType == other.runtimeType &&
            equals(
              [clientId, clientSecret, scope, callbackPort, redirectUri],
              [
                other.clientId,
                other.clientSecret,
                other.scope,
                other.callbackPort,
                other.redirectUri,
              ],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([
        clientId,
        clientSecret,
        scope,
        callbackPort,
        redirectUri,
      ]);

  factory McpOAuthConfig.fromJson(Map<String, dynamic> json) =>
      _$McpOAuthConfigFromJson(json);

  Map<String, dynamic> toJson() => _$McpOAuthConfigToJson(this);

  String toString() {
    return toJson().toString();
  }
}
