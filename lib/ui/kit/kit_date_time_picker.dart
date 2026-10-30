import 'dart:async';
import 'dart:math' as math;

import 'package:clock/clock.dart';
import 'package:flutter/material.dart';
import 'package:flutter/semantics.dart';
import 'package:flutter/services.dart';
import 'package:intl/intl.dart' show DateFormat;

import '../../l10n/app_localizations.dart';
import '../app_iconography.dart';
import 'kit_buttons.dart';
import 'kit_field.dart';
import 'kit_icon_button.dart';
import 'kit_max_width.dart';
import 'kit_motion.dart';
import 'kit_row.dart';
import 'kit_segmented.dart';
import 'kit_sheet.dart';
import 'kit_text.dart';
import 'kit_tokens.dart';
import 'motion/kit_reveal.dart';

AppLocalizations _l10n(BuildContext context) =>
    lookupAppLocalizations(Localizations.localeOf(context));

String _localeTag(BuildContext context) =>
    Localizations.localeOf(context).toString();

void _announce(BuildContext context, String message) {
  final view = View.maybeOf(context);
  if (view == null) return;
  unawaited(
    SemanticsService.sendAnnouncement(
      view,
      message,
      Directionality.maybeOf(context) ?? TextDirection.ltr,
    ),
  );
}

/// What a [KitDateTimeRow] or a picker sets: a day, a time of day, or both.
enum KitDateTimeMode { date, time, dateAndTime }

/// The one way to pick a date, a time, or both
/// (docs/ux-system/kit-api/KitDateTimePicker.md): a kit sheet whose title
/// says which value it sets and whose button is a verb. It replaces the
/// stock `showDatePicker` and `showTimePicker` dialogs.
///
/// Open it with [showKitDatePicker], [showKitTimePicker] or
/// [showKitDateTimePicker]; show a value in a form or a settings list with
/// [KitDateTimeRow].
///
/// States: date, date-typed, date-invalid, time, time-12h, time-invalid,
/// date-and-time, row-empty, row-set, row-error, row-disabled.
///
/// - Loading: none; the values are local.
/// - Empty: a row with no value shows "Not set" (or its placeholder) in
///   `text3`.
/// - Error: a typed date or time that does not parse or is out of range
///   shows its reason under the field; a `validate` reason shows under the
///   time fields. The primary stays enabled: pressing it while invalid
///   focuses the error, announces it once and does not close.
/// - Disabled: a row without `onChanged` is dimmed with its reason; days
///   outside the range or refused by `selectable` are inert and announced
///   as unavailable.
///
/// Nothing is written by the part: the value goes back to the host, and a
/// dismissal (close, Esc, back, a swipe) returns null. Typed partial
/// entries are not kept. Local time only.
abstract final class KitDateTimePicker {
  /// The default range when a caller gives none (the form renderer's today).
  static final DateTime earliest = DateTime(1900);
  static final DateTime latest = DateTime(2100, 12, 31);
}

/// A date. Returns the chosen day (local, time 00:00), or null when
/// dismissed.
Future<DateTime?> showKitDatePicker(
  BuildContext context, {
  required String title,
  DateTime? initial,
  DateTime? first,
  DateTime? last,
  bool Function(DateTime day)? selectable,
  String? confirmLabel,
  String? helper,
  Key? sheetKey,
  Key? confirmKey,
}) async {
  final result = await _openPicker(
    context,
    mode: KitDateTimeMode.date,
    title: title,
    initial: initial,
    first: first,
    last: last,
    selectable: selectable,
    confirmLabel: confirmLabel ?? _l10n(context).kitDateSet,
    helper: helper,
    sheetKey: sheetKey,
    confirmKey: confirmKey,
  );
  return result is DateTime ? result : null;
}

/// A time of day. Returns the chosen time, or null when dismissed.
Future<TimeOfDay?> showKitTimePicker(
  BuildContext context, {
  required String title,
  required TimeOfDay initial,
  String? confirmLabel,
  String? helper,
  String? Function(TimeOfDay value)? validate,
  Key? sheetKey,
  Key? confirmKey,
}) async {
  final result = await _openPicker(
    context,
    mode: KitDateTimeMode.time,
    title: title,
    initialTime: initial,
    confirmLabel: confirmLabel ?? _l10n(context).kitTimeSet,
    helper: helper,
    validate: validate,
    sheetKey: sheetKey,
    confirmKey: confirmKey,
  );
  return result is TimeOfDay ? result : null;
}

/// A day and a time in one sheet (the calendar, then the time entry).
/// Returns the combined local [DateTime], or null when dismissed.
Future<DateTime?> showKitDateTimePicker(
  BuildContext context, {
  required String title,
  DateTime? initial,
  DateTime? first,
  DateTime? last,
  bool Function(DateTime day)? selectable,
  String? confirmLabel,
  String? helper,
  Key? sheetKey,
  Key? confirmKey,
}) async {
  final result = await _openPicker(
    context,
    mode: KitDateTimeMode.dateAndTime,
    title: title,
    initial: initial,
    initialTime: initial == null ? null : TimeOfDay.fromDateTime(initial),
    first: first,
    last: last,
    selectable: selectable,
    confirmLabel: confirmLabel ?? _l10n(context).kitDateTimeSet,
    helper: helper,
    sheetKey: sheetKey,
    confirmKey: confirmKey,
  );
  return result is DateTime ? result : null;
}

