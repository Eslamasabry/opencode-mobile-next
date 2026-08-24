//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/pty.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'pty_created_data.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class PtyCreatedData {
  /// Returns a new [PtyCreatedData] instance.
  PtyCreatedData({required this.info});

  @JsonKey(name: r'info', required: true, includeIfNull: false)
  final Pty info;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is PtyCreatedData &&
            runtimeType == other.runtimeType &&
            equals([info], [other.info]);
  }

  int get hashCode => runtimeType.hashCode ^ mapPropsToHashCode([info]);

  factory PtyCreatedData.fromJson(Map<String, dynamic> json) =>
      _$PtyCreatedDataFromJson(json);

  Map<String, dynamic> toJson() => _$PtyCreatedDataToJson(this);

  String toString() {
    return toJson().toString();
  }
}
