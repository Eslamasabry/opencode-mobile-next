//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union056.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'mcp_add_request.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class McpAddRequest {
  /// Returns a new [McpAddRequest] instance.
  McpAddRequest({required this.name, required this.config});

  @JsonKey(name: r'name', required: true, includeIfNull: false)
  final String name;

  @JsonKey(name: r'config', required: true, includeIfNull: false)
  final OpencodeSdkRawUnion056 config;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is McpAddRequest &&
            runtimeType == other.runtimeType &&
            equals([name, config], [other.name, other.config]);
  }

  int get hashCode => runtimeType.hashCode ^ mapPropsToHashCode([name, config]);

  factory McpAddRequest.fromJson(Map<String, dynamic> json) =>
      _$McpAddRequestFromJson(json);

  Map<String, dynamic> toJson() => _$McpAddRequestToJson(this);

  String toString() {
    return toJson().toString();
  }
}