Future<Object?> _openPicker(
  BuildContext context, {
  required KitDateTimeMode mode,
  required String title,
  required String confirmLabel,
  DateTime? initial,
  TimeOfDay? initialTime,
  DateTime? first,
  DateTime? last,
  bool Function(DateTime day)? selectable,
  String? helper,
  String? Function(TimeOfDay value)? validate,
  Key? sheetKey,
  Key? confirmKey,
}) async {
  final localizations = MaterialLocalizations.of(context);
  final use24 =
      MediaQuery.alwaysUse24HourFormatOf(context) ||
      hourFormat(of: localizations.timeOfDayFormat()) != HourFormat.h;
  final draft = _PickerDraft(
    mode: mode,
    initial: initial ?? clock.now(),
    initialTime: initialTime ?? TimeOfDay.fromDateTime(clock.now()),
    first: first ?? KitDateTimePicker.earliest,
    last: last ?? KitDateTimePicker.latest,
    selectable: selectable,
    use24: use24,
    validate: validate,
  );
  void submit() {
    final result = draft.confirm(context);
    if (result != null) Navigator.of(context).pop(result);
  }

  draft.onSubmit = submit;
  return showKitSheet<Object>(
    context,
    title: title,
    subtitle: helper,
    sheetKey: sheetKey,
    primary: KitAction(label: confirmLabel, onPressed: submit, key: confirmKey),
    // The body owns the draft: it is disposed with the sheet's route.
    body: (_) => _PickerBody(draft: draft),
  );
}

/// The picker's live input: the day on the calendar, the typed texts and
/// the reasons. Held for the sheet's life only; nothing is saved.
class _PickerDraft extends ChangeNotifier {
  _PickerDraft({
    required this.mode,
    required DateTime initial,
    required TimeOfDay initialTime,
    required DateTime first,
    required DateTime last,
    required this.selectable,
    required this.use24,
    required this.validate,
  }) : first = DateUtils.dateOnly(first),
       last = DateUtils.dateOnly(last),
       fallbackTime = initialTime {
    final day = DateUtils.dateOnly(initial);
    final inRange = !day.isBefore(this.first) && !day.isAfter(this.last);
    selected = inRange && _allows(day) ? day : null;
    focusedDay = inRange
        ? day
        : (day.isBefore(this.first) ? this.first : this.last);
    month = DateTime(focusedDay.year, focusedDay.month);
    _setTime(initialTime);
  }

  final KitDateTimeMode mode;
  final DateTime first;
  final DateTime last;
  final bool Function(DateTime day)? selectable;
  final bool use24;
  final String? Function(TimeOfDay value)? validate;
  final TimeOfDay fallbackTime;
  VoidCallback? onSubmit;

  DateTime? selected;
  late DateTime focusedDay;
  late DateTime month;

  /// +1 when the last month change moved forward in time, -1 backward.
  int monthStep = 1;

  bool typed = false;
  final dateText = TextEditingController();
  final hourText = TextEditingController();
  final minuteText = TextEditingController();
  DayPeriod period = DayPeriod.am;

  String? gridReason;
  String? dateError;
  String? hourError;
  String? minuteError;
  String? timeReason;

  final gridNode = FocusNode(debugLabel: 'kit-date-grid');
  final dateNode = FocusNode(debugLabel: 'kit-date-typed');
  late final hourNode = FocusNode(
    debugLabel: 'kit-time-hour',
    onKeyEvent: (_, event) => _stepKey(event, hour: true),
  );
  late final minuteNode = FocusNode(
    debugLabel: 'kit-time-minute',
    onKeyEvent: (_, event) => _stepKey(event, hour: false),
  );

  bool get hasDate => mode != KitDateTimeMode.time;
  bool get hasTime => mode != KitDateTimeMode.date;

  bool _allows(DateTime day) => selectable?.call(day) ?? true;

  /// Whether [day] can be picked: in range and allowed by the caller.
  bool canPick(DateTime day) =>
      !day.isBefore(first) && !day.isAfter(last) && _allows(day);

  void changed() => notifyListeners();

  void pick(DateTime day) {
    if (!canPick(day)) return;
    selected = day;
    focusedDay = day;
    gridReason = null;
    notifyListeners();
  }

  /// Moves the keyboard's day, turning the month when it leaves it.
  /// Returns whether the month changed.
  bool focusDay(DateTime day) {
    final clamped = day.isBefore(first)
        ? first
        : day.isAfter(last)
        ? last
        : day;
    focusedDay = clamped;
    final target = DateTime(clamped.year, clamped.month);
    final turned = target != month;
    if (turned) {
      monthStep = target.isAfter(month) ? 1 : -1;
      month = target;
    }
    notifyListeners();
    return turned;
  }

  bool get canGoBack => month.isAfter(DateTime(first.year, first.month));
  bool get canGoForward => month.isBefore(DateTime(last.year, last.month));

  void turnMonth(int by) {
    final target = DateUtils.addMonthsToMonthDate(month, by);
    monthStep = by > 0 ? 1 : -1;
    month = target;
    final days = DateUtils.getDaysInMonth(target.year, target.month);
    focusDay(
      DateTime(target.year, target.month, math.min(focusedDay.day, days)),
    );
  }

  // ── Time ────────────────────────────────────────────────────────────────

