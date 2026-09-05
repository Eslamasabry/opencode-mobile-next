import 'package:flutter/services.dart';

/// Arrow history must leave editing, selection, IME and suggestion keys alone.
bool isPromptHistoryKey(
  KeyEvent event,
  TextEditingValue value, {
  required bool suggestionsOpen,
}) {
  if (event is! KeyDownEvent ||
      suggestionsOpen ||
      HardwareKeyboard.instance.isControlPressed ||
      HardwareKeyboard.instance.isAltPressed ||
      HardwareKeyboard.instance.isMetaPressed ||
      HardwareKeyboard.instance.isShiftPressed ||
      !value.selection.isValid ||
      !value.selection.isCollapsed ||
      (!value.composing.isCollapsed && value.composing.isValid)) {
    return false;
  }
  return (event.logicalKey == LogicalKeyboardKey.arrowUp &&
          value.selection.extentOffset == 0) ||
      (event.logicalKey == LogicalKeyboardKey.arrowDown &&
          value.selection.extentOffset == value.text.length);
}

/// Retains the original draft even if a recalled prompt is edited.
class PromptHistoryNavigation {
  TextEditingValue? original;
  List<String> _prompts = [];
  int _index = -1;

  TextEditingValue? move(
    bool older,
    TextEditingValue current,
    List<String> prompts,
  ) {
    if (original == null) {
      if (!older || prompts.isEmpty) return null;
      original = current;
      _prompts = List.of(prompts);
      _index = -1;
    }
    if (older) {
      if (_index + 1 >= _prompts.length) return null;
      _index++;
    } else {
      if (_index <= 0) return restore();
      _index--;
    }
    final text = _prompts[_index];
    return TextEditingValue(
      text: text,
      selection: TextSelection.collapsed(offset: older ? 0 : text.length),
    );
  }

  TextEditingValue? restore() {
    final value = original;
    original = null;
    _prompts = [];
    _index = -1;
    return value;
  }
}
