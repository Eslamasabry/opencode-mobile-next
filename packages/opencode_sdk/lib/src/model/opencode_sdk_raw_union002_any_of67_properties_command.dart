//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'opencode_sdk_raw_union002_any_of67_properties_command.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class OpencodeSdkRawUnion002AnyOf67PropertiesCommand {
  /// Returns a new [OpencodeSdkRawUnion002AnyOf67PropertiesCommand] instance.
  OpencodeSdkRawUnion002AnyOf67PropertiesCommand();

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is OpencodeSdkRawUnion002AnyOf67PropertiesCommand &&
            runtimeType == other.runtimeType &&
            equals([], []);
  }

  int get hashCode => runtimeType.hashCode ^ mapPropsToHashCode([]);

  factory OpencodeSdkRawUnion002AnyOf67PropertiesCommand.fromJson(
    Map<String, dynamic> json,
  ) => _$OpencodeSdkRawUnion002AnyOf67PropertiesCommandFromJson(json);

  Map<String, dynamic> toJson() =>
      _$OpencodeSdkRawUnion002AnyOf67PropertiesCommandToJson(this);

  String toString() {
    return toJson().toString();
  }
}
