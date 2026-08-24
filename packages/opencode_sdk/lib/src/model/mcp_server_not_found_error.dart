//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'mcp_server_not_found_error.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class McpServerNotFoundError {
  /// Returns a new [McpServerNotFoundError] instance.
  McpServerNotFoundError({
    required this.tag,

    required this.name,

    required this.message,
  });

  @JsonKey(
    name: r'_tag',
    required: true,
    includeIfNull: false,
    unknownEnumValue: McpServerNotFoundErrorTagEnum.unknownDefaultOpenApi,
  )
  final McpServerNotFoundErrorTagEnum tag;

  @JsonKey(name: r'name', required: true, includeIfNull: false)
  final String name;

  @JsonKey(name: r'message', required: true, includeIfNull: false)
  final String message;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is McpServerNotFoundError &&
            runtimeType == other.runtimeType &&
            equals(
              [tag, name, message],
              [other.tag, other.name, other.message],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([tag, name, message]);

  factory McpServerNotFoundError.fromJson(Map<String, dynamic> json) =>
      _$McpServerNotFoundErrorFromJson(json);

  Map<String, dynamic> toJson() => _$McpServerNotFoundErrorToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum McpServerNotFoundErrorTagEnum {
  @JsonValue(r'McpServerNotFoundError')
  mcpServerNotFoundError(r'McpServerNotFoundError'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const McpServerNotFoundErrorTagEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
