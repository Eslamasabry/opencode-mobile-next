//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'mcp_status_disabled.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class MCPStatusDisabled {
  /// Returns a new [MCPStatusDisabled] instance.
  MCPStatusDisabled({required this.status});

  @JsonKey(
    name: r'status',
    required: true,
    includeIfNull: false,
    unknownEnumValue: MCPStatusDisabledStatusEnum.unknownDefaultOpenApi,
  )
  final MCPStatusDisabledStatusEnum status;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is MCPStatusDisabled &&
            runtimeType == other.runtimeType &&
            equals([status], [other.status]);
  }

  int get hashCode => runtimeType.hashCode ^ mapPropsToHashCode([status]);

  factory MCPStatusDisabled.fromJson(Map<String, dynamic> json) =>
      _$MCPStatusDisabledFromJson(json);

  Map<String, dynamic> toJson() => _$MCPStatusDisabledToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum MCPStatusDisabledStatusEnum {
  @JsonValue(r'disabled')
  disabled(r'disabled'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const MCPStatusDisabledStatusEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
