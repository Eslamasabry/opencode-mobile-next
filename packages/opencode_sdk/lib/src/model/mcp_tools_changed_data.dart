//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'mcp_tools_changed_data.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class McpToolsChangedData {
  /// Returns a new [McpToolsChangedData] instance.
  McpToolsChangedData({required this.server});

  @JsonKey(name: r'server', required: true, includeIfNull: false)
  final String server;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is McpToolsChangedData &&
            runtimeType == other.runtimeType &&
            equals([server], [other.server]);
  }

  int get hashCode => runtimeType.hashCode ^ mapPropsToHashCode([server]);

  factory McpToolsChangedData.fromJson(Map<String, dynamic> json) =>
      _$McpToolsChangedDataFromJson(json);

  Map<String, dynamic> toJson() => _$McpToolsChangedDataToJson(this);

  String toString() {
    return toJson().toString();
  }
}
