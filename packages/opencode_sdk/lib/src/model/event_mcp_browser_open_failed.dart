//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/mcp_browser_open_failed_data.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'event_mcp_browser_open_failed.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class EventMcpBrowserOpenFailed {
  /// Returns a new [EventMcpBrowserOpenFailed] instance.
  EventMcpBrowserOpenFailed({
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
    unknownEnumValue: EventMcpBrowserOpenFailedTypeEnum.unknownDefaultOpenApi,
  )
  final EventMcpBrowserOpenFailedTypeEnum type;

  @JsonKey(name: r'properties', required: true, includeIfNull: false)
  final McpBrowserOpenFailedData properties;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is EventMcpBrowserOpenFailed &&
            runtimeType == other.runtimeType &&
            equals(
              [id, type, properties],
              [other.id, other.type, other.properties],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([id, type, properties]);

  factory EventMcpBrowserOpenFailed.fromJson(Map<String, dynamic> json) =>
      _$EventMcpBrowserOpenFailedFromJson(json);

  Map<String, dynamic> toJson() => _$EventMcpBrowserOpenFailedToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum EventMcpBrowserOpenFailedTypeEnum {
  @JsonValue(r'mcp.browser.open.failed')
  mcpPeriodBrowserPeriodOpenPeriodFailed(r'mcp.browser.open.failed'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const EventMcpBrowserOpenFailedTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
