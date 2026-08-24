//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'mcp_resource.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class McpResource {
  /// Returns a new [McpResource] instance.
  McpResource({
    required this.name,

    required this.uri,

    this.description,

    this.mimeType,

    required this.client,
  });

  @JsonKey(name: r'name', required: true, includeIfNull: false)
  final String name;

  @JsonKey(name: r'uri', required: true, includeIfNull: false)
  final String uri;

  @JsonKey(name: r'description', required: false, includeIfNull: false)
  final String? description;

  @JsonKey(name: r'mimeType', required: false, includeIfNull: false)
  final String? mimeType;

  @JsonKey(name: r'client', required: true, includeIfNull: false)
  final String client;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is McpResource &&
            runtimeType == other.runtimeType &&
            equals(
              [name, uri, description, mimeType, client],
              [
                other.name,
                other.uri,
                other.description,
                other.mimeType,
                other.client,
              ],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([name, uri, description, mimeType, client]);

  factory McpResource.fromJson(Map<String, dynamic> json) =>
      _$McpResourceFromJson(json);

  Map<String, dynamic> toJson() => _$McpResourceToJson(this);

  String toString() {
    return toJson().toString();
  }
}
