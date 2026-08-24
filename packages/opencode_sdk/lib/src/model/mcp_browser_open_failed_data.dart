//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'mcp_browser_open_failed_data.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class McpBrowserOpenFailedData {
  /// Returns a new [McpBrowserOpenFailedData] instance.
  McpBrowserOpenFailedData({required this.mcpName, required this.url});

  @JsonKey(name: r'mcpName', required: true, includeIfNull: false)
  final String mcpName;

  @JsonKey(name: r'url', required: true, includeIfNull: false)
  final String url;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is McpBrowserOpenFailedData &&
            runtimeType == other.runtimeType &&
            equals([mcpName, url], [other.mcpName, other.url]);
  }

  int get hashCode => runtimeType.hashCode ^ mapPropsToHashCode([mcpName, url]);

  factory McpBrowserOpenFailedData.fromJson(Map<String, dynamic> json) =>
      _$McpBrowserOpenFailedDataFromJson(json);

  Map<String, dynamic> toJson() => _$McpBrowserOpenFailedDataToJson(this);

  String toString() {
    return toJson().toString();
  }
}
