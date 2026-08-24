//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'not_found_error_data.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class NotFoundErrorData {
  /// Returns a new [NotFoundErrorData] instance.
  NotFoundErrorData({required this.message});

  @JsonKey(name: r'message', required: true, includeIfNull: false)
  final String message;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is NotFoundErrorData &&
            runtimeType == other.runtimeType &&
            equals([message], [other.message]);
  }

  int get hashCode => runtimeType.hashCode ^ mapPropsToHashCode([message]);

  factory NotFoundErrorData.fromJson(Map<String, dynamic> json) =>
      _$NotFoundErrorDataFromJson(json);

  Map<String, dynamic> toJson() => _$NotFoundErrorDataToJson(this);

  String toString() {
    return toJson().toString();
  }
}