  void _setTime(TimeOfDay time) {
    period = time.period;
    final hour = use24
        ? time.hour
        : time.hourOfPeriod == 0
        ? 12
        : time.hourOfPeriod;
    hourText.text = hour.toString().padLeft(use24 ? 2 : 1, '0');
    minuteText.text = time.minute.toString().padLeft(2, '0');
    for (final c in [hourText, minuteText]) {
      c.selection = TextSelection.collapsed(offset: c.text.length);
    }
    hourError = null;
    minuteError = null;
    timeReason = null;
  }

  int? _hourValue() {
    final raw = int.tryParse(hourText.text.trim());
    if (raw == null) return null;
    if (use24) return raw >= 0 && raw <= 23 ? raw : null;
    if (raw < 1 || raw > 12) return null;
    return raw % 12 + (period == DayPeriod.pm ? 12 : 0);
  }

  int? _minuteValue() {
    final raw = int.tryParse(minuteText.text.trim());
    return raw != null && raw >= 0 && raw <= 59 ? raw : null;
  }

  TimeOfDay? get time {
    final hour = _hourValue();
    final minute = _minuteValue();
    if (hour == null || minute == null) return null;
    return TimeOfDay(hour: hour, minute: minute);
  }

  void setPeriod(DayPeriod value) {
    period = value;
    timeReason = null;
    notifyListeners();
  }

  /// Arrow Up/Down step the focused field by one; Page Up/Down move the
  /// time by ten minutes. The time wraps round the day.
  KeyEventResult _stepKey(KeyEvent event, {required bool hour}) {
    if (event is! KeyDownEvent && event is! KeyRepeatEvent) {
      return KeyEventResult.ignored;
    }
    final key = event.logicalKey;
    final int by;
    if (key == LogicalKeyboardKey.arrowUp) {
      by = hour ? 60 : 1;
    } else if (key == LogicalKeyboardKey.arrowDown) {
      by = hour ? -60 : -1;
    } else if (key == LogicalKeyboardKey.pageUp) {
      by = 10;
    } else if (key == LogicalKeyboardKey.pageDown) {
      by = -10;
    } else {
      return KeyEventResult.ignored;
    }
    final now = time ?? fallbackTime;
    final total = (now.hour * 60 + now.minute + by) % (24 * 60);
    _setTime(TimeOfDay(hour: total ~/ 60, minute: total % 60));
    notifyListeners();
    return KeyEventResult.handled;
  }

  // ── Typed date ──────────────────────────────────────────────────────────

  void setTyped(BuildContext context, bool value) {
    final format = DateFormat.yMd(_localeTag(context));
    if (value) {
      final day = selected;
      final text = day == null ? '' : format.format(day);
      // The caret already at the end: focusing then moves no selection, so
      // no touch handle pops up over the field.
      dateText.value = TextEditingValue(
        text: text,
        selection: TextSelection.collapsed(offset: text.length),
      );
      dateError = null;
    } else {
      final day = _parseTyped(context, report: false);
      if (day != null) {
        selected = day;
        focusDay(day);
      }
    }
    typed = value;
    notifyListeners();
    final node = value ? dateNode : gridNode;
    WidgetsBinding.instance.addPostFrameCallback((_) => node.requestFocus());
  }

  String rangeReason(BuildContext context) {
    final format = DateFormat.yMMMd(_localeTag(context));
    return _l10n(
      context,
    ).kitDateOutOfRange(format.format(first), format.format(last));
  }

  DateTime? _parseTyped(BuildContext context, {required bool report}) {
    final l10n = _l10n(context);
    DateTime? day;
    try {
      day = DateFormat.yMd(
        _localeTag(context),
      ).parseStrict(dateText.text.trim());
    } on FormatException {
      day = null;
    }
    String? reason;
    if (day == null) {
      reason = l10n.kitDateInvalid;
    } else if (day.isBefore(first) || day.isAfter(last)) {
      reason = rangeReason(context);
    } else if (!_allows(day)) {
      reason = l10n.kitDateUnavailable;
    }
    if (report) dateError = reason;
    return reason == null ? day : null;
  }

  // ── Confirm ─────────────────────────────────────────────────────────────

  /// The value to return, or null after showing, focusing and announcing
  /// why it cannot be set.
  Object? confirm(BuildContext context) {
    final l10n = _l10n(context);
    String? announce;
    FocusNode? focus;
    DateTime? day;
    if (hasDate) {
      if (typed) {
        day = _parseTyped(context, report: true);
        if (day == null) {
          announce = dateError;
          focus = dateNode;
        }
      } else {
        day = selected;
        if (day == null) {
          gridReason = rangeReason(context);
          announce = gridReason;
          focus = gridNode;
        }
      }
    }
    TimeOfDay? chosen;
    if (hasTime) {
      final hour = _hourValue();
      final minute = _minuteValue();
      hourError = hour == null ? l10n.kitTimeInvalid : null;
      minuteError = minute == null ? l10n.kitTimeInvalid : null;
      timeReason = null;
      if (hour != null && minute != null) {
        chosen = TimeOfDay(hour: hour, minute: minute);
        timeReason = validate?.call(chosen);
        if (timeReason != null) {
          chosen = null;
          announce ??= timeReason;
          focus ??= hourNode;
        }
      } else {
        announce ??= l10n.kitTimeInvalid;
        focus ??= hour == null ? hourNode : minuteNode;
      }
    }
    notifyListeners();
    if (announce != null) {
      focus?.requestFocus();
      _announce(context, announce);
      return null;
    }
    return switch (mode) {
      KitDateTimeMode.date => day,
      KitDateTimeMode.time => chosen,
      KitDateTimeMode.dateAndTime => DateTime(
        day!.year,
        day.month,
        day.day,
        chosen!.hour,
        chosen.minute,
      ),
    };
  }

