//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'mcp_status_failed.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class MCPStatusFailed {
  /// Returns a new [MCPStatusFailed] instance.
  MCPStatusFailed({required this.status, required this.error});

  @JsonKey(
    name: r'status',
    required: true,
    includeIfNull: false,
    unknownEnumValue: MCPStatusFailedStatusEnum.unknownDefaultOpenApi,
  )
  final MCPStatusFailedStatusEnum status;

  @JsonKey(name: r'error', required: true, includeIfNull: false)
  final String error;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is MCPStatusFailed &&
            runtimeType == other.runtimeType &&
            equals([status, error], [other.status, other.error]);
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([status, error]);

  factory MCPStatusFailed.fromJson(Map<String, dynamic> json) =>
      _$MCPStatusFailedFromJson(json);

  Map<String, dynamic> toJson() => _$MCPStatusFailedToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum MCPStatusFailedStatusEnum {
  @JsonValue(r'failed')
  failed(r'failed'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const MCPStatusFailedStatusEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
