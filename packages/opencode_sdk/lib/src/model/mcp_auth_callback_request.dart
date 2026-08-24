//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'mcp_auth_callback_request.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class McpAuthCallbackRequest {
  /// Returns a new [McpAuthCallbackRequest] instance.
  McpAuthCallbackRequest({required this.code});

  @JsonKey(name: r'code', required: true, includeIfNull: false)
  final String code;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is McpAuthCallbackRequest &&
            runtimeType == other.runtimeType &&
            equals([code], [other.code]);
  }

  int get hashCode => runtimeType.hashCode ^ mapPropsToHashCode([code]);

  factory McpAuthCallbackRequest.fromJson(Map<String, dynamic> json) =>
      _$McpAuthCallbackRequestFromJson(json);

  Map<String, dynamic> toJson() => _$McpAuthCallbackRequestToJson(this);

  String toString() {
    return toJson().toString();
  }
}