  @override
  void dispose() {
    for (final c in [dateText, hourText, minuteText]) {
      c.dispose();
    }
    for (final n in [gridNode, dateNode, hourNode, minuteNode]) {
      n.dispose();
    }
    super.dispose();
  }
}

/// The sheet's body: the calendar (or the typed date), then the time.
class _PickerBody extends StatefulWidget {
  const _PickerBody({required this.draft});

  final _PickerDraft draft;

  @override
  State<_PickerBody> createState() => _PickerBodyState();
}

class _PickerBodyState extends State<_PickerBody> with WidgetsBindingObserver {
  _PickerDraft get draft => widget.draft;
  final _timeKey = GlobalKey();

  /// With the keyboard open the sheet's body is short; the time entry
  /// (Hour, Minute and AM/PM) is brought wholly into view, so AM/PM is never
  /// left clipped under the pinned button.
  Timer? _showTimer;

  void _showTime() {
    if (!draft.hasTime) return;
    // After the field's own scroll-to-caret and the keyboard's slide, which
    // would otherwise move the view again.
    _showTimer?.cancel();
    _showTimer = Timer(const Duration(milliseconds: 260), () {
      final target = _timeKey.currentContext;
      if (!mounted || target == null) return;
      if (!(draft.hourNode.hasFocus || draft.minuteNode.hasFocus)) return;
      unawaited(
        Scrollable.ensureVisible(
          target,
          duration: KitMotion.reduced(context)
              ? Duration.zero
              : const Duration(milliseconds: 200),
          curve: KitMotion.stepOut,
          alignmentPolicy: ScrollPositionAlignmentPolicy.keepVisibleAtEnd,
        ),
      );
    });
  }

  @override
  void didChangeMetrics() => _showTime();

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    draft.hourNode.addListener(_showTime);
    draft.minuteNode.addListener(_showTime);
    // Focus starts on the Hour field for a time (the calendar takes it for
    // a date). Asked after the first frame: the sheet frame autofocuses
    // itself first, and a field's own autofocus would lose to it.
    if (draft.mode == KitDateTimeMode.time) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (mounted) draft.hourNode.requestFocus();
      });
    }
  }

  @override
  void dispose() {
    _showTimer?.cancel();
    WidgetsBinding.instance.removeObserver(this);
    draft.hourNode.removeListener(_showTime);
    draft.minuteNode.removeListener(_showTime);
    draft.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final l10n = _l10n(context);
    return ListenableBuilder(
      listenable: draft,
      builder: (context, _) => Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          if (draft.hasDate) ...[
            KitReveal(child: draft.typed ? null : _Calendar(draft: draft)),
            KitReveal(child: draft.typed ? _TypedDate(draft: draft) : null),
            Align(
              alignment: AlignmentDirectional.centerStart,
              child: KitButton.tertiary(
                label: draft.typed ? l10n.kitDateCalendar : l10n.kitDateType,
                icon: draft.typed
                    ? AppIconography.calendar
                    : AppIconography.keyboard,
                onPressed: () => draft.setTyped(context, !draft.typed),
              ),
            ),
          ],
          if (draft.hasDate && draft.hasTime) SizedBox(height: tokens.space3),
          if (draft.hasTime) _TimeEntry(key: _timeKey, draft: draft),
        ],
      ),
    );
  }
}

/// A reason under a control: the error glyph and the words (never colour
/// alone). Not a live region: the picker announces it once itself.
class _Reason extends StatelessWidget {
  const _Reason(this.text);

