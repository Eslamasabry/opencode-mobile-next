//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'mcp_status_connected.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class MCPStatusConnected {
  /// Returns a new [MCPStatusConnected] instance.
  MCPStatusConnected({required this.status});

  @JsonKey(
    name: r'status',
    required: true,
    includeIfNull: false,
    unknownEnumValue: MCPStatusConnectedStatusEnum.unknownDefaultOpenApi,
  )
  final MCPStatusConnectedStatusEnum status;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is MCPStatusConnected &&
            runtimeType == other.runtimeType &&
            equals([status], [other.status]);
  }

  int get hashCode => runtimeType.hashCode ^ mapPropsToHashCode([status]);

  factory MCPStatusConnected.fromJson(Map<String, dynamic> json) =>
      _$MCPStatusConnectedFromJson(json);

  Map<String, dynamic> toJson() => _$MCPStatusConnectedToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum MCPStatusConnectedStatusEnum {
  @JsonValue(r'connected')
  connected(r'connected'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const MCPStatusConnectedStatusEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
