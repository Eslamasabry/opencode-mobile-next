//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'find_text200_response_inner_path.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class FindText200ResponseInnerPath {
  /// Returns a new [FindText200ResponseInnerPath] instance.
  FindText200ResponseInnerPath({required this.text});

  @JsonKey(name: r'text', required: true, includeIfNull: false)
  final String text;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is FindText200ResponseInnerPath &&
            runtimeType == other.runtimeType &&
            equals([text], [other.text]);
  }

  int get hashCode => runtimeType.hashCode ^ mapPropsToHashCode([text]);

  factory FindText200ResponseInnerPath.fromJson(Map<String, dynamic> json) =>
      _$FindText200ResponseInnerPathFromJson(json);

  Map<String, dynamic> toJson() => _$FindText200ResponseInnerPathToJson(this);

  String toString() {
    return toJson().toString();
  }
}
