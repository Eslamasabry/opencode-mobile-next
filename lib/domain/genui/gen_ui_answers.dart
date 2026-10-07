sealed class GenUiAnswer {
  const GenUiAnswer();
}

final class GenUiChoiceAnswer extends GenUiAnswer {
  GenUiChoiceAnswer(List<String> ids) : ids = List.unmodifiable(ids);
  final List<String> ids;
}

final class GenUiFormAnswer extends GenUiAnswer {
  GenUiFormAnswer(Map<String, Object> values)
    : values = Map.unmodifiable(values);

  /// String, num or bool values; absent optional fields are omitted.
  final Map<String, Object> values;
}

final class GenUiConfirmAnswer extends GenUiAnswer {
  const GenUiConfirmAnswer(this.value);
  final bool value;
}

final class GenUiPhotoAnswer extends GenUiAnswer {
  const GenUiPhotoAnswer(this.count);
  final int count;
}
