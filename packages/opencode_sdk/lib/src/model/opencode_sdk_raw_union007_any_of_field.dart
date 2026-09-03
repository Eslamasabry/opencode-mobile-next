//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'opencode_sdk_raw_union007_any_of_field.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class OpencodeSdkRawUnion007AnyOfField {
  /// Returns a new [OpencodeSdkRawUnion007AnyOfField] instance.
  OpencodeSdkRawUnion007AnyOfField();

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is OpencodeSdkRawUnion007AnyOfField &&
            runtimeType == other.runtimeType &&
            equals([], []);
  }

  int get hashCode => runtimeType.hashCode ^ mapPropsToHashCode([]);

  factory OpencodeSdkRawUnion007AnyOfField.fromJson(
    Map<String, dynamic> json,
  ) => _$OpencodeSdkRawUnion007AnyOfFieldFromJson(json);

  Map<String, dynamic> toJson() =>
      _$OpencodeSdkRawUnion007AnyOfFieldToJson(this);

  String toString() {
    return toJson().toString();
  }
}
