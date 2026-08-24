//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'mcp_auth_remove200_response.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class McpAuthRemove200Response {
  /// Returns a new [McpAuthRemove200Response] instance.
  McpAuthRemove200Response({required this.success});

  @JsonKey(
    name: r'success',
    required: true,
    includeIfNull: false,
    unknownEnumValue: McpAuthRemove200ResponseSuccessEnum.unknownDefaultOpenApi,
  )
  final McpAuthRemove200ResponseSuccessEnum success;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is McpAuthRemove200Response &&
            runtimeType == other.runtimeType &&
            equals([success], [other.success]);
  }

  int get hashCode => runtimeType.hashCode ^ mapPropsToHashCode([success]);

  factory McpAuthRemove200Response.fromJson(Map<String, dynamic> json) =>
      _$McpAuthRemove200ResponseFromJson(json);

  Map<String, dynamic> toJson() => _$McpAuthRemove200ResponseToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum McpAuthRemove200ResponseSuccessEnum {
  @JsonValue('true')
  true_('true'),
  @JsonValue('11184809')
  unknownDefaultOpenApi('11184809');

  const McpAuthRemove200ResponseSuccessEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
