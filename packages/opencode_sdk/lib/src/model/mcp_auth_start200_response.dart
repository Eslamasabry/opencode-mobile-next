//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'mcp_auth_start200_response.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class McpAuthStart200Response {
  /// Returns a new [McpAuthStart200Response] instance.
  McpAuthStart200Response({
    required this.authorizationUrl,

    required this.oauthState,
  });

  @JsonKey(name: r'authorizationUrl', required: true, includeIfNull: false)
  final String authorizationUrl;

  @JsonKey(name: r'oauthState', required: true, includeIfNull: false)
  final String oauthState;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is McpAuthStart200Response &&
            runtimeType == other.runtimeType &&
            equals(
              [authorizationUrl, oauthState],
              [other.authorizationUrl, other.oauthState],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([authorizationUrl, oauthState]);

  factory McpAuthStart200Response.fromJson(Map<String, dynamic> json) =>
      _$McpAuthStart200ResponseFromJson(json);

  Map<String, dynamic> toJson() => _$McpAuthStart200ResponseToJson(this);

  String toString() {
    return toJson().toString();
  }
}