  final String text;

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final l10n = _l10n(context);
    return Padding(
      padding: EdgeInsetsDirectional.only(top: tokens.space2),
      child: Semantics(
        container: true,
        label: '${l10n.kitFieldErrorLabel}: $text',
        child: ExcludeSemantics(
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Icon(
                AppIconography.error,
                size: tokens.iconSize(context, tokens.smallIconSize),
                color: tokens.roles.text1,
              ),
              SizedBox(width: tokens.space2),
              Expanded(
                child: KitText(
                  text,
                  role: KitTextRole.secondary,
                  tone: KitTextTone.primary,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

// ── The calendar ──────────────────────────────────────────────────────────

/// A month grid drawn by the kit: 7 columns in the locale's week order,
/// day cells of at least [KitTokens.minTarget], one Tab stop whose arrow
/// keys move the day.
class _Calendar extends StatefulWidget {
  const _Calendar({required this.draft});

  final _PickerDraft draft;

  @override
  State<_Calendar> createState() => _CalendarState();
}

class _CalendarState extends State<_Calendar> {
  _PickerDraft get draft => widget.draft;
  DateTime? _hovered;
  bool _ring = false;

  @override
  void initState() {
    super.initState();
    draft.gridNode.addListener(_onFocus);
    FocusManager.instance.addHighlightModeListener(_onHighlight);
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted && !draft.typed) draft.gridNode.requestFocus();
    });
  }

  @override
  void dispose() {
    draft.gridNode.removeListener(_onFocus);
    FocusManager.instance.removeHighlightModeListener(_onHighlight);
    super.dispose();
  }

  void _onFocus() => _onHighlight(FocusManager.instance.highlightMode);

  void _onHighlight(FocusHighlightMode mode) {
    final ring =
        draft.gridNode.hasFocus && mode == FocusHighlightMode.traditional;
    if (ring != _ring && mounted) setState(() => _ring = ring);
  }

  void _announceMonth() {
    _announce(
      context,
      MaterialLocalizations.of(context).formatMonthYear(draft.month),
    );
  }

  void _turn(int by) {
    draft.turnMonth(by);
    _announceMonth();
  }

  KeyEventResult _onKey(FocusNode node, KeyEvent event) {
    if (event is! KeyDownEvent && event is! KeyRepeatEvent) {
      return KeyEventResult.ignored;
    }
    final key = event.logicalKey;
    final rtl = Directionality.of(context) == TextDirection.rtl;
    final day = draft.focusedDay;
    DateTime? target;
    if (key == LogicalKeyboardKey.arrowLeft) {
      target = DateTime(day.year, day.month, day.day + (rtl ? 1 : -1));
    } else if (key == LogicalKeyboardKey.arrowRight) {
      target = DateTime(day.year, day.month, day.day + (rtl ? -1 : 1));
    } else if (key == LogicalKeyboardKey.arrowUp) {
      target = DateTime(day.year, day.month, day.day - 7);
    } else if (key == LogicalKeyboardKey.arrowDown) {
      target = DateTime(day.year, day.month, day.day + 7);
    } else if (key == LogicalKeyboardKey.pageUp ||
        key == LogicalKeyboardKey.pageDown) {
      final by = key == LogicalKeyboardKey.pageUp ? -1 : 1;
      final month = DateUtils.addMonthsToMonthDate(
        DateTime(day.year, day.month),
        by,
      );
      final days = DateUtils.getDaysInMonth(month.year, month.month);
      target = DateTime(month.year, month.month, math.min(day.day, days));
    } else if (key == LogicalKeyboardKey.home ||
        key == LogicalKeyboardKey.end) {
      final firstIndex = MaterialLocalizations.of(context).firstDayOfWeekIndex;
      final column = (day.weekday % 7 - firstIndex) % 7;
      target = key == LogicalKeyboardKey.home
          ? DateTime(day.year, day.month, day.day - column)
          : DateTime(day.year, day.month, day.day + 6 - column);
    } else if (key == LogicalKeyboardKey.enter ||
        key == LogicalKeyboardKey.numpadEnter ||
        key == LogicalKeyboardKey.space) {
      draft.pick(day);
      return KeyEventResult.handled;
    } else {
      return KeyEventResult.ignored;
    }
    target = DateUtils.dateOnly(target);
    if (draft.focusDay(target)) _announceMonth();
    return KeyEventResult.handled;
  }

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final localizations = MaterialLocalizations.of(context);
    final rtl = Directionality.of(context) == TextDirection.rtl;
    final reduced = KitMotion.reduced(context);
    final scaler = MediaQuery.textScalerOf(context);
    // A day cell is a 48 dp target at least, and grows with the text.
    final rowHeight = math.max(
      tokens.minTarget,
      scaler.scale(KitText.styleFor(KitTextRole.rowTitle).fontSize!) +
          tokens.space5,
    );
    final header = Row(
      children: [
        KitIconButton(
          icon: AppIconography.chevronLeft,
          tooltip: localizations.previousMonthTooltip,
          size: 20,
          onPressed: draft.canGoBack ? () => _turn(-1) : null,
        ),
        Expanded(
          child: KitText(
            localizations.formatMonthYear(draft.month),
            role: KitTextRole.label,
            tone: KitTextTone.primary,
            textAlign: TextAlign.center,
          ),
        ),
        KitIconButton(
          icon: AppIconography.chevronRight,
          tooltip: localizations.nextMonthTooltip,
          size: 20,
          onPressed: draft.canGoForward ? () => _turn(1) : null,
        ),
      ],
    );
    final firstIndex = localizations.firstDayOfWeekIndex;
    final weekdays = ExcludeSemantics(
      child: Row(
        children: [
          for (var i = 0; i < 7; i++)
            Expanded(
              child: Padding(
                padding: EdgeInsetsDirectional.symmetric(
                  vertical: tokens.space2,
                ),
                child: KitText(
                  localizations.narrowWeekdays[(firstIndex + i) % 7],
                  role: KitTextRole.secondary,
                  textAlign: TextAlign.center,
                  maxLines: 1,
                ),
              ),
            ),
        ],
      ),
    );
    final month = draft.month;
    final grid = KeyedSubtree(
      key: ValueKey(month),
      child: _MonthDays(
        draft: draft,
        month: month,
        rowHeight: rowHeight,
        hovered: _hovered,
        ring: _ring,
        onHover: (day) => setState(() => _hovered = day),
      ),
    );
    final forward = draft.monthStep > 0;
    // The new month comes in from the end side going forward (the start
    // side in RTL), and the old one leaves the other way (MOT-2: a slide,
    // never a fade-scale).
    final enterFrom = (forward ? 1.0 : -1.0) * (rtl ? -1 : 1);
    final Widget days = reduced
        ? grid
        : ClipRect(
            child: AnimatedSwitcher(
              duration: KitMotion.quick,
              switchInCurve: KitMotion.enter,
              switchOutCurve: KitMotion.exit,
              transitionBuilder: (child, animation) {
                final incoming = child.key == ValueKey(month);
                return SlideTransition(
                  position: Tween(
                    begin: Offset(incoming ? enterFrom : -enterFrom, 0),
                    end: Offset.zero,
                  ).animate(animation),
                  child: child,
                );
              },
              child: grid,
            ),
          );
    final reason = draft.gridReason;
    return Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        header,
        weekdays,
        Focus(focusNode: draft.gridNode, onKeyEvent: _onKey, child: days),
        if (reason != null) _Reason(reason),
      ],
    );
  }
}

