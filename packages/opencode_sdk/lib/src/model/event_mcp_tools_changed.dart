//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/mcp_tools_changed_data.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'event_mcp_tools_changed.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class EventMcpToolsChanged {
  /// Returns a new [EventMcpToolsChanged] instance.
  EventMcpToolsChanged({
    required this.id,

    required this.type,

    required this.properties,
  });

  @JsonKey(name: r'id', required: true, includeIfNull: false)
  final String id;

  @JsonKey(
    name: r'type',
    required: true,
    includeIfNull: false,
    unknownEnumValue: EventMcpToolsChangedTypeEnum.unknownDefaultOpenApi,
  )
  final EventMcpToolsChangedTypeEnum type;

  @JsonKey(name: r'properties', required: true, includeIfNull: false)
  final McpToolsChangedData properties;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is EventMcpToolsChanged &&
            runtimeType == other.runtimeType &&
            equals(
              [id, type, properties],
              [other.id, other.type, other.properties],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([id, type, properties]);

  factory EventMcpToolsChanged.fromJson(Map<String, dynamic> json) =>
      _$EventMcpToolsChangedFromJson(json);

  Map<String, dynamic> toJson() => _$EventMcpToolsChangedToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum EventMcpToolsChangedTypeEnum {
  @JsonValue(r'mcp.tools.changed')
  mcpPeriodToolsPeriodChanged(r'mcp.tools.changed'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const EventMcpToolsChangedTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
