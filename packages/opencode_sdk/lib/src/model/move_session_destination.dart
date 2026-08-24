//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'move_session_destination.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class MoveSessionDestination {
  /// Returns a new [MoveSessionDestination] instance.
  MoveSessionDestination({required this.directory});

  @JsonKey(name: r'directory', required: true, includeIfNull: false)
  final String directory;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is MoveSessionDestination &&
            runtimeType == other.runtimeType &&
            equals([directory], [other.directory]);
  }

  int get hashCode => runtimeType.hashCode ^ mapPropsToHashCode([directory]);

  factory MoveSessionDestination.fromJson(Map<String, dynamic> json) =>
      _$MoveSessionDestinationFromJson(json);

  Map<String, dynamic> toJson() => _$MoveSessionDestinationToJson(this);

  String toString() {
    return toJson().toString();
  }
}