/// The weeks of one month: only the rows it needs, so no blank week sits
/// between the days and the actions.
class _MonthDays extends StatelessWidget {
  const _MonthDays({
    required this.draft,
    required this.month,
    required this.rowHeight,
    required this.hovered,
    required this.ring,
    required this.onHover,
  });

  final _PickerDraft draft;
  final DateTime month;
  final double rowHeight;
  final DateTime? hovered;
  final bool ring;
  final ValueChanged<DateTime?> onHover;

  @override
  Widget build(BuildContext context) {
    final localizations = MaterialLocalizations.of(context);
    final offset =
        (DateTime(month.year, month.month).weekday % 7 -
            localizations.firstDayOfWeekIndex) %
        7;
    final count = DateUtils.getDaysInMonth(month.year, month.month);
    final today = DateUtils.dateOnly(clock.now());
    return Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        for (var week = 0; week < ((offset + count) / 7).ceil(); week++)
          SizedBox(
            height: rowHeight,
            child: Row(
              children: [
                for (var column = 0; column < 7; column++)
                  Expanded(
                    child: switch (week * 7 + column - offset + 1) {
                      final n when n >= 1 && n <= count => _DayCell(
                        day: DateTime(month.year, month.month, n),
                        draft: draft,
                        today: today,
                        hovered: hovered,
                        ring: ring,
                        onHover: onHover,
                      ),
                      _ => const SizedBox.shrink(),
                    },
                  ),
              ],
            ),
          ),
      ],
    );
  }
}

class _DayCell extends StatelessWidget {
  const _DayCell({
    required this.day,
    required this.draft,
    required this.today,
    required this.hovered,
    required this.ring,
    required this.onHover,
  });

  final DateTime day;
  final _PickerDraft draft;
  final DateTime today;
  final DateTime? hovered;
  final bool ring;
  final ValueChanged<DateTime?> onHover;

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final roles = tokens.roles;
    final localizations = MaterialLocalizations.of(context);
    final enabled = draft.canPick(day);
    final isSelected = DateUtils.isSameDay(day, draft.selected);
    final isToday = DateUtils.isSameDay(day, today);
    final isFocused = ring && DateUtils.isSameDay(day, draft.focusedDay);
    final isHovered = enabled && DateUtils.isSameDay(day, hovered);
    final label = isToday
        ? '${localizations.formatFullDate(day)}, ${localizations.currentDateLabel}'
        : localizations.formatFullDate(day);
    final Border? border = isFocused
        ? Border.all(
            color: roles.accent,
            width: KitTokens.focusRingWidth(context),
          )
        : isToday && !isSelected
        ? Border.all(
            color: roles.accent,
            width: KitTokens.hairlineWidth(context),
          )
        : null;
    final number = KitText(
      localizations.formatDecimal(day.day),
      role: KitTextRole.rowTitle,
      tabular: true,
      maxLines: 1,
      tone: isSelected
          ? KitTextTone.onAccent
          : enabled
          ? KitTextTone.primary
          : KitTextTone.tertiary,
    );
    return Semantics(
      container: true,
      button: true,
      enabled: enabled,
      selected: isSelected,
      label: label,
      onTap: enabled ? () => draft.pick(day) : null,
      child: ExcludeSemantics(
        child: MouseRegion(
          cursor: enabled ? SystemMouseCursors.click : MouseCursor.defer,
          onEnter: (_) => onHover(day),
          onExit: (_) => onHover(null),
          child: GestureDetector(
            behavior: HitTestBehavior.opaque,
            onTap: enabled ? () => draft.pick(day) : null,
            child: LayoutBuilder(
              builder: (context, constraints) {
                final side =
                    math.min(constraints.maxWidth, constraints.maxHeight) -
                    tokens.space1;
                return Center(
                  child: Container(
                    width: side,
                    height: side,
                    alignment: Alignment.center,
                    decoration: BoxDecoration(
                      shape: BoxShape.circle,
                      color: isSelected
                          ? roles.accent
                          : isHovered
                          ? roles.surface3
                          : null,
                      border: border,
                    ),
                    child: FittedBox(fit: BoxFit.scaleDown, child: number),
                  ),
                );
              },
            ),
          ),
        ),
      ),
    );
  }
}

/// The typed date: a [KitField] that reads the locale's short date.
class _TypedDate extends StatelessWidget {
  const _TypedDate({required this.draft});

  final _PickerDraft draft;

