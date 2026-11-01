# :android:widgets

The home-screen widgets (plan 11 §11; plan 15 S4-WIDGETS; unit WIDGETS, report `docs/native/units/WIDGETS.md`).
RemoteViews at the switch (ADR §3.3): the Flutter app's providers, layouts, binders, size policy and `previewImage`
PNGs, copied with every provider class name unchanged, fed from the native engines.

## Layout

| Path | What it holds |
|---|---|
| `src/main/kotlin/io/github/eslamasabry/opencode_mobile/` | 35 copies from `android/app/src/main/kotlin/…`, original package (the OS stores these FQCNs). |
| `src/main/kotlin/io/github/eslamasabry/oc/android/widgets/` | Native-authored: the seams (`FrozenComponents.kt`, `WidgetActionRuntime.kt`, `NativeStrings.kt`), the engine feed (`WidgetFeed.kt`, `LiveWidgetDispatch.kt`) and the publish side of `WidgetSurface` (`AndroidWidgetSurface.kt`). |
| `src/main/res/` | Widget layouts, provider XML, drawables, the 14 preview PNGs, `colors.xml` (both themes) and `widget_v2_strings.xml` verbatim; `strings.xml` (en, ar) and `styles.xml` are verbatim subsets (only what the copies read); `widget_seams.xml` is native-authored. |
| `src/main/AndroidManifest.xml` | The seven provider receivers and `WidgetActionReceiver`, as in the Flutter manifest but with the FQCN written out (a library resolves `.Name` against its own namespace). |
| `src/test/kotlin/io/github/eslamasabry/opencode_mobile/` | The 12 JVM test classes plan 11 §4.3 lists for widgets. |
| `src/test/kotlin/io/github/eslamasabry/oc/android/widgets/` | Native-authored, row-named tests of the seams, the feed and the surface. |
| `TWINS-rows.md` | The rows for `native/TWINS.md` (coordinator-owned): source, last source commit, and whether the copy is byte-identical or carries a seam. |

## The seam rule (plan 11 §4.4)

Copied files change only at the compile seams. Every seam edit in a copied file is one of these:

| Seam | Edit in the copy | Why |
|---|---|---|
| W-R | `import io.github.eslamasabry.oc.android.widgets.R` | The module's `R` lives in its namespace. An `R` shim in the app package would duplicate `:androidApp`'s `R` class in the dex. |
| E4 | `import io.github.eslamasabry.oc.android.widgets.NativeStrings` | `NativeStrings` is copied with only its package changed: `:android:linux` already holds one in the app package. Replaced by the Words resolver with S11-8. |
| E5 | `import …widgets.MainActivity`; `Intent(ctx, MainActivity::class.java)` → `Intent().setClassName(ctx, MainActivity.COMPONENT)` | The launcher activity is a `:androidApp` shell. Same component, same package, built from the frozen FQCN. |
| E7 | `import …widgets.BackgroundConnectionService` (and the class literal → `setClassName`) | The service is a `:androidApp` shell; `active`, `widgetActionPending` and `dispatchWidgetAction` read `WidgetFeed`. The dispatch body (`LiveWidgetDispatch`) is the Flutter body with the MethodChannel replaced by `WidgetLiveDispatcher`. |
| E7 | `import …widgets.StatusNotification`, `…WidgetOverlayActivity`, `…WidgetRouteActivity` (class literal → `setClassName`) | Frozen constants and component names only. |
| E10 | `import …widgets.BuiltinLinux`, `…BuiltinServerService` | "This phone's Linux" reads `WidgetFeed.phoneServer`, derived from `:engine:runtime`'s `RuntimeStatus`. |
| W-RT | `import …widgets.WidgetActionRuntime` | The headless Dart engine is deleted (plan 11 §4.2); the seam keeps its constructor and hands the run to the bound `WidgetBackgroundRunner` (`WidgetActionRunner`/`WidgetStatusRunner` of `:engine:attention`). |

Three test edits, each a seam: `R` imports in `TwoWidgetsRenderTest` and `WidgetCardRenderTest` (W-R);
`WidgetProvidersTest` reads receiver names with `substringAfterLast(".")` instead of `removePrefix(".")` (the library
manifest writes FQCNs); `WidgetActionLeaseTest` drops its two lines about `BackgroundConnectionService.kt`, which moves
to `:android:background` (S4-BG) and is not in this module.

## How the engines feed the widgets

- `AndroidWidgetSurface.publish(snapshotJson, profileId, targetJson)`: `oc.widgetTarget.<p>` first, then
  `oc.widgetSessions`, `commit()`, redraw; unchanged bytes are neither written nor redrawn. `redrawAll`,
  `invalidateConnection` and `clearProfile` complete plan 11 §11.3's `WidgetSurface`; S4-ATTNE adapts it to the
  `:engine:attention` interface.
- `WidgetFeed.onRuntimeStatus(context, RuntimeStatus)`: the Status widget's Linux line (`Off | Running | Restoring`);
  redraws only on a change.
- `WidgetFeed.onConnection(context, active)`: the end of the live link marks the snapshot offline (titles kept); a
  start only redraws.
- `WidgetFeed.liveDispatcher` / `backgroundRunner`: bound by the app graph to the live connection and to
  `WidgetActionRunner` / `WidgetStatusRunner`.

## Checks

```bash
./gradlew :android:widgets:testDebugUnitTest
```
