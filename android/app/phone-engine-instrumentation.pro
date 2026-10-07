# Explicit QA builds only: keep the ABI shared by separately shrunk app/test APKs.
# Native proofs use the same packaged executables and hashes as other releases.
-keep class kotlin.** { *; }
-keep class io.github.eslamasabry.opencode_mobile.** { *; }
# BD9 awaits this public result bridge in release AOT without a VM service.
-keep class dev.flutter.plugins.integration_test.** { *; }
# The separately shrunk test APK calls the public view/engine/plugin ABI.
# App-only R8 otherwise renames or inlines those methods before tests see them.
-keep class io.flutter.embedding.android.FlutterActivity { *; }
-keep class io.flutter.embedding.android.FlutterView { *; }
-keep class io.flutter.embedding.engine.FlutterEngine { *; }
-keep class io.flutter.embedding.engine.plugins.** { *; }
-keep interface io.flutter.embedding.engine.plugins.** { *; }