  @override
  Widget build(BuildContext context) {
    final l10n = _l10n(context);
    final example = DateFormat.yMd(
      _localeTag(context),
    ).format(draft.selected ?? draft.focusedDay);
    return KitField(
      label: l10n.kitDateField,
      controller: draft.dateText,
      focusNode: draft.dateNode,
      hint: l10n.kitDateFormatHint(example),
      error: draft.dateError,
      textInputAction: draft.hasTime
          ? TextInputAction.next
          : TextInputAction.done,
      onChanged: (_) {
        if (draft.dateError != null) {
          draft.dateError = null;
          draft.changed();
        }
      },
      onSubmitted: (_) => draft.hasTime
          ? draft.hourNode.requestFocus()
          : draft.onSubmit?.call(),
    );
  }
}

// ── The time ──────────────────────────────────────────────────────────────

/// Hour and minute as typed numbers, left to right as clocks are written,
/// and AM/PM on a 12-hour clock.
class _TimeEntry extends StatelessWidget {
  const _TimeEntry({super.key, required this.draft});

  final _PickerDraft draft;

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final l10n = _l10n(context);
    final localizations = MaterialLocalizations.of(context);
    void clear() {
      if (draft.hourError != null ||
          draft.minuteError != null ||
          draft.timeReason != null) {
        draft.hourError = null;
        draft.minuteError = null;
        draft.timeReason = null;
        draft.changed();
      }
    }

    final hour = KitField(
      label: l10n.kitTimeHour,
      kind: KitFieldKind.number,
      controller: draft.hourText,
      focusNode: draft.hourNode,
      selectAllOnFocus: true,
      inputFormatters: [LengthLimitingTextInputFormatter(2)],
      error: draft.hourError,
      textInputAction: TextInputAction.next,
      onChanged: (_) => clear(),
      onSubmitted: (_) => draft.minuteNode.requestFocus(),
    );
    final minute = KitField(
      label: l10n.kitTimeMinute,
      kind: KitFieldKind.number,
      controller: draft.minuteText,
      focusNode: draft.minuteNode,
      selectAllOnFocus: true,
      inputFormatters: [LengthLimitingTextInputFormatter(2)],
      error: draft.minuteError,
      textInputAction: TextInputAction.done,
      onChanged: (_) => clear(),
      onSubmitted: (_) => draft.onSubmit?.call(),
    );
    final scaler = MediaQuery.textScalerOf(context);
    final reason = draft.timeReason;
    return Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        LayoutBuilder(
          builder: (context, constraints) {
            // Side by side while each field keeps room for its label at
            // the person's text size; stacked otherwise.
            final fits =
                constraints.maxWidth / 2 - tokens.space3 >=
                scaler.scale(tokens.minTarget * 3);
            if (!fits) {
              return Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  hour,
                  SizedBox(height: tokens.space3),
                  minute,
                ],
              );
            }
            return Row(
              textDirection: TextDirection.ltr,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Expanded(child: hour),
                SizedBox(width: tokens.space3),
                Expanded(child: minute),
              ],
            );
          },
        ),
        if (!draft.use24) ...[
          SizedBox(height: tokens.space3),
          KitSegmented<DayPeriod>(
            semanticsLabel: l10n.kitTimePeriod,
            selected: draft.period,
            onChanged: draft.setPeriod,
            segments: [
              KitSegment(
                value: DayPeriod.am,
                label: localizations.anteMeridiemAbbreviation,
              ),
              KitSegment(
                value: DayPeriod.pm,
                label: localizations.postMeridiemAbbreviation,
              ),
            ],
          ),
        ],
        if (reason != null) _Reason(reason),
      ],
    );
  }
}

// ── The row ───────────────────────────────────────────────────────────────

/// The row a form or a settings list shows for a date or time value:
/// "Due date · Oct 3, 2026 ›". A tap opens the matching picker; the chosen
/// value is passed to `onChanged`. [clearable] adds a labelled Clear button.
///
/// It is a [KitRow]; put it in a [KitRowGroup]. A null `onChanged` disables
/// it, and then [disabledReason] is required and shown on the row.
///
/// States: disabled.
class KitDateTimeRow extends StatelessWidget {
  const KitDateTimeRow.date({
    super.key,
    required this.title,
    required DateTime? value,
    required ValueChanged<DateTime?>? onChanged,
    this.pickerTitle,
    this.first,
    this.last,
    this.selectable,
    this.placeholder,
    this.clearable = false,
    this.supporting,
    this.error,
    this.disabledReason,
    this.rowKey,
    this.clearKey,
  }) : assert(
         onChanged != null || disabledReason != null,
         'KitDateTimeRow: a disabled row says why (STATE-8).',
       ),
       _mode = KitDateTimeMode.date,
       _date = value,
       _time = null,
       _onDate = onChanged,
       _onTime = null;

  const KitDateTimeRow.time({
    super.key,
    required this.title,
    required TimeOfDay? value,
    required ValueChanged<TimeOfDay?>? onChanged,
    this.pickerTitle,
    this.placeholder,
    this.clearable = false,
    this.supporting,
    this.error,
    this.disabledReason,
    this.rowKey,
    this.clearKey,
  }) : assert(
         onChanged != null || disabledReason != null,
         'KitDateTimeRow: a disabled row says why (STATE-8).',
       ),
       _mode = KitDateTimeMode.time,
       _date = null,
       _time = value,
       _onDate = null,
       _onTime = onChanged,
       first = null,
       last = null,
       selectable = null;

