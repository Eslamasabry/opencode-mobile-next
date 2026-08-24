//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/opencode_sdk_raw_union002_any_of45_properties_error.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'opencode_sdk_raw_union002_any_of45_properties.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class OpencodeSdkRawUnion002AnyOf45Properties {
  /// Returns a new [OpencodeSdkRawUnion002AnyOf45Properties] instance.
  OpencodeSdkRawUnion002AnyOf45Properties({this.sessionID, this.error});

  @JsonKey(name: r'sessionID', required: false, includeIfNull: false)
  final String? sessionID;

  @JsonKey(name: r'error', required: false, includeIfNull: false)
  final OpencodeSdkRawUnion002AnyOf45PropertiesError? error;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is OpencodeSdkRawUnion002AnyOf45Properties &&
            runtimeType == other.runtimeType &&
            equals([sessionID, error], [other.sessionID, other.error]);
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([sessionID, error]);

  factory OpencodeSdkRawUnion002AnyOf45Properties.fromJson(
    Map<String, dynamic> json,
  ) => _$OpencodeSdkRawUnion002AnyOf45PropertiesFromJson(json);

  Map<String, dynamic> toJson() =>
      _$OpencodeSdkRawUnion002AnyOf45PropertiesToJson(this);

  String toString() {
    return toJson().toString();
  }
}
