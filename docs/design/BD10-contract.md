# BD10 native notification language

`ConnectionController.setAppLocale(Locale?)` saves the acknowledged `oc.appLocale` choice before calling `BackgroundLiveController.refreshNativeLocale()`; `oc/background.refreshNativeLocale` takes no arguments and returns `{updated: bool}`. No UI hook or additional saved language is needed.

`NativeStrings.get/quantity` read only `FlutterSharedPreferences/flutter.oc.appLocale` per render: English/Arabic resources follow that choice; other valid shipped languages use English resources. System mode follows the first app-supported preferred system language, with English resource fallback. Failed cosmetic refresh never rejects a saved language; the next native render reads it again.

Dart-supplied setup/server/team-progress captions remain caller-authored: supply `AppLocalizations` resolved from the chosen app language (`ChannelSetupEngine(strings: () => chosenStrings)`), including when updating a running task. Existing event/result notifications retain their posted language until reposted; the refresh immediately rebuilds live/phone copy and relabels existing native channels without changing service state or notification permission choices.
