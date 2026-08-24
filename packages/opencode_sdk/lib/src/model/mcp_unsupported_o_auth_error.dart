//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'mcp_unsupported_o_auth_error.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class McpUnsupportedOAuthError {
  /// Returns a new [McpUnsupportedOAuthError] instance.
  McpUnsupportedOAuthError({required this.error});

  @JsonKey(name: r'error', required: true, includeIfNull: false)
  final String error;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is McpUnsupportedOAuthError &&
            runtimeType == other.runtimeType &&
            equals([error], [other.error]);
  }

  int get hashCode => runtimeType.hashCode ^ mapPropsToHashCode([error]);

  factory McpUnsupportedOAuthError.fromJson(Map<String, dynamic> json) =>
      _$McpUnsupportedOAuthErrorFromJson(json);

  Map<String, dynamic> toJson() => _$McpUnsupportedOAuthErrorToJson(this);

  String toString() {
    return toJson().toString();
  }
}
