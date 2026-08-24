//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/location_ref.dart';
import 'package:opencode_sdk/src/model/mcp_tools_changed_data.dart';
import 'package:opencode_sdk/src/model/session_status_schema2_durable.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'mcp_tools_changed.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class McpToolsChanged {
  /// Returns a new [McpToolsChanged] instance.
  McpToolsChanged({
    required this.id,

    this.metadata,

    required this.type,

    this.durable,

    this.location,

    required this.data,
  });

  @JsonKey(name: r'id', required: true, includeIfNull: false)
  final String id;

  @JsonKey(name: r'metadata', required: false, includeIfNull: false)
  final Object? metadata;

  @JsonKey(
    name: r'type',
    required: true,
    includeIfNull: false,
    unknownEnumValue: McpToolsChangedTypeEnum.unknownDefaultOpenApi,
  )
  final McpToolsChangedTypeEnum type;

  @JsonKey(name: r'durable', required: false, includeIfNull: false)
  final SessionStatusSchema2Durable? durable;

  @JsonKey(name: r'location', required: false, includeIfNull: false)
  final LocationRef? location;

  @JsonKey(name: r'data', required: true, includeIfNull: false)
  final McpToolsChangedData data;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is McpToolsChanged &&
            runtimeType == other.runtimeType &&
            equals(
              [id, metadata, type, durable, location, data],
              [
                other.id,
                other.metadata,
                other.type,
                other.durable,
                other.location,
                other.data,
              ],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([id, metadata, type, durable, location, data]);

  factory McpToolsChanged.fromJson(Map<String, dynamic> json) =>
      _$McpToolsChangedFromJson(json);

  Map<String, dynamic> toJson() => _$McpToolsChangedToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum McpToolsChangedTypeEnum {
  @JsonValue(r'mcp.tools.changed')
  mcpPeriodToolsPeriodChanged(r'mcp.tools.changed'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const McpToolsChangedTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
