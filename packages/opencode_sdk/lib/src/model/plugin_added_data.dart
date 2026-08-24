//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'plugin_added_data.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class PluginAddedData {
  /// Returns a new [PluginAddedData] instance.
  PluginAddedData({required this.id});

  @JsonKey(name: r'id', required: true, includeIfNull: false)
  final String id;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is PluginAddedData &&
            runtimeType == other.runtimeType &&
            equals([id], [other.id]);
  }

  int get hashCode => runtimeType.hashCode ^ mapPropsToHashCode([id]);

  factory PluginAddedData.fromJson(Map<String, dynamic> json) =>
      _$PluginAddedDataFromJson(json);

  Map<String, dynamic> toJson() => _$PluginAddedDataToJson(this);

  String toString() {
    return toJson().toString();
  }
}
