//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'unknown_error_data.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class UnknownErrorData {
  /// Returns a new [UnknownErrorData] instance.
  UnknownErrorData({required this.message, this.ref});

  @JsonKey(name: r'message', required: true, includeIfNull: false)
  final String message;

  @JsonKey(name: r'ref', required: false, includeIfNull: false)
  final String? ref;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is UnknownErrorData &&
            runtimeType == other.runtimeType &&
            equals([message, ref], [other.message, other.ref]);
  }

  int get hashCode => runtimeType.hashCode ^ mapPropsToHashCode([message, ref]);

  factory UnknownErrorData.fromJson(Map<String, dynamic> json) =>
      _$UnknownErrorDataFromJson(json);

  Map<String, dynamic> toJson() => _$UnknownErrorDataToJson(this);

  String toString() {
    return toJson().toString();
  }
}