  const KitDateTimeRow.dateAndTime({
    super.key,
    required this.title,
    required DateTime? value,
    required ValueChanged<DateTime?>? onChanged,
    this.pickerTitle,
    this.first,
    this.last,
    this.selectable,
    this.placeholder,
    this.clearable = false,
    this.supporting,
    this.error,
    this.disabledReason,
    this.rowKey,
    this.clearKey,
  }) : assert(
         onChanged != null || disabledReason != null,
         'KitDateTimeRow: a disabled row says why (STATE-8).',
       ),
       _mode = KitDateTimeMode.dateAndTime,
       _date = value,
       _time = null,
       _onDate = onChanged,
       _onTime = null;

  /// What the value is: "Due date".
  final String title;

  /// The picker's title; null uses [title].
  final String? pickerTitle;
  final DateTime? first;
  final DateTime? last;
  final bool Function(DateTime day)? selectable;

  /// Shown when there is no value; null shows "Not set".
  final String? placeholder;

  /// Adds a labelled Clear button while a value is set.
  final bool clearable;

  /// A muted line under the title.
  final String? supporting;

  /// A form validation message, shown under the row.
  final String? error;

  /// Why the row is disabled; required when `onChanged` is null.
  final String? disabledReason;
  final Key? rowKey;
  final Key? clearKey;

  final KitDateTimeMode _mode;
  final DateTime? _date;
  final TimeOfDay? _time;
  final ValueChanged<DateTime?>? _onDate;
  final ValueChanged<TimeOfDay?>? _onTime;

  bool get _enabled => _onDate != null || _onTime != null;
  bool get _hasValue => _date != null || _time != null;

  String? _formatted(BuildContext context) {
    final tag = _localeTag(context);
    final use24 = MediaQuery.alwaysUse24HourFormatOf(context);
    final localizations = MaterialLocalizations.of(context);
    switch (_mode) {
      case KitDateTimeMode.date:
        final date = _date;
        return date == null ? null : DateFormat.yMMMd(tag).format(date);
      case KitDateTimeMode.time:
        final time = _time;
        return time == null
            ? null
            : localizations.formatTimeOfDay(time, alwaysUse24HourFormat: use24);
      case KitDateTimeMode.dateAndTime:
        final date = _date;
        if (date == null) return null;
        final format = DateFormat.yMMMd(tag);
        final twelve =
            !use24 &&
            hourFormat(of: localizations.timeOfDayFormat()) == HourFormat.h;
        return (twelve ? format.add_jm() : format.add_Hm()).format(date);
    }
  }

  Future<void> _open(BuildContext context) async {
    final pickTitle = pickerTitle ?? title;
    switch (_mode) {
      case KitDateTimeMode.date:
        final picked = await showKitDatePicker(
          context,
          title: pickTitle,
          initial: _date,
          first: first,
          last: last,
          selectable: selectable,
        );
        if (picked != null) _onDate?.call(picked);
      case KitDateTimeMode.time:
        final picked = await showKitTimePicker(
          context,
          title: pickTitle,
          initial: _time ?? TimeOfDay.fromDateTime(clock.now()),
        );
        if (picked != null) _onTime?.call(picked);
      case KitDateTimeMode.dateAndTime:
        final picked = await showKitDateTimePicker(
          context,
          title: pickTitle,
          initial: _date,
          first: first,
          last: last,
          selectable: selectable,
        );
        if (picked != null) _onDate?.call(picked);
    }
  }

  void _clear() {
    _onDate?.call(null);
    _onTime?.call(null);
  }

  @override
  Widget build(BuildContext context) =>
      KitMaxWidth.list(child: _buildContent(context));

  Widget _buildContent(BuildContext context) {
    final l10n = _l10n(context);
    final tokens = KitTokens.of(context);
    final value = _formatted(context) ?? placeholder ?? l10n.kitDateTimeNotSet;
    final showClear = clearable && _enabled && _hasValue;
    final error = this.error;
    final supporting = this.supporting;
    final row = KitRow(
      key: rowKey,
      title: title,
      supporting: supporting == null ? null : TextSpan(text: supporting),
      enabled: _enabled,
      disabledReason: disabledReason,
      onTap: _enabled ? () => unawaited(_open(context)) : null,
      trailing: KitRowValue(value, chevron: _enabled && !showClear),
      below: error == null
          ? null
          : Semantics(
              container: true,
              liveRegion: true,
              label: '${l10n.kitFieldErrorLabel}: $error',
              child: ExcludeSemantics(
                child: Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Icon(
                      AppIconography.error,
                      size: tokens.iconSize(context, tokens.smallIconSize),
                      color: tokens.roles.text1,
                    ),
                    SizedBox(width: tokens.space1),
                    Expanded(
                      child: KitText(
                        error,
                        role: KitTextRole.secondary,
                        tone: KitTextTone.primary,
                      ),
                    ),
                  ],
                ),
              ),
            ),
    );
    if (!showClear) return row;
    // Clear sits beside the row's tap target, not inside it, so the row
    // still reads as one button ("Due date, Oct 3, 2026") and Clear as
    // another.
    return Row(
      children: [
        Expanded(child: row),
        Padding(
          padding: EdgeInsetsDirectional.only(end: tokens.space1),
          child: KitIconButton(
            key: clearKey,
            icon: AppIconography.close,
            tooltip: l10n.kitDateTimeClear(title),
            size: 20,
            onPressed: _clear,
          ),
        ),
      ],
    );
  }
}
