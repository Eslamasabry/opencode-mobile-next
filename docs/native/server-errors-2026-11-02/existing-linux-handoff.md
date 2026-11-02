# Reusing the phone’s existing Linux

Reviewed from the GLM 5.3 phone-handoff draft by the coordinator. This is a source feasibility result, not a device qualification or implemented handoff. No phone data or credentials were inspected.

The current native preview can reconnect automatically **after a compatible server profile and its authorized credentials have been saved once**. It cannot currently discover/import the Flutter app’s private Linux connection automatically.

## Current boundary

- The preview has its own `.native` package and private stores; see [application configuration](../../../native/build-logic/convention/src/main/kotlin/oc/buildlogic/OcAndroidApplicationPlugin.kt) and [PreviewStores](../../../native/androidApp/src/main/kotlin/io/github/eslamasabry/opencode_mobile/graph/PreviewStores.kt). It does not adopt the Flutter package’s data.
- The preview’s [app dependencies](../../../native/androidApp/build.gradle.kts) omit the Linux host and runtime modules. Adding those modules alone would not give a separate Android package access to Flutter-owned files.
- In the inspected Flutter source, `MainActivity.kt` registers `builtin_linux` and its credential operation on an in-process MethodChannel. The Flutter manifest keeps `BuiltinServerService` and `SetupService` unexported and declares no connection credential provider. No current cross-package discovery/credential contract was found.
- Flutter `lib/builtin/builtin_linux.dart` defines a default OC2 endpoint of `http://127.0.0.1:4097` with username `opencode`. `lib/builtin/builtin_server.dart` creates a random password and stores it privately per profile. This default is source evidence, not proof of what is running on the owner’s phone.

The Flutter files above were inspected in the existing Flutter checkout and supplied to the isolated GLM worker as read-only references. The coordinator removed unverified UI instructions, a setup re-run suggestion, and an incorrect credential-file attribution from the original worker draft. Its original patch remains in machine-local evidence.

## What can work without changing either installation

If the owner supplies the verified endpoint and credentials for an already-running compatible OC2 server, the preview’s existing Servers screen can save that connection. On the same phone, its loopback address can be used as a client connection; the actual phone path remains untested. [PreviewRuntime initialization](../../../native/androidApp/src/main/kotlin/io/github/eslamasabry/opencode_mobile/graph/PreviewRuntime.kt) restores the active saved profile and reconnects at app startup. The preview does not thereby own, start or wake the other package’s Linux server.

An OC1-only server is not made compatible by entering its address. Do not switch engines, migrate history, reinstall Linux, or alter the OC1 database to make a connection test pass. No credential-reveal path in the existing Flutter UI has been qualified here.

## Remaining integration steps

For automatic discovery/import in the separate preview, five concrete steps remain:

1. Define a callable handoff in the owning app: explicit owner consent, exact requesting package/signature identity, bounded current server facts and compatibility, scoped credential transfer, expiry/replay protection and revocation. No such endpoint currently exists.
2. Implement and independently test that owner-side handoff; never export a general credential service or include credentials in URLs, logs or public descriptors.
3. Implement preview-side admission, profile persistence and deletion using the verified handoff, with no duplicate server installation and no access outside its authority.
4. Integrate owner-controlled start/wake/demand and stopped/refused/stale-server states. An unknown or stopped owner is not proof that Linux is absent.
5. Exercise allowed and denied real device actions, expiry, restart, removal and cross-package isolation. Static readback is not runtime proof.

The planned **same-package native replacement** follows a different path: preserve the existing package/signing identity, bind the actual runtime and adopted stores, then complete migration/restore/device gates before an approved replacement release. See [the migration plan](../../design/native-rewrite-2026-11-01/plan/05-data-migration.md). It is not qualified by the separate preview APK.

The current connection-diagnostics slice implements neither handoff nor migration. It exposes the safe failure category/stage so the present phone connection problem can be identified first.
