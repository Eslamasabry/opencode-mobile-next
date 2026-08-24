//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'opencode_sdk_raw_union006_any_of_field.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class OpencodeSdkRawUnion006AnyOfField {
  /// Returns a new [OpencodeSdkRawUnion006AnyOfField] instance.
  OpencodeSdkRawUnion006AnyOfField();

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is OpencodeSdkRawUnion006AnyOfField &&
            runtimeType == other.runtimeType &&
            equals([], []);
  }

  int get hashCode => runtimeType.hashCode ^ mapPropsToHashCode([]);

  factory OpencodeSdkRawUnion006AnyOfField.fromJson(
    Map<String, dynamic> json,
  ) => _$OpencodeSdkRawUnion006AnyOfFieldFromJson(json);

  Map<String, dynamic> toJson() =>
      _$OpencodeSdkRawUnion006AnyOfFieldToJson(this);

  String toString() {
    return toJson().toString();
  }
}
