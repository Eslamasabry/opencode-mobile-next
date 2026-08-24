//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'pty_deleted_data.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class PtyDeletedData {
  /// Returns a new [PtyDeletedData] instance.
  PtyDeletedData({required this.id});

  @JsonKey(name: r'id', required: true, includeIfNull: false)
  final String id;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is PtyDeletedData &&
            runtimeType == other.runtimeType &&
            equals([id], [other.id]);
  }

  int get hashCode => runtimeType.hashCode ^ mapPropsToHashCode([id]);

  factory PtyDeletedData.fromJson(Map<String, dynamic> json) =>
      _$PtyDeletedDataFromJson(json);

  Map<String, dynamic> toJson() => _$PtyDeletedDataToJson(this);

  String toString() {
    return toJson().toString();
  }
}
