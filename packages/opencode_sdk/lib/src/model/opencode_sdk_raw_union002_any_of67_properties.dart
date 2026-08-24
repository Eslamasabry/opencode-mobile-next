//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union002_any_of67_properties_command.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'opencode_sdk_raw_union002_any_of67_properties.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class OpencodeSdkRawUnion002AnyOf67Properties {
  /// Returns a new [OpencodeSdkRawUnion002AnyOf67Properties] instance.
  OpencodeSdkRawUnion002AnyOf67Properties({required this.command});

  @JsonKey(name: r'command', required: true, includeIfNull: false)
  final OpencodeSdkRawUnion002AnyOf67PropertiesCommand command;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is OpencodeSdkRawUnion002AnyOf67Properties &&
            runtimeType == other.runtimeType &&
            equals([command], [other.command]);
  }

  int get hashCode => runtimeType.hashCode ^ mapPropsToHashCode([command]);

  factory OpencodeSdkRawUnion002AnyOf67Properties.fromJson(
    Map<String, dynamic> json,
  ) => _$OpencodeSdkRawUnion002AnyOf67PropertiesFromJson(json);

  Map<String, dynamic> toJson() =>
      _$OpencodeSdkRawUnion002AnyOf67PropertiesToJson(this);

  String toString() {
    return toJson().toString();
  }
}
