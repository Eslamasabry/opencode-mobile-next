# E8 desktop runtime handoff

Implemented in `lib/ui/desktop/`: focusable context-menu regions with visible
keyboard focus, Shift+F10/Menu activation, explicit popup focus, overlay-local
placement, duplicate-open protection, and no action after the owner unmounts.
Unexpected drop-handler failures now show a generic keyboard-dismissible dialog
instead of escaping the unawaited callback; overlapping drops are ignored while
a handler/dialog is active. Recovery directs users to the existing keyboard-
accessible Add → Attach file path and warns against duplicating partial results.

Geometry scope blocker: the implementation is `lib/desktop/window_state.dart`,
not the permitted (absent) `lib/platform/desktop_window.dart`. It already restores
saved bounds against current work areas, rehomes disconnected monitors, and
falls back on storage/display exceptions; defaults remain 900×700 / 480×600.
Lead follow-up: reject non-finite display rectangles and avoid treating unknown
display positions as origin; first-launch defaults also need fitting to small
work areas. No geometry or native files were edited.

Pending verification (not performed):
- Linux/Windows: Tab to a region, Shift+F10/Menu, arrows, Enter, Escape, focus
  return, nested controls, right click near each screen edge, enlarged text.
- Throw during drop/read, including partial success; dismiss with Enter/Escape,
  use Add → Attach file by keyboard, navigate away during processing, drop again.
- Restart after monitor removal/resolution or scale change; missing, malformed,
  unavailable geometry/storage; maximize/unmaximize; minimum-size work areas.
- Confirm Linux/Windows registrations and Android/iOS feature gates unchanged.

Lead-owned localization copy (suggested keys):
- `desktopDropFailedTitle`: “Could not attach dropped files”
- `desktopDropFailedRecovery`: “Check the attachments already added before trying
  again. You can also use the keyboard to open Add, then Attach file.”
- `desktopContextMenuShortcutKeys`: “Right click / Shift + F10 / Menu”

Only Dart source formatting was run. No tests, analyzer, builds, installation,
device commands, signing, or commits; native/hardware validation remains undone.
